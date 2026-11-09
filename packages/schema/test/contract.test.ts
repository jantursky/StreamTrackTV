import { existsSync, readdirSync, readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'
import type { z } from 'zod'
import {
  apiErrorSchema,
  eventsRequestSchema,
  eventsResponseSchema,
  pairPollRequestSchema,
  pairPollResponseSchema,
  pairStartRequestSchema,
  pairStartResponseSchema,
  pinResetPollRequestSchema,
  pinResetPollResponseSchema,
  pinResetStartRequestSchema,
  pinResetStartResponseSchema,
  syncPayloadSchema,
  syncResponseSchema,
} from '../src/index.js'

/** JSON fixtures the Android client tests its serialization against; both sides must agree on them. */
const FIXTURES = fileURLToPath(new URL('../../../android/core/src/test/resources/contract/', import.meta.url))

const SCHEMAS: Record<string, z.ZodType> = {
  'api-error': apiErrorSchema,
  'events-request': eventsRequestSchema,
  'events-response': eventsResponseSchema,
  'pair-poll-claimed': pairPollResponseSchema,
  'pair-poll-expired': pairPollResponseSchema,
  'pair-poll-pending': pairPollResponseSchema,
  'pair-poll-request': pairPollRequestSchema,
  'pair-start-request': pairStartRequestSchema,
  'pair-start-response': pairStartResponseSchema,
  'pin-reset-poll-confirmed': pinResetPollResponseSchema,
  'pin-reset-poll-expired': pinResetPollResponseSchema,
  'pin-reset-poll-pending': pinResetPollResponseSchema,
  'pin-reset-poll-request': pinResetPollRequestSchema,
  'pin-reset-start-request': pinResetStartRequestSchema,
  'pin-reset-start-response': pinResetStartResponseSchema,
  'sync-payload': syncPayloadSchema,
  'sync-response': syncResponseSchema,
}

const present = existsSync(FIXTURES)
const files = present ? readdirSync(FIXTURES).filter((f) => f.endsWith('.json')) : []

describe.skipIf(!present)('Android contract fixtures', () => {
  it('has a schema for every fixture', () => {
    expect(files.length).toBeGreaterThan(0)
    expect(files.map((f) => f.replace(/\.json$/, '')).filter((name) => !(name in SCHEMAS))).toEqual([])
  })

  it.each(files)('%s matches its schema', (file) => {
    const schema = SCHEMAS[file.replace(/\.json$/, '')]
    const result = schema?.safeParse(JSON.parse(readFileSync(FIXTURES + file, 'utf8')))
    expect(result?.error?.issues ?? []).toEqual([])
    expect(result?.success).toBe(true)
  })
})

if (!present) console.warn(`Android contract fixtures not found at ${FIXTURES}; skipping the contract test.`)
