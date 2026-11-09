/** Paths that need a signed-in user; the proxy sends anonymous visitors to the sign-in page. */
export function isProtectedPath(pathname: string): boolean {
  return (
    pathname === '/app' ||
    pathname.startsWith('/app/') ||
    pathname === '/pair' ||
    pathname === '/pin-reset' ||
    pathname === '/reset-password'
  )
}

const ORIGIN = 'http://localhost'

/**
 * Post-login target. Only a local path survives: browsers drop tabs and newlines inside URLs and read `\`
 * as `/`, so `/<TAB>/evil.example` or `/\evil.example` would otherwise turn into `//evil.example`.
 */
export function safeNext(next: string | null | undefined, fallback = '/app'): string {
  if (!next?.startsWith('/') || /[\p{Cc}\\]/u.test(next)) return fallback
  const url = URL.parse(next, ORIGIN)
  if (url?.origin !== ORIGIN || url.pathname.startsWith('//')) return fallback
  return url.pathname + url.search
}

export function loginUrl(pathname: string, search: string): string {
  return `/login?next=${encodeURIComponent(safeNext(pathname + search))}`
}
