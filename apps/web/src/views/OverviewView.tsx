import { BarChart } from '@/components/charts/BarChart'
import { RatioBar } from '@/components/charts/RatioBar'
import { Sparkline } from '@/components/charts/Sparkline'
import { durationTicks } from '@/components/charts/scale'
import { GoalList, goalTargetName } from '@/components/dashboard/GoalList'
import { RangeBar } from '@/components/dashboard/RangeBar'
import { Headline } from '@/components/ui/Headline'
import { Empty, Kpi, Panel } from '@/components/ui/Panel'
import {
  appShares,
  categoryShares,
  change,
  screenByDay,
  screenByHour,
  selectBuckets,
  totals,
} from '@/lib/analytics/aggregate'
import { alerts, type Alert } from '@/lib/analytics/alerts'
import { evaluateGoal } from '@/lib/analytics/goals'
import {
  formatDay,
  formatDuration,
  formatDurationShort,
  formatHour,
  formatNumber,
  formatPercent,
} from '@/lib/i18n/format'
import type { Translate } from '@/lib/i18n/translate'
import { addDays, startOfWeek } from '@/lib/time/days'
import { parseRange, previousPeriod, rangeDays } from '@/lib/time/range'
import { viewContext, type ViewContext } from '@/lib/workspace'
import type { ViewProps } from './types'

const MAX_ALERTS = 5
const TOP_APPS = 6

function alertText(alert: Alert, ctx: ViewContext, t: Translate, locale: string): string {
  const vars = {
    device: ctx.devices.find((d) => d.id === alert.deviceId)?.name ?? t('common.unknown'),
    hours: alert.hours,
    start: formatHour(alert.startHour),
    day: formatDay(alert.day, locale),
  }
  return alert.kind === 'idle'
    ? t('overview.idleAlert', vars)
    : t('overview.bingeAlert', { ...vars, app: ctx.resolve(alert.packageName).label })
}

