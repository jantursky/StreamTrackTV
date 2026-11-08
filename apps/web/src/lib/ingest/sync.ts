import { bucketKey, syncPayloadSchema, type SyncResponse } from 'streamtrack-tv'
import { apiError, jsonResponse, parseBody, rateLimited } from '../http/api'
import { bearerToken, digestsEqual, sha256 } from '../security/tokens'
import type { IngestStore, RateLimiter } from './ports'

/** Three kinds of 2000 rows stay well below this; anything larger is not a TV talking to us. */
export const SYNC_MAX_BYTES = 1_048_576
/** A TV syncs every 15 minutes at most; this leaves room for retries and backlogs. */
export const SYNC_RATE_LIMIT = { limit: 60, windowSeconds: 3600 } as const

export interface SyncDeps {
  store: IngestStore
  limiter: RateLimiter
  now: () => Date
}

/** Keeps the last row for each bucket key, so one payload never upserts the same row twice. */
export function dedupeBuckets<T extends { day: string; hour: number }>(rows: readonly T[]): T[] {
  return [...new Map(rows.map((row) => [bucketKey(row), row])).values()]
}

/** `POST /api/v1/sync`: authenticates the device token, validates the payload and upserts its buckets. */
export async function handleSync(request: Request, deps: SyncDeps): Promise<Response> {
  const token = bearerToken(request.headers.get('authorization'))
  if (token === null) return apiError('unauthorized', 'Missing or malformed device token')

  const tokenHash = sha256(token)
  const device = await deps.store.deviceByTokenHash(tokenHash)
  if (device === null || !digestsEqual(device.tokenHash, tokenHash)) {
    return apiError('unauthorized', 'Unknown or revoked device token')
  }

  const { limit, windowSeconds } = SYNC_RATE_LIMIT
  if (!(await deps.limiter.hit(`sync:${device.id}`, limit, windowSeconds))) {
    return rateLimited('Too many syncs, try again later', windowSeconds, deps.now())
  }

  const body = await parseBody(request, syncPayloadSchema, SYNC_MAX_BYTES)
  if (!body.ok) return body.response

  const batch = {
    timezone: body.value.timezone,
    usage: dedupeBuckets(body.value.usage),
    keys: dedupeBuckets(body.value.keys),
    screen: dedupeBuckets(body.value.screen),
  }
  await deps.store.ingest(device.id, batch)

  const response: SyncResponse = {
    accepted: { usage: batch.usage.length, keys: batch.keys.length, screen: batch.screen.length },
    serverTime: deps.now().toISOString(),
  }
  return jsonResponse(response)
}
