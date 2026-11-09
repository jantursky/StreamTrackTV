import { LIMITS, eventsRequestSchema, type EventsResponse, type SecurityEvent } from 'streamtrack-tv'
import { apiError, jsonResponse, parseBody, rateLimited } from '../http/api'
import type { IngestStore, RateLimiter } from '../ingest/ports'
import { bearerToken, digestsEqual, sha256 } from '../security/tokens'

/** Write side of the security log. Implemented over Supabase with the service role, and in memory for tests. */
export interface EventsStore {
  /** Stores the events for the device; events it already holds (same `eventId`) are skipped. Returns the new ones. */
  ingest(deviceId: string, events: readonly SecurityEvent[]): Promise<number>
}

export interface EventsDeps {
  store: EventsStore
  devices: Pick<IngestStore, 'deviceByTokenHash'>
  limiter: RateLimiter
  now: () => Date
}

/** A batch holds 100 small events; a few kilobytes more than that is not a TV talking to us. */
export const EVENTS_MAX_BYTES = 32_768
/** A TV sends events once per sync run, and up to five batches to catch up after a long time offline. */
export const EVENTS_RATE_LIMIT = { limit: 60, windowSeconds: 3600 } as const

/** `POST /api/v1/events`: authenticates the device token, validates the batch and stores its events. */
export async function handleEvents(request: Request, deps: EventsDeps): Promise<Response> {
  const token = bearerToken(request.headers.get('authorization'))
  if (token === null) return apiError('unauthorized', 'Missing or malformed device token')

  const tokenHash = sha256(token)
  const device = await deps.devices.deviceByTokenHash(tokenHash)
  if (device === null || !digestsEqual(device.tokenHash, tokenHash)) {
    return apiError('unauthorized', 'Unknown or revoked device token')
  }

  const { limit, windowSeconds } = EVENTS_RATE_LIMIT
  if (!(await deps.limiter.hit(`events:${device.id}`, limit, windowSeconds))) {
    return rateLimited('Too many event uploads, try again later', windowSeconds, deps.now())
  }

  const body = await parseBody(request, eventsRequestSchema, EVENTS_MAX_BYTES)
  if (!body.ok) return body.response

  const latest = deps.now().getTime() + LIMITS.maxEventFutureMs
  const future = body.value.events.findIndex((event) => Date.parse(event.at) > latest)
  if (future !== -1) return apiError('invalid_payload', `events.${future}.at is too far in the future`)

  const accepted = await deps.store.ingest(device.id, body.value.events)
  const response: EventsResponse = { accepted }
  return jsonResponse(response)
}