export async function OverviewView({ ws, params, path }: ViewProps) {
  const { t, locale, source } = ws
  const ctx = await viewContext(ws, params)
  const range = parseRange(params, ctx.today)
  const previous = previousPeriod(range)
  const days = rangeDays(range)
  // A single day still gets a two-week trend line.
  const trendDays = days.length > 1 ? days : rangeDays({ ...range, from: addDays(range.to, -13) })
  const fetchFrom = [previous.from, trendDays[0] ?? range.from].sort()[0] ?? range.from

  const [buckets, goals, members] = await Promise.all([
    source.buckets({ from: fetchFrom, to: range.to }, ctx.deviceIds),
    source.goals(),
    source.members(),
  ])
  const goalBuckets =
    goals.length > 0 ? await source.buckets({ from: startOfWeek(ctx.today), to: ctx.today }, null) : null

  const current = selectBuckets(buckets, range)
  const sums = totals(current)
  const before = totals(selectBuckets(buckets, previous))
  const delta = change(sums.screenOnMs, before.screenOnMs)
  const daily = screenByDay(current, days)
  const trend = screenByDay(buckets, trendDays)
  const apps = appShares(current, ctx.resolve)
  const categories = categoryShares(apps)
  const recent = alerts(current).slice(0, MAX_ALERTS)
  const progress = goalBuckets ? goals.map((g) => evaluateGoal(g, goalBuckets, ctx)) : []

  const chart =
    range.kind === 'day'
      ? {
          title: t('overview.hourly'),
          labels: Array.from({ length: 24 }, (_, h) => formatHour(h)),
          values: screenByHour(current),
          every: 3,
        }
      : {
          title: t('overview.trend'),
          labels: days.map((d) => formatDay(d, locale)),
          values: daily,
          every: Math.ceil(days.length / 10),
        }
  const isToday = range.kind === 'day' && range.from === ctx.today

  return (
    <div className="flex flex-col gap-6">
      <Headline
        text={t('overview.headline', { period: isToday ? t('overview.periodToday') : t('overview.periodRange') })}
        highlight={formatDuration(sums.screenOnMs, t)}
      />
      <RangeBar ws={ws} ctx={ctx} path={path} params={params} range={range} />

      <div className="grid grid-cols-2 gap-3 lg:grid-cols-4" data-testid="kpis">
        <Kpi
          label={t('kpi.screenOn')}
          value={formatDuration(sums.screenOnMs, t)}
          detail={
            delta === null ? t('kpi.noPrevious') : t('kpi.vsPrevious', { change: formatPercent(delta, locale, true) })
          }
        />
        <Kpi label={t('kpi.appTime')} value={formatDuration(sums.appMs, t)} />
        <Kpi label={t('kpi.keypresses')} value={formatNumber(sums.keypresses, locale)} />
        <Kpi label={t('kpi.dailyAverage')} value={formatDuration(sums.screenOnMs / days.length, t)} />
      </div>

      <div className="grid gap-6 lg:grid-cols-3">
        <Panel
          title={chart.title}
          className="lg:col-span-2"
          aside={<Sparkline values={trend} label={t('overview.trend')} className="h-8 w-32" />}
        >
          <BarChart
            caption={chart.title}
            labels={chart.labels}
            series={[{ name: t('kpi.screenOn'), tone: 0, values: chart.values }]}
            ticks={durationTicks(Math.max(...chart.values, 1))}
            format={(v) => formatDurationShort(v, t)}
            labelEvery={chart.every}
          />
        </Panel>

        <Panel title={t('overview.topApps')}>
          {apps.length === 0 ? (
            <Empty>{t('common.noData')}</Empty>
          ) : (
            <ul className="flex flex-col gap-3" data-testid="top-apps">
              {apps.slice(0, TOP_APPS).map((app) => (
                <li key={app.packageName} className="flex flex-col gap-1">
                  <div className="flex items-baseline justify-between gap-3">
                    <span className="truncate text-ink">{app.label}</span>
                    <span className="text-small whitespace-nowrap tabular-nums">{formatDuration(app.ms, t)}</span>
                  </div>
                  <RatioBar ratio={app.ms / (apps[0]?.ms ?? 1)} />
                </li>
              ))}
            </ul>
          )}
        </Panel>

        {source.kind !== 'shared' && (
          <Panel title={t('overview.goals')}>
            {progress.length === 0 ? (
              <Empty>{t('overview.goalsEmpty')}</Empty>
            ) : (
              <GoalList
                items={progress}
                targetName={(g) => goalTargetName(g, { devices: ctx.devices, members, resolve: ctx.resolve, t })}
                t={t}
                locale={locale}
              />
            )}
          </Panel>
        )}

        <Panel title={t('overview.alerts')}>
          {recent.length === 0 ? (
            <Empty>{t('overview.alertsEmpty')}</Empty>
          ) : (
            <ul className="flex flex-col divide-y divide-line" data-testid="alerts">
              {recent.map((alert) => (
                <li key={`${alert.kind}-${alert.deviceId}-${alert.day}-${alert.startHour}`} className="py-2 first:pt-0">
                  {alertText(alert, ctx, t, locale)}
                </li>
              ))}
            </ul>
          )}
        </Panel>

        <Panel title={t('overview.categories')}>
          {categories.length === 0 ? (
            <Empty>{t('common.noData')}</Empty>
          ) : (
            <ul className="flex flex-col gap-3">
              {categories.map((c) => (
                <li key={c.category} className="flex flex-col gap-1">
                  <div className="flex items-baseline justify-between gap-3">
                    <span className="text-ink">{t(`categories.${c.category}`)}</span>
                    <span className="text-small tabular-nums">{formatPercent(c.share, locale)}</span>
                  </div>
                  <RatioBar ratio={c.share} tone={2} />
                </li>
              ))}
            </ul>
          )}
        </Panel>
      </div>
    </div>
  )
}
