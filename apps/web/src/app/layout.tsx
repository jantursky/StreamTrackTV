import type { Metadata, Viewport } from 'next'
import { cookies } from 'next/headers'
import type { ReactNode } from 'react'
import { siteUrl } from '@/lib/env'
import { createTranslator } from '@/lib/i18n/translate'
import { parseTheme } from '@/lib/theme'
import './globals.css'

const t = createTranslator()

export const metadata: Metadata = {
  metadataBase: new URL(siteUrl()),
  title: { default: t('app.name'), template: `%s · ${t('app.name')}` },
  description: t('app.description'),
  applicationName: t('app.name'),
  icons: { icon: '/favicon.svg', apple: '/apple-touch-icon.png' },
  appleWebApp: { capable: true, title: t('app.name'), statusBarStyle: 'default' },
  openGraph: { title: t('app.name'), description: t('app.description'), type: 'website' },
}

export const viewport: Viewport = {
  colorScheme: 'light dark',
  themeColor: [
    { media: '(prefers-color-scheme: light)', color: '#fafaf9' },
    { media: '(prefers-color-scheme: dark)', color: '#0c0a09' },
  ],
}

export default async function RootLayout({ children }: { children: ReactNode }) {
  // Reading the cookie also renders every page per request, which the nonce-based CSP requires.
  const theme = parseTheme((await cookies()).get('theme')?.value)
  return (
    <html lang="en" data-theme={theme === 'system' ? undefined : theme}>
      <body>
        <a
          href="#main"
          className="sr-only focus:not-sr-only focus:fixed focus:top-2 focus:left-2 focus:z-50 focus:rounded-pill focus:bg-surface focus:px-4 focus:py-2 focus:text-ink"
        >
          {t('common.skipToContent')}
        </a>
        {children}
      </body>
    </html>
  )
}
