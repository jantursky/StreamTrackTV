import { addDays, daysBetween, eachDay, isIsoDay, startOfWeek } from './days'

export const RANGE_KINDS = ['day', 'week', 'custom'] as const
export type RangeKind = (typeof RANGE_KINDS)[number]

/** An inclusive span of local calendar days. */
export interface DayRange {
  kind: RangeKind
  from: string
  to: string
}

export type SearchParams = Record<string, string | string[] | undefined>

/** Longest custom range, so one request never pulls more than a year of buckets. */
export const MAX_RANGE_DAYS = 366

export function param(params: SearchParams, key: string): string | undefined {
  const value = params[key]
  return Array.isArray(value) ? value[0] : value
}

function validDay(value: string | undefined, today: string): string | undefined {
  return value !== undefined && isIsoDay(value) && value <= today ? value : undefined
}

export function dayRange(day: string): DayRange {
  return { kind: 'day', from: day, to: day }
}

/** Monday to Sunday week containing `day`, cut at `today` so the current week ends now. */
export function weekRange(day: string, today: string): DayRange {
  const from = startOfWeek(day)
  const end = addDays(from, 6)
  return { kind: 'week', from, to: end < today ? end : today }
}

/**
 * Reads `range`, `date`, `from` and `to` from the URL. Anything invalid or in the future falls back to the
 * current week, so a hand-edited URL never errors.
 */
export function parseRange(params: SearchParams, today: string, fallback: RangeKind = 'week'): DayRange {
  const kind = param(params, 'range') ?? fallback
  const date = validDay(param(params, 'date'), today) ?? today
  if (kind === 'day') return dayRange(date)
  if (kind === 'custom') {
    const from = validDay(param(params, 'from'), today)
    const to = validDay(param(params, 'to'), today)
    if (from !== undefined && to !== undefined && from <= to && daysBetween(from, to) < MAX_RANGE_DAYS) {
      return { kind: 'custom', from, to }
    }
  }
  return weekRange(date, today)
}

export function rangeDays(range: DayRange): string[] {
  return eachDay(range.from, range.to)
}

export function rangeLength(range: DayRange): number {
  return daysBetween(range.from, range.to) + 1
}

/** The window of the same length right before `range`, used for period-over-period deltas. */
export function previousPeriod(range: DayRange): DayRange {
  const length = range.kind === 'week' ? 7 : rangeLength(range)
  return { kind: range.kind, from: addDays(range.from, -length), to: addDays(range.from, -1) }
}

/** The range one step back (`-1`) or forward (`1`); `null` when the step would start in the future. */
export function stepRange(range: DayRange, direction: -1 | 1, today: string): DayRange | null {
  if (range.kind === 'day') {
    const day = addDays(range.from, direction)
    return day <= today ? dayRange(day) : null
  }
  if (range.kind === 'week') {
    const day = addDays(range.from, 7 * direction)
    return day <= today ? weekRange(day, today) : null
  }
  const length = rangeLength(range)
  const from = addDays(range.from, length * direction)
  if (from > today) return null
  const to = addDays(range.to, length * direction)
  return { kind: 'custom', from, to: to < today ? to : today }
}

/** URL parameters that reproduce `range` through `parseRange`. */
export function rangeParams(range: DayRange): Record<string, string> {
  if (range.kind === 'custom') return { range: 'custom', from: range.from, to: range.to }
  return { range: range.kind, date: range.from }
}

/**
 * Parses a compare operand: a single day (`2026-11-01`) or an inclusive span (`2026-11-01..2026-11-07`).
 */
export function parseSpan(value: string | undefined, today: string): DayRange | null {
  if (value === undefined) return null
  const [from = '', to = from] = value.split('..')
  const start = validDay(from, today)
  const end = validDay(to, today)
  if (start === undefined || end === undefined || start > end || daysBetween(start, end) >= MAX_RANGE_DAYS) return null
  return { kind: start === end ? 'day' : 'custom', from: start, to: end }
}

export function formatSpan(range: DayRange): string {
  return range.from === range.to ? range.from : `${range.from}..${range.to}`
}
