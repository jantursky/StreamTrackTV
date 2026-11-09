import { createAdminClient } from '../supabase/server'
import { isDemoOnly } from '../env'
import {
  supabaseEventsStore,
  supabaseIngestStore,
  supabasePairingStore,
  supabasePinResetStore,
  supabaseRateLimiter,
} from '../supabase/stores'
import { apiError } from './api'

/** Service-role ports for the device API; `null` on a demo-only deployment. */
export function deviceApi() {
  const admin = isDemoOnly() ? null : createAdminClient()
  if (admin === null) return null
  return {
    events: supabaseEventsStore(admin),
    ingest: supabaseIngestStore(admin),
    pairing: supabasePairingStore(admin),
    pinReset: supabasePinResetStore(admin),
    limiter: supabaseRateLimiter(admin),
    now: () => new Date(),
  }
}

export const disabled = () => apiError('not_found', 'The device API is not enabled on this deployment')

/** Turns any unexpected failure into an `ApiError` without leaking internals. */
export async function guarded(handler: () => Promise<Response>): Promise<Response> {
  try {
    return await handler()
  } catch (error) {
    console.error(error)
    return apiError('server_error', 'Unexpected server error')
  }
}

/** Client address as reported by the platform proxy; only used to key rate limits. */
export function clientAddress(request: Request): string {
  const forwarded = request.headers.get('x-forwarded-for')?.split(',')[0]?.trim()
  if (forwarded) return forwarded
  return request.headers.get('x-real-ip') ?? 'unknown'
}
