import { SCHEMA_VERSION, apiErrorSchema, syncResponseSchema } from 'streamtrack-tv'
import { beforeEach, describe, expect, it } from 'vitest'
import { SYNC_MAX_BYTES, SYNC_RATE_LIMIT, dedupeBuckets, handleSync } from '../src/lib/ingest/sync'
import { sha256 } from '../src/lib/security/tokens'
import { MemoryIngestStore, MemoryLimiter, jsonRequest } from './helpers/memory'

const TOKEN = 'a'.repeat(43)
const URL = 'https://tv.example/api/v1/sync'
const NOW = new Date('2026-11-08T20:00:00Z')

/** The same well-formed payload the schema package uses in its own tests. */
const valid = {
  schemaVersion: SCHEMA_VERSION,
  sentAt: '2026-11-08T20:15:00+01:00',
  timezone: 'Europe/Bratislava',
  usage: [{ day: '2026-11-08', hour: 19, packageName: 'com.netflix.ninja', label: 'Netflix', foregroundMs: 1_800_000 }],
  keys: [{ day: '2026-11-08', hour: 19, keyClass: 'dpad', count: 42 }],
  screen: [{ day: '2026-11-08', hour: 19, screenOnMs: 3_000_000 }],
}

let store: MemoryIngestStore
let limiter: MemoryLimiter
const send = (body: unknown, token: string | null = TOKEN) =>
  handleSync(jsonRequest(URL, body, token ? { authorization: `Bearer ${token}` } : {}), {
    store,
    limiter,
    now: () => NOW,
  })

beforeEach(() => {
  store = new MemoryIngestStore()
  limiter = new MemoryLimiter()
  store.devices.push({ id: 'tv-1', userId: 'user-1', tokenHash: sha256(TOKEN) })
})

describe('POST /api/v1/sync (contract)', () => {
  it('accepts the valid payload of the schema package and answers a SyncResponse', async () => {
    const response = await send(valid)
    expect(response.status).toBe(200)
    const body = syncResponseSchema.parse(await response.json())
    expect(body).toEqual({ accepted: { usage: 1, keys: 1, screen: 1 }, serverTime: NOW.toISOString() })
    expect(store.rows.get('tv-1|2026-11-08|19|app|com.netflix.ninja')).toBe(1_800_000)
    expect(store.seen.get('tv-1')).toBe('Europe/Bratislava')
  })

  it('rejects a raw key code with an ApiError naming the field', async () => {
    const keys = [{ day: '2026-11-08', hour: 19, keyClass: 'KEYCODE_DPAD_UP', count: 1 }]
    const response = await send({ ...valid, keys })
    expect(response.status).toBe(400)
    const error = apiErrorSchema.parse(await response.json())
    expect(error.error).toBe('invalid_payload')
    expect(error.message).toMatch(/^keys\.0\.keyClass/)
    expect(store.rows.size).toBe(0)
  })
})

describe('handleSync', () => {
  it('is idempotent: re-sending a bucket replaces its value', async () => {
    await send(valid)
    await send({ ...valid, usage: [{ ...valid.usage[0], foregroundMs: 2_000_000 }] })
    expect(store.rows.size).toBe(3)
    expect(store.rows.get('tv-1|2026-11-08|19|app|com.netflix.ninja')).toBe(2_000_000)
  })

  it('collapses duplicate buckets inside one payload, last one wins', async () => {
    const screen = [
      { day: '2026-11-08', hour: 19, screenOnMs: 1 },
      { day: '2026-11-08', hour: 19, screenOnMs: 2 },
    ]
    const body = syncResponseSchema.parse(await (await send({ ...valid, screen })).json())
    expect(body.accepted.screen).toBe(1)
    expect(store.rows.get('tv-1|2026-11-08|19|screen')).toBe(2)
  })

  it('needs a bearer token', async () => {
    const response = await send(valid, null)
    expect(response.status).toBe(401)
    expect(apiErrorSchema.parse(await response.json()).error).toBe('unauthorized')
  })

  it('refuses an unknown token', async () => {
    const response = await send(valid, 'b'.repeat(43))
    expect(response.status).toBe(401)
  })

  it('rate limits per device', async () => {
    limiter.hits.set('sync:tv-1', SYNC_RATE_LIMIT.limit)
    const response = await send(valid)
    expect(response.status).toBe(429)
    expect(response.headers.get('retry-after')).toBe(String(SYNC_RATE_LIMIT.windowSeconds))
  })

  it('refuses bodies over the size limit before parsing them', async () => {
    const huge = 'x'.repeat(SYNC_MAX_BYTES + 1)
    const response = await send(huge)
    expect(response.status).toBe(413)
    expect(apiErrorSchema.parse(await response.json()).error).toBe('invalid_payload')
  })

  it('refuses malformed JSON', async () => {
    const response = await send('{not json')
    expect(response.status).toBe(400)
    expect(apiErrorSchema.parse(await response.json()).message).toMatch(/JSON/)
  })

  it('refuses an unknown schema version', async () => {
    expect((await send({ ...valid, schemaVersion: 2 })).status).toBe(400)
  })

  it('sends no-store on every answer', async () => {
    expect((await send(valid)).headers.get('cache-control')).toBe('no-store')
  })
})

describe('dedupeBuckets', () => {
  it('keeps distinct bucket kinds apart', () => {
    const rows = [
      { day: '2026-11-08', hour: 1, packageName: 'a.b', foregroundMs: 1 },
      { day: '2026-11-08', hour: 1, packageName: 'a.c', foregroundMs: 1 },
      { day: '2026-11-08', hour: 2, packageName: 'a.b', foregroundMs: 1 },
    ]
    expect(dedupeBuckets(rows)).toHaveLength(3)
  })
})
