import { cookies } from 'next/headers'
import type { ReactNode } from 'react'
import { ThemeToggle } from '../client/ThemeToggle'
import { Brand } from '../ui/Brand'
import type { Translate } from '@/lib/i18n/translate'
import { parseTheme, THEMES, type Theme } from '@/lib/theme'

interface ShellProps {
  t: Translate
  /** Shown next to the brand, e.g. the demo badge. */
  badge?: ReactNode
  /** Right side of the header: sign out, sign up. */
  actions?: ReactNode
  nav?: ReactNode
  children: ReactNode
}

export function DemoBadge({ t }: { t: Translate }) {
  return (
    <span
      className="rounded-pill border border-accent-edge px-2.5 py-0.5 text-small font-medium text-accent-edge"
      title={t('demo.note')}
      data-testid="demo-badge"
    >
      {t('demo.badge')}
    </span>
  )
}

/** Page frame of the dashboard and the share view: header, optional section tabs, content. */
export async function Shell({ t, badge, actions, nav, children }: ShellProps) {
  const theme = parseTheme((await cookies()).get('theme')?.value)
  const labels = Object.fromEntries(THEMES.map((m) => [m, t('theme.label', { mode: t(`theme.${m}`) })])) as Record<
    Theme,
    string
  >
  return (
    <div className="mx-auto flex w-full max-w-content flex-col gap-6 px-4 pt-4 pb-16 sm:px-6 sm:pt-6">
      <header className="flex flex-col gap-4 border-b border-line pb-4">
        <div className="flex items-center justify-between gap-3">
          <div className="flex min-w-0 items-center gap-3">
            <Brand href="/" name={t('app.name')} />
            {badge}
          </div>
          <div className="flex items-center gap-2">
            {actions}
            <ThemeToggle initial={theme} labels={labels} />
          </div>
        </div>
        {nav}
      </header>
      <main id="main" className="min-w-0">
        {children}
      </main>
    </div>
  )
}
