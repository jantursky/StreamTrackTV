import { appShares, change, totals, type AppShare } from '../analytics/aggregate'
import type { AppInfo, Buckets } from '../data/types'
import { formatDay, formatDuration, formatPercent } from '../i18n/format'
import type { Translate } from '../i18n/translate'
import { addDays, startOfWeek } from '../time/days'

export interface DigestWeek {
  from: string
  to: string
}

/** The last full Monday-to-Sunday week before `today`, and the week before it for comparison. */
export function digestWeeks(today: string): { current: DigestWeek; previous: DigestWeek } {
  const from = addDays(startOfWeek(today), -7)
  return {
    current: { from, to: addDays(from, 6) },
    previous: { from: addDays(from, -7), to: addDays(from, -1) },
  }
}

export interface Digest {
  subject: string
  text: string
  html: string
}

export function escapeHtml(value: string): string {
  return value
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;')
}

/** Plain-text and HTML email of one week. App labels come from TVs, so the HTML escapes everything. */
export function buildDigest(
  week: DigestWeek,
  current: Buckets,
  previous: Buckets,
  resolve: (packageName: string) => AppInfo,
  t: Translate,
  locale: string,
): Digest {
  const now = totals(current)
  const delta = change(now.screenOnMs, totals(previous).screenOnMs)
  const top: AppShare[] = appShares(current, resolve).slice(0, 5)
  const total = formatDuration(now.screenOnMs, t)
  const lines = [
    t('digest.intro', { from: formatDay(week.from, locale), to: formatDay(week.to, locale) }),
    t('digest.total', { value: total }),
    ...(delta === null ? [] : [t('digest.change', { value: formatPercent(delta, locale, true) })]),
  ]
  const apps = top.map((a) => `${a.label}: ${formatDuration(a.ms, t)}`)
  const footer = t('digest.footer')

  const text = [
    ...lines,
    '',
    ...(apps.length ? [t('digest.topApps'), ...apps.map((a) => `- ${a}`), ''] : []),
    footer,
  ].join('\n')
  const paragraph = (line: string) => `<p>${escapeHtml(line)}</p>`
  const html = [
    ...lines.map(paragraph),
    ...(apps.length
      ? [paragraph(t('digest.topApps')), `<ul>${apps.map((a) => `<li>${escapeHtml(a)}</li>`).join('')}</ul>`]
      : []),
    `<p style="color:#78716c">${escapeHtml(footer)}</p>`,
  ].join('\n')

  return { subject: t('digest.subject', { total }), text, html }
}
