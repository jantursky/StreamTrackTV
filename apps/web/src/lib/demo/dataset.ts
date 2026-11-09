import type { KeyClass } from 'streamtrack-tv'
import { addDays, weekdayIndex } from '../time/days'
import type { AppRecord, Buckets, Device, Goal, KeyRow, Member, SecurityEventRecord, Settings } from '../data/types'
import { createRandom, type Random } from './prng'

export const DEMO_TODAY = '2026-11-08'
export const DEMO_SEED = 20_261_108
export const DEMO_WEEKS = 12

export interface DemoDataset {
  today: string
  settings: Settings
  members: Member[]
  devices: Device[]
  apps: AppRecord[]
  goals: Goal[]
  buckets: Buckets
  events: SecurityEventRecord[]
}

const MINUTE = 60_000
const HOUR = 60 * MINUTE

const MEMBERS: Member[] = [
  { id: 'demo-alex', name: 'Alex' },
  { id: 'demo-mia', name: 'Mia' },
]

const APPS: AppRecord[] = [
  { packageName: 'com.netflix.ninja', label: 'Netflix', category: 'streaming' },
  { packageName: 'com.disney.disneyplus', label: 'Disney+', category: 'streaming' },
  { packageName: 'com.amazon.amazonvideo.livingroom', label: 'Prime Video', category: 'streaming' },
  { packageName: 'com.wbd.stream', label: 'Max', category: 'streaming' },
  { packageName: 'com.google.android.youtube.tv', label: 'YouTube', category: 'video' },
  { packageName: 'tv.twitch.android.app', label: 'Twitch', category: 'video' },
  { packageName: 'com.plexapp.android', label: 'Plex', category: 'video' },
  { packageName: 'com.spotify.tv.android', label: 'Spotify', category: 'music' },
]

type Weights = readonly (readonly [string, number])[]

interface Profile {
  device: Device
  /** Probability that the TV is in use during each hour, weekday and weekend. */
  weekday: readonly number[]
  weekend: readonly number[]
  apps: Weights
  bingeApps: Weights
  bingeChance: number
  idleChance: number
}

const curve = (points: Record<number, number>) => Array.from({ length: 24 }, (_, h) => points[h] ?? 0)

const PROFILES: Profile[] = [
  {
    device: {
      id: 'demo-living-room',
      name: 'Living room TV',
      model: 'Chromecast with Google TV',
      memberId: 'demo-alex',
      lastSeenAt: `${DEMO_TODAY}T21:12:00Z`,
      revokedAt: null,
      createdAt: '2026-08-10T18:30:00Z',
    },
    weekday: curve({ 7: 0.12, 8: 0.08, 17: 0.15, 18: 0.35, 19: 0.6, 20: 0.85, 21: 0.9, 22: 0.6, 23: 0.25, 0: 0.06 }),
    weekend: curve({
      9: 0.25,
      10: 0.35,
      11: 0.3,
      14: 0.4,
      15: 0.45,
      16: 0.4,
      17: 0.35,
      18: 0.5,
      19: 0.7,
      20: 0.9,
      21: 0.9,
      22: 0.75,
      23: 0.4,
      0: 0.15,
    }),
    apps: [
      ['com.netflix.ninja', 30],
      ['com.disney.disneyplus', 14],
      ['com.amazon.amazonvideo.livingroom', 10],
      ['com.wbd.stream', 8],
      ['com.google.android.youtube.tv', 22],
      ['com.plexapp.android', 6],
      ['com.spotify.tv.android', 10],
    ],
    bingeApps: [
      ['com.netflix.ninja', 5],
      ['com.disney.disneyplus', 3],
      ['com.wbd.stream', 2],
    ],
    bingeChance: 0.7,
    idleChance: 0.06,
  },
  {
    device: {
      id: 'demo-bedroom',
      name: 'Bedroom TV',
      model: 'Sony Bravia',
      memberId: 'demo-mia',
      lastSeenAt: `${DEMO_TODAY}T22:40:00Z`,
      revokedAt: null,
      createdAt: '2026-08-12T20:05:00Z',
    },
    weekday: curve({ 16: 0.2, 17: 0.25, 20: 0.3, 21: 0.55, 22: 0.75, 23: 0.6, 0: 0.3, 1: 0.1 }),
    weekend: curve({ 11: 0.3, 12: 0.35, 13: 0.4, 14: 0.3, 20: 0.4, 21: 0.6, 22: 0.8, 23: 0.7, 0: 0.45, 1: 0.2 }),
    apps: [
      ['com.google.android.youtube.tv', 34],
      ['tv.twitch.android.app', 22],
      ['com.netflix.ninja', 18],
      ['com.disney.disneyplus', 8],
      ['com.spotify.tv.android', 12],
    ],
    bingeApps: [
      ['tv.twitch.android.app', 3],
      ['com.netflix.ninja', 2],
    ],
    bingeChance: 0.35,
    idleChance: 0.02,
  },
]

