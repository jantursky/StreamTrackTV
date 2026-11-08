import { isDemoOnly } from '@/lib/env'
import { apiError, jsonResponse } from '@/lib/http/api'
import { isCronRequest } from '@/lib/http/cron'
import { guarded } from '@/lib/http/route'
import { createAdminClient } from '@/lib/supabase/server'
import { unwrap } from '@/lib/supabase/query'

/** Daily: deletes buckets past each user's retention plus expired pairing codes, links and counters. */
export function GET(request: Request) {
  return guarded(async () => {
    if (!isCronRequest(request)) return apiError('unauthorized', 'Missing or wrong cron secret')
    const admin = isDemoOnly() ? null : createAdminClient()
    if (admin === null) return jsonResponse({ skipped: 'Supabase is not configured' })
    unwrap(await admin.rpc('apply_retention'))
    return jsonResponse({ status: 'ok' })
  })
}
