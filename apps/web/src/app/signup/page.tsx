import type { Metadata } from 'next'
import Link from 'next/link'
import { AuthCard, EmailField, PasswordField } from '@/components/auth/AuthCard'
import { ActionForm, SubmitButton } from '@/components/client/ActionForm'
import { TimeZoneField } from '@/components/client/BrowserTimeZone'
import { signUp } from '@/lib/auth/actions'
import { createTranslator } from '@/lib/i18n/translate'

const t = createTranslator()

export const metadata: Metadata = { title: t('auth.signUpTitle') }

export default function SignUpPage() {
  return (
    <AuthCard
      t={t}
      headline={t('auth.signUpHeadline')}
      highlight={t('auth.signUpHighlight')}
      footer={
        <Link href="/login" className="link self-start">
          {t('auth.haveAccount')}
        </Link>
      }
    >
      <ActionForm action={signUp} className="flex flex-col gap-4">
        <EmailField t={t} />
        <PasswordField t={t} label={t('auth.password')} autoComplete="new-password" />
        <TimeZoneField />
        <div>
          <SubmitButton>{t('auth.signUpAction')}</SubmitButton>
        </div>
      </ActionForm>
    </AuthCard>
  )
}
