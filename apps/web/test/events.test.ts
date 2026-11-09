import { SCHEMA_VERSION, apiErrorSchema, eventsResponseSchema, LIMITS } from 'streamtrack-tv'
import { beforeEach, describe, expect, it } from 'vitest'
import { EVENTS_RATE_LIMIT, handleEvents } from '../src/lib/events/events'
import { sha256 } from '../src/lib/security/tokens'
import { MemoryEventsStore, MemoryIngestStore, MemoryLimiter, jsonRequest } from './helpers/memory'

const TOKEN = 'a'.repeat(43)
const URL = 'https://tv.example/api/v1/events'
const NOW = new Date('2026-11-09T12:00:00Z')

const event = (n: number, extra: object = {}) => ({
  eventId: `0b9a3a52-6f0c-4b8e-9a3e-${String(n).padStart(12, '0')}`,
  type: 'pin_wrong',
  at: '2026-11-09T11:59:00Z',
  value: 3,
  ...extra,
})
const body = (...events: object[]) => ({ schemaVersion: SCHEMA_VERSION, events })

let store: MemoryEventsStore
let devices: MemoryIngestStore
let limiter: MemoryLimiter
const send = (payload: unknown, token: string | null = TOKEN) =>
  handleEvents(jsonRequest(URL, payload, token ? { authorization: `Bearer ${token}` } : {}), {
    store,
    devices,
    limiter,
    now: () => NOW,
  })

beforeEach(() => {
  store = new MemoryEventsStore()
  devices = new MemoryIngestStore()
  limiter = new MemoryLimiter()
  devices.devices.push({ id: 'tv-1', userId: 'user-1', tokenHash: sha256(TOKEN) })
})

describe('POST /api/v1/events', () => {
  it('stores a valid batch and says how many are new', async () => {
    const response = await send(body(event(1), event(2, { type: 'app_opened', value: undefined })))
    expect(response.status).toBe(200)
    expect(eventsResponseSchema.parse(await response.json())).toEqual({ accepted: 2 })
    expect(store.events.size).toBe(2)
  })

  it('stores a batch that is sent twice once', async () => {
    await send(body(event(1), event(2)))
    const again = await send(body(event(1), event(2), event(3)))
    expect(eventsResponseSchema.parse(await again.json())).toEqual({ accepted: 1 })
    expect(store.events.size).toBe(3)
  })

  it('rejects an unknown type, an extra field and an empty or oversized batch, storing nothing', async () => {
    const bad = [
      body(event(1, { type: 'pin_typed' })),
      body(event(1, { pin: '1234' })),
      body(),
      body(...Array.from({ length: LIMITS.maxEventsPerBatch + 1 }, (_, i) => event(i + 1))),
    ]
    for (const payload of bad) {
      const response = await send(payload)
      expect(response.status).toBe(400)
      expect(apiErrorSchema.parse(await response.json()).error).toBe('invalid_payload')
    }
    expect(store.events.size).toBe(0)
  })

  it('rejects an event more than a day ahead of the server clock, and takes one just inside', async () => {
    const tooFar = new Date(NOW.getTime() + LIMITS.maxEventFutureMs + 1000).toISOString()
    const inside = new Date(NOW.getTime() + LIMITS.maxEventFutureMs - 1000).toISOString()
    const refused = await send(body(event(1), event(2, { at: tooFar })))
    expect(refused.status).toBe(400)
    expect(((await refused.json()) as { message: string }).message).toMatch(/^events\.1\.at/)
    expect(store.events.size).toBe(0)
    expect((await send(body(event(3, { at: inside })))).status).toBe(200)
  })

  it('answers 401 without a token, with a malformed one and for an unknown or revoked device', async () => {
    expect((await send(body(event(1)), null)).status).toBe(401)
    expect((await send(body(event(1)), 'short')).status).toBe(401)
    expect((await send(body(event(1)), 'b'.repeat(43))).status).toBe(401)
    expect(store.events.size).toBe(0)
  })

  it('limits uploads per device', async () => {
    limiter.hits.set('events:tv-1', EVENTS_RATE_LIMIT.limit)
    const response = await send(body(event(1)))
    expect(response.status).toBe(429)
    expect(response.headers.get('retry-after')).not.toBeNull()
    expect(store.events.size).toBe(0)
  })
})
