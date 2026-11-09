import type { SupabaseClient } from '@supabase/supabase-js'
import { KEY_CLASSES, SECURITY_EVENT_TYPES } from 'streamtrack-tv'
import { z } from 'zod'
import { fetchAllRows, parseRow, parseRows } from '../supabase/query'
import { pickTimeZone, todayIn } from '../time/days'
import type { DashboardSource } from './source'
import { CATEGORIES, GOAL_METRICS, GOAL_PERIODS, GOAL_SCOPES, type Settings } from './types'

const settingsRow = z.object({
  timezone: z.string().nullable(),
  locale: z.string(),
  retention_days: z.number(),
  digest_enabled: z.boolean(),
})

const DEFAULT_SETTINGS = { locale: 'en', retentionDays: 365, digestEnabled: false } as const

const deviceRow = z.object({
  id: z.string(),
  name: z.string(),
  model: z.string().nullable(),
  member_id: z.string().nullable(),
  last_seen_at: z.string().nullable(),
  revoked_at: z.string().nullable(),
  created_at: z.string(),
})

const goalRow = z.object({
  id: z.string(),
  scope: z.enum(GOAL_SCOPES),
  target: z.string(),
  metric: z.enum(GOAL_METRICS),
  period: z.enum(GOAL_PERIODS),
  limit_value: z.coerce.number(),
})

const usageRow = z.object({
  device_id: z.string(),
  day: z.string(),
  hour: z.number(),
  package_name: z.string(),
  foreground_ms: z.number(),
})
const keyRow = z.object({
  device_id: z.string(),
  day: z.string(),
  hour: z.number(),
  key_class: z.enum(KEY_CLASSES),
  count: z.number(),
})
const screenRow = z.object({ device_id: z.string(), day: z.string(), hour: z.number(), screen_on_ms: z.number() })

/**
 * Dashboard data of one user. Works with a session client (row level security applies) and with the
 * service-role client for share links; either way every query filters by `userId` explicitly.
 */
