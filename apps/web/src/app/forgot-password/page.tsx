import type { Metadata } from 'next'
import Link from 'next/link'
import { AuthCard, EmailField } from '@/components/auth/AuthCard'
import { ActionForm, SubmitButton } from '@/components/client/ActionForm'
import { requestPasswordReset } from '@/lib/auth/actions'
import { createTranslator } from '@/lib/i18n/translate'

const t = createTranslator()

export const metadata: Metadata = { title: t('auth.forgotTitle') }

export default function ForgotPasswordPage() {
  return (
    <AuthCard
      t={t}
      headline={t('auth.forgotHeadline')}
      highlight={t('auth.forgotHighlight')}
      footer={
        <Link href="/login" className="link self-start">
          {t('auth.haveAccount')}
        </Link>
      }
    >
      <ActionForm action={requestPasswordReset} className="flex flex-col gap-4">
        <EmailField t={t} />
        <div>
          <SubmitButton>{t('auth.forgotAction')}</SubmitButton>
        </div>
      </ActionForm>
    </AuthCard>
  )
}
