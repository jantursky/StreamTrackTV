import { notFound, redirect } from 'next/navigation'
import { cache } from 'react'
import { requireUser } from './auth/session'
import { appDirectory } from './data/catalog'
import { createDemoSource } from './data/demo-source'
import { sharedSource, type DashboardSource } from './data/source'
import { createSupabaseSource } from './data/supabase-source'
import type { AppInfo, Device, Settings } from './data/types'
import { isDemoOnly } from './env'
import { createTranslator, type Translate } from './i18n/translate'
import { sha256 } from './security/tokens'
import { createAdminClient, createSessionClient } from './supabase/server'
import { param, type SearchParams } from './time/range'
import { z } from 'zod'
import { parseRow } from './supabase/query'

/** Everything a dashboard view needs to render, independent of where the data comes from. */
export interface Workspace {
  source: DashboardSource
  /** `/app`, `/demo` or `/s/<token>`; section links hang off it. */
  basePath: string
  /** True only for the signed-in owner; demo and share views are read-only. */
  editable: boolean
  settings: Settings
  locale: string
  t: Translate
}

async function workspace(source: DashboardSource, basePath: string, editable: boolean): Promise<Workspace> {
  const settings = await source.settings()
  return { source, basePath, editable, settings, locale: settings.locale, t: createTranslator(settings.locale) }
}

/** Resolves the `[space]` segment: the seeded demo or the signed-in user's household. */
export const getWorkspace = cache(async (space: string): Promise<Workspace> => {
  if (space === 'demo') return workspace(createDemoSource(), '/demo', false)
  if (space !== 'app') notFound()
  if (isDemoOnly()) redirect('/demo')
  const user = await requireUser('/app')
  const client = await createSessionClient()
  if (client === null) redirect('/demo')
  return workspace(
    createSupabaseSource(client, user.id, () => new Date()),
    '/app',
    true,
  )
})

const shareRow = z.object({ user_id: z.string(), device_id: z.string().nullable() })

/** A public share link: the owner's data through the service role, narrowed to what the link allows. */
export const getSharedWorkspace = cache(async (token: string): Promise<Workspace | null> => {
  const admin = isDemoOnly() ? null : createAdminClient()
  if (admin === null || !/^[A-Za-z0-9_-]{43}$/.test(token)) return null
  const link = parseRow(
    shareRow,
    await admin
      .from('share_links')
      .select('user_id, device_id')
      .eq('token_hash', sha256(token))
      .gt('expires_at', new Date().toISOString())
      .maybeSingle(),
  )
  if (link === null) return null
  const source = sharedSource(
    createSupabaseSource(admin, link.user_id, () => new Date()),
    link.device_id,
  )
  return workspace(source, `/s/${token}`, false)
})

export interface ViewContext {
  today: string
  devices: Device[]
  /** Devices selected by the `device` parameter; `null` means all. */
  deviceIds: string[] | null
  resolve: (packageName: string) => AppInfo
}

/** Shared lookups of every view, including the device filter from the URL. */
export async function viewContext(ws: Workspace, params: SearchParams): Promise<ViewContext> {
  const [today, devices, apps] = await Promise.all([ws.source.today(), ws.source.devices(), ws.source.apps(null)])
  const selected = param(params, 'device')
  const deviceIds = selected !== undefined && devices.some((d) => d.id === selected) ? [selected] : null
  return { today, devices, deviceIds, resolve: appDirectory(apps) }
}
