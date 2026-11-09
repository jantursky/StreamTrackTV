import {
  LIMITS,
  apiErrorSchema,
  pinResetPollResponseSchema,
  pinResetStartResponseSchema,
  type PinResetStartResponse,
} from 'streamtrack-tv'
import { beforeEach, describe, expect, it } from 'vitest'
import {
  PIN_RESET_LIMITS,
  confirmPinReset,
  pollPinReset,
  startPinReset,
  type PinResetDeps,
} from '../src/lib/pin-reset/pin-reset'
import { sha256 } from '../src/lib/security/tokens'
import { MemoryIngestStore, MemoryLimiter, MemoryPinResetStore, jsonRequest } from './helpers/memory'

const START = 'https://tv.example/api/v1/pin-reset/start'
const POLL = 'https://tv.example/api/v1/pin-reset/poll'
const TTL = LIMITS.pinResetTtlSeconds * 1000
const DEVICE_TOKEN = 'd'.repeat(43)
const OTHER_TOKEN = 'e'.repeat(43)

let store: MemoryPinResetStore
let devices: MemoryIngestStore
let limiter: MemoryLimiter
let now: Date
let deps: PinResetDeps

beforeEach(() => {
  store = new MemoryPinResetStore()
  devices = new MemoryIngestStore()
  limiter = new MemoryLimiter()
  now = new Date('2026-11-08T20:00:00Z')
  devices.devices.push(
    { id: 'tv-1', userId: 'owner', tokenHash: sha256(DEVICE_TOKEN) },
    { id: 'tv-2', userId: 'neighbour', tokenHash: sha256(OTHER_TOKEN) },
  )
  store.owners.set('tv-1', 'owner')
  store.owners.set('tv-2', 'neighbour')
  deps = { store, devices, limiter, now: () => now }
})

const auth = (token: string) => ({ authorization: `Bearer ${token}` })
const startRaw = (token = DEVICE_TOKEN) => startPinReset(jsonRequest(START, {}, auth(token)), deps)
const pollRaw = (pollToken: string, token = DEVICE_TOKEN) =>
  pollPinReset(jsonRequest(POLL, { pollToken }, auth(token)), deps)

async function start(token = DEVICE_TOKEN): Promise<PinResetStartResponse> {
  const response = await startRaw(token)
  expect(response.status).toBe(201)
  return pinResetStartResponseSchema.parse(await response.json())
}

const poll = async (pollToken: string) => pinResetPollResponseSchema.parse(await (await pollRaw(pollToken)).json())

describe('PIN reset flow', () => {
  it('goes pending, confirmed, then tells the TV once', async () => {
    const { code, pollToken, expiresAt } = await start()
    expect(Date.parse(expiresAt) - now.getTime()).toBe(TTL)
    expect(store.requests[0]?.codeHash).toBe(sha256(code))
    expect(store.requests[0]?.deviceId).toBe('tv-1')

    expect(await poll(pollToken)).toEqual({ status: 'pending' })
    expect(await confirmPinReset(code.toLowerCase(), 'owner', deps)).toEqual({ status: 'confirmed' })
    expect(await poll(pollToken)).toEqual({ status: 'confirmed' })
    expect(await poll(pollToken)).toEqual({ status: 'expired' })
  })

  it('refuses a signed-in user who does not own the TV and leaves the request pending', async () => {
    const { code, pollToken } = await start()
    expect(await confirmPinReset(code, 'neighbour', deps)).toEqual({ status: 'forbidden' })
    expect(await poll(pollToken)).toEqual({ status: 'pending' })
  })

  it('refuses a code a second time', async () => {
    const { code } = await start()
    await confirmPinReset(code, 'owner', deps)
    expect(await confirmPinReset(code, 'owner', deps)).toEqual({ status: 'expired' })
  })

  it('expires unconfirmed requests after the TTL', async () => {
    const { code, pollToken } = await start()
    now = new Date(now.getTime() + TTL + 1)
    expect(await poll(pollToken)).toEqual({ status: 'expired' })
    expect(await confirmPinReset(code, 'owner', deps)).toEqual({ status: 'expired' })
  })

  it('lets a confirmed request be collected for one more TTL, then drops it', async () => {
    const { code, pollToken } = await start()
    await confirmPinReset(code, 'owner', deps)
    now = new Date(now.getTime() + 2 * TTL + 1)
    expect(await poll(pollToken)).toEqual({ status: 'expired' })
  })

  it('replaces an open request when the TV asks again', async () => {
    const first = await start()
    const second = await start()
    expect(await confirmPinReset(first.code, 'owner', deps)).toEqual({ status: 'invalid' })
    expect(await confirmPinReset(second.code, 'owner', deps)).toEqual({ status: 'confirmed' })
  })

  it('rejects codes of the wrong length and unknown codes', async () => {
    await start()
    expect(await confirmPinReset('ABC', 'owner', deps)).toEqual({ status: 'invalid' })
    expect(await confirmPinReset('ZZZZZZ', 'owner', deps)).toEqual({ status: 'invalid' })
  })
})

