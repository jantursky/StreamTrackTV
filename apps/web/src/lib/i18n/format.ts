import type { Translate } from './translate'

const MINUTE = 60_000

/** "3 h 20 min", "45 min"; rounds to whole minutes. */
export function formatDuration(ms: number, t: Translate): string {
  const minutes = Math.round(ms / MINUTE)
  const h = Math.floor(minutes / 60)
  const m = minutes % 60
  if (h === 0) return t('units.minutes', { m })
  return m === 0 ? t('units.hours', { h }) : t('units.hoursMinutes', { h, m })
}

/** Compact duration for chart labels: "3.4 h", "45 m". */
export function formatDurationShort(ms: number, t: Translate): string {
  const hours = ms / (60 * MINUTE)
  if (hours >= 1) return t('units.hoursShort', { h: hours >= 10 ? Math.round(hours) : Math.round(hours * 10) / 10 })
  return t('units.minutesShort', { m: Math.round(ms / MINUTE) })
}

export function formatNumber(value: number, locale: string): string {
  return new Intl.NumberFormat(locale, { maximumFractionDigits: 0 }).format(value)
}

export function formatPercent(ratio: number, locale: string, signed = false): string {
  return new Intl.NumberFormat(locale, {
    style: 'percent',
    maximumFractionDigits: 0,
    signDisplay: signed ? 'exceptZero' : 'auto',
  }).format(ratio)
}

const dayDate = (day: string) => new Date(`${day}T12:00:00Z`)

export function formatDay(day: string, locale: string, style: 'long' | 'short' | 'weekday' = 'short'): string {
  const options: Intl.DateTimeFormatOptions =
    style === 'long'
      ? { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' }
      : style === 'weekday'
        ? { weekday: 'short' }
        : { day: 'numeric', month: 'short' }
  return new Intl.DateTimeFormat(locale, { ...options, timeZone: 'UTC' }).format(dayDate(day))
}

export function formatMonth(month: string, locale: string): string {
  return new Intl.DateTimeFormat(locale, { month: 'long', year: 'numeric', timeZone: 'UTC' }).format(
    dayDate(`${month}-01`),
  )
}

export function formatSpanLabel(span: { from: string; to: string }, locale: string): string {
  return span.from === span.to
    ? formatDay(span.from, locale, 'long')
    : `${formatDay(span.from, locale)} – ${formatDay(span.to, locale)}`
}

export function formatHour(hour: number): string {
  return `${String(hour).padStart(2, '0')}:00`
}

/** "3 hours ago", "yesterday"; picks the largest unit that fits. */
export function formatRelative(iso: string, now: Date, locale: string): string {
  const seconds = (Date.parse(iso) - now.getTime()) / 1000
  const units: [Intl.RelativeTimeFormatUnit, number][] = [
    ['day', 86_400],
    ['hour', 3600],
    ['minute', 60],
  ]
  const format = new Intl.RelativeTimeFormat(locale, { numeric: 'auto' })
  for (const [unit, size] of units) {
    if (Math.abs(seconds) >= size) return format.format(Math.round(seconds / size), unit)
  }
  return format.format(0, 'minute')
}
