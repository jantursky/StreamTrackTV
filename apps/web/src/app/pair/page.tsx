import type { Metadata } from 'next'
import { LIMITS } from 'streamtrack-tv'
import { ActionForm, SubmitButton } from '@/components/client/ActionForm'
import { Shell } from '@/components/dashboard/Shell'
import { Headline } from '@/components/ui/Headline'
import { pairDevice } from '@/lib/account/actions'
import { requireUser } from '@/lib/auth/session'
import { createTranslator } from '@/lib/i18n/translate'
import { normalizePairCode } from '@/lib/security/tokens'
import { param } from '@/lib/time/range'

const t = createTranslator()

export const metadata: Metadata = { title: t('pair.title') }

/** Opened from the QR code on the TV (`/pair?code=ABC234`) or from the devices page. */
export default async function PairPage({ searchParams }: PageProps<'/pair'>) {
  const params = await searchParams
  const code = normalizePairCode(param(params, 'code') ?? '').slice(0, LIMITS.pairCodeLength)
  await requireUser(code ? `/pair?code=${code}` : '/pair')

  return (
    <Shell t={t}>
      <div className="mx-auto flex max-w-md flex-col gap-6 pt-8">
        <Headline text={t('pair.headline')} highlight={t('pair.highlight')} />
        <div className="card">
          <p className="mb-4">{t('devices.pairHint')}</p>
          <ActionForm action={pairDevice} className="flex flex-col gap-4">
            <label>
              <span className="label">{t('pair.code')}</span>
              <input
                name="code"
                defaultValue={code}
                required
                autoComplete="off"
                autoCapitalize="characters"
                maxLength={LIMITS.pairCodeLength + 2}
                className="input font-mono text-body-lg tracking-widest uppercase"
              />
            </label>
            <div>
              <SubmitButton>{t('pair.confirm')}</SubmitButton>
            </div>
          </ActionForm>
        </div>
      </div>
    </Shell>
  )
}
