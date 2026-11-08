/**
 * Strict, nonce-based Content Security Policy for HTML pages. Next.js reads the nonce from the request
 * header and puts it on its own scripts; `strict-dynamic` lets those load the rest of the bundle.
 * Development adds what React's dev tooling and the error overlay need.
 */
export function contentSecurityPolicy(nonce: string, development: boolean): string {
  const directives: Record<string, string[]> = {
    'default-src': ["'self'"],
    'script-src': ["'self'", `'nonce-${nonce}'`, "'strict-dynamic'", ...(development ? ["'unsafe-eval'"] : [])],
    'style-src': ["'self'", development ? "'unsafe-inline'" : `'nonce-${nonce}'`],
    'img-src': ["'self'", 'data:', 'blob:'],
    'font-src': ["'self'"],
    'connect-src': ["'self'"],
    'manifest-src': ["'self'"],
    'worker-src': ["'self'"],
    'object-src': ["'none'"],
    'base-uri': ["'self'"],
    'form-action': ["'self'"],
    'frame-ancestors': ["'none'"],
  }
  return Object.entries(directives)
    .map(([name, values]) => `${name} ${values.join(' ')}`)
    .join('; ')
}

export function createNonce(): string {
  return Buffer.from(crypto.randomUUID()).toString('base64')
}
