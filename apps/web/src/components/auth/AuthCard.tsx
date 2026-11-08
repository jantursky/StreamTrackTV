import Link from 'next/link'
import type { ReactNode } from 'react'
import { Shell } from '../dashboard/Shell'
import { Headline } from '../ui/Headline'
import { isDemoOnly } from '@/lib/env'
import type { Translate } from '@/lib/i18n/translate'

interface AuthCardProps {
  t: Translate
  headline: string
  highlight: string
  children: ReactNode
  footer?: ReactNode
}

/** Frame of the sign-in, sign-up and password pages; explains itself on a demo-only deployment. */
export function AuthCard({ t, headline, highlight, children, footer }: AuthCardProps) {
  return (
    <Shell t={t}>
      <div className="mx-auto flex max-w-md flex-col gap-6 pt-8">
        <Headline text={headline} highlight={highlight} />
        {isDemoOnly() ? (
          <div className="card flex flex-col items-start gap-4">
            <p>{t('auth.unavailable')}</p>
            <Link href="/demo" className="btn-primary">
              {t('landing.ctaDemo')}
            </Link>
          </div>
        ) : (
          <div className="card">{children}</div>
        )}
        {footer && <div className="flex flex-col gap-2 text-small">{footer}</div>}
      </div>
    </Shell>
  )
}

export function EmailField({ t }: { t: Translate }) {
  return (
    <label>
      <span className="label">{t('auth.email')}</span>
      <input type="email" name="email" autoComplete="email" required maxLength={254} className="input" />
    </label>
  )
}

export function PasswordField({ t, label, autoComplete }: { t: Translate; label: string; autoComplete: string }) {
  return (
    <label>
      <span className="label">{label}</span>
      <input
        type="password"
        name="password"
        autoComplete={autoComplete}
        required
        minLength={autoComplete === 'new-password' ? 8 : 1}
        maxLength={72}
        className="input"
      />
      {autoComplete === 'new-password' && (
        <span className="mt-1 block text-small text-faint">{t('auth.passwordHint')}</span>
      )}
    </label>
  )
}
