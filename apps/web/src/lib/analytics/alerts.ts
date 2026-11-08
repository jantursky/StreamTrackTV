import type { Buckets } from '../data/types'

/** An hour counts as "busy" for one app or as "on" for the screen from this much time in it. */
const FULL_HOUR_MS = 45 * 60_000
/** Below this much app time and with no key presses, a screen-on hour counts as idle. */
const IDLE_APP_MS = 5 * 60_000
export const IDLE_MIN_HOURS = 2
export const BINGE_MIN_HOURS = 3

export type Alert =
  | { kind: 'idle'; deviceId: string; day: string; startHour: number; hours: number }
  | { kind: 'binge'; deviceId: string; packageName: string; day: string; startHour: number; hours: number }

/** Absolute hour index, so runs can continue across midnight. */
const hourIndex = (day: string, hour: number) => Date.parse(`${day}T00:00:00Z`) / 3_600_000 + hour

interface Slot {
  deviceId: string
  day: string
  hour: number
  key: string
}

interface Run {
  first: Slot
  hours: number
}

/** Groups slots into runs of consecutive hours per device and key, keeping runs of at least `minHours`. */
function runs(slots: Slot[], minHours: number): Run[] {
  const sorted = [...slots].sort(
    (a, b) =>
      a.deviceId.localeCompare(b.deviceId) ||
      a.key.localeCompare(b.key) ||
      hourIndex(a.day, a.hour) - hourIndex(b.day, b.hour),
  )
  const result: Run[] = []
  let current: Run | null = null
  let last: Slot | null = null
  for (const slot of sorted) {
    const continues =
      last?.deviceId === slot.deviceId &&
      last.key === slot.key &&
      hourIndex(slot.day, slot.hour) === hourIndex(last.day, last.hour) + 1
    if (current && continues) {
      current.hours++
    } else {
      if (current && current.hours >= minHours) result.push(current)
      current = { first: slot, hours: 1 }
    }
    last = slot
  }
  if (current && current.hours >= minHours) result.push(current)
  return result
}

const slotId = (deviceId: string, day: string, hour: number) => `${deviceId}|${day}|${hour}`

/** Hours where the screen was on but nobody watched anything or touched the remote. */
export function idleAlerts(buckets: Buckets): Alert[] {
  const appMs = new Map<string, number>()
  for (const r of buckets.usage) {
    const id = slotId(r.deviceId, r.day, r.hour)
    appMs.set(id, (appMs.get(id) ?? 0) + r.foregroundMs)
  }
  const pressed = new Set(buckets.keys.filter((r) => r.count > 0).map((r) => slotId(r.deviceId, r.day, r.hour)))
  const idle = buckets.screen
    .filter((r) => {
      const id = slotId(r.deviceId, r.day, r.hour)
      return r.screenOnMs >= FULL_HOUR_MS && (appMs.get(id) ?? 0) < IDLE_APP_MS && !pressed.has(id)
    })
    .map((r) => ({ deviceId: r.deviceId, day: r.day, hour: r.hour, key: 'screen' }))
  return runs(idle, IDLE_MIN_HOURS).map(({ first, hours }) => ({
    kind: 'idle',
    deviceId: first.deviceId,
    day: first.day,
    startHour: first.hour,
    hours,
  }))
}

/** Long uninterrupted stretches in one app. */
export function bingeAlerts(buckets: Buckets): Alert[] {
  const busy = buckets.usage
    .filter((r) => r.foregroundMs >= FULL_HOUR_MS)
    .map((r) => ({ deviceId: r.deviceId, day: r.day, hour: r.hour, key: r.packageName }))
  return runs(busy, BINGE_MIN_HOURS).map(({ first, hours }) => ({
    kind: 'binge',
    deviceId: first.deviceId,
    packageName: first.key,
    day: first.day,
    startHour: first.hour,
    hours,
  }))
}

/** All alerts, newest first. */
export function alerts(buckets: Buckets): Alert[] {
  return [...idleAlerts(buckets), ...bingeAlerts(buckets)].sort(
    (a, b) => hourIndex(b.day, b.startHour) - hourIndex(a.day, a.startHour),
  )
}
