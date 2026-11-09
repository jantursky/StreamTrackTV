import { SECURITY_EVENT_TYPES, type SecurityEventType } from 'streamtrack-tv'
import { Headline } from '@/components/ui/Headline'
import { Empty, Panel } from '@/components/ui/Panel'
import type { SecurityEventRecord } from '@/lib/data/types'
import type { MessageKey, Translate } from '@/lib/i18n/translate'
import { param } from '@/lib/time/range'
import { viewContext } from '@/lib/workspace'
import type { ViewProps } from './types'

/** Newest events one page shows. */
export const SECURITY_PAGE_SIZE = 200

/** Events an owner wants to notice: someone failed the PIN, or the PIN was reset. */
const ATTENTION: ReadonlySet<SecurityEventType> = new Set([
  'pin_wrong',
  'pin_wait_started',
  'forgot_pin_opened',
  'pin_reset_confirmed',
])

const isType = (value: string | undefined): value is SecurityEventType =>
  value !== undefined && (SECURITY_EVENT_TYPES as readonly string[]).includes(value)

const detail = (event: SecurityEventRecord, t: Translate): string | null => {
  if (event.value === null) return null
  if (event.type === 'pin_wrong') return t('security.tries', { count: event.value })
  if (event.type === 'pin_wait_started') return t('security.wait', { seconds: event.value })
  return null
}

export async function SecurityView({ ws, params }: ViewProps) {
  const { t, source } = ws
  const ctx = await viewContext(ws, params)
  const requested = param(params, 'type')
  const type = isType(requested) ? requested : null
  const events = await source.securityEvents({
    deviceIds: ctx.deviceIds,
    types: type === null ? null : [type],
    limit: SECURITY_PAGE_SIZE,
  })
  const format = new Intl.DateTimeFormat(ws.locale, {
    dateStyle: 'medium',
    timeStyle: 'medium',
    timeZone: ws.settings.timezone,
  })
  const deviceName = (id: string) => ctx.devices.find((d) => d.id === id)?.name ?? t('common.unknown')
  const filtered = type !== null || ctx.deviceIds !== null

  return (
    <div className="flex flex-col gap-6">
      <Headline text={t('security.headline')} highlight={t('security.highlight')} />
      <p className="max-w-2xl">{t('security.hint')}</p>
      <Panel title={t('security.title')}>
        <form method="get" className="mb-4 flex flex-wrap items-end gap-3">
          <label className="min-w-0 flex-1 basis-40">
            <span className="label">{t('security.tv')}</span>
            <select name="device" defaultValue={ctx.deviceIds?.[0] ?? ''} className="input">
              <option value="">{t('common.allDevices')}</option>
              {ctx.devices.map((d) => (
                <option key={d.id} value={d.id}>
                  {d.name}
                </option>
              ))}
            </select>
          </label>
          <label className="min-w-0 flex-1 basis-40">
            <span className="label">{t('security.type')}</span>
            <select name="type" defaultValue={type ?? ''} className="input">
              <option value="">{t('security.allTypes')}</option>
              {SECURITY_EVENT_TYPES.map((value) => (
                <option key={value} value={value}>
                  {t(`security.events.${value}` satisfies MessageKey)}
                </option>
              ))}
            </select>
          </label>
          <button type="submit" className="btn-primary">
            {t('security.filter')}
          </button>
        </form>
        {events.length === 0 ? (
          <Empty>{t(filtered ? 'security.emptyFiltered' : 'security.empty')}</Empty>
        ) : (
          <>
            <ul className="flex flex-col divide-y divide-line" data-testid="security-events">
              {events.map((event) => {
                const attention = ATTENTION.has(event.type)
                const extra = detail(event, t)
                return (
                  <li
                    key={event.id}
                    data-testid="security-event"
                    data-attention={attention ? 'true' : undefined}
                    className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1 py-3"
                  >
                    <div className="min-w-0">
                      <p className={attention ? 'font-semibold text-ink' : 'text-ink'}>
                        {attention && (
                          <span aria-hidden="true" className="mr-2">
                            ⚠
                          </span>
                        )}
                        {t(`security.events.${event.type}` satisfies MessageKey)}
                        {attention && <span className="sr-only"> ({t('security.attention')})</span>}
                      </p>
                      <p className="text-small">
                        {deviceName(event.deviceId)}
                        {extra !== null && ` · ${extra}`}
                      </p>
                    </div>
                    <time dateTime={event.at} className="text-small tabular-nums" title={t('security.when')}>
                      {format.format(new Date(event.at))}
                    </time>
                  </li>
                )
              })}
            </ul>
            {events.length === SECURITY_PAGE_SIZE && (
              <p className="mt-3 text-small text-faint">{t('security.shown', { count: SECURITY_PAGE_SIZE })}</p>
            )}
          </>
        )}
      </Panel>
    </div>
  )
}
