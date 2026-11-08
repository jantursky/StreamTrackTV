import Link from 'next/link'
import { Shell } from '@/components/dashboard/Shell'
import { createTranslator } from '@/lib/i18n/translate'

const t = createTranslator()

export default function NotFound() {
  return (
    <Shell t={t}>
      <div className="mx-auto flex max-w-md flex-col items-start gap-4 pt-8">
        <h1 className="text-heading">{t('errors.notFoundTitle')}</h1>
        <p>{t('errors.notFoundBody')}</p>
        <Link href="/" className="btn-ghost">
          {t('errors.backHome')}
        </Link>
      </div>
    </Shell>
  )
}
