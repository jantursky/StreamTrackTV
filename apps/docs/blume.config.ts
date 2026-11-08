import { readFileSync } from 'node:fs'

import { defineConfig } from 'blume'

const pkg = JSON.parse(readFileSync(new URL('../../packages/schema/package.json', import.meta.url), 'utf8')) as {
  version: string
}

export default defineConfig({
  title: 'StreamTrack TV',
  description: 'Privacy-first usage analytics for Android TV. Self-hosted on Vercel and Supabase.',
  logo: { image: { light: '/logo.svg', dark: '/icon-dark.svg', alt: '' } },
  github: {
    owner: 'jantursky',
    repo: 'StreamTrackTV',
    dir: 'apps/docs',
  },
  navigation: {
    actions: [
      { href: 'https://streamtrack-tv.vercel.app/demo', label: 'Demo' },
      { href: 'https://www.npmjs.com/package/streamtrack-tv', label: 'npm' },
      { href: 'https://github.com/jantursky/StreamTrackTV/releases', label: 'Releases' },
    ],
  },
  deployment: { site: 'https://streamtrack-tv-docs.vercel.app' },
  content: {
    root: 'docs',
  },
  theme: {
    accent: { light: '#3ba6f1', dark: '#5bb6f5' },
    mode: 'system',
    fonts: { display: 'inter-tight', body: 'inter' },
  },
  variables: {
    version: pkg.version,
  },
})
