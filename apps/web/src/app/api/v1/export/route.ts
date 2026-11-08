import type { NextRequest } from 'next/server'
import { currentUser } from '@/lib/auth/session'
import { createSupabaseSource } from '@/lib/data/supabase-source'
import { parseExportQuery, renderExport } from '@/lib/export/export'
import { apiError } from '@/lib/http/api'
import { guarded } from '@/lib/http/route'
import { createSessionClient } from '@/lib/supabase/server'

/** Download of the signed-in user's buckets. Reads through the session, so row level security applies. */
export function GET(request: NextRequest) {
  return guarded(async () => {
    const user = await currentUser()
    const client = user && (await createSessionClient())
    if (!user || !client) return apiError('unauthorized', 'Sign in to export your data')

    const query = parseExportQuery(request.nextUrl.searchParams)
    if (query === null) return apiError('invalid_payload', 'Expected from and to as YYYY-MM-DD and format csv or json')

    const source = createSupabaseSource(client, user.id, () => new Date())
    const [devices, buckets] = await Promise.all([
      source.devices(),
      source.buckets(query, query.device ? [query.device] : null),
    ])
    const file = renderExport(query, buckets, devices, new Date())
    return new Response(file.body, {
      headers: {
        'Content-Type': file.contentType,
        'Content-Disposition': `attachment; filename="${file.fileName}"`,
        'Cache-Control': 'no-store',
      },
    })
  })
}
