import type { Metadata } from 'next'
import { LIMITS } from 'streamtrack-tv'
import { ActionForm, SubmitButton } from '@/components/client/ActionForm'
import { Shell } from '@/components/dashboard/Shell'
import { Headline } from '@/components/ui/Headline'
import { resetTvPin } from '@/lib/account/actions'
import { requireUser } from '@/lib/auth/session'
import { createTranslator } from '@/lib/i18n/translate'
import { normalizePairCode } from '@/lib/security/tokens'
import { param } from '@/lib/time/range'

const t = createTranslator()

export const metadata: Metadata = { title: t('pinReset.title') }

/** Opened from the QR code on the TV's "Forgot PIN" screen (`/pin-reset?code=ABC234`). */
export default async function PinResetPage({ searchParams }: PageProps<'/pin-reset'>) {
  const params = await searchParams
  const code = normalizePairCode(param(params, 'code') ?? '').slice(0, LIMITS.pairCodeLength)
  await requireUser(code ? `/pin-reset?code=${code}` : '/pin-reset')

  return (
    <Shell t={t}>
      <div className="mx-auto flex max-w-md flex-col gap-6 pt-8">
        <Headline text={t('pinReset.headline')} highlight={t('pinReset.highlight')} />
        <div className="card">
          <p className="mb-4">{t('pinReset.hint')}</p>
          <ActionForm action={resetTvPin} className="flex flex-col gap-4">
            <label>
              <span className="label">{t('pinReset.code')}</span>
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
              <SubmitButton>{t('pinReset.confirm')}</SubmitButton>
            </div>
          </ActionForm>
        </div>
      </div>
    </Shell>
  )
}
