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
  pinMinLength: 4,
  pinMaxLength: 8,
  pinResetTtlSeconds: 600,
  maxEventsPerBatch: 100,
  maxEventValue: 100_000,
  /** Newest events the TV keeps. */
  maxStoredEvents: 500,
  /** How far ahead of the server clock an event time may be, in milliseconds. */
  maxEventFutureMs: 86_400_000,
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

/**
 * Body of `POST /api/v1/pin-reset/start`, sent by a paired TV (device token) that shows "Forgot PIN".
 * It carries nothing: the device is known from its token.
 */
export const pinResetStartRequestSchema = z.strictObject({})
export type PinResetStartRequest = z.infer<typeof pinResetStartRequestSchema>

/** Same shape as a pairing code: the owner types or scans `code` on the web, the TV polls with `pollToken`. */
export const pinResetStartResponseSchema = z.object({
  code: pairCode,
  pollToken: z.string().min(LIMITS.minTokenLength),
  expiresAt: z.iso.datetime({ offset: true }),
})
export type PinResetStartResponse = z.infer<typeof pinResetStartResponseSchema>

/** Body of `POST /api/v1/pin-reset/poll`, sent by the same TV until the owner confirms on the web. */
export const pinResetPollRequestSchema = z.strictObject({ pollToken: z.string().min(LIMITS.minTokenLength) })
export type PinResetPollRequest = z.infer<typeof pinResetPollRequestSchema>

/** `confirmed` is returned once; the TV then clears its PIN. */
export const pinResetPollResponseSchema = z.discriminatedUnion('status', [
  z.object({ status: z.literal('pending') }),
  z.object({ status: z.literal('expired') }),
  z.object({ status: z.literal('confirmed') }),
])
export type PinResetPollResponse = z.infer<typeof pinResetPollResponseSchema>

/** What the TV records at its lock screen and in its PIN settings. Never anything typed. */
export const SECURITY_EVENT_TYPES = [
  'app_opened',
  'lock_shown',
  'pin_wrong',
  'pin_wait_started',
  'unlocked',
  'forgot_pin_opened',
  'pin_reset_confirmed',
  'pin_enabled',
  'pin_changed',
  'pin_disabled',
] as const
export type SecurityEventType = (typeof SECURITY_EVENT_TYPES)[number]

/**
 * One event. `value` is the wrong tries so far for `pin_wrong` and the wait in seconds for `pin_wait_started`.
 * `eventId` is made on the TV so a resent batch does not store an event twice.
 */
export const securityEventSchema = z.strictObject({
  eventId: z.uuid(),
  type: z.enum(SECURITY_EVENT_TYPES),
  at: z.iso.datetime({ offset: true }),
  value: z.number().int().min(0).max(LIMITS.maxEventValue).optional(),
})
export type SecurityEvent = z.infer<typeof securityEventSchema>

/** Body of `POST /api/v1/events`, sent with the device token after the usage sync. */
export const eventsRequestSchema = z.strictObject({
  schemaVersion: z.literal(SCHEMA_VERSION),
  events: z.array(securityEventSchema).min(1).max(LIMITS.maxEventsPerBatch),
})
export type EventsRequest = z.infer<typeof eventsRequestSchema>

export const eventsResponseSchema = z.object({ accepted: z.number().int().min(0) })
export type EventsResponse = z.infer<typeof eventsResponseSchema>

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
