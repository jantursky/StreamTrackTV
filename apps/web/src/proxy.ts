import { createServerClient } from '@supabase/ssr'
import { NextResponse, type NextRequest } from 'next/server'
import { isProtectedPath, loginUrl } from './lib/auth/redirects'
import { isDemoOnly, supabaseConfig } from './lib/env'
import { contentSecurityPolicy, createNonce } from './lib/http/csp'

/**
 * Runs before every page: sets a per-request CSP nonce, refreshes the Supabase session cookie and sends
 * anonymous visitors of protected pages to sign in. Pages still verify the user themselves.
 */
export async function proxy(request: NextRequest) {
  const nonce = createNonce()
  const csp = contentSecurityPolicy(nonce, process.env.NODE_ENV === 'development')
  const forward = () => {
    const headers = new Headers(request.headers)
    headers.set('x-nonce', nonce)
    headers.set('content-security-policy', csp)
    return NextResponse.next({ request: { headers } })
  }

  let response = forward()
  const config = supabaseConfig()
  const { pathname, search } = request.nextUrl

  if (isDemoOnly() || config === null) {
    if (isProtectedPath(pathname)) response = NextResponse.redirect(new URL('/demo', request.url))
  } else {
    const supabase = createServerClient(config.url, config.publishableKey, {
      cookies: {
        getAll: () => request.cookies.getAll(),
        setAll(cookiesToSet) {
          for (const { name, value } of cookiesToSet) request.cookies.set(name, value)
          response = forward()
          for (const { name, value, options } of cookiesToSet) response.cookies.set(name, value, options)
        },
      },
    })
    // Refreshes an expiring session; nothing may run between creating the client and this call.
    const { data } = await supabase.auth.getClaims()
    if (!data?.claims && isProtectedPath(pathname)) {
      const redirect = NextResponse.redirect(new URL(loginUrl(pathname, search), request.url))
      for (const cookie of response.cookies.getAll()) redirect.cookies.set(cookie)
      response = redirect
    }
  }

  response.headers.set('content-security-policy', csp)
  return response
}

export const config = {
  matcher: [
    '/((?!api/|_next/static|_next/image|favicon\\.svg|apple-touch-icon\\.png|icons/|manifest\\.webmanifest).*)',
  ],
}
