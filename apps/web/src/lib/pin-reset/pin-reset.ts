import {
  LIMITS,
  pinResetPollRequestSchema,
  pinResetStartRequestSchema,
  type PinResetPollResponse,
  type PinResetStartResponse,
} from 'streamtrack-tv'
import { apiError, jsonResponse, parseBody, rateLimited } from '../http/api'
import type { IngestStore, RateLimiter } from '../ingest/ports'
import {
  bearerToken,
  digestsEqual,
  generatePairCode,
  generateToken,
  normalizePairCode,
  sha256,
} from '../security/tokens'

export interface PinResetRecord {
  id: string
  deviceId: string
  expiresAt: string
  confirmedBy: string | null
  consumedAt: string | null
}

export interface NewPinReset {
  deviceId: string
  codeHash: string
  pollTokenHash: string
  expiresAt: string
}

export interface PinResetStore {
  /** Opens a request and drops the device's earlier ones; `false` when a live request uses the same code. */
  create(request: NewPinReset): Promise<boolean>
  byPollTokenHash(pollTokenHash: string): Promise<PinResetRecord | null>
  byCodeHash(codeHash: string): Promise<PinResetRecord | null>
  /** Confirms for the owner of the device; `false` for anyone else, or when it was confirmed or expired. */
  confirm(requestId: string, userId: string): Promise<boolean>
  /** Hands the confirmation to the TV once; `false` when it was not confirmed or already collected. */
  consume(requestId: string): Promise<boolean>
}

export interface PinResetDeps {
  store: PinResetStore
  devices: Pick<IngestStore, 'deviceByTokenHash'>
  limiter: RateLimiter
  now: () => Date
}

export type PinResetState = 'pending' | 'confirmed' | 'consumed' | 'expired'

const TTL_MS = LIMITS.pinResetTtlSeconds * 1000
const MAX_BYTES = 4096
/** How often the TV polls; the limits leave twice that rate for retries. */
export const PIN_RESET_POLL_INTERVAL_SECONDS = 5
const POLLS_PER_CODE = (2 * LIMITS.pinResetTtlSeconds) / PIN_RESET_POLL_INTERVAL_SECONDS
export const PIN_RESET_LIMITS = {
  /** Per device. */
  start: { limit: 5, windowSeconds: 3600 },
  /** Per poll token. */
  poll: { limit: POLLS_PER_CODE, windowSeconds: LIMITS.pinResetTtlSeconds },
  /** Per signed-in user, which makes guessing codes pointless. */
  confirm: { limit: 10, windowSeconds: 600 },
} as const

/** Pending until the owner confirms, then confirmed until the TV collects it (within one more TTL). */
export function pinResetState(record: PinResetRecord, now: Date): PinResetState {
  if (record.consumedAt !== null) return 'consumed'
  const expires = Date.parse(record.expiresAt)
  if (record.confirmedBy !== null) return now.getTime() <= expires + TTL_MS ? 'confirmed' : 'expired'
  return now.getTime() <= expires ? 'pending' : 'expired'
}

/** The paired device behind the bearer token, or the 401 to send. */
async function authenticate(request: Request, deps: PinResetDeps) {
  const token = bearerToken(request.headers.get('authorization'))
  if (token === null)
    return { ok: false as const, response: apiError('unauthorized', 'Missing or malformed device token') }
  const tokenHash = sha256(token)
  const device = await deps.devices.deviceByTokenHash(tokenHash)
  if (device === null || !digestsEqual(device.tokenHash, tokenHash)) {
    return { ok: false as const, response: apiError('unauthorized', 'Unknown or revoked device token') }
  }
  return { ok: true as const, device }
}

/** `POST /api/v1/pin-reset/start`: a paired TV asks for a code to show and a token to poll with. */
export async function startPinReset(request: Request, deps: PinResetDeps): Promise<Response> {
  const auth = await authenticate(request, deps)
  if (!auth.ok) return auth.response
  const { limit, windowSeconds } = PIN_RESET_LIMITS.start
  if (!(await deps.limiter.hit(`pin-reset-start:${auth.device.id}`, limit, windowSeconds))) {
    return rateLimited('Too many PIN reset requests, try again later', windowSeconds, deps.now())
  }
  const body = await parseBody(request, pinResetStartRequestSchema, MAX_BYTES)
  if (!body.ok) return body.response

  const expiresAt = new Date(deps.now().getTime() + TTL_MS).toISOString()
  const pollToken = generateToken()
  for (let attempt = 0; attempt < 3; attempt++) {
    const code = generatePairCode()
    const created = await deps.store.create({
      deviceId: auth.device.id,
      codeHash: sha256(code),
      pollTokenHash: sha256(pollToken),
      expiresAt,
    })
    if (created) {
      const response: PinResetStartResponse = { code, pollToken, expiresAt }
      return jsonResponse(response, { status: 201 })
    }
  }
  return apiError('server_error', 'Could not allocate a reset code')
}

/** `POST /api/v1/pin-reset/poll`: tells the TV once that the owner confirmed. Only the device that asked may poll. */
export async function pollPinReset(request: Request, deps: PinResetDeps): Promise<Response> {
  const auth = await authenticate(request, deps)
  if (!auth.ok) return auth.response
  const body = await parseBody(request, pinResetPollRequestSchema, MAX_BYTES)
  if (!body.ok) return body.response

  const pollTokenHash = sha256(body.value.pollToken)
  const record = await deps.store.byPollTokenHash(pollTokenHash)
  if (record?.deviceId !== auth.device.id) return apiError('not_found', 'Unknown poll token')

  const { limit, windowSeconds } = PIN_RESET_LIMITS.poll
  if (!(await deps.limiter.hit(`pin-reset-poll:${pollTokenHash}`, limit, windowSeconds))) {
    return rateLimited('Polling too fast', windowSeconds, deps.now())
  }

  let response: PinResetPollResponse = { status: 'expired' }
  const state = pinResetState(record, deps.now())
  if (state === 'pending') response = { status: 'pending' }
  if (state === 'confirmed' && (await deps.store.consume(record.id))) response = { status: 'confirmed' }
  return jsonResponse(response)
}

export type ConfirmPinResetResult =
  { status: 'confirmed' } | { status: 'invalid' | 'expired' | 'rate_limited' | 'forbidden' }

/** The signed-in user confirms the code shown on the TV; only the owner of that TV can. */
export async function confirmPinReset(
  rawCode: string,
  userId: string,
  deps: PinResetDeps,
): Promise<ConfirmPinResetResult> {
  const { limit, windowSeconds } = PIN_RESET_LIMITS.confirm
  if (!(await deps.limiter.hit(`pin-reset-confirm:${userId}`, limit, windowSeconds))) return { status: 'rate_limited' }

  const code = normalizePairCode(rawCode)
  if (code.length !== LIMITS.pairCodeLength) return { status: 'invalid' }
  const record = await deps.store.byCodeHash(sha256(code))
  if (record === null) return { status: 'invalid' }
  if (pinResetState(record, deps.now()) !== 'pending') return { status: 'expired' }

  // The store only confirms for the owner; a refusal here is another account, or a request that just changed.
  return (await deps.store.confirm(record.id, userId)) ? { status: 'confirmed' } : { status: 'forbidden' }
}
