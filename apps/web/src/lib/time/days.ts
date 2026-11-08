/**
 * Calendar-day arithmetic on `YYYY-MM-DD` strings. Buckets carry the TV's local day, so all math stays
 * in plain dates (UTC internally) and never converts through a time zone.
 */

const DAY_MS = 86_400_000
const ISO_DAY = /^\d{4}-\d{2}-\d{2}$/

export function isIsoDay(value: string): boolean {
  if (!ISO_DAY.test(value)) return false
  return toIsoDay(parseDay(value)) === value
}

function parseDay(day: string): Date {
  return new Date(`${day}T00:00:00Z`)
}

function toIsoDay(date: Date): string {
  return date.toISOString().slice(0, 10)
}

export function addDays(day: string, amount: number): string {
  return toIsoDay(new Date(parseDay(day).getTime() + amount * DAY_MS))
}

/** Whole days from `from` to `to`; negative when `to` is earlier. */
export function daysBetween(from: string, to: string): number {
  return Math.round((parseDay(to).getTime() - parseDay(from).getTime()) / DAY_MS)
}

/** Every day from `from` to `to`, both inclusive. */
export function eachDay(from: string, to: string): string[] {
  const count = daysBetween(from, to) + 1
  return Array.from({ length: Math.max(0, count) }, (_, i) => addDays(from, i))
}

/** Day of week with Monday = 0 and Sunday = 6. */
export function weekdayIndex(day: string): number {
  return (parseDay(day).getUTCDay() + 6) % 7
}

export function startOfWeek(day: string): string {
  return addDays(day, -weekdayIndex(day))
}

export function monthOf(day: string): string {
  return day.slice(0, 7)
}

export function isIsoMonth(value: string): boolean {
  return /^\d{4}-(0[1-9]|1[0-2])$/.test(value)
}

export function addMonths(month: string, amount: number): string {
  const [year = 0, mon = 1] = month.split('-').map(Number)
  const date = new Date(Date.UTC(year, mon - 1 + amount, 1))
  return toIsoDay(date).slice(0, 7)
}

export function lastDayOfMonth(month: string): string {
  return addDays(`${addMonths(month, 1)}-01`, -1)
}

/** Weeks (Monday first) covering a month; days outside the month are `null`. */
export function monthGrid(month: string): (string | null)[][] {
  const first = `${month}-01`
  const last = lastDayOfMonth(month)
  const start = startOfWeek(first)
  const weeks: (string | null)[][] = []
  for (let cursor = start; cursor <= last; cursor = addDays(cursor, 7)) {
    weeks.push(Array.from({ length: 7 }, (_, i) => addDays(cursor, i)).map((d) => (monthOf(d) === month ? d : null)))
  }
  return weeks
}

/** Current calendar day in an IANA time zone. */
export function todayIn(timeZone: string, now: Date): string {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).formatToParts(now)
  const get = (type: string) => parts.find((p) => p.type === type)?.value ?? ''
  return `${get('year')}-${get('month')}-${get('day')}`
}

/**
 * The zone that decides "today" for an account: the one the user set, else the one the most recently synced
 * TV reported (`deviceZones` newest first), else UTC.
 */
export function pickTimeZone(
  account: string | null,
  deviceZones: readonly (string | null)[],
): { timezone: string; source: 'account' | 'device' | 'default' } {
  if (account !== null && isTimeZone(account)) return { timezone: account, source: 'account' }
  const device = deviceZones.find((zone) => zone !== null && isTimeZone(zone))
  return device ? { timezone: device, source: 'device' } : { timezone: 'UTC', source: 'default' }
}

export function isTimeZone(value: string): boolean {
  try {
    new Intl.DateTimeFormat('en', { timeZone: value })
    return true
  } catch {
    return false
  }
}
