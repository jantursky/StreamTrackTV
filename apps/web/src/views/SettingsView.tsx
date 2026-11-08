import { ActionForm, SubmitButton } from '@/components/client/ActionForm'
import { Headline } from '@/components/ui/Headline'
import { Empty, Panel } from '@/components/ui/Panel'
import { createShareLink, deleteAccount, revokeShareLink, saveSettings } from '@/lib/account/actions'
import { RETENTION_CHOICES, SHARE_DAYS } from '@/lib/account/forms'
import { formatDay } from '@/lib/i18n/format'
import { LOCALES } from '@/lib/i18n/translate'
import { addDays } from '@/lib/time/days'
import { viewContext } from '@/lib/workspace'
import type { ViewProps } from './types'

const LANGUAGE_NAMES: Record<string, string> = { en: 'English' }

export async function SettingsView({ ws, params }: ViewProps) {
  const { t, locale, settings, source } = ws
  const [ctx, links] = await Promise.all([viewContext(ws, params), source.shareLinks()])
  const disabled = !ws.editable
  const zones = Intl.supportedValuesOf('timeZone')
  const retention = (RETENTION_CHOICES as readonly number[]).includes(settings.retentionDays)
    ? RETENTION_CHOICES
    : [...RETENTION_CHOICES, settings.retentionDays].sort((a, b) => a - b)

  return (
    <div className="flex flex-col gap-6">
      <Headline text={t('settings.headline')} highlight={t('settings.highlight')} />
      {disabled && <p className="text-small text-faint">{t('common.readOnly')}</p>}

      <div className="grid gap-6 lg:grid-cols-2">
        <Panel title={t('settings.general')}>
          <ActionForm action={saveSettings} className="flex flex-col gap-4">
            <fieldset disabled={disabled} className="flex flex-col gap-4">
              <label>
                <span className="label">{t('settings.timezone')}</span>
                <select name="timezone" defaultValue={settings.timezone} className="input">
                  {zones.map((z) => (
                    <option key={z} value={z}>
                      {z}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                <span className="label">{t('settings.locale')}</span>
                <select name="locale" defaultValue={settings.locale} className="input">
                  {LOCALES.map((l) => (
                    <option key={l} value={l}>
                      {LANGUAGE_NAMES[l] ?? l}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                <span className="label">{t('settings.retention')}</span>
                <select name="retentionDays" defaultValue={String(settings.retentionDays)} className="input">
                  {retention.map((days) => (
                    <option key={days} value={days}>
                      {t('settings.retentionDays', { days })}
                    </option>
                  ))}
                </select>
              </label>
              <label className="flex items-center gap-2 text-ink">
                <input
                  type="checkbox"
                  name="digestEnabled"
                  defaultChecked={settings.digestEnabled}
                  className="size-4 accent-accent"
                />
                {t('settings.digest')}
              </label>
              <div>
                <SubmitButton>{t('common.save')}</SubmitButton>
              </div>
            </fieldset>
          </ActionForm>
        </Panel>

        <Panel title={t('settings.share')}>
          <p className="mb-4">{t('settings.shareHint')}</p>
          {links.length === 0 ? (
            <Empty>{t('settings.shareEmpty')}</Empty>
          ) : (
            <ul className="mb-4 flex flex-col divide-y divide-line">
              {links.map((link) => (
                <li key={link.id} className="flex items-center justify-between gap-3 py-2">
                  <span>
                    <span className="block text-ink">
                      {ctx.devices.find((d) => d.id === link.deviceId)?.name ?? t('common.allDevices')}
                    </span>
                    <span className="text-small">
                      {t('settings.shareExpiresOn', { date: formatDay(link.expiresAt.slice(0, 10), locale) })}
                    </span>
                  </span>
                  <ActionForm action={revokeShareLink}>
                    <input type="hidden" name="id" value={link.id} />
                    <SubmitButton variant="ghost">{t('settings.shareRevoke')}</SubmitButton>
                  </ActionForm>
                </li>
              ))}
            </ul>
          )}
          <ActionForm
            action={createShareLink}
            className="flex flex-wrap items-end gap-3"
            valueLabel={t('settings.share')}
          >
            <fieldset disabled={disabled} className="contents">
              <label className="min-w-0 flex-1 basis-36">
                <span className="label">{t('settings.shareScope')}</span>
                <select name="deviceId" className="input">
                  <option value="">{t('common.allDevices')}</option>
                  {ctx.devices.map((d) => (
                    <option key={d.id} value={d.id}>
                      {d.name}
                    </option>
                  ))}
                </select>
              </label>
              <label className="min-w-0 flex-1 basis-28">
                <span className="label">{t('settings.shareExpires')}</span>
                <select name="days" defaultValue="7" className="input">
                  {SHARE_DAYS.map((d) => (
                    <option key={d} value={d}>
                      {t('settings.shareDays', { days: d })}
                    </option>
                  ))}
                </select>
              </label>
              <SubmitButton>{t('settings.shareCreate')}</SubmitButton>
            </fieldset>
          </ActionForm>
        </Panel>

        <Panel title={t('settings.export')}>
          <p className="mb-4">{t('settings.exportHint')}</p>
          <form method="get" action="/api/v1/export" className="flex flex-wrap items-end gap-3">
            <fieldset disabled={disabled} className="contents">
              <label className="min-w-0 flex-1 basis-36">
                <span className="label">{t('common.from')}</span>
                <input
                  type="date"
                  name="from"
                  defaultValue={addDays(ctx.today, -29)}
                  max={ctx.today}
                  required
                  className="input"
                />
              </label>
              <label className="min-w-0 flex-1 basis-36">
                <span className="label">{t('common.to')}</span>
                <input type="date" name="to" defaultValue={ctx.today} max={ctx.today} required className="input" />
              </label>
              <button type="submit" name="format" value="csv" className="btn-ghost">
                {t('settings.exportCsv')}
              </button>
              <button type="submit" name="format" value="json" className="btn-ghost">
                {t('settings.exportJson')}
              </button>
            </fieldset>
          </form>
        </Panel>

        <Panel title={t('settings.danger')}>
          <p className="mb-4">{t('settings.dangerHint')}</p>
          <ActionForm action={deleteAccount} className="flex flex-wrap items-end gap-3">
            <fieldset disabled={disabled} className="contents">
              <label className="min-w-0 flex-1 basis-40">
                <span className="label">{t('settings.dangerConfirm')}</span>
                <input name="confirm" autoComplete="off" required pattern="DELETE" className="input" />
              </label>
              <SubmitButton variant="ghost">{t('settings.dangerAction')}</SubmitButton>
            </fieldset>
          </ActionForm>
        </Panel>
      </div>
    </div>
  )
}
