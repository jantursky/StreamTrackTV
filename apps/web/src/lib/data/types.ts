import type { KeyClass } from 'streamtrack-tv'

export const CATEGORIES = ['streaming', 'video', 'music', 'live-tv', 'games', 'kids', 'system', 'other'] as const
export type Category = (typeof CATEGORIES)[number]

export function isCategory(value: string): value is Category {
  return (CATEGORIES as readonly string[]).includes(value)
}

export interface Device {
  id: string
  name: string
  model: string | null
  memberId: string | null
  lastSeenAt: string | null
  revokedAt: string | null
  createdAt: string
}

export interface Member {
  id: string
  name: string
}

/** What the user and the TV told us about an app; the catalog fills in the rest. */
export interface AppRecord {
  packageName: string
  label: string | null
  category: Category | null
}

export interface AppInfo {
  packageName: string
  label: string
  category: Category
}

export interface UsageRow {
  deviceId: string
  day: string
  hour: number
  packageName: string
  foregroundMs: number
}

export interface KeyRow {
  deviceId: string
  day: string
  hour: number
  keyClass: KeyClass
  count: number
}

export interface ScreenRow {
  deviceId: string
  day: string
  hour: number
  screenOnMs: number
}

export interface Buckets {
  usage: UsageRow[]
  keys: KeyRow[]
  screen: ScreenRow[]
}

export const GOAL_SCOPES = ['device', 'member', 'app', 'category'] as const
export type GoalScope = (typeof GOAL_SCOPES)[number]
export const GOAL_METRICS = ['screen_on', 'app_time', 'keypresses'] as const
export type GoalMetric = (typeof GOAL_METRICS)[number]
export const GOAL_PERIODS = ['day', 'week'] as const
export type GoalPeriod = (typeof GOAL_PERIODS)[number]

/**
 * A limit on one metric. `target` is a device id, member id, package name or category, depending on
 * `scope`. Time limits are in milliseconds, keypress limits are counts.
 */
export interface Goal {
  id: string
  scope: GoalScope
  target: string
  metric: GoalMetric
  period: GoalPeriod
  limit: number
}

/** Where the time zone of an account comes from: chosen in settings, the last synced TV, or neither. */
export type TimeZoneSource = 'account' | 'device' | 'default'

export interface Settings {
  timezone: string
  timezoneSource: TimeZoneSource
  locale: string
  retentionDays: number
  digestEnabled: boolean
}

export interface ShareLink {
  id: string
  deviceId: string | null
  expiresAt: string
  createdAt: string
}
