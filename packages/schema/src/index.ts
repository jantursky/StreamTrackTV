import { z } from 'zod'

/** Wire format version. Bump on any breaking change to the payloads below. */
export const SCHEMA_VERSION = 1

/** Upper bounds that the ingest API enforces and the Android app respects. */
export const LIMITS = {
  maxRowsPerKind: 2000,
  maxPackageLength: 255,
  maxLabelLength: 120,
  msPerHour: 3_600_000,
  maxKeyCount: 1_000_000,
  maxTimezoneLength: 64,
  maxDeviceNameLength: 60,
  maxModelLength: 80,
  minTokenLength: 32,
  pairCodeLength: 6,
  pairCodeTtlSeconds: 600,
} as const

/** Characters of a pairing code: uppercase letters and digits without the look-alikes I, O, 0 and 1. */
export const PAIR_CODE_ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'

/**
 * Remote keys are reported by class only. The app never sends raw key codes, text or window content.
 */
export const KEY_CLASSES = ['dpad', 'select', 'back', 'home', 'media', 'volume', 'other'] as const
export type KeyClass = (typeof KEY_CLASSES)[number]

const day = z.iso.date().describe('Local calendar day on the TV, YYYY-MM-DD')
const hour = z.number().int().min(0).max(23).describe('Local hour of day, 0-23')
const hourMs = z.number().int().min(0).max(LIMITS.msPerHour)
const packageName = z
  .string()
  .min(1)
  .max(LIMITS.maxPackageLength)
  .regex(/^[A-Za-z][\w]*(\.[A-Za-z][\w]*)+$/, 'Expected an Android package name')

/** Foreground time of one app during one local hour. */
export const usageBucketSchema = z.strictObject({
  day,
  hour,
  packageName,
  label: z.string().min(1).max(LIMITS.maxLabelLength).optional(),
  foregroundMs: hourMs,
})
export type UsageBucket = z.infer<typeof usageBucketSchema>

/** Remote key presses of one class during one local hour. */
export const keyBucketSchema = z.strictObject({
  day,
  hour,
  keyClass: z.enum(KEY_CLASSES),
  count: z.number().int().min(0).max(LIMITS.maxKeyCount),
})
export type KeyBucket = z.infer<typeof keyBucketSchema>

/** Screen-on time during one local hour. */
export const screenBucketSchema = z.strictObject({ day, hour, screenOnMs: hourMs })
export type ScreenBucket = z.infer<typeof screenBucketSchema>

/**
 * Body of `POST /api/v1/sync`. Rows are absolute values per bucket, so re-sending is idempotent.
 * Request schemas are strict: a field they do not name, such as a raw key code, fails validation.
 */
export const syncPayloadSchema = z.strictObject({
  schemaVersion: z.literal(SCHEMA_VERSION),
  sentAt: z.iso.datetime({ offset: true }),
  timezone: z.string().min(1).max(LIMITS.maxTimezoneLength).describe('IANA zone of the TV, e.g. Europe/Bratislava'),
  usage: z.array(usageBucketSchema).max(LIMITS.maxRowsPerKind),
  keys: z.array(keyBucketSchema).max(LIMITS.maxRowsPerKind),
  screen: z.array(screenBucketSchema).max(LIMITS.maxRowsPerKind),
})
export type SyncPayload = z.infer<typeof syncPayloadSchema>

export const syncResponseSchema = z.object({
  accepted: z.object({ usage: z.number().int(), keys: z.number().int(), screen: z.number().int() }),
  serverTime: z.iso.datetime({ offset: true }),
})
export type SyncResponse = z.infer<typeof syncResponseSchema>

const pairCode = z
  .string()
  .length(LIMITS.pairCodeLength)
  .regex(new RegExp(`^[${PAIR_CODE_ALPHABET}]+$`), 'Uppercase letters and digits without I, O, 0, 1')

/** Body of `POST /api/v1/pair/start`, sent by the TV. */
export const pairStartRequestSchema = z.strictObject({
  deviceName: z.string().min(1).max(LIMITS.maxDeviceNameLength),
  model: z.string().max(LIMITS.maxModelLength).optional(),
})
export type PairStartRequest = z.infer<typeof pairStartRequestSchema>

export const pairStartResponseSchema = z.object({
  code: pairCode,
  pollToken: z.string().min(LIMITS.minTokenLength),
  expiresAt: z.iso.datetime({ offset: true }),
})
export type PairStartResponse = z.infer<typeof pairStartResponseSchema>

/** Body of `POST /api/v1/pair/poll`, sent by the TV until the user confirms the code on the web. */
export const pairPollRequestSchema = z.strictObject({ pollToken: z.string().min(LIMITS.minTokenLength) })
export type PairPollRequest = z.infer<typeof pairPollRequestSchema>

export const pairPollResponseSchema = z.discriminatedUnion('status', [
  z.object({ status: z.literal('pending') }),
  z.object({ status: z.literal('expired') }),
  z.object({ status: z.literal('claimed'), deviceToken: z.string().min(LIMITS.minTokenLength) }),
])
export type PairPollResponse = z.infer<typeof pairPollResponseSchema>

/** Shape of every non-2xx response of the API. */
export const apiErrorSchema = z.object({
  error: z.enum(['invalid_payload', 'unauthorized', 'rate_limited', 'not_found', 'server_error']),
  message: z.string(),
})
export type ApiError = z.infer<typeof apiErrorSchema>

/** Stable identity of a bucket, used for idempotent upserts on the server and for sync bookkeeping on the TV. */
export function bucketKey(
  row: { day: string; hour: number } & ({ packageName: string } | { keyClass: KeyClass } | object),
): string {
  const base = `${row.day}T${String(row.hour).padStart(2, '0')}`
  if ('packageName' in row) return `${base}|app|${row.packageName}`
  if ('keyClass' in row) return `${base}|key|${row.keyClass}`
  return `${base}|screen`
}
