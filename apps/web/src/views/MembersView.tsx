import { ActionForm, SubmitButton } from '@/components/client/ActionForm'
import { Headline } from '@/components/ui/Headline'
import { Empty, Panel } from '@/components/ui/Panel'
import { addMember, deleteMember } from '@/lib/account/actions'
import { selectBuckets, totals } from '@/lib/analytics/aggregate'
import { formatDuration } from '@/lib/i18n/format'
import { startOfWeek } from '@/lib/time/days'
import { viewContext } from '@/lib/workspace'
import type { ViewProps } from './types'

export async function MembersView({ ws, params }: ViewProps) {
  const { t, source } = ws
  const [ctx, members] = await Promise.all([viewContext(ws, params), source.members()])
  const week = { from: startOfWeek(ctx.today), to: ctx.today }
  const buckets = await source.buckets(week, null)

  return (
    <div className="flex flex-col gap-6">
      <Headline text={t('members.headline')} highlight={t('members.highlight')} />
      <p className="max-w-2xl">{t('members.hint')}</p>
      <div className="grid gap-6 lg:grid-cols-3">
        <div className="lg:col-span-2">
          {members.length === 0 ? (
            <div className="card">
              <Empty>{t('members.empty')}</Empty>
            </div>
          ) : (
            <ul className="grid gap-4 md:grid-cols-2" data-testid="members">
              {members.map((member) => {
                const deviceIds = ctx.devices.filter((d) => d.memberId === member.id).map((d) => d.id)
                const sums = totals(selectBuckets(buckets, { deviceIds }))
                return (
                  <li key={member.id} className="card flex flex-col gap-3">
                    <div className="flex items-baseline justify-between gap-2">
                      <h2 className="text-body-lg">{member.name}</h2>
                      <span className="text-small">{t('members.devices', { count: deviceIds.length })}</span>
                    </div>
                    <p className="text-small">
                      {t('members.thisWeek')}:{' '}
                      <span className="text-ink tabular-nums">{formatDuration(sums.screenOnMs, t)}</span>
                    </p>
                    {ws.editable && (
                      <ActionForm action={deleteMember} className="flex flex-wrap items-center gap-3">
                        <input type="hidden" name="id" value={member.id} />
                        <SubmitButton variant="ghost">{t('common.delete')}</SubmitButton>
                      </ActionForm>
                    )}
                  </li>
                )
              })}
            </ul>
          )}
        </div>
        <Panel title={t('members.addTitle')}>
          {ws.editable ? (
            <ActionForm action={addMember} className="flex flex-wrap items-end gap-3">
              <label className="min-w-0 flex-1 basis-40">
                <span className="label">{t('members.name')}</span>
                <input name="name" required maxLength={60} className="input" />
              </label>
              <SubmitButton>{t('common.add')}</SubmitButton>
            </ActionForm>
          ) : (
            <p className="text-small text-faint">{t('common.readOnly')}</p>
          )}
        </Panel>
      </div>
    </div>
  )
}
