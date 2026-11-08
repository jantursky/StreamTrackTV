'use client'

import { createTranslator } from '@/lib/i18n/translate'

const t = createTranslator()

export default function ErrorPage({ reset }: { error: Error; reset: () => void }) {
  return (
    <main id="main" className="mx-auto flex max-w-md flex-col items-start gap-4 px-4 pt-16">
      <h1 className="text-heading">{t('errors.errorTitle')}</h1>
      <p>{t('errors.errorBody')}</p>
      <button type="button" onClick={reset} className="btn-ghost">
        {t('errors.retry')}
      </button>
    </main>
  )
}
