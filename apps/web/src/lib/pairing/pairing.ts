import {
  LIMITS,
  pairPollRequestSchema,
  pairStartRequestSchema,
  type PairPollResponse,
  type PairStartResponse,
} from 'streamtrack-tv'
import { apiError, jsonResponse, parseBody, rateLimited } from '../http/api'
import type { RateLimiter } from '../ingest/ports'
import { generatePairCode, generateToken, normalizePairCode, sha256 } from '../security/tokens'

export interface PairingRecord {
  id: string
  deviceName: string
  expiresAt: string
  deviceId: string | null
  deliveredAt: string | null
}

export interface NewPairing {
  codeHash: string
  pollTokenHash: string
  deviceName: string
  model: string | null
  expiresAt: string
}

export interface PairingStore {
  /** `false` when a live pairing already uses the same code. */
  create(pairing: NewPairing): Promise<boolean>
  byPollTokenHash(pollTokenHash: string): Promise<PairingRecord | null>
  byCodeHash(codeHash: string): Promise<PairingRecord | null>
  /** Creates the device for `userId` and links it; `null` when someone claimed the code first. */
  claim(pairingId: string, userId: string): Promise<string | null>
  /** Stores the device token hash and consumes the pairing; `false` when it was already delivered. */
  deliver(pairingId: string, tokenHash: string): Promise<boolean>
}

export interface PairingDeps {
  store: PairingStore
  limiter: RateLimiter
  now: () => Date
}

export type PairingState = 'pending' | 'claimed' | 'delivered' | 'expired'

const TTL_MS = LIMITS.pairCodeTtlSeconds * 1000
const PAIR_MAX_BYTES = 4096
/** How often the TV app polls. The poll limit leaves twice that rate for retries. */
export const POLL_INTERVAL_SECONDS = 5
const POLLS_PER_CODE = (2 * LIMITS.pairCodeTtlSeconds) / POLL_INTERVAL_SECONDS
export const PAIR_LIMITS = {
  /** Per client address. */
  start: { limit: 10, windowSeconds: 3600 },
  /** Per poll token. */
  poll: { limit: POLLS_PER_CODE, windowSeconds: LIMITS.pairCodeTtlSeconds },
  /** Per client address, so guessing poll tokens costs as much as polling. Leaves room for a few TVs. */
  pollAddress: { limit: 4 * POLLS_PER_CODE, windowSeconds: LIMITS.pairCodeTtlSeconds },
  /** Per signed-in user, which makes guessing codes pointless. */
  confirm: { limit: 10, windowSeconds: 600 },
} as const

/**
 * The lifecycle of a pairing: pending until the user confirms the code, then claimed until the TV
 * collects its token (within one more TTL), then delivered. Unconfirmed codes expire after the TTL.
 */
export function pairingState(record: PairingRecord, now: Date): PairingState {
  if (record.deliveredAt !== null) return 'delivered'
  const expires = Date.parse(record.expiresAt)
  if (record.deviceId !== null) return now.getTime() <= expires + TTL_MS ? 'claimed' : 'expired'
  return now.getTime() <= expires ? 'pending' : 'expired'
}

/** `POST /api/v1/pair/start`: the TV asks for a code to show and a token to poll with. */
export async function startPairing(request: Request, clientKey: string, deps: PairingDeps): Promise<Response> {
  const { limit, windowSeconds } = PAIR_LIMITS.start
  if (!(await deps.limiter.hit(`pair-start:${sha256(clientKey)}`, limit, windowSeconds))) {
    return rateLimited('Too many pairing attempts, try again later', windowSeconds, deps.now())
  }
  const body = await parseBody(request, pairStartRequestSchema, PAIR_MAX_BYTES)
  if (!body.ok) return body.response

  const expiresAt = new Date(deps.now().getTime() + TTL_MS).toISOString()
  const pollToken = generateToken()
  // A collision among live codes is rare (32^6 codes); retry a few times before giving up.
  for (let attempt = 0; attempt < 3; attempt++) {
    const code = generatePairCode()
    const created = await deps.store.create({
      codeHash: sha256(code),
      pollTokenHash: sha256(pollToken),
      deviceName: body.value.deviceName,
      model: body.value.model ?? null,
      expiresAt,
    })
    if (created) {
      const response: PairStartResponse = { code, pollToken, expiresAt }
      return jsonResponse(response, { status: 201 })
    }
  }
  return apiError('server_error', 'Could not allocate a pairing code')
}

/**
 * `POST /api/v1/pair/poll`: hands out the device token exactly once, after the user confirmed. The token is
 * looked up before its own limit is counted, so made-up tokens do not create counter rows; the address
 * limit covers those.
 */
export async function pollPairing(request: Request, clientKey: string, deps: PairingDeps): Promise<Response> {
  const body = await parseBody(request, pairPollRequestSchema, PAIR_MAX_BYTES)
  if (!body.ok) return body.response

  const address = PAIR_LIMITS.pollAddress
  if (!(await deps.limiter.hit(`pair-poll-address:${sha256(clientKey)}`, address.limit, address.windowSeconds))) {
    return rateLimited('Polling too fast', address.windowSeconds, deps.now())
  }
  const pollTokenHash = sha256(body.value.pollToken)
  const record = await deps.store.byPollTokenHash(pollTokenHash)
  if (record === null) return apiError('not_found', 'Unknown poll token')

  const { limit, windowSeconds } = PAIR_LIMITS.poll
  if (!(await deps.limiter.hit(`pair-poll:${pollTokenHash}`, limit, windowSeconds))) {
    return rateLimited('Polling too fast', windowSeconds, deps.now())
  }

  let response: PairPollResponse = { status: 'expired' }
  const state = pairingState(record, deps.now())
  if (state === 'pending') response = { status: 'pending' }
  if (state === 'claimed') {
    const deviceToken = generateToken()
    if (await deps.store.deliver(record.id, sha256(deviceToken))) response = { status: 'claimed', deviceToken }
  }
  return jsonResponse(response)
}

export type ConfirmResult =
  { status: 'paired'; deviceId: string; deviceName: string } | { status: 'invalid' | 'expired' | 'rate_limited' }

/** The signed-in user confirms the code shown on the TV. */
export async function confirmPairing(rawCode: string, userId: string, deps: PairingDeps): Promise<ConfirmResult> {
  const { limit, windowSeconds } = PAIR_LIMITS.confirm
  if (!(await deps.limiter.hit(`pair-confirm:${userId}`, limit, windowSeconds))) return { status: 'rate_limited' }

  const code = normalizePairCode(rawCode)
  if (code.length !== LIMITS.pairCodeLength) return { status: 'invalid' }
  const record = await deps.store.byCodeHash(sha256(code))
  if (record === null) return { status: 'invalid' }
  if (pairingState(record, deps.now()) !== 'pending') return { status: 'expired' }

  const deviceId = await deps.store.claim(record.id, userId)
  return deviceId === null ? { status: 'expired' } : { status: 'paired', deviceId, deviceName: record.deviceName }
}
