import {
  LIMITS,
  apiErrorSchema,
  pairPollResponseSchema,
  pairStartResponseSchema,
  type PairStartResponse,
} from 'streamtrack-tv'
import { beforeEach, describe, expect, it } from 'vitest'
import {
  PAIR_LIMITS,
  POLL_INTERVAL_SECONDS,
  confirmPairing,
  pairingState,
  pollPairing,
  startPairing,
  type PairingDeps,
} from '../src/lib/pairing/pairing'
import { sha256 } from '../src/lib/security/tokens'
import { MemoryLimiter, MemoryPairingStore, jsonRequest } from './helpers/memory'

const START = 'https://tv.example/api/v1/pair/start'
const POLL = 'https://tv.example/api/v1/pair/poll'
const TTL = LIMITS.pairCodeTtlSeconds * 1000

let store: MemoryPairingStore
let limiter: MemoryLimiter
let now: Date
let deps: PairingDeps

beforeEach(() => {
  store = new MemoryPairingStore()
  limiter = new MemoryLimiter()
  now = new Date('2026-11-08T20:00:00Z')
  deps = { store, limiter, now: () => now }
})

async function start(): Promise<PairStartResponse> {
  const response = await startPairing(jsonRequest(START, { deviceName: 'Living room TV' }), '203.0.113.7', deps)
  expect(response.status).toBe(201)
  return pairStartResponseSchema.parse(await response.json())
}

const TV = '203.0.113.7'
const pollRaw = (pollToken: string, address = TV) => pollPairing(jsonRequest(POLL, { pollToken }), address, deps)
const poll = async (pollToken: string) => pairPollResponseSchema.parse(await (await pollRaw(pollToken)).json())

describe('pairing flow', () => {
  it('goes pending, claimed, then hands out the token exactly once', async () => {
    const { code, pollToken, expiresAt } = await start()
    expect(Date.parse(expiresAt) - now.getTime()).toBe(TTL)
    expect(store.pairings[0]?.codeHash).toBe(sha256(code))
    expect(store.pairings[0]?.pollTokenHash).toBe(sha256(pollToken))

    expect(await poll(pollToken)).toEqual({ status: 'pending' })

    const confirmed = await confirmPairing(code.toLowerCase(), 'user-1', deps)
    expect(confirmed).toEqual({ status: 'paired', deviceId: 'device-for-user-1', deviceName: 'Living room TV' })

    const claimed = await poll(pollToken)
    if (claimed.status !== 'claimed') throw new Error('expected a token')
    expect(store.pairings[0]?.tokenHash).toBe(sha256(claimed.deviceToken))

    expect(await poll(pollToken)).toEqual({ status: 'expired' })
  })

  it('refuses a code a second time', async () => {
    const { code } = await start()
    await confirmPairing(code, 'user-1', deps)
    expect(await confirmPairing(code, 'user-2', deps)).toEqual({ status: 'expired' })
  })

  it('expires unconfirmed codes after the TTL', async () => {
    const { code, pollToken } = await start()
    now = new Date(now.getTime() + TTL + 1)
    expect(await poll(pollToken)).toEqual({ status: 'expired' })
    expect(await confirmPairing(code, 'user-1', deps)).toEqual({ status: 'expired' })
  })

  it('rejects codes of the wrong length or unknown codes', async () => {
    await start()
    expect(await confirmPairing('ABC', 'user-1', deps)).toEqual({ status: 'invalid' })
    expect(await confirmPairing('ZZZZZZ', 'user-1', deps)).toEqual({ status: 'invalid' })
  })

  it('rate limits confirmations per user', async () => {
    const { limit } = PAIR_LIMITS.confirm
    limiter.hits.set('pair-confirm:user-1', limit)
    expect(await confirmPairing('ABCDEF', 'user-1', deps)).toEqual({ status: 'rate_limited' })
  })

  it('rate limits starts per client address, hashed', async () => {
    limiter.hits.set(`pair-start:${sha256('203.0.113.7')}`, PAIR_LIMITS.start.limit)
    const response = await startPairing(jsonRequest(START, { deviceName: 'TV' }), '203.0.113.7', deps)
    expect(response.status).toBe(429)
    expect(apiErrorSchema.parse(await response.json()).error).toBe('rate_limited')
  })

  it('lets a TV poll at its interval for a whole code lifetime with room for retries', () => {
    const { limit, windowSeconds } = PAIR_LIMITS.poll
    expect(windowSeconds).toBe(LIMITS.pairCodeTtlSeconds)
    expect(limit).toBeGreaterThanOrEqual((LIMITS.pairCodeTtlSeconds / POLL_INTERVAL_SECONDS) * 1.5)
    expect(PAIR_LIMITS.pollAddress.limit).toBeGreaterThan(limit)
  })

  it('rate limits polling per token and says when to retry', async () => {
    const { pollToken } = await start()
    limiter.hits.set(`pair-poll:${sha256(pollToken)}`, PAIR_LIMITS.poll.limit)
    now = new Date('2026-11-08T20:01:40Z') // 100 s into a 600 s window
    const response = await pollRaw(pollToken)
    expect(response.status).toBe(429)
    expect(response.headers.get('Retry-After')).toBe('500')
  })

  it('rate limits polling per client address before looking anything up', async () => {
    const { pollToken } = await start()
    limiter.hits.set(`pair-poll-address:${sha256(TV)}`, PAIR_LIMITS.pollAddress.limit)
    expect((await pollRaw(pollToken)).status).toBe(429)
    expect((await pollRaw(pollToken, '198.51.100.9')).status).toBe(200)
  })

  it('does not count made-up poll tokens per token', async () => {
    const made = 'z'.repeat(43)
    expect((await pollRaw(made)).status).toBe(404)
    expect(limiter.hits.has(`pair-poll:${sha256(made)}`)).toBe(false)
    expect(limiter.hits.get(`pair-poll-address:${sha256(TV)}`)).toBe(1)
  })

  it('retries when a code collides and gives up after three tries', async () => {
    store.collisions = 2
    await start()
    store.collisions = 3
    const response = await startPairing(jsonRequest(START, { deviceName: 'TV' }), 'x', deps)
    expect(response.status).toBe(500)
  })

  it('answers not_found for an unknown poll token', async () => {
    expect((await pollRaw('z'.repeat(43))).status).toBe(404)
  })

  it('validates the start body with the schema', async () => {
    const response = await startPairing(jsonRequest(START, { deviceName: '' }), 'x', deps)
    expect(response.status).toBe(400)
  })
})

describe('pairingState', () => {
  const base = { id: 'p', deviceName: 'TV', expiresAt: '2026-11-08T20:10:00Z', deviceId: null, deliveredAt: null }
  const at = (iso: string) => new Date(iso)

  it('is pending until the TTL ends', () => {
    expect(pairingState(base, at('2026-11-08T20:10:00Z'))).toBe('pending')
    expect(pairingState(base, at('2026-11-08T20:10:01Z'))).toBe('expired')
  })

  it('stays claimed for one more TTL so a slow TV still gets its token', () => {
    const claimed = { ...base, deviceId: 'd' }
    expect(pairingState(claimed, at('2026-11-08T20:19:59Z'))).toBe('claimed')
    expect(pairingState(claimed, at('2026-11-08T20:20:01Z'))).toBe('expired')
  })

  it('is delivered once the token went out', () => {
    expect(pairingState({ ...base, deviceId: 'd', deliveredAt: 'x' }, at('2026-11-08T20:00:00Z'))).toBe('delivered')
  })
})
