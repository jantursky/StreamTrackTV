/**
 * Runtime configuration. Names follow the Supabase Vercel integration (publishable / secret keys) and
 * fall back to the legacy anon / service-role names for projects set up by hand.
 */

type Env = Readonly<Record<string, string | undefined>>

const present = (value: string | undefined) => (value === undefined || value.trim() === '' ? undefined : value)

export interface SupabaseConfig {
  url: string
  publishableKey: string
}

export function supabaseConfig(env: Env = process.env): SupabaseConfig | null {
  const url = present(env.NEXT_PUBLIC_SUPABASE_URL)
  const publishableKey = present(env.NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY) ?? present(env.NEXT_PUBLIC_SUPABASE_ANON_KEY)
  return url && publishableKey ? { url, publishableKey } : null
}

/** Server-only key that bypasses row level security. Never read from client code. */
export function supabaseSecretKey(env: Env = process.env): string | null {
  return present(env.SUPABASE_SECRET_KEY) ?? present(env.SUPABASE_SERVICE_ROLE_KEY) ?? null
}

/**
 * A demo-only deployment serves the landing page and `/demo`, and turns accounts and the device API off.
 * It is also the fallback when Supabase is not configured at all.
 */
export function isDemoOnly(env: Env = process.env): boolean {
  const flag = present(env.DEMO_ONLY)?.toLowerCase()
  return flag === '1' || flag === 'true' || supabaseConfig(env) === null || supabaseSecretKey(env) === null
}

/** Absolute origin used in emails and share links. */
export function siteUrl(env: Env = process.env): string {
  const explicit = present(env.NEXT_PUBLIC_SITE_URL)
  if (explicit) return explicit.replace(/\/+$/, '')
  const vercel = present(env.VERCEL_PROJECT_PRODUCTION_URL) ?? present(env.VERCEL_URL)
  return vercel ? `https://${vercel}` : 'http://localhost:3000'
}

export function cronSecret(env: Env = process.env): string | null {
  return present(env.CRON_SECRET) ?? null
}

export interface MailConfig {
  apiKey: string
  from: string
}

export function mailConfig(env: Env = process.env): MailConfig | null {
  const apiKey = present(env.RESEND_API_KEY)
  return apiKey ? { apiKey, from: present(env.DIGEST_FROM) ?? 'StreamTrack TV <onboarding@resend.dev>' } : null
}