const GOALS: Goal[] = [
  {
    id: 'demo-goal-living',
    scope: 'device',
    target: 'demo-living-room',
    metric: 'screen_on',
    period: 'day',
    limit: 6 * HOUR,
  },
  {
    id: 'demo-goal-youtube',
    scope: 'app',
    target: 'com.google.android.youtube.tv',
    metric: 'app_time',
    period: 'day',
    limit: 3 * HOUR,
  },
  {
    id: 'demo-goal-streaming',
    scope: 'category',
    target: 'streaming',
    metric: 'app_time',
    period: 'week',
    limit: 24 * HOUR,
  },
  { id: 'demo-goal-mia', scope: 'member', target: 'demo-mia', metric: 'screen_on', period: 'week', limit: 21 * HOUR },
  { id: 'demo-goal-remote', scope: 'device', target: 'demo-bedroom', metric: 'keypresses', period: 'day', limit: 1500 },
]

/** One hour of use, with up to two apps sharing it. */
interface Hour {
  day: string
  hour: number
  apps: [string, number][]
  screenOnMs: number
  keys: boolean
}

function keyRows(random: Random, deviceId: string, slot: Hour): KeyRow[] {
  const minutes = slot.apps.reduce((acc, [, ms]) => acc + ms, 0) / MINUTE
  const dpad = Math.round(minutes * (0.6 + random.next() * 1.6))
  const counts: Record<KeyClass, number> = {
    dpad,
    select: Math.round(dpad * (0.2 + random.next() * 0.15)),
    back: Math.round(dpad * (0.1 + random.next() * 0.1)),
    home: random.int(0, 2),
    media: Math.round(minutes * random.next() * 0.15),
    volume: random.int(0, 6),
    other: random.int(0, 1),
  }
  return Object.entries(counts)
    .filter(([, count]) => count > 0)
    .map(([keyClass, count]) => ({ deviceId, day: slot.day, hour: slot.hour, keyClass: keyClass as KeyClass, count }))
}

function regularHour(random: Random, profile: Profile, day: string, hour: number): Hour {
  const first = random.weighted(profile.apps)
  const minutes = random.int(15, 58)
  const apps: [string, number][] = [[first, minutes * MINUTE]]
  if (minutes < 45 && random.chance(0.3)) {
    const second = random.weighted(profile.apps)
    if (second !== first) apps.push([second, random.int(3, 60 - minutes - 1) * MINUTE])
  }
  const used = apps.reduce((acc, [, ms]) => acc + ms, 0)
  return { day, hour, apps, screenOnMs: Math.min(HOUR, used + random.int(1, 4) * MINUTE), keys: true }
}

