import type { Metadata } from 'next'
import Link from 'next/link'
import { redirect } from 'next/navigation'
import { AuthCard, EmailField, PasswordField } from '@/components/auth/AuthCard'
import { ActionForm, SubmitButton } from '@/components/client/ActionForm'
import { signIn } from '@/lib/auth/actions'
import { safeNext } from '@/lib/auth/redirects'
import { currentUser } from '@/lib/auth/session'
import { createTranslator } from '@/lib/i18n/translate'
import { param } from '@/lib/time/range'

const t = createTranslator()

export const metadata: Metadata = { title: t('auth.signInTitle') }

export default async function LoginPage({ searchParams }: PageProps<'/login'>) {
  const params = await searchParams
  const next = safeNext(param(params, 'next'))
  if (await currentUser()) redirect(next)

  return (
    <AuthCard
      t={t}
      headline={t('auth.signInHeadline')}
      highlight={t('auth.signInHighlight')}
      footer={
        <>
          <Link href="/forgot-password" className="link self-start">
            {t('auth.forgotLink')}
          </Link>
          <Link href="/signup" className="link self-start">
            {t('auth.noAccount')}
          </Link>
        </>
      }
    >
      {param(params, 'error') === 'link' && (
        <p role="alert" className="mb-4 text-small text-error">
          {t('auth.linkInvalid')}
        </p>
      )}
      <ActionForm action={signIn} className="flex flex-col gap-4">
        <input type="hidden" name="next" value={next} />
        <EmailField t={t} />
        <PasswordField t={t} label={t('auth.password')} autoComplete="current-password" />
        <div>
          <SubmitButton>{t('auth.signInAction')}</SubmitButton>
        </div>
      </ActionForm>
    </AuthCard>
  )
}
