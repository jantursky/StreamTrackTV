import { createServerClient } from '@supabase/ssr'
import { createClient, type SupabaseClient } from '@supabase/supabase-js'
import { cookies } from 'next/headers'
import { supabaseConfig, supabaseSecretKey } from '../env'

/**
 * Client acting as the signed-in user, so row level security applies. Create one per request; never
 * share it between requests.
 */
export async function createSessionClient(): Promise<SupabaseClient | null> {
  const config = supabaseConfig()
  if (config === null) return null
  const store = await cookies()
  return createServerClient(config.url, config.publishableKey, {
    cookies: {
      getAll: () => store.getAll(),
      setAll(cookiesToSet) {
        try {
          for (const { name, value, options } of cookiesToSet) store.set(name, value, options)
        } catch {
          // Server Components cannot set cookies; the proxy refreshes the session on the next request.
        }
      },
    },
  })
}

/**
 * Service-role client that bypasses row level security. Only for route handlers and server actions that
 * have authenticated the caller themselves (device token, session or cron secret). Every query made with
 * it must filter by the owning user explicitly.
 */
export function createAdminClient(): SupabaseClient | null {
  const config = supabaseConfig()
  const secret = supabaseSecretKey()
  if (config === null || secret === null) return null
  return createClient(config.url, secret, { auth: { persistSession: false, autoRefreshToken: false } })
}
