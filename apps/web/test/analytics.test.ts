import { describe, expect, it } from 'vitest'
import {
  appShares,
  categoryShares,
  change,
  keysByClass,
  keysByHour,
  screenByDay,
  screenByHour,
  selectBuckets,
  totals,
  weekdayHourAverages,
} from '../src/lib/analytics/aggregate'
import { alerts, bingeAlerts, idleAlerts } from '../src/lib/analytics/alerts'
import { filterApps } from '../src/lib/analytics/apps'
import { evaluateGoal, goalWindow, metricsForScope } from '../src/lib/analytics/goals'
import { appDirectory, resolveApp } from '../src/lib/data/catalog'
import type { Buckets, Device, Goal } from '../src/lib/data/types'
import { createTranslator } from '../src/lib/i18n/translate'

const MIN = 60_000
const HOUR = 60 * MIN

const buckets: Buckets = {
  usage: [
    { deviceId: 'a', day: '2026-11-02', hour: 20, packageName: 'com.netflix.ninja', foregroundMs: 40 * MIN },
    {
      deviceId: 'a',
      day: '2026-11-02',
      hour: 21,
      packageName: 'com.google.android.youtube.tv',
      foregroundMs: 20 * MIN,
    },
    { deviceId: 'b', day: '2026-11-03', hour: 21, packageName: 'com.netflix.ninja', foregroundMs: 20 * MIN },
    { deviceId: 'b', day: '2026-11-03', hour: 22, packageName: 'org.example.unknown', foregroundMs: 20 * MIN },
  ],
  keys: [
    { deviceId: 'a', day: '2026-11-02', hour: 20, keyClass: 'dpad', count: 30 },
    { deviceId: 'a', day: '2026-11-02', hour: 20, keyClass: 'select', count: 5 },
    { deviceId: 'b', day: '2026-11-03', hour: 21, keyClass: 'back', count: 7 },
  ],
  screen: [
    { deviceId: 'a', day: '2026-11-02', hour: 20, screenOnMs: 45 * MIN },
    { deviceId: 'a', day: '2026-11-02', hour: 21, screenOnMs: 25 * MIN },
    { deviceId: 'b', day: '2026-11-03', hour: 21, screenOnMs: 30 * MIN },
    { deviceId: 'b', day: '2026-11-09', hour: 21, screenOnMs: 30 * MIN },
  ],
}

const resolve = appDirectory([{ packageName: 'org.example.unknown', label: 'Example', category: 'games' }])

describe('aggregation', () => {
  it('sums totals', () => {
    expect(totals(buckets)).toEqual({ screenOnMs: 130 * MIN, appMs: 100 * MIN, keypresses: 42 })
  })

  it('filters by device and day', () => {
    const only = selectBuckets(buckets, { deviceIds: ['b'], from: '2026-11-03', to: '2026-11-08' })
    expect(totals(only)).toEqual({ screenOnMs: 30 * MIN, appMs: 40 * MIN, keypresses: 7 })
    expect(totals(selectBuckets(buckets, { deviceIds: null })).screenOnMs).toBe(130 * MIN)
  })

  it('splits screen time by day and hour', () => {
    expect(screenByDay(buckets, ['2026-11-01', '2026-11-02', '2026-11-03'])).toEqual([0, 70 * MIN, 30 * MIN])
    const hours = screenByHour(buckets)
    expect(hours).toHaveLength(24)
    expect(hours[21]).toBe(85 * MIN)
  })

  it('ranks apps with shares and resolves labels and categories', () => {
    const apps = appShares(buckets, resolve)
    expect(apps.map((a) => [a.label, a.ms])).toEqual([
      ['Netflix', 60 * MIN],
      ['Example', 20 * MIN],
      ['YouTube', 20 * MIN],
    ])
    expect(apps[0]?.share).toBeCloseTo(0.6)
    expect(categoryShares(apps)).toEqual([
      { category: 'streaming', ms: 60 * MIN, share: 0.6 },
      { category: 'games', ms: 20 * MIN, share: 0.2 },
      { category: 'video', ms: 20 * MIN, share: 0.2 },
    ])
    expect(appShares({ usage: [], keys: [], screen: [] }, resolve)).toEqual([])
  })

  it('counts keys by class and hour', () => {
    expect(keysByClass(buckets)).toEqual({ dpad: 30, select: 5, back: 7, home: 0, media: 0, volume: 0, other: 0 })
    expect(keysByHour(buckets)[20]?.dpad).toBe(30)
    expect(keysByHour(buckets)[21]?.back).toBe(7)
  })

  it('averages the weekday-hour grid by weekday occurrences', () => {
    const days = ['2026-11-02', '2026-11-03', '2026-11-09'] // Mon, Tue, Mon
    const grid = weekdayHourAverages(buckets, days)
    expect(grid[0]?.[20]).toBe(22.5 * MIN)
    expect(grid[0]?.[21]).toBe(27.5 * MIN)
    expect(grid[1]?.[21]).toBe(30 * MIN)
    expect(grid[6]?.[21]).toBe(0)
  })

  it('computes relative change only with a baseline', () => {
    expect(change(150, 100)).toBe(0.5)
    expect(change(5, 0)).toBeNull()
  })
})

