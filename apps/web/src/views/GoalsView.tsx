import { ActionForm, SubmitButton } from '@/components/client/ActionForm'
import { GoalList, goalTargetName } from '@/components/dashboard/GoalList'
import { Headline } from '@/components/ui/Headline'
import { Empty, Panel } from '@/components/ui/Panel'
import { addGoal, deleteGoal } from '@/lib/account/actions'
import { evaluateGoal } from '@/lib/analytics/goals'
import { CATEGORIES, GOAL_METRICS, GOAL_PERIODS } from '@/lib/data/types'
import { startOfWeek } from '@/lib/time/days'
import { viewContext } from '@/lib/workspace'
import type { ViewProps } from './types'

export async function GoalsView({ ws, params }: ViewProps) {
  const { t, locale, source } = ws
  const [ctx, goals, members, apps] = await Promise.all([
    viewContext(ws, params),
    source.goals(),
    source.members(),
    source.apps(null),
  ])
  const buckets = await source.buckets({ from: startOfWeek(ctx.today), to: ctx.today }, null)
  const progress = goals.map((g) => evaluateGoal(g, buckets, ctx))
  const targetName = (goal: (typeof goals)[number]) =>
    goalTargetName(goal, { devices: ctx.devices, members, resolve: ctx.resolve, t })
  const appOptions = apps.map((a) => ctx.resolve(a.packageName)).sort((x, y) => x.label.localeCompare(y.label))

  return (
    <div className="flex flex-col gap-6">
      <Headline text={t('goals.headline')} highlight={t('goals.highlight')} />
      <div className="grid gap-6 lg:grid-cols-3">
        <Panel title={t('goals.title')} className="lg:col-span-2">
          {progress.length === 0 ? (
            <Empty>{t('goals.empty')}</Empty>
          ) : (
            <ul className="flex flex-col divide-y divide-line" data-testid="goals">
              {progress.map((item) => (
                <li key={item.goal.id} className="flex flex-col gap-3 py-4 first:pt-0 last:pb-0">
                  <GoalList items={[item]} targetName={targetName} t={t} locale={locale} />
                  {ws.editable && (
                    <ActionForm action={deleteGoal} className="flex items-center gap-3">
                      <input type="hidden" name="id" value={item.goal.id} />
                      <SubmitButton variant="ghost">{t('common.delete')}</SubmitButton>
                    </ActionForm>
                  )}
                </li>
              ))}
            </ul>
          )}
        </Panel>

        <Panel title={t('goals.addTitle')}>
          {ws.editable ? (
            <ActionForm action={addGoal} className="flex flex-col gap-3">
              <label>
                <span className="label">{t('goals.scope')}</span>
                <select name="target" required className="input">
                  <optgroup label={t('goals.scopes.device')}>
                    {ctx.devices.map((d) => (
                      <option key={d.id} value={`device:${d.id}`}>
                        {d.name}
                      </option>
                    ))}
                  </optgroup>
                  <optgroup label={t('goals.scopes.member')}>
                    {members.map((m) => (
                      <option key={m.id} value={`member:${m.id}`}>
                        {m.name}
                      </option>
                    ))}
                  </optgroup>
                  <optgroup label={t('goals.scopes.category')}>
                    {CATEGORIES.map((c) => (
                      <option key={c} value={`category:${c}`}>
                        {t(`categories.${c}`)}
                      </option>
                    ))}
                  </optgroup>
                  <optgroup label={t('goals.scopes.app')}>
                    {appOptions.map((a) => (
                      <option key={a.packageName} value={`app:${a.packageName}`}>
                        {a.label}
                      </option>
                    ))}
                  </optgroup>
                </select>
              </label>
              <div className="grid grid-cols-2 gap-3">
                <label>
                  <span className="label">{t('goals.metric')}</span>
                  <select name="metric" className="input">
                    {GOAL_METRICS.map((m) => (
                      <option key={m} value={m}>
                        {t(`goals.metrics.${m}`)}
                      </option>
                    ))}
                  </select>
                </label>
                <label>
                  <span className="label">{t('goals.period')}</span>
                  <select name="period" className="input">
                    {GOAL_PERIODS.map((p) => (
                      <option key={p} value={p}>
                        {t(`goals.periods.${p}`)}
                      </option>
                    ))}
                  </select>
                </label>
              </div>
              <label>
                <span className="label">{t('goals.limit')}</span>
                <input name="limit" type="number" min="0.25" step="0.25" required className="input" />
                <span className="mt-1 block text-small text-faint">{t('goals.limitHint')}</span>
              </label>
              <p className="text-small text-faint">{t('goals.invalidMetric')}</p>
              <div>
                <SubmitButton>{t('common.add')}</SubmitButton>
              </div>
            </ActionForm>
          ) : (
            <p className="text-small text-faint">{t('common.readOnly')}</p>
          )}
        </Panel>
      </div>
    </div>
  )
}
