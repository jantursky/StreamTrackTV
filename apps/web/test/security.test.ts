import { createHash } from 'node:crypto'
import { PAIR_CODE_ALPHABET, pairStartResponseSchema } from 'streamtrack-tv'
import { describe, expect, it } from 'vitest'
import { isProtectedPath, loginUrl, safeNext } from '../src/lib/auth/redirects'
import { contentSecurityPolicy, createNonce } from '../src/lib/http/csp'
import { isCronRequest } from '../src/lib/http/cron'
import {
  bearerToken,
  digestsEqual,
  generatePairCode,
  generateToken,
  normalizePairCode,
  sha256,
} from '../src/lib/security/tokens'

describe('tokens', () => {
  it('generates 256-bit url-safe tokens that never repeat', () => {
    const tokens = new Set(Array.from({ length: 200 }, generateToken))
    expect(tokens.size).toBe(200)
    for (const token of tokens) {
      expect(token).toMatch(/^[A-Za-z0-9_-]{43}$/)
      expect(Buffer.from(token, 'base64url')).toHaveLength(32)
    }
  })

  it('hashes with SHA-256 hex', () => {
    expect(sha256('abc')).toBe(createHash('sha256').update('abc').digest('hex'))
    expect(sha256('abc')).toMatch(/^[0-9a-f]{64}$/)
  })

  it('compares digests in constant time and refuses mismatched or empty input', () => {
    expect(digestsEqual(sha256('a'), sha256('a'))).toBe(true)
    expect(digestsEqual(sha256('a'), sha256('b'))).toBe(false)
    expect(digestsEqual(sha256('a'), 'abcd')).toBe(false)
    expect(digestsEqual('', '')).toBe(false)
  })

  it('makes pairing codes the schema accepts, from the readable alphabet', () => {
    for (let i = 0; i < 500; i++) {
      const code = generatePairCode()
      expect(pairStartResponseSchema.shape.code.safeParse(code).success).toBe(true)
      expect(code).toMatch(new RegExp(`^[${PAIR_CODE_ALPHABET}]+$`))
    }
    expect(normalizePairCode(' ab-c 23d ')).toBe('ABC23D')
  })

  it('reads bearer tokens strictly', () => {
    const token = generateToken()
    expect(bearerToken(`Bearer ${token}`)).toBe(token)
    expect(bearerToken(`bearer ${token}`)).toBeNull()
    expect(bearerToken('Bearer short')).toBeNull()
    expect(bearerToken(null)).toBeNull()
  })
})

describe('redirect targets', () => {
  it('only allows same-site relative paths', () => {
    expect(safeNext('/app/devices?x=1')).toBe('/app/devices?x=1')
    expect(safeNext('//evil.example')).toBe('/app')
    expect(safeNext('/\\evil.example')).toBe('/app')
    expect(safeNext('https://evil.example')).toBe('/app')
    expect(safeNext(undefined, '/x')).toBe('/x')
    for (const attack of [
      '/\t/evil.example',
      '/\n/evil.example',
      '/\r\n/evil.example',
      '/\u0000/evil.example',
      '/\\/evil.example',
      '\\\\evil.example',
      'http://evil.example/app',
      '//evil.example/app',
      '///evil.example',
      'javascript:alert(1)',
      '',
    ]) {
      expect(safeNext(attack), JSON.stringify(attack)).toBe('/app')
    }
    // Still-encoded input stays a local path: the browser does not decode `%2F` or `%09` into a host.
    expect(safeNext('/%2F%2Fevil.example')).toBe('/%2F%2Fevil.example')
    expect(safeNext('/%09/evil.example')).toBe('/%09/evil.example')
    expect(safeNext(decodeURIComponent('/%09/evil.example'))).toBe('/app')
    expect(safeNext('/app/../pair?code=AB#frag')).toBe('/pair?code=AB')
    expect(loginUrl('/pair', '?code=ABC')).toBe('/login?next=%2Fpair%3Fcode%3DABC')
  })

  it('protects the dashboard, pairing and password reset only', () => {
    expect(isProtectedPath('/app')).toBe(true)
    expect(isProtectedPath('/app/settings')).toBe(true)
    expect(isProtectedPath('/pair')).toBe(true)
    expect(isProtectedPath('/reset-password')).toBe(true)
    expect(isProtectedPath('/apps')).toBe(false)
    expect(isProtectedPath('/demo')).toBe(false)
  })
})

describe('content security policy', () => {
  it('is strict in production', () => {
    const csp = contentSecurityPolicy('n0nce', false)
    expect(csp).toContain("script-src 'self' 'nonce-n0nce' 'strict-dynamic'")
    expect(csp).toContain("style-src 'self' 'nonce-n0nce'")
    expect(csp).toContain("frame-ancestors 'none'")
    expect(csp).toContain("object-src 'none'")
    expect(csp).not.toContain('unsafe')
  })

  it('relaxes only what development tooling needs', () => {
    const csp = contentSecurityPolicy('n', true)
    expect(csp).toContain("'unsafe-eval'")
    expect(csp).toContain("style-src 'self' 'unsafe-inline'")
  })

  it('creates a fresh nonce per call', () => {
    expect(createNonce()).not.toBe(createNonce())
  })
})

describe('cron authorization', () => {
  const request = (auth?: string) =>
    new Request('https://x/api/cron/digest', { headers: auth ? { authorization: auth } : {} })

  it('accepts only the configured secret', () => {
    expect(isCronRequest(request('Bearer s3cret'), 's3cret')).toBe(true)
    expect(isCronRequest(request('Bearer wrong'), 's3cret')).toBe(false)
    expect(isCronRequest(request(), 's3cret')).toBe(false)
  })

  it('refuses everything when no secret is configured', () => {
    expect(isCronRequest(request('Bearer '), null)).toBe(false)
  })
})
