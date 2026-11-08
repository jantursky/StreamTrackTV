import Link from 'next/link'
import { BarChart } from '@/components/charts/BarChart'
import { Legend } from '@/components/charts/Legend'
import { MonthCalendar } from '@/components/charts/MonthCalendar'
import { durationTicks } from '@/components/charts/scale'
import { DeviceFilter } from '@/components/client/DeviceFilter'
import { Headline } from '@/components/ui/Headline'
import { Empty, Panel } from '@/components/ui/Panel'
import { appShares, screenByHour, selectBuckets, totals } from '@/lib/analytics/aggregate'
import type { Buckets } from '@/lib/data/types'
import {
  formatDay,
  formatDuration,
  formatDurationShort,
  formatHour,
  formatMonth,
  formatNumber,
  formatSpanLabel,
} from '@/lib/i18n/format'
import { addDays, addMonths, isIsoDay, isIsoMonth, lastDayOfMonth, monthGrid, monthOf } from '@/lib/time/days'
import { formatSpan, param, parseSpan, rangeLength, type DayRange } from '@/lib/time/range'
import { withParams } from '@/lib/url'
import { viewContext, type ViewContext, type Workspace } from '@/lib/workspace'
import type { ViewProps } from './types'

const KEEP = ['device', 'month', 'day', 'a', 'b'] as const
const HOURS = Array.from({ length: 24 }, (_, h) => formatHour(h))

/** Average screen time per hour of day over the days of a span. */
const hourlyAverage = (buckets: Buckets, span: DayRange) => screenByHour(buckets).map((ms) => ms / rangeLength(span))

function SpanSummary({
  ws,
  ctx,
  span,
  buckets,
  titled = true,
}: {
  ws: Workspace
  ctx: ViewContext
  span: DayRange
  buckets: Buckets
  /** The day panel already names the day in its heading. */
  titled?: boolean
}) {
  const { t, locale } = ws
  const sums = totals(buckets)
  const apps = appShares(buckets, ctx.resolve).slice(0, 5)
  return (
    <div className="flex min-w-0 flex-col gap-3">
      {titled && <p className="text-ink">{formatSpanLabel(span, locale)}</p>}
      <dl className="grid grid-cols-3 gap-2 text-small">
        <div>
          <dt>{t('kpi.screenOn')}</dt>
          <dd className="text-body-lg text-ink tabular-nums">{formatDuration(sums.screenOnMs, t)}</dd>
        </div>
        <div>
          <dt>{t('kpi.appTime')}</dt>
          <dd className="text-body-lg text-ink tabular-nums">{formatDuration(sums.appMs, t)}</dd>
        </div>
        <div>
          <dt>{t('kpi.keypresses')}</dt>
          <dd className="text-body-lg text-ink tabular-nums">{formatNumber(sums.keypresses, locale)}</dd>
        </div>
      </dl>
      {apps.length === 0 ? (
        <Empty>{t('common.noData')}</Empty>
      ) : (
        <ol className="flex flex-col divide-y divide-line">
          {apps.map((app) => (
            <li key={app.packageName} className="flex justify-between gap-3 py-1.5">
              <span className="truncate text-ink">{app.label}</span>
              <span className="text-small tabular-nums">{formatDuration(app.ms, t)}</span>
            </li>
          ))}
        </ol>
      )}
    </div>
  )
}

