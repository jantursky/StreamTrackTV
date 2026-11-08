import { runDigest } from '@/lib/digest/run'
import { isDemoOnly, mailConfig } from '@/lib/env'
import { apiError, jsonResponse } from '@/lib/http/api'
import { isCronRequest } from '@/lib/http/cron'
import { guarded } from '@/lib/http/route'
import { createAdminClient } from '@/lib/supabase/server'

/** Weekly email summary (Monday morning, see vercel.json). Skips cleanly when no mail provider is set. */
export function GET(request: Request) {
  return guarded(async () => {
    if (!isCronRequest(request)) return apiError('unauthorized', 'Missing or wrong cron secret')
    const mail = mailConfig()
    const admin = isDemoOnly() ? null : createAdminClient()
    if (mail === null || admin === null) {
      return jsonResponse({ skipped: mail === null ? 'RESEND_API_KEY is not set' : 'Supabase is not configured' })
    }
    return jsonResponse(await runDigest(admin, mail, new Date()))
  })
}
