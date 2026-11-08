import { Heatmap } from '@/components/charts/Heatmap'
import { RangeBar } from '@/components/dashboard/RangeBar'
import { Headline } from '@/components/ui/Headline'
import { Panel } from '@/components/ui/Panel'
import { weekdayHourAverages } from '@/lib/analytics/aggregate'
import { formatDurationShort, formatHour } from '@/lib/i18n/format'
import { addDays } from '@/lib/time/days'
import { param, parseRange, rangeDays, type DayRange } from '@/lib/time/range'
import { viewContext } from '@/lib/workspace'
import type { ViewProps } from './types'

const WEEKDAYS = ['mon', 'tue', 'wed', 'thu', 'fri', 'sat', 'sun'] as const

export async function HeatmapView({ ws, params, path }: ViewProps) {
  const { t, source } = ws
  const ctx = await viewContext(ws, params)
  // Habits need several weeks, so without a chosen period the view shows the last four.
  const range: DayRange =
    param(params, 'range') === undefined
      ? { kind: 'custom', from: addDays(ctx.today, -27), to: ctx.today }
      : parseRange(params, ctx.today)
  const buckets = await source.buckets(range, ctx.deviceIds)
  const grid = weekdayHourAverages(buckets, rangeDays(range))
  const weekdays = WEEKDAYS.map((w) => t(`calendar.weekday.${w}`))
  const hours = Array.from({ length: 24 }, (_, h) => formatHour(h))

  return (
    <div className="flex flex-col gap-6">
      <Headline text={t('heatmap.headline')} highlight={t('heatmap.highlight')} />
      <RangeBar ws={ws} ctx={ctx} path={path} params={params} range={range} />
      <Panel title={t('heatmap.caption')}>
        <Heatmap
          caption={t('heatmap.caption')}
          rowLabels={weekdays}
          columnLabels={hours}
          values={grid}
          format={(v) => formatDurationShort(v, t)}
          cellLabel={(row, column, value) =>
            t('heatmap.cellLabel', { weekday: weekdays[row] ?? '', hour: hours[column] ?? '', value })
          }
          labelEvery={3}
          legend={{ less: t('heatmap.legendLess'), more: t('heatmap.legendMore') }}
        />
        <p className="mt-3 text-small text-faint">{t('heatmap.footnote')}</p>
      </Panel>
    </div>
  )
}
