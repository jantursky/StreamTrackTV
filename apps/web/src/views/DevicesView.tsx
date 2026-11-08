import { LIMITS } from 'streamtrack-tv'
import { ActionForm, SubmitButton } from '@/components/client/ActionForm'
import { Headline } from '@/components/ui/Headline'
import { Empty, Panel } from '@/components/ui/Panel'
import { deleteDeviceData, revokeDevice, updateDevice } from '@/lib/account/actions'
import type { Device, Member } from '@/lib/data/types'
import { formatRelative } from '@/lib/i18n/format'
import { viewContext, type Workspace } from '@/lib/workspace'
import type { ViewProps } from './types'

function DeviceCard({ ws, device, members }: { ws: Workspace; device: Device; members: readonly Member[] }) {
  const { t, locale } = ws
  const member = members.find((m) => m.id === device.memberId)
  const status = device.revokedAt
    ? t('devices.revoked')
    : device.lastSeenAt
      ? t('devices.lastSeen', { when: formatRelative(device.lastSeenAt, ws.source.now(), locale) })
      : t('devices.neverSeen')

  return (
    <li className="card flex flex-col gap-4" data-testid="device">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <h2 className="text-body-lg">{device.name}</h2>
        <span className={`text-small ${device.revokedAt ? 'text-error' : ''}`}>{status}</span>
      </div>
      <dl className="grid grid-cols-2 gap-2 text-small">
        <div>
          <dt>{t('devices.model')}</dt>
          <dd className="text-ink">{device.model ?? t('common.unknown')}</dd>
        </div>
        <div>
          <dt>{t('devices.member')}</dt>
          <dd className="text-ink">{member?.name ?? t('common.none')}</dd>
        </div>
      </dl>

      {ws.editable && (
        <div className="flex flex-col gap-4 border-t border-line pt-4">
          <ActionForm action={updateDevice} className="flex flex-wrap items-end gap-3">
            <input type="hidden" name="id" value={device.id} />
            <label className="min-w-0 flex-1 basis-40">
              <span className="label">{t('devices.rename')}</span>
              <input name="name" defaultValue={device.name} maxLength={60} required className="input" />
            </label>
            <label className="min-w-0 flex-1 basis-40">
              <span className="label">{t('devices.member')}</span>
              <select name="memberId" defaultValue={device.memberId ?? ''} className="input">
                <option value="">{t('common.none')}</option>
                {members.map((m) => (
                  <option key={m.id} value={m.id}>
                    {m.name}
                  </option>
                ))}
              </select>
            </label>
            <SubmitButton variant="ghost">{t('common.save')}</SubmitButton>
          </ActionForm>

          {!device.revokedAt && (
            <ActionForm action={revokeDevice} className="flex flex-wrap items-center gap-3">
              <input type="hidden" name="id" value={device.id} />
              <SubmitButton variant="ghost">{t('devices.revoke')}</SubmitButton>
              <span className="text-small text-faint">{t('devices.revokeHint')}</span>
            </ActionForm>
          )}

          <ActionForm action={deleteDeviceData} className="flex flex-wrap items-end gap-3">
            <input type="hidden" name="id" value={device.id} />
            <label className="min-w-0 flex-1 basis-40">
              <span className="label">{t('devices.deleteDataConfirm')}</span>
              <input name="confirm" autoComplete="off" required className="input" placeholder={device.name} />
            </label>
            <SubmitButton variant="ghost">{t('devices.deleteData')}</SubmitButton>
          </ActionForm>
        </div>
      )}
    </li>
  )
}

export async function DevicesView({ ws, params }: ViewProps) {
  const { t } = ws
  const [ctx, members] = await Promise.all([viewContext(ws, params), ws.source.members()])

  return (
    <div className="flex flex-col gap-6">
      <Headline text={t('devices.headline')} highlight={t('devices.count', { count: ctx.devices.length })} />
      <div className="grid gap-6 lg:grid-cols-3">
        <div className="lg:col-span-2">
          {ctx.devices.length === 0 ? (
            <div className="card">
              <Empty>{t('devices.empty')}</Empty>
            </div>
          ) : (
            <ul className="grid gap-4 md:grid-cols-2">
              {ctx.devices.map((device) => (
                <DeviceCard key={device.id} ws={ws} device={device} members={members} />
              ))}
            </ul>
          )}
        </div>
        <Panel title={t('devices.pairTitle')}>
          <p className="mb-4">{t('devices.pairHint')}</p>
          {ws.editable ? (
            <form method="get" action="/pair" className="flex flex-wrap items-end gap-3">
              <label className="min-w-0 flex-1 basis-32">
                <span className="label">{t('pair.code')}</span>
                <input
                  name="code"
                  required
                  autoComplete="off"
                  autoCapitalize="characters"
                  maxLength={LIMITS.pairCodeLength + 2}
                  className="input font-mono tracking-widest uppercase"
                />
              </label>
              <button type="submit" className="btn-primary">
                {t('devices.pairAction')}
              </button>
            </form>
          ) : (
            <p className="text-small text-faint">{t('common.readOnly')}</p>
          )}
        </Panel>
      </div>
    </div>
  )
}