export function createSupabaseSource(client: SupabaseClient, userId: string, now: () => Date): DashboardSource {
  let settings: Promise<Settings> | undefined

  const loadSettings = async (): Promise<Settings> => {
    const [settingsResult, zonesResult] = await Promise.all([
      client
        .from('settings')
        .select('timezone, locale, retention_days, digest_enabled')
        .eq('user_id', userId)
        .maybeSingle(),
      client
        .from('devices')
        .select('timezone')
        .eq('user_id', userId)
        .not('timezone', 'is', null)
        .not('last_seen_at', 'is', null)
        .order('last_seen_at', { ascending: false })
        .limit(1),
    ])
    const row = parseRow(settingsRow, settingsResult)
    const zones = parseRows(z.object({ timezone: z.string() }), zonesResult).map((r) => r.timezone)
    const zone = pickTimeZone(row?.timezone ?? null, zones)
    const rest = row
      ? { locale: row.locale, retentionDays: row.retention_days, digestEnabled: row.digest_enabled }
      : DEFAULT_SETTINGS
    return { timezone: zone.timezone, timezoneSource: zone.source, ...rest }
  }

  const getSettings = () => (settings ??= loadSettings())

  /**
   * Rows of one bucket table for a day span, page by page in primary key order. `primaryKey` lists the
   * key columns after (device_id, day, hour); `value` is the measured column.
   */
  const bucketRows = <T>(
    schema: z.ZodType<T>,
    table: string,
    columns: { primaryKey: readonly string[]; value: string },
    span: { from: string; to: string },
    deviceIds: readonly string[] | null,
  ) =>
    fetchAllRows(schema, (from, to) => {
      const order = ['device_id', 'day', 'hour', ...columns.primaryKey]
      let query = client
        .from(table)
        .select([...order, columns.value].join(', '))
        .eq('user_id', userId)
        .gte('day', span.from)
        .lte('day', span.to)
      if (deviceIds) query = query.in('device_id', deviceIds)
      for (const column of order) query = query.order(column)
      return query.range(from, to)
    })

  return {
    kind: 'live',
    now,
    settings: getSettings,
    today: async () => todayIn((await getSettings()).timezone, now()),
    async devices() {
      const rows = parseRows(
        deviceRow,
        await client
          .from('devices')
          .select('id, name, model, member_id, last_seen_at, revoked_at, created_at')
          .eq('user_id', userId)
          .order('created_at'),
      )
      return rows.map((r) => ({
        id: r.id,
        name: r.name,
        model: r.model,
        memberId: r.member_id,
        lastSeenAt: r.last_seen_at,
        revokedAt: r.revoked_at,
        createdAt: r.created_at,
      }))
    },
    async members() {
      return parseRows(
        z.object({ id: z.string(), name: z.string() }),
        await client.from('household_members').select('id, name').eq('user_id', userId).order('created_at'),
      )
    },
    async apps(deviceIds) {
      if (deviceIds?.length === 0) return []
      const [rows, used] = await Promise.all([
        client.from('apps').select('package_name, label, category').eq('user_id', userId),
        deviceIds &&
          fetchAllRows(z.object({ package_name: z.string() }), (from, to) =>
            client
              .from('device_apps')
              .select('package_name')
              .eq('user_id', userId)
              .in('device_id', deviceIds)
              .order('package_name')
              .range(from, to),
          ),
      ])
      const packages = used && new Set(used.map((r) => r.package_name))
      return parseRows(
        z.object({ package_name: z.string(), label: z.string().nullable(), category: z.enum(CATEGORIES).nullable() }),
        rows,
      )
        .filter((r) => packages === null || packages.has(r.package_name))
        .map((r) => ({ packageName: r.package_name, label: r.label, category: r.category }))
    },
    async goals() {
      const rows = parseRows(
        goalRow,
        await client
          .from('goals')
          .select('id, scope, target, metric, period, limit_value')
          .eq('user_id', userId)
          .order('created_at'),
      )
      return rows.map(({ limit_value, ...goal }) => ({ ...goal, limit: limit_value }))
    },
    async shareLinks() {
      const rows = parseRows(
        z.object({ id: z.string(), device_id: z.string().nullable(), expires_at: z.string(), created_at: z.string() }),
        await client
          .from('share_links')
          .select('id, device_id, expires_at, created_at')
          .eq('user_id', userId)
          .gt('expires_at', now().toISOString())
          .order('created_at'),
      )
      return rows.map((r) => ({ id: r.id, deviceId: r.device_id, expiresAt: r.expires_at, createdAt: r.created_at }))
    },
    async securityEvents(filter) {
      if (filter.deviceIds?.length === 0 || filter.types?.length === 0) return []
      let query = client
        .from('security_events')
        .select('id, device_id, type, occurred_at, received_at, value')
        .eq('user_id', userId)
      if (filter.deviceIds) query = query.in('device_id', filter.deviceIds)
      if (filter.types) query = query.in('type', filter.types)
      const rows = parseRows(
        z.object({
          id: z.string(),
          device_id: z.string(),
          type: z.enum(SECURITY_EVENT_TYPES),
          occurred_at: z.string(),
          received_at: z.string(),
          value: z.number().nullable(),
        }),
        await query.order('occurred_at', { ascending: false }).order('id').limit(filter.limit),
      )
      return rows.map((r) => ({
        id: r.id,
        deviceId: r.device_id,
        type: r.type,
        at: r.occurred_at,
        receivedAt: r.received_at,
        value: r.value,
      }))
    },
    async buckets(span, deviceIds) {
      if (deviceIds?.length === 0) return { usage: [], keys: [], screen: [] }
      const [usage, keys, screen] = await Promise.all([
        bucketRows(
          usageRow,
          'usage_buckets',
          { primaryKey: ['package_name'], value: 'foreground_ms' },
          span,
          deviceIds,
        ),
        bucketRows(keyRow, 'key_buckets', { primaryKey: ['key_class'], value: 'count' }, span, deviceIds),
        bucketRows(screenRow, 'screen_buckets', { primaryKey: [], value: 'screen_on_ms' }, span, deviceIds),
      ])
      return {
        usage: usage.map((r) => ({
          deviceId: r.device_id,
          day: r.day,
          hour: r.hour,
          packageName: r.package_name,
          foregroundMs: r.foreground_ms,
        })),
        keys: keys.map((r) => ({
          deviceId: r.device_id,
          day: r.day,
          hour: r.hour,
          keyClass: r.key_class,
          count: r.count,
        })),
        screen: screen.map((r) => ({ deviceId: r.device_id, day: r.day, hour: r.hour, screenOnMs: r.screen_on_ms })),
      }
    },
  }
}