describe('catalog', () => {
  it('prefers user data over defaults over the package name', () => {
    expect(resolveApp('com.netflix.ninja')).toEqual({
      packageName: 'com.netflix.ninja',
      label: 'Netflix',
      category: 'streaming',
    })
    expect(
      resolveApp('com.netflix.ninja', { packageName: 'com.netflix.ninja', label: null, category: 'kids' }).category,
    ).toBe('kids')
    expect(resolveApp('x.y')).toEqual({ packageName: 'x.y', label: 'x.y', category: 'other' })
  })
})

describe('filterApps', () => {
  const t = createTranslator()
  const apps = appShares(buckets, resolve)

  it('searches label and package', () => {
    expect(filterApps(apps, 'net', 'time', t).map((a) => a.label)).toEqual(['Netflix'])
    expect(filterApps(apps, 'YOUTUBE.TV', 'time', t).map((a) => a.label)).toEqual(['YouTube'])
    expect(filterApps(apps, '  ', 'time', t)).toHaveLength(3)
  })

  it('sorts by name and category', () => {
    expect(filterApps(apps, '', 'name', t).map((a) => a.label)).toEqual(['Example', 'Netflix', 'YouTube'])
    expect(filterApps(apps, '', 'category', t).map((a) => a.category)).toEqual(['games', 'streaming', 'video'])
  })
})

describe('alerts', () => {
  const idleNight: Buckets = {
    usage: [{ deviceId: 'a', day: '2026-11-03', hour: 1, packageName: 'x.y', foregroundMs: 2 * MIN }],
    keys: [{ deviceId: 'a', day: '2026-11-03', hour: 3, keyClass: 'dpad', count: 1 }],
    screen: [23, 0, 1, 2, 3].map((hour) => ({
      deviceId: 'a',
      day: hour === 23 ? '2026-11-02' : '2026-11-03',
      hour,
      screenOnMs: HOUR,
    })),
  }

  it('finds idle stretches across midnight and stops at a key press', () => {
    expect(idleAlerts(idleNight)).toEqual([{ kind: 'idle', deviceId: 'a', day: '2026-11-02', startHour: 23, hours: 4 }])
  })

  it('ignores short idle stretches', () => {
    expect(idleAlerts({ ...idleNight, screen: idleNight.screen.slice(0, 1) })).toEqual([])
  })

  it('finds binges of one app and ignores app switches', () => {
    const usage = [19, 20, 21, 22].map((hour) => ({
      deviceId: 'a',
      day: '2026-11-07',
      hour,
      packageName: hour === 22 ? 'other.app' : 'com.netflix.ninja',
      foregroundMs: 55 * MIN,
    }))
    expect(bingeAlerts({ usage, keys: [], screen: [] })).toEqual([
      { kind: 'binge', deviceId: 'a', packageName: 'com.netflix.ninja', day: '2026-11-07', startHour: 19, hours: 3 },
    ])
    const sorted = alerts({ ...idleNight, usage })
    expect(sorted.map((a) => a.kind)).toEqual(['binge', 'idle'])
  })
})

describe('goals', () => {
  const devices: Device[] = [
    { id: 'a', name: 'A', model: null, memberId: 'm1', lastSeenAt: null, revokedAt: null, createdAt: '' },
    { id: 'b', name: 'B', model: null, memberId: null, lastSeenAt: null, revokedAt: null, createdAt: '' },
  ]
  const context = { today: '2026-11-03', devices, resolve }
  const goal = (g: Partial<Goal>): Goal => ({
    id: 'g',
    scope: 'device',
    target: 'a',
    metric: 'screen_on',
    period: 'week',
    limit: HOUR,
    ...g,
  })

  it('measures within the window of the period', () => {
    expect(goalWindow({ period: 'day' }, '2026-11-04')).toEqual({ kind: 'day', from: '2026-11-04', to: '2026-11-04' })
    expect(goalWindow({ period: 'week' }, '2026-11-04')).toEqual({ kind: 'week', from: '2026-11-02', to: '2026-11-04' })
    expect(evaluateGoal(goal({ period: 'day' }), buckets, context).used).toBe(0)
  })

  it('reports ok, near and over', () => {
    expect(evaluateGoal(goal({}), buckets, context)).toMatchObject({ used: 70 * MIN, status: 'over' })
    expect(evaluateGoal(goal({ limit: 80 * MIN }), buckets, context).status).toBe('near')
    expect(evaluateGoal(goal({ limit: 3 * HOUR }), buckets, context).status).toBe('ok')
  })

  it('scopes by member, app and category', () => {
    expect(
      evaluateGoal(goal({ scope: 'member', target: 'm1', metric: 'keypresses', limit: 100 }), buckets, context).used,
    ).toBe(35)
    expect(
      evaluateGoal(goal({ scope: 'app', target: 'com.netflix.ninja', metric: 'app_time' }), buckets, context).used,
    ).toBe(60 * MIN)
    expect(evaluateGoal(goal({ scope: 'category', target: 'games', metric: 'app_time' }), buckets, context).used).toBe(
      20 * MIN,
    )
  })

  it('allows only app time for app and category goals', () => {
    expect(metricsForScope('app')).toEqual(['app_time'])
    expect(metricsForScope('device')).toContain('keypresses')
  })
})
