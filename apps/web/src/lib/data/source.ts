import type {
  AppRecord,
  Buckets,
  Device,
  Goal,
  Member,
  SecurityEventFilter,
  SecurityEventRecord,
  Settings,
  ShareLink,
} from './types'

export type SourceKind = 'demo' | 'live' | 'shared'

/**
 * Read side of the dashboard. Views only talk to this interface, so the same pages render a signed-in
 * household, the seeded demo and a public share link.
 */
export interface DashboardSource {
  readonly kind: SourceKind
  /** Current instant; fixed for the demo so relative times never drift. */
  now(): Date
  /** Current local day of the household. */
  today(): Promise<string>
  settings(): Promise<Settings>
  devices(): Promise<Device[]>
  members(): Promise<Member[]>
  /** Apps of the household, or only those with usage on the given devices. */
  apps(deviceIds: readonly string[] | null): Promise<AppRecord[]>
  goals(): Promise<Goal[]>
  shareLinks(): Promise<ShareLink[]>
  /** Security log of the household, newest first. Never part of a share link. */
  securityEvents(filter: SecurityEventFilter): Promise<SecurityEventRecord[]>
  /** Buckets of the inclusive day span, for the given devices or all when `null`. */
  buckets(span: { from: string; to: string }, deviceIds: readonly string[] | null): Promise<Buckets>
}

/**
 * Narrows a source to what a share link may reveal: usage of one device (or all) and nothing about goals,
 * people or other links.
 */
export function sharedSource(source: DashboardSource, deviceId: string | null): DashboardSource {
  const allowed = (ids: readonly string[] | null) => {
    if (deviceId === null) return ids
    return ids === null || ids.includes(deviceId) ? [deviceId] : []
  }
  return {
    kind: 'shared',
    now: () => source.now(),
    today: () => source.today(),
    settings: () => source.settings(),
    devices: async () => (await source.devices()).filter((d) => deviceId === null || d.id === deviceId),
    members: () => Promise.resolve([]),
    apps: (ids) => source.apps(allowed(ids)),
    goals: () => Promise.resolve([]),
    shareLinks: () => Promise.resolve([]),
    securityEvents: () => Promise.resolve([]),
    buckets: (span, ids) => source.buckets(span, allowed(ids)),
  }
}
