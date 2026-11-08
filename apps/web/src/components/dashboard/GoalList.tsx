import { RatioBar } from '../charts/RatioBar'
import type { GoalProgress } from '@/lib/analytics/goals'
import { NEAR_RATIO } from '@/lib/analytics/goals'
import { isCategory, type AppInfo, type Device, type Goal, type Member } from '@/lib/data/types'
import { formatDuration, formatNumber } from '@/lib/i18n/format'
import type { Translate } from '@/lib/i18n/translate'

export function formatGoalValue(goal: Goal, value: number, t: Translate, locale: string): string {
  return goal.metric === 'keypresses' ? formatNumber(value, locale) : formatDuration(value, t)
}

/** Readable name of what a goal limits; falls back to the raw id when the target was deleted. */
export function goalTargetName(
  goal: Goal,
  lookup: { devices: readonly Device[]; members: readonly Member[]; resolve: (p: string) => AppInfo; t: Translate },
): string {
  switch (goal.scope) {
    case 'device':
      return lookup.devices.find((d) => d.id === goal.target)?.name ?? lookup.t('common.unknown')
    case 'member':
      return lookup.members.find((m) => m.id === goal.target)?.name ?? lookup.t('common.unknown')
    case 'app':
      return lookup.resolve(goal.target).label
    case 'category':
      return isCategory(goal.target) ? lookup.t(`categories.${goal.target}`) : goal.target
  }
}

const STATUS_TONE = { ok: 'text-muted', near: 'text-warning', over: 'text-error' } as const

/** Progress rows; status is spelled out, color only repeats it. */
export function GoalList({
  items,
  targetName,
  t,
  locale,
}: {
  items: readonly GoalProgress[]
  targetName: (goal: Goal) => string
  t: Translate
  locale: string
}) {
  return (
    <ul className="flex flex-col gap-4">
      {items.map(({ goal, used, ratio, status }) => (
        <li key={goal.id} className="flex flex-col gap-1.5">
          <div className="flex flex-wrap items-baseline justify-between gap-x-3">
            <span className="text-ink">
              {t('goals.summary', {
                metric: t(`goals.metrics.${goal.metric}`),
                target: targetName(goal),
                period: t(`goals.periods.${goal.period}`).toLowerCase(),
              })}
            </span>
            <span className={`text-small ${STATUS_TONE[status]}`}>{t(`goals.status.${status}`)}</span>
          </div>
          <RatioBar ratio={ratio} tone={status === 'ok' ? 0 : 1} marker={NEAR_RATIO} />
          <span className="text-small tabular-nums">
            {t('goals.progress', {
              used: formatGoalValue(goal, used, t, locale),
              limit: formatGoalValue(goal, goal.limit, t, locale),
            })}
          </span>
        </li>
      ))}
    </ul>
  )
}
