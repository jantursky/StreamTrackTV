import { describe, expect, it } from 'vitest'
import {
  KEY_CLASSES,
  LIMITS,
  SCHEMA_VERSION,
  PAIR_CODE_ALPHABET,
  apiErrorSchema,
  bucketKey,
  pairPollRequestSchema,
  pairPollResponseSchema,
  pairStartRequestSchema,
  pairStartResponseSchema,
  syncPayloadSchema,
} from '../src/index.js'

const valid = {
  schemaVersion: SCHEMA_VERSION,
  sentAt: '2026-11-08T20:15:00+01:00',
  timezone: 'Europe/Bratislava',
  usage: [{ day: '2026-11-08', hour: 19, packageName: 'com.netflix.ninja', label: 'Netflix', foregroundMs: 1_800_000 }],
  keys: [{ day: '2026-11-08', hour: 19, keyClass: 'dpad', count: 42 }],
  screen: [{ day: '2026-11-08', hour: 19, screenOnMs: 3_000_000 }],
}

describe('syncPayloadSchema', () => {
  it('accepts a well-formed payload', () => {
    expect(syncPayloadSchema.safeParse(valid).success).toBe(true)
  })

  it('rejects an unknown schema version', () => {
    expect(syncPayloadSchema.safeParse({ ...valid, schemaVersion: 2 }).success).toBe(false)
  })

  it('rejects more than one hour of foreground time in a bucket', () => {
    const usage = [{ ...valid.usage[0], foregroundMs: LIMITS.msPerHour + 1 }]
    expect(syncPayloadSchema.safeParse({ ...valid, usage }).success).toBe(false)
  })

  it('rejects a raw key code in place of a key class', () => {
    const keys = [{ day: '2026-11-08', hour: 19, keyClass: 'KEYCODE_DPAD_UP', count: 1 }]
    expect(syncPayloadSchema.safeParse({ ...valid, keys }).success).toBe(false)
  })

  it('rejects fields the schema does not name, at every level', () => {
    const keys = [{ ...valid.keys[0], keyCode: 19 }]
    const usage = [{ ...valid.usage[0], text: 'typed text' }]
    const screen = [{ ...valid.screen[0], windowTitle: 'Inbox' }]
    expect(syncPayloadSchema.safeParse({ ...valid, keys }).success).toBe(false)
    expect(syncPayloadSchema.safeParse({ ...valid, usage }).success).toBe(false)
    expect(syncPayloadSchema.safeParse({ ...valid, screen }).success).toBe(false)
    expect(syncPayloadSchema.safeParse({ ...valid, windowTitle: 'Inbox' }).success).toBe(false)
  })

  it('caps the key count and the time zone length', () => {
    const keys = [{ ...valid.keys[0], count: LIMITS.maxKeyCount + 1 }]
    expect(syncPayloadSchema.safeParse({ ...valid, keys }).success).toBe(false)
    const timezone = 'x'.repeat(LIMITS.maxTimezoneLength + 1)
    expect(syncPayloadSchema.safeParse({ ...valid, timezone }).success).toBe(false)
  })

  it('rejects a malformed package name', () => {
    const usage = [{ ...valid.usage[0], packageName: 'not a package' }]
    expect(syncPayloadSchema.safeParse({ ...valid, usage }).success).toBe(false)
  })

  it('rejects an hour outside 0-23 and a bad day', () => {
    expect(
      syncPayloadSchema.safeParse({ ...valid, screen: [{ day: '2026-11-08', hour: 24, screenOnMs: 1 }] }).success,
    ).toBe(false)
    expect(
      syncPayloadSchema.safeParse({ ...valid, screen: [{ day: '08.11.2026', hour: 1, screenOnMs: 1 }] }).success,
    ).toBe(false)
  })

  it('caps the number of rows per kind', () => {
    const keys = Array.from({ length: LIMITS.maxRowsPerKind + 1 }, () => valid.keys[0])
    expect(syncPayloadSchema.safeParse({ ...valid, keys }).success).toBe(false)
  })

  it('lists every key class the server accepts', () => {
    expect(KEY_CLASSES).toContain('dpad')
    expect(KEY_CLASSES).toHaveLength(7)
  })
})

describe('pairing', () => {
  const token = 'a'.repeat(32)

  it('accepts a code without ambiguous characters', () => {
    const res = pairStartResponseSchema.safeParse({
      code: 'K7M2QX',
      pollToken: token,
      expiresAt: '2026-11-08T20:25:00Z',
    })
    expect(res.success).toBe(true)
  })

  it('rejects codes with I, O, 0 or 1', () => {
    for (const code of ['K7M2Q0', 'K7M2QO', 'K7M2QI', 'K7M2Q1']) {
      const res = pairStartResponseSchema.safeParse({ code, pollToken: token, expiresAt: '2026-11-08T20:25:00Z' })
      expect(res.success).toBe(false)
    }
  })

  it('builds codes from an alphabet without look-alikes', () => {
    expect(PAIR_CODE_ALPHABET).toHaveLength(32)
    expect(PAIR_CODE_ALPHABET).not.toMatch(/[IO01]/)
  })

  it('keeps pairing requests strict and within the limits', () => {
    expect(pairStartRequestSchema.safeParse({ deviceName: 'TV' }).success).toBe(true)
    expect(pairStartRequestSchema.safeParse({ deviceName: 'TV', serial: 'X1' }).success).toBe(false)
    expect(pairStartRequestSchema.safeParse({ deviceName: 'x'.repeat(LIMITS.maxDeviceNameLength + 1) }).success).toBe(
      false,
    )
    expect(
      pairStartRequestSchema.safeParse({ deviceName: 'TV', model: 'x'.repeat(LIMITS.maxModelLength + 1) }).success,
    ).toBe(false)
    expect(pairPollRequestSchema.safeParse({ pollToken: token }).success).toBe(true)
    expect(pairPollRequestSchema.safeParse({ pollToken: token.slice(1) }).success).toBe(false)
    expect(pairPollRequestSchema.safeParse({ pollToken: token, deviceId: 'x' }).success).toBe(false)
  })

  it('returns a device token only when claimed', () => {
    expect(pairPollResponseSchema.safeParse({ status: 'pending' }).success).toBe(true)
    expect(pairPollResponseSchema.safeParse({ status: 'claimed' }).success).toBe(false)
    expect(pairPollResponseSchema.safeParse({ status: 'claimed', deviceToken: token }).success).toBe(true)
  })
})

describe('apiErrorSchema', () => {
  it('only allows known error codes', () => {
    expect(apiErrorSchema.safeParse({ error: 'rate_limited', message: 'Slow down' }).success).toBe(true)
    expect(apiErrorSchema.safeParse({ error: 'teapot', message: 'no' }).success).toBe(false)
  })
})

describe('bucketKey', () => {
  it('builds distinct keys per bucket kind', () => {
    expect(bucketKey({ day: '2026-11-08', hour: 7, packageName: 'com.a.b' })).toBe('2026-11-08T07|app|com.a.b')
    expect(bucketKey({ day: '2026-11-08', hour: 7, keyClass: 'back' })).toBe('2026-11-08T07|key|back')
    expect(bucketKey({ day: '2026-11-08', hour: 7 })).toBe('2026-11-08T07|screen')
  })
})
