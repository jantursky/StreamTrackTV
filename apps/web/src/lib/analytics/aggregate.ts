import { KEY_CLASSES, type KeyClass } from 'streamtrack-tv'
import { weekdayIndex } from '../time/days'
import type { AppInfo, Buckets, Category } from '../data/types'

export interface Totals {
  screenOnMs: number
  appMs: number
  keypresses: number
}

export interface AppShare extends AppInfo {
  ms: number
  share: number
}

export interface CategoryShare {
  category: Category
  ms: number
  share: number
}

export type KeyCounts = Record<KeyClass, number>

const sum = (values: Iterable<number>) => {
  let total = 0
  for (const v of values) total += v
  return total
}

export function emptyBuckets(): Buckets {
  return { usage: [], keys: [], screen: [] }
}

/** Keeps rows of the given devices and days; `null` devices means all. */
export function selectBuckets(
  buckets: Buckets,
  filter: { deviceIds?: readonly string[] | null; from?: string; to?: string },
): Buckets {
  const devices = filter.deviceIds ? new Set(filter.deviceIds) : null
  const keep = (row: { deviceId: string; day: string }) =>
    (devices === null || devices.has(row.deviceId)) &&
    (filter.from === undefined || row.day >= filter.from) &&
    (filter.to === undefined || row.day <= filter.to)
  return { usage: buckets.usage.filter(keep), keys: buckets.keys.filter(keep), screen: buckets.screen.filter(keep) }
}

export function totals(buckets: Buckets): Totals {
  return {
    screenOnMs: sum(buckets.screen.map((r) => r.screenOnMs)),
    appMs: sum(buckets.usage.map((r) => r.foregroundMs)),
    keypresses: sum(buckets.keys.map((r) => r.count)),
  }
}

/** Screen-on time per day, in the order of `days`, zero for days without data. */
export function screenByDay(buckets: Buckets, days: readonly string[]): number[] {
  const byDay = new Map<string, number>()
  for (const row of buckets.screen) byDay.set(row.day, (byDay.get(row.day) ?? 0) + row.screenOnMs)
  return days.map((d) => byDay.get(d) ?? 0)
}

export function screenByHour(buckets: Buckets): number[] {
  const hours = Array.from({ length: 24 }, () => 0)
  for (const row of buckets.screen) hours[row.hour] = (hours[row.hour] ?? 0) + row.screenOnMs
  return hours
}

export function appShares(buckets: Buckets, resolve: (packageName: string) => AppInfo): AppShare[] {
  const byPackage = new Map<string, number>()
  for (const row of buckets.usage)
    byPackage.set(row.packageName, (byPackage.get(row.packageName) ?? 0) + row.foregroundMs)
  const total = sum(byPackage.values())
  return [...byPackage]
    .map(([packageName, ms]) => ({ ...resolve(packageName), ms, share: total > 0 ? ms / total : 0 }))
    .sort((a, b) => b.ms - a.ms || a.label.localeCompare(b.label))
}

export function categoryShares(apps: readonly AppShare[]): CategoryShare[] {
  const byCategory = new Map<Category, number>()
  for (const app of apps) byCategory.set(app.category, (byCategory.get(app.category) ?? 0) + app.ms)
  const total = sum(byCategory.values())
  return [...byCategory]
    .map(([category, ms]) => ({ category, ms, share: total > 0 ? ms / total : 0 }))
    .sort((a, b) => b.ms - a.ms)
}

const emptyKeyCounts = (): KeyCounts => Object.fromEntries(KEY_CLASSES.map((k) => [k, 0])) as KeyCounts

export function keysByClass(buckets: Buckets): KeyCounts {
  const counts = emptyKeyCounts()
  for (const row of buckets.keys) counts[row.keyClass] += row.count
  return counts
}

export function keysByHour(buckets: Buckets): KeyCounts[] {
  const hours = Array.from({ length: 24 }, emptyKeyCounts)
  for (const row of buckets.keys) {
    const hour = hours[row.hour]
    if (hour) hour[row.keyClass] += row.count
  }
  return hours
}

/**
 * Average screen-on time per weekday (rows, Monday first) and hour (columns). Each cell divides by the
 * number of times that weekday occurs in `days`, so partial weeks do not skew the picture.
 */
export function weekdayHourAverages(buckets: Buckets, days: readonly string[]): number[][] {
  const occurrences = Array.from({ length: 7 }, () => 0)
  for (const day of days) occurrences[weekdayIndex(day)] = (occurrences[weekdayIndex(day)] ?? 0) + 1
  const grid = Array.from({ length: 7 }, () => Array.from({ length: 24 }, () => 0))
  for (const row of buckets.screen) {
    const line = grid[weekdayIndex(row.day)]
    if (line) line[row.hour] = (line[row.hour] ?? 0) + row.screenOnMs
  }
  return grid.map((line, weekday) => line.map((ms) => ms / Math.max(1, occurrences[weekday] ?? 0)))
}

/** Relative change against a baseline; `null` when there is no baseline to compare with. */
export function change(current: number, previous: number): number | null {
  return previous > 0 ? (current - previous) / previous : null
}