describe('PIN reset device checks', () => {
  it('answers 401 without a token, with a made-up token and for a revoked device', async () => {
    for (const headers of [{}, auth('z'.repeat(43)), { authorization: 'Basic abc' }]) {
      const response = await startPinReset(jsonRequest(START, {}, headers), deps)
      expect(response.status).toBe(401)
      expect(apiErrorSchema.parse(await response.json()).error).toBe('unauthorized')
    }
    devices.devices.length = 0
    expect((await startRaw()).status).toBe(401)
    expect((await pollRaw('z'.repeat(43))).status).toBe(401)
  })

  it('does not let another TV poll a request that is not its own', async () => {
    const { pollToken } = await start()
    expect((await pollRaw(pollToken, OTHER_TOKEN)).status).toBe(404)
  })

  it('answers 404 for an unknown poll token and 400 for a bad body', async () => {
    expect((await pollRaw('z'.repeat(43))).status).toBe(404)
    expect((await startPinReset(jsonRequest(START, { deviceId: 'tv-2' }, auth(DEVICE_TOKEN)), deps)).status).toBe(400)
    expect((await pollPinReset(jsonRequest(POLL, { pollToken: 'short' }, auth(DEVICE_TOKEN)), deps)).status).toBe(400)
  })
})

describe('PIN reset limits', () => {
  it('limits starts per device', async () => {
    limiter.hits.set('pin-reset-start:tv-1', PIN_RESET_LIMITS.start.limit)
    const response = await startRaw()
    expect(response.status).toBe(429)
    expect(apiErrorSchema.parse(await response.json()).error).toBe('rate_limited')
    expect((await startRaw(OTHER_TOKEN)).status).toBe(201)
  })

  it('limits polling per token and says when to retry', async () => {
    const { pollToken } = await start()
    limiter.hits.set(`pin-reset-poll:${sha256(pollToken)}`, PIN_RESET_LIMITS.poll.limit)
    now = new Date('2026-11-08T20:01:40Z')
    const response = await pollRaw(pollToken)
    expect(response.status).toBe(429)
    expect(response.headers.get('Retry-After')).toBe('500')
  })

  it('limits confirmations per user', async () => {
    limiter.hits.set('pin-reset-confirm:owner', PIN_RESET_LIMITS.confirm.limit)
    expect(await confirmPinReset('ABCDEF', 'owner', deps)).toEqual({ status: 'rate_limited' })
  })

  it('retries a colliding code and gives up after three tries', async () => {
    store.requests.push({
      id: 'x',
      deviceId: 'tv-2',
      codeHash: 'y',
      pollTokenHash: 'z',
      expiresAt: new Date(now.getTime() + TTL).toISOString(),
      confirmedBy: null,
      consumedAt: null,
    })
    const create = store.create.bind(store)
    let calls = 0
    store.create = (request) => {
      calls++
      return calls <= 3 ? Promise.resolve(false) : create(request)
    }
    expect((await startRaw()).status).toBe(500)
    expect(calls).toBe(3)
  })
})