export async function CalendarView({ ws, params, path }: ViewProps) {
  const { t, locale, source } = ws
  const ctx = await viewContext(ws, params)
  const dayParam = param(params, 'day')
  const day = dayParam !== undefined && isIsoDay(dayParam) && dayParam <= ctx.today ? dayParam : null
  const monthParam = param(params, 'month')
  const month = monthParam !== undefined && isIsoMonth(monthParam) ? monthParam : monthOf(day ?? ctx.today)
  const monthEnd = lastDayOfMonth(month)
  const a = parseSpan(param(params, 'a'), ctx.today)
  const b = parseSpan(param(params, 'b'), ctx.today)
  const compareRequested = param(params, 'a') !== undefined || param(params, 'b') !== undefined

  const [monthBuckets, dayBuckets, aBuckets, bBuckets] = await Promise.all([
    source.buckets({ from: `${month}-01`, to: monthEnd < ctx.today ? monthEnd : ctx.today }, ctx.deviceIds),
    day ? source.buckets({ from: day, to: day }, ctx.deviceIds) : null,
    a ? source.buckets(a, ctx.deviceIds) : null,
    b ? source.buckets(b, ctx.deviceIds) : null,
  ])

  const perDay = new Map<string, number>()
  for (const row of monthBuckets.screen) perDay.set(row.day, (perDay.get(row.day) ?? 0) + row.screenOnMs)
  const link = (overrides: Record<string, string | null>) => withParams(path, params, overrides, KEEP)
  const weekdays = (['mon', 'tue', 'wed', 'thu', 'fri', 'sat', 'sun'] as const).map((w) => t(`calendar.weekday.${w}`))
  const nextMonth = addMonths(month, 1)
  const stepClass = 'tab'

  return (
    <div className="flex flex-col gap-6">
      <Headline text={t('calendar.headline')} highlight={formatMonth(month, locale)} />

      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex gap-2">
          <Link href={link({ month: addMonths(month, -1), day: null })} className={stepClass} scroll={false}>
            {t('calendar.previousMonth')}
          </Link>
          {`${nextMonth}-01` <= ctx.today && (
            <Link href={link({ month: nextMonth, day: null })} className={stepClass} scroll={false}>
              {t('calendar.nextMonth')}
            </Link>
          )}
        </div>
        {ctx.devices.length > 1 && (
          <DeviceFilter
            label={t('range.device')}
            allLabel={t('common.allDevices')}
            devices={ctx.devices}
            value={ctx.deviceIds?.[0] ?? null}
          />
        )}
      </div>

      <div className="grid gap-6 lg:grid-cols-5">
        <Panel title={formatMonth(month, locale)} className="lg:col-span-3">
          <MonthCalendar
            caption={formatMonth(month, locale)}
            weekdays={weekdays}
            weeks={monthGrid(month)}
            values={perDay}
            today={ctx.today}
            selected={new Set(day ? [day] : [])}
            hrefFor={(d) => link({ day: d, month })}
            cellLabel={(d, v) =>
              t('calendar.cellLabel', { day: formatDay(d, locale, 'long'), value: formatDuration(v, t) })
            }
          />
        </Panel>

        <Panel title={day ? formatDay(day, locale, 'long') : t('calendar.dayDetail')} className="lg:col-span-2">
          {day && dayBuckets ? (
            <div className="flex flex-col gap-4" data-testid="day-detail">
              <SpanSummary
                ws={ws}
                ctx={ctx}
                span={{ kind: 'day', from: day, to: day }}
                buckets={dayBuckets}
                titled={false}
              />
              <BarChart
                caption={t('overview.hourly')}
                labels={HOURS}
                series={[{ name: t('kpi.screenOn'), tone: 0, values: screenByHour(dayBuckets) }]}
                ticks={durationTicks(Math.max(...screenByHour(dayBuckets), 1))}
                format={(v) => formatDurationShort(v, t)}
                labelEvery={6}
              />
              <Link href={link({ a: day, b: addDays(day, -7) })} className="btn-ghost self-start" scroll={false}>
                {t('calendar.compareWithDay')}
              </Link>
            </div>
          ) : (
            <Empty>{t('calendar.pickDay')}</Empty>
          )}
        </Panel>
      </div>

      <Panel title={t('calendar.compare')}>
        <form method="get" action={path} className="flex flex-wrap items-end gap-3">
          {ctx.deviceIds && <input type="hidden" name="device" value={ctx.deviceIds[0]} />}
          <input type="hidden" name="month" value={month} />
          {day && <input type="hidden" name="day" value={day} />}
          <label className="min-w-0 flex-1 basis-48">
            <span className="label">{t('calendar.sideA')}</span>
            <input
              name="a"
              defaultValue={a ? formatSpan(a) : ''}
              className="input"
              placeholder="2026-11-01..2026-11-07"
            />
          </label>
          <label className="min-w-0 flex-1 basis-48">
            <span className="label">{t('calendar.sideB')}</span>
            <input
              name="b"
              defaultValue={b ? formatSpan(b) : ''}
              className="input"
              placeholder="2026-10-25..2026-10-31"
            />
          </label>
          <button type="submit" className="btn-primary">
            {t('calendar.compareAction')}
          </button>
          <p className="w-full text-small text-faint">{t('calendar.compareHint')}</p>
        </form>

        {a && b && aBuckets && bBuckets ? (
          <div className="mt-6 flex flex-col gap-6" data-testid="compare">
            <div className="grid gap-6 sm:grid-cols-2">
              <SpanSummary ws={ws} ctx={ctx} span={a} buckets={selectBuckets(aBuckets, a)} />
              <SpanSummary ws={ws} ctx={ctx} span={b} buckets={selectBuckets(bBuckets, b)} />
            </div>
            <Legend
              items={[
                { name: formatSpanLabel(a, locale), tone: 0 },
                { name: formatSpanLabel(b, locale), tone: 2 },
              ]}
            />
            <BarChart
              caption={t('overview.hourly')}
              labels={HOURS}
              series={[
                { name: formatSpanLabel(a, locale), tone: 0, values: hourlyAverage(aBuckets, a) },
                { name: formatSpanLabel(b, locale), tone: 2, values: hourlyAverage(bBuckets, b) },
              ]}
              ticks={durationTicks(Math.max(...hourlyAverage(aBuckets, a), ...hourlyAverage(bBuckets, b), 1))}
              format={(v) => formatDurationShort(v, t)}
              labelEvery={3}
            />
          </div>
        ) : (
          compareRequested && (
            <p role="alert" className="mt-3 text-small text-error">
              {t('calendar.compareInvalid')}
            </p>
          )
        )}
      </Panel>
    </div>
  )
}
