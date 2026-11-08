import type { Translate } from '../i18n/translate'
import type { AppShare } from './aggregate'

export const APP_SORTS = ['time', 'name', 'category'] as const
export type AppSort = (typeof APP_SORTS)[number]

/** Filters by label or package name and orders the list; pure so it can be tested. */
export function filterApps(apps: readonly AppShare[], query: string, sort: AppSort, t: Translate): AppShare[] {
  const needle = query.trim().toLowerCase()
  const matching = needle
    ? apps.filter((a) => a.label.toLowerCase().includes(needle) || a.packageName.toLowerCase().includes(needle))
    : [...apps]
  const byName = (x: AppShare, y: AppShare) => x.label.localeCompare(y.label)
  if (sort === 'name') return matching.sort(byName)
  if (sort === 'category') {
    return matching.sort(
      (x, y) => t(`categories.${x.category}`).localeCompare(t(`categories.${y.category}`)) || y.ms - x.ms,
    )
  }
  return matching.sort((x, y) => y.ms - x.ms || byName(x, y))
}
