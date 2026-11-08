import { selectBuckets } from '../analytics/aggregate'
import { generateDemoDataset, type DemoDataset } from '../demo/dataset'
import type { DashboardSource } from './source'

let shared: DemoDataset | undefined

/** Read-only source over the seeded demo household. The dataset is generated once per process. */
export function createDemoSource(dataset: DemoDataset = (shared ??= generateDemoDataset())): DashboardSource {
  return {
    kind: 'demo',
    now: () => new Date(`${dataset.today}T22:45:00Z`),
    today: () => Promise.resolve(dataset.today),
    settings: () => Promise.resolve(dataset.settings),
    devices: () => Promise.resolve(dataset.devices),
    members: () => Promise.resolve(dataset.members),
    apps: (deviceIds) => {
      if (deviceIds === null) return Promise.resolve(dataset.apps)
      const used = new Set(selectBuckets(dataset.buckets, { deviceIds }).usage.map((r) => r.packageName))
      return Promise.resolve(dataset.apps.filter((app) => used.has(app.packageName)))
    },
    goals: () => Promise.resolve(dataset.goals),
    shareLinks: () => Promise.resolve([]),
    buckets: (span, deviceIds) =>
      Promise.resolve(selectBuckets(dataset.buckets, { deviceIds, from: span.from, to: span.to })),
  }
}
