import type { MetadataRoute } from 'next'
import { createTranslator } from '@/lib/i18n/translate'

const t = createTranslator()

export default function manifest(): MetadataRoute.Manifest {
  return {
    id: '/',
    name: t('app.name'),
    short_name: 'StreamTrack',
    description: t('app.description'),
    start_url: '/app',
    scope: '/',
    display: 'standalone',
    background_color: '#fafaf9',
    theme_color: '#fafaf9',
    icons: [
      { src: '/favicon.svg', sizes: 'any', type: 'image/svg+xml' },
      { src: '/icons/icon-192.png', sizes: '192x192', type: 'image/png' },
      { src: '/icons/icon-512.png', sizes: '512x512', type: 'image/png' },
      { src: '/icons/icon-maskable-512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' },
    ],
  }
}
