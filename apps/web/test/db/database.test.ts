import { execFileSync } from 'node:child_process'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import pg, { type Client } from 'pg'
import { afterAll, beforeAll, describe, expect, it } from 'vitest'

/**
 * Runs the real migrations and SQL functions against Postgres. Set TEST_DATABASE_URL to a throwaway
 * database whose name contains "test" (its public and auth schemas are dropped first), for example
 * `postgres://postgres:postgres@localhost:5432/streamtrack_test`. Skipped when unset.
 */
const url = process.env.TEST_DATABASE_URL
const WEB = fileURLToPath(new URL('../..', import.meta.url))

const USER_A = '00000000-0000-4000-8000-00000000000a'
const USER_B = '00000000-0000-4000-8000-00000000000b'
const hash = (n: number) => n.toString(16).padStart(64, '0')

let db: Client

const connect = async () => {
  const client = new pg.Client({ connectionString: url ?? '' })
  await client.connect()
  return client
}

/** Runs `task` as a signed-in user, the way PostgREST does, and rolls everything back afterwards. */
async function asUser<T>(userId: string, task: (client: Client) => Promise<T>): Promise<T> {
  const client = await connect()
  try {
    await client.query('begin')
    await client.query('set local role authenticated')
    await client.query("select set_config('request.jwt.claim.sub', $1, true)", [userId])
    return await task(client)
  } finally {
    await client.query('rollback')
    await client.end()
  }
}

/** The SQL error a statement fails with, or null. */
async function failure(client: Client, sql: string, values: readonly unknown[] = []): Promise<string | null> {
  await client.query('savepoint attempt')
  try {
    await client.query(sql, values)
    await client.query('release savepoint attempt')
    return null
  } catch (error) {
    await client.query('rollback to savepoint attempt')
    return error instanceof Error ? error.message : String(error)
  }
}

async function addDevice(userId: string, tokenHash: string | null, extra = ''): Promise<string> {
  const { rows } = await db.query<{ id: string }>(
    `insert into public.devices (user_id, name, token_hash${extra ? ', created_at, last_seen_at' : ''})
     values ($1, 'TV', $2${extra}) returning id`,
    [userId, tokenHash],
  )
  return rows[0]?.id ?? ''
}

const usage = (foregroundMs: number, label?: string) =>
  JSON.stringify([{ day: '2026-11-08', hour: 20, packageName: 'com.netflix.ninja', label, foregroundMs }])
const keys = (count: number) => JSON.stringify([{ day: '2026-11-08', hour: 20, keyClass: 'dpad', count }])
const screen = (ms: number) => JSON.stringify([{ day: '2026-11-08', hour: 20, screenOnMs: ms }])

const ingest = (client: Client, deviceId: string, n: number, label?: string) =>
  client.query('select public.ingest_sync($1, $2, $3::jsonb, $4::jsonb, $5::jsonb)', [
    deviceId,
    'America/Los_Angeles',
    usage(n * 1000, label),
    keys(n),
    screen(n * 2000),
  ])

