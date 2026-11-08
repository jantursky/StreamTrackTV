import type { Metadata } from 'next'
import Link from 'next/link'
import { Shell } from '@/components/dashboard/Shell'
import { createTranslator } from '@/lib/i18n/translate'
import { getSharedWorkspace } from '@/lib/workspace'
import { OverviewView } from '@/views/OverviewView'

const t = createTranslator()

export const metadata: Metadata = { title: t('share.title'), robots: { index: false, follow: false } }

/** Read-only overview behind a share link. Invalid and expired links look the same. */
export default async function SharePage({ params, searchParams }: PageProps<'/s/[token]'>) {
  const { token } = await params
  const ws = await getSharedWorkspace(token)

  return (
    <Shell t={ws?.t ?? t}>
      {ws === null ? (
        <div className="mx-auto flex max-w-md flex-col items-start gap-4 pt-8">
          <h1 className="text-heading">{t('share.title')}</h1>
          <p>{t('share.invalid')}</p>
          <Link href="/" className="btn-ghost">
            {t('errors.backHome')}
          </Link>
        </div>
      ) : (
        <div className="flex flex-col gap-4">
          <p className="text-small text-faint">{t('share.note')}</p>
          <OverviewView ws={ws} params={await searchParams} path={ws.basePath} />
        </div>
      )}
    </Shell>
  )
}
