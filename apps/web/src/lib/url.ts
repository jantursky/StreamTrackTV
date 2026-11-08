import type { SearchParams } from './time/range'

/** Parameters that carry over when moving between dashboard sections. */
export const SHARED_PARAMS = ['range', 'date', 'from', 'to', 'device'] as const

/**
 * Builds `path?query` from the current parameters with some replaced; `null` removes a parameter.
 * Only `keep` parameters of the current URL survive, so section-specific state does not leak.
 */
export function withParams(
  path: string,
  current: SearchParams,
  overrides: Record<string, string | null> = {},
  keep: readonly string[] = SHARED_PARAMS,
): string {
  const query = new URLSearchParams()
  for (const key of keep) {
    const value = current[key]
    const first = Array.isArray(value) ? value[0] : value
    if (first !== undefined && first !== '') query.set(key, first)
  }
  // A new range kind replaces every parameter of the old one.
  if ('range' in overrides) for (const key of ['date', 'from', 'to']) query.delete(key)
  for (const [key, value] of Object.entries(overrides)) {
    if (value === null) query.delete(key)
    else query.set(key, value)
  }
  const text = query.toString()
  return text ? `${path}?${text}` : path
}
