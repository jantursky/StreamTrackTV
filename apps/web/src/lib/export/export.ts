import { z } from 'zod'
import type { Buckets, Device } from '../data/types'
import { daysBetween, isIsoDay } from '../time/days'
import { MAX_RANGE_DAYS } from '../time/range'

export const EXPORT_FORMATS = ['csv', 'json'] as const
export type ExportFormat = (typeof EXPORT_FORMATS)[number]

const day = z.string().refine(isIsoDay)

const exportQuery = z
  .object({
    from: day,
    to: day,
    format: z.enum(EXPORT_FORMATS).default('csv'),
    device: z.uuid().optional(),
  })
  .refine((q) => q.from <= q.to && daysBetween(q.from, q.to) < MAX_RANGE_DAYS)

export type ExportQuery = z.infer<typeof exportQuery>

/** Validates `?from=&to=&format=&device=` of the export endpoint; `null` when anything is off. */
export function parseExportQuery(query: URLSearchParams): ExportQuery | null {
  const parsed = exportQuery.safeParse(Object.fromEntries(query))
  return parsed.success ? parsed.data : null
}

export interface ExportRow {
  deviceId: string
  device: string
  day: string
  hour: number
  kind: 'app' | 'key' | 'screen'
  /** Package name for app rows, key class for key rows, empty for screen rows. */
  key: string
  /** Milliseconds for app and screen rows, a count for key rows. */
  value: number
}

const CSV_COLUMNS = ['device_id', 'device', 'day', 'hour', 'kind', 'key', 'value'] as const

/** Flattens buckets into one sorted table, the shape of the CSV file. */
export function exportRows(buckets: Buckets, devices: readonly Device[]): ExportRow[] {
  const names = new Map(devices.map((d) => [d.id, d.name]))
  const base = (r: { deviceId: string; day: string; hour: number }) => ({
    deviceId: r.deviceId,
    device: names.get(r.deviceId) ?? '',
    day: r.day,
    hour: r.hour,
  })
  const rows: ExportRow[] = [
    ...buckets.screen.map((r) => ({ ...base(r), kind: 'screen' as const, key: '', value: r.screenOnMs })),
    ...buckets.usage.map((r) => ({ ...base(r), kind: 'app' as const, key: r.packageName, value: r.foregroundMs })),
    ...buckets.keys.map((r) => ({ ...base(r), kind: 'key' as const, key: r.keyClass, value: r.count })),
  ]
  return rows.sort(
    (a, b) =>
      a.day.localeCompare(b.day) ||
      a.hour - b.hour ||
      a.device.localeCompare(b.device) ||
      a.kind.localeCompare(b.kind) ||
      a.key.localeCompare(b.key),
  )
}

/**
 * RFC 4180 quoting, plus a leading apostrophe on cells a spreadsheet would treat as a formula
 * (device names are user input).
 */
export function csvCell(value: string | number): string {
  let text = String(value)
  if (typeof value === 'string' && /^[=+\-@\t\r]/.test(text)) text = `'${text}`
  return /[",\r\n]/.test(text) ? `"${text.replaceAll('"', '""')}"` : text
}

export function toCsv(rows: readonly ExportRow[]): string {
  const lines = rows.map((r) => [r.deviceId, r.device, r.day, r.hour, r.kind, r.key, r.value].map(csvCell).join(','))
  return [CSV_COLUMNS.join(','), ...lines].join('\r\n') + '\r\n'
}

export function toJsonExport(
  span: { from: string; to: string },
  buckets: Buckets,
  devices: readonly Device[],
  exportedAt: Date,
) {
  return {
    exportedAt: exportedAt.toISOString(),
    from: span.from,
    to: span.to,
    devices: devices.map(({ id, name, model }) => ({ id, name, model })),
    usage: buckets.usage,
    keys: buckets.keys,
    screen: buckets.screen,
  }
}

export function exportFileName(span: { from: string; to: string }, format: ExportFormat): string {
  return `streamtrack-${span.from}_${span.to}.${format}`
}

/** File body, type and name of an export; the route only adds the session check and headers. */
export function renderExport(
  query: ExportQuery,
  buckets: Buckets,
  devices: readonly Device[],
  exportedAt: Date,
): { body: string; contentType: string; fileName: string } {
  const fileName = exportFileName(query, query.format)
  return query.format === 'csv'
    ? { body: toCsv(exportRows(buckets, devices)), contentType: 'text/csv; charset=utf-8', fileName }
    : {
        body: JSON.stringify(toJsonExport(query, buckets, devices, exportedAt), null, 2),
        contentType: 'application/json; charset=utf-8',
        fileName,
      }
}
