import { KEY_CLASSES } from 'streamtrack-tv'
import { BarChart } from '@/components/charts/BarChart'
import { Legend } from '@/components/charts/Legend'
import { RatioBar } from '@/components/charts/RatioBar'
import { linearTicks, type Tone } from '@/components/charts/scale'
import { RangeBar } from '@/components/dashboard/RangeBar'
import { Headline } from '@/components/ui/Headline'
import { Empty, Panel } from '@/components/ui/Panel'
import { keysByClass, keysByHour } from '@/lib/analytics/aggregate'
import { formatHour, formatNumber, formatPercent } from '@/lib/i18n/format'
import { parseRange } from '@/lib/time/range'
import { viewContext } from '@/lib/workspace'
import type { ViewProps } from './types'

export async function RemoteView({ ws, params, path }: ViewProps) {
  const { t, locale, source } = ws
  const ctx = await viewContext(ws, params)
  const range = parseRange(params, ctx.today)
  const buckets = await source.buckets(range, ctx.deviceIds)
  const byClass = keysByClass(buckets)
  const byHour = keysByHour(buckets)
  const total = KEY_CLASSES.reduce((acc, k) => acc + byClass[k], 0)
  const series = KEY_CLASSES.map((k, i) => ({
    name: t(`remote.keys.${k}`),
    tone: i as Tone,
    values: byHour.map((hour) => hour[k]),
  }))
  const hourTotals = byHour.map((hour) => KEY_CLASSES.reduce((acc, k) => acc + hour[k], 0))

  return (
    <div className="flex flex-col gap-6">
      <Headline text={t('remote.headline')} highlight={formatNumber(total, locale)} />
      <RangeBar ws={ws} ctx={ctx} path={path} params={params} range={range} />

      <div className="grid gap-6 lg:grid-cols-3">
        <Panel title={t('remote.split')}>
          {total === 0 ? (
            <Empty>{t('common.noData')}</Empty>
          ) : (
            <ul className="flex flex-col gap-3" data-testid="key-split">
              {KEY_CLASSES.map((k, i) => (
                <li key={k} className="flex flex-col gap-1">
                  <div className="flex items-baseline justify-between gap-3">
                    <span className="text-ink">{t(`remote.keys.${k}`)}</span>
                    <span className="text-small tabular-nums">
                      {formatNumber(byClass[k], locale)} · {formatPercent(byClass[k] / total, locale)}
                    </span>
                  </div>
                  <RatioBar ratio={byClass[k] / total} tone={i as Tone} />
                </li>
              ))}
            </ul>
          )}
        </Panel>

        <Panel title={t('remote.perHour')} className="lg:col-span-2">
          <div className="flex flex-col gap-4">
            <BarChart
              caption={t('remote.perHour')}
              labels={Array.from({ length: 24 }, (_, h) => formatHour(h))}
              series={series}
              layout="stacked"
              ticks={linearTicks(Math.max(...hourTotals, 1))}
              format={(v) => formatNumber(v, locale)}
              labelEvery={3}
            />
            <Legend items={series} />
          </div>
        </Panel>
      </div>
      <p className="text-small text-faint">{t('remote.privacy')}</p>
    </div>
  )
}
