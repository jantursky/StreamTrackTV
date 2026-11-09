import Link from 'next/link'
import { TimeZoneSync } from '@/components/client/BrowserTimeZone'
import { NavTabs } from '@/components/client/NavTabs'
import { DemoBadge, Shell } from '@/components/dashboard/Shell'
import { adoptTimeZone } from '@/lib/account/actions'
import { signOut } from '@/lib/auth/actions'
import { getWorkspace } from '@/lib/workspace'
import { SECTIONS } from '@/views/section'

export default async function SpaceLayout({ children, params }: LayoutProps<'/[space]'>) {
  const { space } = await params
  const ws = await getWorkspace(space)
  const { t } = ws
  const items = SECTIONS.filter((s) => s.slug !== 'security' || ws.source.kind !== 'shared').map((s) => ({
    href: s.slug ? `${ws.basePath}/${s.slug}` : ws.basePath,
    label: t(s.title),
  }))

  return (
    <Shell
      t={t}
      badge={ws.source.kind === 'demo' ? <DemoBadge t={t} /> : undefined}
      actions={
        ws.editable ? (
          <form action={signOut}>
            <button type="submit" className="btn-ghost">
              {t('nav.signOut')}
            </button>
          </form>
        ) : (
          <Link href="/signup" className="btn-ghost hidden sm:inline-flex">
            {t('nav.signUp')}
          </Link>
        )
      }
      nav={<NavTabs label={t('nav.main')} items={items} />}
    >
      {ws.editable && ws.settings.timezoneSource !== 'account' && <TimeZoneSync adopt={adoptTimeZone} />}
      {children}
    </Shell>
  )
}
