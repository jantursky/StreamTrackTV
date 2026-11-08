import { createHash, randomBytes, randomInt, timingSafeEqual } from 'node:crypto'
import { LIMITS, PAIR_CODE_ALPHABET } from 'streamtrack-tv'

/** 256-bit random secret, base64url encoded (43 characters). */
export function generateToken(): string {
  return randomBytes(32).toString('base64url')
}

/** Hex SHA-256. Only hashes of tokens and codes are stored. */
export function sha256(value: string): string {
  return createHash('sha256').update(value, 'utf8').digest('hex')
}

/** Constant-time comparison of two hex digests. */
export function digestsEqual(a: string, b: string): boolean {
  const left = Buffer.from(a, 'hex')
  const right = Buffer.from(b, 'hex')
  return left.length === right.length && left.length > 0 && timingSafeEqual(left, right)
}

export function generatePairCode(): string {
  return Array.from(
    { length: LIMITS.pairCodeLength },
    () => PAIR_CODE_ALPHABET[randomInt(PAIR_CODE_ALPHABET.length)],
  ).join('')
}

/** Accepts what people type: lower case, spaces and dashes are fine. */
export function normalizePairCode(input: string): string {
  return input.toUpperCase().replace(/[\s-]/g, '')
}

/** Reads `Authorization: Bearer <token>`; `null` when absent or malformed. */
export function bearerToken(header: string | null): string | null {
  const match = /^Bearer ([A-Za-z0-9_-]{32,128})$/.exec(header ?? '')
  return match?.[1] ?? null
}
