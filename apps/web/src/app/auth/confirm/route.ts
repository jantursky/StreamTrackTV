import type { EmailOtpType } from '@supabase/supabase-js'
import { redirect } from 'next/navigation'
import type { NextRequest } from 'next/server'
import { safeNext } from '@/lib/auth/redirects'
import { createSessionClient } from '@/lib/supabase/server'

const OTP_TYPES: readonly EmailOtpType[] = ['signup', 'invite', 'magiclink', 'recovery', 'email_change', 'email']

/**
 * Target of the links in confirmation and password-reset emails. Handles both the PKCE `code` flow and
 * the `token_hash` flow of custom email templates, then continues to `next`.
 */
export async function GET(request: NextRequest) {
  const { searchParams } = request.nextUrl
  const next = safeNext(searchParams.get('next'))
  const client = await createSessionClient()
  if (client === null) redirect('/login?error=link')

  const code = searchParams.get('code')
  const tokenHash = searchParams.get('token_hash')
  const type = OTP_TYPES.find((t) => t === searchParams.get('type'))

  const { error } = code
    ? await client.auth.exchangeCodeForSession(code)
    : tokenHash && type
      ? await client.auth.verifyOtp({ type, token_hash: tokenHash })
      : { error: new Error('Missing code') }

  // redirect() keeps the session cookies the client just set on this response.
  redirect(error ? '/login?error=link' : next)
}
