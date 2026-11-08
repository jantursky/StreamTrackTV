import type { Metadata } from 'next'
import type { ComponentType } from 'react'
import type { MessageKey } from '@/lib/i18n/translate'
import type { SearchParams } from '@/lib/time/range'
import { getWorkspace } from '@/lib/workspace'
import type { ViewProps } from './types'

/** Dashboard sections in tab order; the empty slug is the overview. */
export const SECTIONS = [
  { slug: '', title: 'nav.overview' },
  { slug: 'calendar', title: 'nav.calendar' },
  { slug: 'apps', title: 'nav.apps' },
  { slug: 'remote', title: 'nav.remote' },
  { slug: 'heatmap', title: 'nav.heatmap' },
  { slug: 'devices', title: 'nav.devices' },
  { slug: 'members', title: 'nav.members' },
  { slug: 'goals', title: 'nav.goals' },
  { slug: 'settings', title: 'nav.settings' },
] as const satisfies readonly { slug: string; title: MessageKey }[]

type Slug = (typeof SECTIONS)[number]['slug']

interface SectionPageProps {
  params: Promise<{ space: string }>
  searchParams: Promise<SearchParams>
}

/**
 * Page and metadata of one section. The same view renders under `/app` and `/demo`; which data it shows
 * depends only on the workspace.
 */
export function section(View: ComponentType<ViewProps>, slug: Slug) {
  const title = SECTIONS.find((s) => s.slug === slug)?.title ?? 'nav.overview'
  return {
    generateMetadata: async ({ params }: Pick<SectionPageProps, 'params'>): Promise<Metadata> => {
      const ws = await getWorkspace((await params).space)
      return { title: ws.t(title) }
    },
    Page: async ({ params, searchParams }: SectionPageProps) => {
      const ws = await getWorkspace((await params).space)
      const path = slug ? `${ws.basePath}/${slug}` : ws.basePath
      return <View ws={ws} params={await searchParams} path={path} />
    },
  }
}
