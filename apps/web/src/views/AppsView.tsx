import { RatioBar } from '@/components/charts/RatioBar'
import { ActionForm, SubmitButton } from '@/components/client/ActionForm'
import { RangeBar } from '@/components/dashboard/RangeBar'
import { Headline } from '@/components/ui/Headline'
import { Empty, Panel } from '@/components/ui/Panel'
import { setAppCategory } from '@/lib/account/actions'
import { appShares, categoryShares } from '@/lib/analytics/aggregate'
import { APP_SORTS, filterApps, type AppSort } from '@/lib/analytics/apps'
import { CATEGORIES } from '@/lib/data/types'
import { formatDuration, formatNumber, formatPercent } from '@/lib/i18n/format'
import { param, parseRange } from '@/lib/time/range'
import { viewContext } from '@/lib/workspace'
import type { ViewProps } from './types'

export async function AppsView({ ws, params, path }: ViewProps) {
  const { t, locale, source } = ws
  const ctx = await viewContext(ws, params)
  const range = parseRange(params, ctx.today)
  const buckets = await source.buckets(range, ctx.deviceIds)
  const all = appShares(buckets, ctx.resolve)
  const query = param(params, 'q') ?? ''
  const sortParam = param(params, 'sort')
  const sort: AppSort = APP_SORTS.find((s) => s === sortParam) ?? 'time'
  const apps = filterApps(all, query, sort, t)
  const categories = categoryShares(all)

  return (
    <div className="flex flex-col gap-6">
      <Headline text={t('apps.headline')} highlight={formatNumber(all.length, locale)} />
      <RangeBar ws={ws} ctx={ctx} path={path} params={params} range={range} />

      <div className="grid gap-6 lg:grid-cols-3">
        <Panel title={t('apps.title')} className="lg:col-span-2">
          <form method="get" action={path} className="mb-4 flex flex-wrap items-end gap-3" role="search">
            {(['range', 'date', 'from', 'to', 'device'] as const).map((key) => {
              const value = param(params, key)
              return value ? <input key={key} type="hidden" name={key} value={value} /> : null
            })}
            <label className="min-w-0 flex-1 basis-48">
              <span className="label">{t('apps.searchLabel')}</span>
              <input
                type="search"
                name="q"
                defaultValue={query}
                placeholder={t('apps.searchPlaceholder')}
                className="input"
              />
            </label>
            <label>
              <span className="label">{t('common.sortBy')}</span>
              <select name="sort" defaultValue={sort} className="input w-auto">
                {APP_SORTS.map((s) => (
                  <option key={s} value={s}>
                    {t(`apps.sort.${s}`)}
                  </option>
                ))}
              </select>
            </label>
            <button type="submit" className="btn-ghost">
              {t('common.apply')}
            </button>
          </form>

          {apps.length === 0 ? (
            <Empty>{all.length === 0 ? t('common.noData') : t('apps.empty')}</Empty>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left" data-testid="apps-table">
                <thead className="text-small">
                  <tr className="border-b border-line">
                    <th scope="col" className="py-2 pr-3 font-normal">
                      {t('apps.columns.app')}
                    </th>
                    <th scope="col" className="py-2 pr-3 font-normal">
                      {t('apps.columns.category')}
                    </th>
                    <th scope="col" className="py-2 pr-3 text-right font-normal">
                      {t('apps.columns.time')}
                    </th>
                    <th scope="col" className="hidden w-32 py-2 font-normal sm:table-cell">
                      {t('apps.columns.share')}
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {apps.map((app) => (
                    <tr key={app.packageName} className="border-b border-line last:border-0">
                      <td className="max-w-48 py-2.5 pr-3">
                        <span className="block truncate text-ink">{app.label}</span>
                        <span className="block truncate text-caption text-faint">{app.packageName}</span>
                      </td>
                      <td className="py-2.5 pr-3">
                        {ws.editable ? (
                          <ActionForm action={setAppCategory} className="flex flex-wrap items-center gap-2">
                            <input type="hidden" name="packageName" value={app.packageName} />
                            <select
                              name="category"
                              defaultValue={app.category}
                              aria-label={t('apps.categoryLabel', { app: app.label })}
                              className="input w-auto py-1"
                            >
                              {CATEGORIES.map((c) => (
                                <option key={c} value={c}>
                                  {t(`categories.${c}`)}
                                </option>
                              ))}
                            </select>
                            <SubmitButton variant="ghost">{t('common.save')}</SubmitButton>
                          </ActionForm>
                        ) : (
                          t(`categories.${app.category}`)
                        )}
                      </td>
                      <td className="py-2.5 pr-3 text-right whitespace-nowrap text-ink tabular-nums">
                        {formatDuration(app.ms, t)}
                      </td>
                      <td className="hidden py-2.5 sm:table-cell">
                        <div className="flex items-center gap-2">
                          <RatioBar ratio={app.share} />
                          <span className="w-10 text-right text-small tabular-nums">
                            {formatPercent(app.share, locale)}
                          </span>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
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
                    <span className="text-small tabular-nums">{formatDuration(c.ms, t)}</span>
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