describe.skipIf(url === undefined)('database', () => {
  beforeAll(async () => {
    db = await connect()
    const { rows } = await db.query<{ name: string }>('select current_database() as name')
    if (!rows[0]?.name.includes('test')) throw new Error('TEST_DATABASE_URL must name a database containing "test"')
    await db.query('drop schema if exists public cascade; drop schema if exists auth cascade; create schema public')
    await db.query(readFileSync(`${WEB}/test/db/supabase-stub.sql`, 'utf8'))
    const output = execFileSync('node', ['scripts/migrate.mjs'], {
      cwd: WEB,
      env: { ...process.env, DATABASE_URL: url, POSTGRES_URL_NON_POOLING: '', VERCEL_ENV: '' },
      encoding: 'utf8',
    })
    expect(output).toContain('migration(s) applied')
    await db.query(
      `insert into auth.users (id, email, raw_user_meta_data) values
         ($1, 'a@example.com', '{"timezone": "America/Los_Angeles"}'),
         ($2, 'b@example.com', '{"timezone": "Mars/Olympus"}')`,
      [USER_A, USER_B],
    )
  }, 60_000)

  afterAll(async () => {
    await db.end()
  })

  it('records every migration and is a no-op the second time', () => {
    const output = execFileSync('node', ['scripts/migrate.mjs'], {
      cwd: WEB,
      env: { ...process.env, DATABASE_URL: url, VERCEL_ENV: '' },
      encoding: 'utf8',
    })
    expect(output).toContain('database is up to date')
  })

  it('takes the sign-up time zone only when Postgres knows it', async () => {
    const { rows } = await db.query<{ user_id: string; timezone: string | null }>(
      'select user_id, timezone from public.settings order by user_id',
    )
    expect(rows).toEqual([
      { user_id: USER_A, timezone: 'America/Los_Angeles' },
      { user_id: USER_B, timezone: null },
    ])
    // The dashboard sets the browser's zone only while none is chosen.
    await asUser(USER_B, async (client) => {
      const set = await client.query("update public.settings set timezone = 'Asia/Tokyo' where timezone is null")
      expect(set.rowCount).toBe(1)
      const again = await client.query("update public.settings set timezone = 'UTC' where timezone is null")
      expect(again.rowCount).toBe(0)
    })
  })

  it('upserts re-sent batches instead of adding to them', async () => {
    const device = await addDevice(USER_A, hash(1))
    await ingest(db, device, 5, 'Netflix')
    await ingest(db, device, 7)
    await ingest(db, device, 7)
    const { rows } = await db.query(
      `select (select count(*)::int from public.usage_buckets where device_id = $1) as usage_rows,
              (select foreground_ms from public.usage_buckets where device_id = $1) as foreground_ms,
              (select count from public.key_buckets where device_id = $1) as key_count,
              (select screen_on_ms from public.screen_buckets where device_id = $1) as screen_ms,
              (select label from public.apps where user_id = $2) as label,
              (select timezone from public.devices where id = $1) as timezone`,
      [device, USER_A],
    )
    expect(rows[0]).toEqual({
      usage_rows: 1,
      foreground_ms: 7000,
      key_count: 7,
      screen_ms: 14000,
      label: 'Netflix',
      timezone: 'America/Los_Angeles',
    })
  })

  it('refuses to ingest for a revoked device and forgets its token', async () => {
    const device = await addDevice(USER_A, hash(2))
    await db.query('update public.devices set revoked_at = now() where id = $1', [device])
    await expect(ingest(db, device, 1)).rejects.toThrow(/unknown or revoked device/)
    const { rows } = await db.query('select token_hash from public.devices where id = $1', [device])
    expect(rows[0]).toEqual({ token_hash: null })
    await db.query('update public.devices set revoked_at = null where id = $1', [device])
    const after = await db.query('select revoked_at is not null as revoked from public.devices where id = $1', [device])
    expect(after.rows[0]).toEqual({ revoked: true })
  })

  it('lets exactly one of two concurrent claims and deliveries win', async () => {
    const { rows } = await db.query<{ id: string }>(
      `insert into public.pairing_codes (code_hash, poll_token_hash, device_name, expires_at)
       values ($1, $2, 'Bedroom TV', now() + interval '10 minutes') returning id`,
      [hash(100), hash(101)],
    )
    const pairing = rows[0]?.id
    const clients = await Promise.all([connect(), connect()])
    try {
      const claims = await Promise.all(
        clients.map((c, i) =>
          c.query<{ device: string | null }>('select public.claim_pairing($1, $2) as device', [
            pairing,
            i === 0 ? USER_A : USER_B,
          ]),
        ),
      )
      expect(claims.map((r) => r.rows[0]?.device).filter(Boolean)).toHaveLength(1)
      const deliveries = await Promise.all(
        clients.map((c, i) =>
          c.query<{ ok: boolean }>('select public.deliver_pairing($1, $2) as ok', [pairing, hash(200 + i)]),
        ),
      )
      expect(deliveries.map((r) => r.rows[0]?.ok).sort()).toEqual([false, true])
    } finally {
      await Promise.all(clients.map((c) => c.end()))
    }
    const devices = await db.query("select count(*)::int as n from public.devices where name = 'Bedroom TV'")
    expect(devices.rows[0]).toEqual({ n: 1 })
  })

  it('confirms a PIN reset only for the owner, once, and hands it to the TV once', async () => {
    const device = await addDevice(USER_A, hash(310))
    const call = async <T>(sql: string, values: unknown[]) => (await db.query<{ v: T }>(sql, values)).rows[0]?.v
    const start = (code: number, poll: number, ttl = '10 minutes') =>
      call<boolean>(`select public.start_pin_reset($1, $2, $3, now() + interval '${ttl}') as v`, [
        device,
        hash(code),
        hash(poll),
      ])
    const requestId = async (code: number) =>
      (await db.query<{ id: string }>('select id from public.pin_reset_requests where code_hash = $1', [hash(code)]))
        .rows[0]?.id
    const confirm = (id: string | undefined, user: string) =>
      call<boolean>('select public.confirm_pin_reset($1, $2) as v', [id, user])
    const consume = (id: string | undefined) => call<boolean>('select public.consume_pin_reset($1) as v', [id])

    expect(await start(320, 321)).toBe(true)
    const first = await requestId(320)
    expect(await confirm(first, USER_B)).toBe(false)
    expect(await consume(first)).toBe(false)

    // A newer request replaces the open one, so the old code is gone.
    expect(await start(322, 323)).toBe(true)
    expect(await requestId(320)).toBeUndefined()
    const second = await requestId(322)
    const other = await addDevice(USER_B, hash(311))
    const clash = await call<boolean>("select public.start_pin_reset($1, $2, $3, now() + interval '10 minutes') as v", [
      other,
      hash(322),
      hash(325),
    ])
    expect(clash).toBe(false)

    expect(await confirm(second, USER_A)).toBe(true)
    expect(await confirm(second, USER_A)).toBe(false)
    expect(await consume(second)).toBe(true)
    expect(await consume(second)).toBe(false)
  })

  it('refuses to confirm an expired PIN reset or one of a revoked device', async () => {
    const device = await addDevice(USER_A, hash(330))
    await db.query(
      `insert into public.pin_reset_requests (device_id, code_hash, poll_token_hash, expires_at)
       values ($1, $2, $3, now() - interval '1 minute'), ($1, $4, $5, now() + interval '10 minutes')`,
      [device, hash(331), hash(332), hash(333), hash(334)],
    )
    const id = async (code: number) =>
      (await db.query<{ id: string }>('select id from public.pin_reset_requests where code_hash = $1', [hash(code)]))
        .rows[0]?.id
    const confirm = async (requestId: string | undefined) =>
      (await db.query<{ v: boolean }>('select public.confirm_pin_reset($1, $2) as v', [requestId, USER_A])).rows[0]?.v
    expect(await confirm(await id(331))).toBe(false)
    await db.query('update public.devices set revoked_at = now() where id = $1', [device])
    expect(await confirm(await id(333))).toBe(false)
  })

  it('keeps PIN reset requests away from signed-in users', async () => {
    await asUser(USER_A, async (client) => {
      expect(await failure(client, 'select * from public.pin_reset_requests')).toMatch(/permission denied/)
      expect(await failure(client, 'select public.confirm_pin_reset(gen_random_uuid(), $1)', [USER_A])).toMatch(
        /permission denied/,
      )
    })
  })

  it('counts rate limits per key and starts over in a new window', async () => {
    const hit = async (key: string) =>
      (await db.query<{ ok: boolean }>('select public.hit_rate_limit($1, 2, 600) as ok', [key])).rows[0]?.ok
    expect([await hit('k1'), await hit('k1'), await hit('k1')]).toEqual([true, true, false])
    expect(await hit('k2')).toBe(true)
    await db.query("update public.rate_limits set window_start = window_start - interval '600 seconds'")
    expect(await hit('k1')).toBe(true)
  })

  it('keeps users apart through row level security', async () => {
    const own = await addDevice(USER_B, hash(3))
    await ingest(db, own, 3, 'Netflix')
    await asUser(USER_A, async (client) => {
      const devices = await client.query<{ user_id: string }>('select user_id from public.devices')
      expect(devices.rows.every((r) => r.user_id === USER_A)).toBe(true)
      const foreign = await client.query('select * from public.usage_buckets where device_id = $1', [own])
      expect(foreign.rowCount).toBe(0)
      const apps = await client.query('select * from public.device_apps where device_id = $1', [own])
      expect(apps.rowCount).toBe(0)
      expect((await client.query("update public.devices set name = 'Mine' where id = $1", [own])).rowCount).toBe(0)
      expect((await client.query('delete from public.screen_buckets where device_id = $1', [own])).rowCount).toBe(0)
      expect(await failure(client, 'update public.devices set token_hash = $1', [hash(9)])).toMatch(/permission denied/)
      expect(
        await failure(
          client,
          `insert into public.usage_buckets (device_id, user_id, day, hour, package_name, foreground_ms)
           values ($1, $2, '2026-11-08', 1, 'a.b', 1)`,
          [own, USER_A],
        ),
      ).toMatch(/permission denied/)
      expect(await failure(client, "select public.ingest_sync($1, 'UTC', '[]', '[]', '[]')", [own])).toMatch(
        /permission denied/,
      )
      expect(await failure(client, 'select public.apply_retention()')).toMatch(/permission denied/)
      expect(await failure(client, 'select * from public.pairing_codes')).toMatch(/permission denied/)
    })
    await asUser(USER_B, async (client) => {
      const apps = await client.query('select package_name from public.device_apps where device_id = $1', [own])
      expect(apps.rows).toEqual([{ package_name: 'com.netflix.ninja' }])
    })
  })

  it('limits share links to 30 days and to the user’s own devices', async () => {
    const own = await addDevice(USER_A, hash(4))
    const foreign = await addDevice(USER_B, hash(5))
    const insert = `insert into public.share_links (user_id, token_hash, device_id, expires_at)
                    values ($1, $2, $3, now() + $4::interval)`
    await asUser(USER_A, async (client) => {
      expect(await failure(client, insert, [USER_A, hash(10), own, '30 days'])).toBeNull()
      expect(await failure(client, insert, [USER_A, hash(11), null, '31 days'])).toMatch(/share_links_lifetime/)
      expect(await failure(client, insert, [USER_A, hash(12), null, '-1 day'])).toMatch(/share_links_lifetime/)
      expect(await failure(client, insert, [USER_B, hash(13), null, '1 day'])).toMatch(/row-level security/)
      expect(await failure(client, insert, [USER_A, hash(14), foreign, '1 day'])).toMatch(/foreign key/)
      expect(
        await failure(
          client,
          `insert into public.share_links (user_id, token_hash, expires_at, created_at)
           values ($1, $2, now() + interval '60 days', now() + interval '40 days')`,
          [USER_A, hash(15)],
        ),
      ).toMatch(/permission denied/)
    })
  })

  it('drops devices that never synced a day after pairing, and old buckets', async () => {
    const stale = await addDevice(USER_A, hash(6), ", now() - interval '2 days', null")
    const fresh = await addDevice(USER_A, hash(7))
    const synced = await addDevice(USER_A, hash(8), ", now() - interval '2 days', now()")
    await db.query(
      `insert into public.screen_buckets (device_id, user_id, day, hour, screen_on_ms)
       values ($1, $2, current_date - 400, 1, 1), ($1, $2, current_date - 1, 1, 1)`,
      [synced, USER_A],
    )
    await db.query('select public.apply_retention()')
    const { rows } = await db.query<{ id: string }>('select id from public.devices where id = any($1)', [
      [stale, fresh, synced],
    ])
    expect(rows.map((r) => r.id).sort()).toEqual([fresh, synced].sort())
    const buckets = await db.query('select count(*)::int as n from public.screen_buckets where device_id = $1', [
      synced,
    ])
    expect(buckets.rows[0]).toEqual({ n: 1 })
  })

  describe('security events', () => {
    const EVENT_A = '0b9a3a52-6f0c-4b8e-9a3e-5d2f1c7e8a10'
    const EVENT_B = '6c1f0a77-2c3d-4e5f-8a9b-0c1d2e3f4a5b'
    const batch = (...events: { id: string; type: string; value?: number }[]) =>
      JSON.stringify(events.map((e) => ({ eventId: e.id, type: e.type, at: '2026-11-09T10:15:00Z', value: e.value })))
    const store = async (deviceId: string, events: string) =>
      (await db.query<{ n: number }>('select public.ingest_events($1, $2::jsonb) as n', [deviceId, events])).rows[0]?.n

    it('stores a batch once, however often it is sent', async () => {
      const device = await addDevice(USER_A, hash(40))
      const events = batch({ id: EVENT_A, type: 'pin_wrong', value: 3 }, { id: EVENT_B, type: 'app_opened' })
      expect(await store(device, events)).toBe(2)
      expect(await store(device, events)).toBe(0)
      const { rows } = await db.query('select count(*)::int as n from public.security_events where device_id = $1', [
        device,
      ])
      expect(rows[0]).toEqual({ n: 2 })
    })

    it('refuses an unknown type and a revoked device', async () => {
      const device = await addDevice(USER_A, hash(41))
      await expect(store(device, batch({ id: EVENT_A, type: 'pin_typed' }))).rejects.toThrow(/check constraint/)
      const revoked = await addDevice(USER_A, hash(42))
      await db.query('update public.devices set revoked_at = now() where id = $1', [revoked])
      await expect(store(revoked, batch({ id: EVENT_A, type: 'app_opened' }))).rejects.toThrow(/unknown or revoked/)
    })

    it('shows events to their owner only, and lets the owner delete them', async () => {
      const device = await addDevice(USER_A, hash(43))
      await store(device, batch({ id: EVENT_A, type: 'forgot_pin_opened' }))
      await asUser(USER_A, async (client) => {
        const { rows } = await client.query('select type from public.security_events where device_id = $1', [device])
        expect(rows).toEqual([{ type: 'forgot_pin_opened' }])
      })
      await asUser(USER_B, async (client) => {
        const { rows } = await client.query('select 1 from public.security_events where device_id = $1', [device])
        expect(rows).toHaveLength(0)
        const deleted = await client.query('delete from public.security_events where device_id = $1', [device])
        expect(deleted.rowCount).toBe(0)
      })
      await asUser(USER_A, async (client) => {
        expect(await failure(client, 'select public.ingest_events($1, $2::jsonb)', [device, '[]'])).toMatch(
          /permission denied/,
        )
        expect(await failure(client, "update public.security_events set type = 'app_opened'")).toMatch(
          /permission denied/,
        )
        const deleted = await client.query('delete from public.security_events where device_id = $1', [device])
        expect(deleted.rowCount).toBe(1)
      })
    })

    it('follows the retention of the user and goes with the device', async () => {
      const device = await addDevice(USER_A, hash(44))
      await store(device, batch({ id: EVENT_A, type: 'app_opened' }))
      await db.query(
        "update public.security_events set occurred_at = now() - interval '4000 days' where device_id = $1",
        [device],
      )
      await db.query('select public.apply_retention()')
      const left = await db.query('select 1 from public.security_events where device_id = $1', [device])
      expect(left.rows).toHaveLength(0)
      await store(device, batch({ id: EVENT_B, type: 'app_opened' }))
      await db.query('delete from public.devices where id = $1', [device])
      const gone = await db.query('select 1 from public.security_events where device_id = $1', [device])
      expect(gone.rows).toHaveLength(0)
    })
  })
})