function dayHours(random: Random, profile: Profile, day: string): Hour[] {
  const weekday = weekdayIndex(day)
  const weekend = weekday >= 5
  const hours = new Map<number, Hour>()
  const curveFor = weekend ? profile.weekend : profile.weekday
  curveFor.forEach((probability, hour) => {
    if (random.chance(probability)) hours.set(hour, regularHour(random, profile, day, hour))
  })
  // Weekend binge: one show for three to five hours straight.
  if (weekend && random.chance(profile.bingeChance)) {
    const app = random.weighted(profile.bingeApps)
    const start = random.chance(0.5) ? random.int(13, 16) : random.int(19, 20)
    const length = random.int(3, 5)
    for (let hour = start; hour < Math.min(24, start + length); hour++) {
      const ms = random.int(55, 60) * MINUTE
      hours.set(hour, { day, hour, apps: [[app, ms]], screenOnMs: HOUR, keys: true })
    }
  }
  // TV left on after everyone went to bed: screen on, no app, no remote.
  if (random.chance(profile.idleChance)) {
    const lastHour = random.int(3, 5)
    for (let hour = 1; hour <= lastHour; hour++) {
      hours.set(hour, { day, hour, apps: [], screenOnMs: HOUR, keys: false })
    }
  }
  return [...hours.values()].sort((a, b) => a.hour - b.hour)
}

/**
 * A short story for the Security page: the TV opens normally, then three wrong PINs, the wait, Forgot PIN
 * and the reset that follows, then a new PIN. Times are fixed on the demo day.
 */
function demoEvents(today: string): SecurityEventRecord[] {
  const deviceId = 'demo-living-room'
  const at = (day: string, time: string) => `${day}T${time}Z`
  const yesterday = addDays(today, -1)
  const story: [string, string, SecurityEventRecord['type'], number | null][] = [
    [yesterday, '17:58:10', 'app_opened', null],
    [yesterday, '17:58:11', 'lock_shown', null],
    [yesterday, '17:58:31', 'unlocked', null],
    [today, '21:12:02', 'app_opened', null],
    [today, '21:12:03', 'lock_shown', null],
    [today, '21:12:20', 'pin_wrong', 1],
    [today, '21:12:26', 'pin_wrong', 2],
    [today, '21:12:33', 'pin_wrong', 3],
    [today, '21:12:33', 'pin_wait_started', 30],
    [today, '21:13:15', 'pin_wrong', 4],
    [today, '21:13:15', 'pin_wait_started', 60],
    [today, '21:14:30', 'forgot_pin_opened', null],
    [today, '21:16:45', 'pin_reset_confirmed', null],
    [today, '21:17:20', 'pin_enabled', null],
  ]
  return story.map(([day, time, type, value], index) => ({
    id: `demo-event-${index + 1}`,
    deviceId,
    type,
    at: at(day, time),
    receivedAt: at(day, time),
    value,
  }))
}

/**
 * A believable household: two TVs, eight apps in three categories, evening peaks, weekend binges and a
 * few nights where a TV stayed on. Pure and deterministic for a given seed, so screenshots and e2e tests
 * always see the same numbers.
 */
export function generateDemoDataset(seed = DEMO_SEED, today = DEMO_TODAY, weeks = DEMO_WEEKS): DemoDataset {
  const buckets: Buckets = { usage: [], keys: [], screen: [] }
  const days = weeks * 7
  PROFILES.forEach((profile, index) => {
    const random = createRandom(seed + index * 7919)
    const deviceId = profile.device.id
    for (let offset = days - 1; offset >= 0; offset--) {
      for (const slot of dayHours(random, profile, addDays(today, -offset))) {
        for (const [packageName, foregroundMs] of slot.apps) {
          buckets.usage.push({ deviceId, day: slot.day, hour: slot.hour, packageName, foregroundMs })
        }
        buckets.screen.push({ deviceId, day: slot.day, hour: slot.hour, screenOnMs: slot.screenOnMs })
        if (slot.keys) buckets.keys.push(...keyRows(random, deviceId, slot))
      }
    }
  })
  return {
    today,
    settings: {
      timezone: 'Europe/Bratislava',
      timezoneSource: 'account',
      locale: 'en',
      retentionDays: 365,
      digestEnabled: true,
    },
    members: MEMBERS,
    devices: PROFILES.map((p) => p.device),
    apps: APPS,
    goals: GOALS,
    buckets,
    events: demoEvents(today),
  }
}
