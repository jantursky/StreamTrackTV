import { describe, expect, it } from 'vitest'
import { syncPayloadSchema, SCHEMA_VERSION } from 'streamtrack-tv'
import { appShares, categoryShares, selectBuckets, totals } from '../src/lib/analytics/aggregate'
import { alerts } from '../src/lib/analytics/alerts'
import { appDirectory } from '../src/lib/data/catalog'
import { createDemoSource } from '../src/lib/data/demo-source'
import { sharedSource } from '../src/lib/data/source'
import { DEMO_TODAY, DEMO_WEEKS, generateDemoDataset } from '../src/lib/demo/dataset'
import { createRandom, mulberry32 } from '../src/lib/demo/prng'
import { addDays, weekdayIndex } from '../src/lib/time/days'

describe('prng', () => {
  it('is deterministic per seed and within [0, 1)', () => {
    const a = mulberry32(42)
    const b = mulberry32(42)
    const values = Array.from({ length: 1000 }, () => a())
    expect(values).toEqual(Array.from({ length: 1000 }, () => b()))
    expect(values.every((v) => v >= 0 && v < 1)).toBe(true)
    expect(mulberry32(43)()).not.toBe(values[0])
  })

  it('draws integers and weighted picks in range', () => {
    const random = createRandom(7)
    for (let i = 0; i < 200; i++) {
      const n = random.int(3, 5)
      expect(n).toBeGreaterThanOrEqual(3)
      expect(n).toBeLessThanOrEqual(5)
    }
    expect(random.weighted([['only', 1]])).toBe('only')
    expect(() => random.weighted([])).toThrow()
  })
})

describe('demo dataset', () => {
  const dataset = generateDemoDataset()

  it('is identical on every run', () => {
    expect(generateDemoDataset()).toEqual(dataset)
    expect(generateDemoDataset(1).buckets).not.toEqual(dataset.buckets)
  })

  it('is a believable household: two TVs, eight apps in three categories, twelve weeks', () => {
    expect(dataset.devices).toHaveLength(2)
    const resolve = appDirectory(dataset.apps)
    const apps = appShares(dataset.buckets, resolve)
    expect(apps).toHaveLength(8)
    expect(categoryShares(apps)).toHaveLength(3)
    const days = new Set(dataset.buckets.screen.map((r) => r.day))
    expect(Math.min(...[...days].map((d) => Date.parse(d)))).toBeGreaterThanOrEqual(
      Date.parse(addDays(DEMO_TODAY, -DEMO_WEEKS * 7)),
    )
    expect(days.has(DEMO_TODAY)).toBe(true)
  })

  it('peaks in the evening and binges on weekends', () => {
    const screen = dataset.buckets.screen
    const at = (hours: number[]) => screen.filter((r) => hours.includes(r.hour)).reduce((a, r) => a + r.screenOnMs, 0)
    expect(at([19, 20, 21, 22])).toBeGreaterThan(at([6, 7, 8, 9]) * 4)
    const found = alerts(dataset.buckets)
    const binges = found.filter((a) => a.kind === 'binge')
    expect(binges.length).toBeGreaterThan(5)
    expect(binges.filter((a) => weekdayIndex(a.day) >= 5).length / binges.length).toBeGreaterThan(0.7)
    expect(found.some((a) => a.kind === 'idle')).toBe(true)
  })

  it('only holds rows the sync schema would accept', () => {
    const strip = <T extends { deviceId: string }>({ deviceId: _deviceId, ...rest }: T) => rest
    const payload = {
      schemaVersion: SCHEMA_VERSION,
      sentAt: '2026-11-08T20:00:00Z',
      timezone: dataset.settings.timezone,
      usage: dataset.buckets.usage.slice(0, 2000).map(strip),
      keys: dataset.buckets.keys.slice(0, 2000).map(strip),
      screen: dataset.buckets.screen.slice(0, 2000).map(strip),
    }
    expect(syncPayloadSchema.safeParse(payload).success).toBe(true)
  })
})

describe('demo source', () => {
  const source = createDemoSource()

  it('serves a fixed clock and filters buckets by span and device', async () => {
    expect(await source.today()).toBe(DEMO_TODAY)
    expect(source.now().toISOString().startsWith(DEMO_TODAY)).toBe(true)
    const span = { from: '2026-11-02', to: DEMO_TODAY }
    const all = await source.buckets(span, null)
    const living = await source.buckets(span, ['demo-living-room'])
    expect(all.screen.every((r) => r.day >= span.from && r.day <= span.to)).toBe(true)
    expect(totals(living).screenOnMs).toBeLessThan(totals(all).screenOnMs)
    expect(await source.shareLinks()).toEqual([])
    expect(await source.goals()).toHaveLength(5)
    expect(await source.members()).toHaveLength(2)
    expect((await source.settings()).timezone).toBe('Europe/Bratislava')
  })

  it('narrows a share to one device and hides goals and people', async () => {
    const shared = sharedSource(source, 'demo-bedroom')
    const span = { from: '2026-11-02', to: DEMO_TODAY }
    expect(shared.kind).toBe('shared')
    expect((await shared.devices()).map((d) => d.id)).toEqual(['demo-bedroom'])
    expect(await shared.goals()).toEqual([])
    expect(await shared.members()).toEqual([])
    expect(await shared.shareLinks()).toEqual([])
    const all = await shared.buckets(span, null)
    expect(all.screen.every((r) => r.deviceId === 'demo-bedroom')).toBe(true)
    expect(await shared.buckets(span, ['demo-living-room'])).toEqual({ usage: [], keys: [], screen: [] })
    expect(totals(await shared.buckets(span, ['demo-bedroom']))).toEqual(totals(all))
    expect(await shared.today()).toBe(DEMO_TODAY)
    const sharedApps = await shared.apps(null)
    const used = new Set(all.usage.map((r) => r.packageName))
    expect(sharedApps.length).toBeGreaterThan(0)
    expect(sharedApps.length).toBeLessThan(8)
    expect(await shared.apps(['demo-living-room'])).toEqual([])
    const ever = await source.buckets({ from: '2026-01-01', to: DEMO_TODAY }, ['demo-bedroom'])
    const everUsed = new Set(ever.usage.map((r) => r.packageName))
    expect(sharedApps.every((app) => everUsed.has(app.packageName))).toBe(true)
    expect([...used].every((p) => sharedApps.some((app) => app.packageName === p))).toBe(true)
    expect((await shared.settings()).locale).toBe('en')
    expect(shared.now()).toEqual(source.now())
  })

  it('shares all devices when the link has no device', async () => {
    const shared = sharedSource(source, null)
    expect(await shared.devices()).toHaveLength(2)
    expect(await shared.apps(null)).toHaveLength(8)
    const span = { from: DEMO_TODAY, to: DEMO_TODAY }
    expect(await shared.buckets(span, null)).toEqual(selectBuckets(await source.buckets(span, null), {}))
  })
})
