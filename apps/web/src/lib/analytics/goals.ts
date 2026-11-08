import { startOfWeek } from '../time/days'
import type { DayRange } from '../time/range'
import type { AppInfo, Buckets, Device, Goal, GoalMetric, GoalScope } from '../data/types'
import { selectBuckets, totals } from './aggregate'

export type GoalStatus = 'ok' | 'near' | 'over'

export interface GoalProgress {
  goal: Goal
  used: number
  ratio: number
  status: GoalStatus
}

/** From this share of the limit on, a goal shows as "near". */
export const NEAR_RATIO = 0.8

/** App and category goals can only measure app time; the other metrics are not attributable to an app. */
export function metricsForScope(scope: GoalScope): readonly GoalMetric[] {
  return scope === 'app' || scope === 'category' ? ['app_time'] : ['screen_on', 'app_time', 'keypresses']
}

/** The days a goal currently counts: today, or this week from Monday to today. */
export function goalWindow(goal: Pick<Goal, 'period'>, today: string): DayRange {
  return goal.period === 'day'
    ? { kind: 'day', from: today, to: today }
    : { kind: 'week', from: startOfWeek(today), to: today }
}

function scopedBuckets(goal: Goal, buckets: Buckets, devices: readonly Device[], resolve: (p: string) => AppInfo) {
  switch (goal.scope) {
    case 'device':
      return selectBuckets(buckets, { deviceIds: [goal.target] })
    case 'member':
      return selectBuckets(buckets, {
        deviceIds: devices.filter((d) => d.memberId === goal.target).map((d) => d.id),
      })
    case 'app':
      return { ...buckets, usage: buckets.usage.filter((r) => r.packageName === goal.target) }
    case 'category':
      return { ...buckets, usage: buckets.usage.filter((r) => resolve(r.packageName).category === goal.target) }
  }
}

/** Progress of a goal against buckets that cover at least its current window. */
export function evaluateGoal(
  goal: Goal,
  buckets: Buckets,
  context: { today: string; devices: readonly Device[]; resolve: (packageName: string) => AppInfo },
): GoalProgress {
  const window = goalWindow(goal, context.today)
  const inWindow = selectBuckets(buckets, { from: window.from, to: window.to })
  const sums = totals(scopedBuckets(goal, inWindow, context.devices, context.resolve))
  const used = goal.metric === 'screen_on' ? sums.screenOnMs : goal.metric === 'app_time' ? sums.appMs : sums.keypresses
  const ratio = goal.limit > 0 ? used / goal.limit : 0
  return { goal, used, ratio, status: ratio >= 1 ? 'over' : ratio >= NEAR_RATIO ? 'near' : 'ok' }
}
