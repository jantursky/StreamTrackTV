import type { Metadata } from 'next'
import { AuthCard, PasswordField } from '@/components/auth/AuthCard'
import { ActionForm, SubmitButton } from '@/components/client/ActionForm'
import { updatePassword } from '@/lib/auth/actions'
import { requireUser } from '@/lib/auth/session'
import { isDemoOnly } from '@/lib/env'
import { createTranslator } from '@/lib/i18n/translate'

const t = createTranslator()

export const metadata: Metadata = { title: t('auth.resetTitle') }

/** Reached from the reset email, which signs the user in for this one step. */
export default async function ResetPasswordPage() {
  if (!isDemoOnly()) await requireUser('/reset-password')
  return (
    <AuthCard t={t} headline={t('auth.resetHeadline')} highlight={t('auth.forgotHighlight')}>
      <ActionForm action={updatePassword} className="flex flex-col gap-4">
        <PasswordField t={t} label={t('auth.newPassword')} autoComplete="new-password" />
        <div>
          <SubmitButton>{t('auth.resetAction')}</SubmitButton>
        </div>
      </ActionForm>
    </AuthCard>
  )
}
