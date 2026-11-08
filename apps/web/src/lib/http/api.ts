import type { ApiError } from 'streamtrack-tv'
import type { z } from 'zod'

const NO_STORE = { 'Cache-Control': 'no-store' }

export const STATUS: Record<ApiError['error'], number> = {
  invalid_payload: 400,
  unauthorized: 401,
  not_found: 404,
  rate_limited: 429,
  server_error: 500,
}

export function jsonResponse(
  body: unknown,
  init: { status?: number; headers?: Record<string, string> } = {},
): Response {
  return Response.json(body, { status: init.status, headers: { ...NO_STORE, ...init.headers } })
}

/** Every non-2xx answer of the API has the `ApiError` shape. */
export function apiError(
  error: ApiError['error'],
  message: string,
  init: { status?: number; headers?: Record<string, string> } = {},
): Response {
  const body: ApiError = { error, message }
  return jsonResponse(body, { status: init.status ?? STATUS[error], headers: init.headers })
}

export type BodyResult = { ok: true; value: unknown } | { ok: false; response: Response }

/**
 * Reads a JSON body without ever buffering more than `maxBytes`, so an oversized upload is cut off
 * early instead of being parsed.
 */
export async function readJsonBody(request: Request, maxBytes: number): Promise<BodyResult> {
  const tooLarge = (): BodyResult => ({
    ok: false,
    response: apiError('invalid_payload', `Body exceeds ${maxBytes} bytes`, { status: 413 }),
  })
  const declared = Number(request.headers.get('content-length') ?? '0')
  if (declared > maxBytes) return tooLarge()
  if (request.body === null) return { ok: false, response: apiError('invalid_payload', 'Missing JSON body') }

  const reader = request.body.getReader()
  const chunks: Uint8Array[] = []
  let size = 0
  for (;;) {
    const { done, value } = await reader.read()
    if (done) break
    size += value.byteLength
    if (size > maxBytes) {
      await reader.cancel()
      return tooLarge()
    }
    chunks.push(value)
  }
  try {
    return { ok: true, value: JSON.parse(Buffer.concat(chunks).toString('utf8')) }
  } catch {
    return { ok: false, response: apiError('invalid_payload', 'Body is not valid JSON') }
  }
}

/**
 * `429` for a fixed-window limit. `Retry-After` names the seconds left in the current window, which is when
 * the counter of the `hit_rate_limit` SQL function starts over.
 */
export function rateLimited(message: string, windowSeconds: number, now: Date): Response {
  const elapsed = Math.floor(now.getTime() / 1000) % windowSeconds
  return apiError('rate_limited', message, { headers: { 'Retry-After': String(windowSeconds - elapsed) } })
}

/** Short, human-readable reason of the first validation issue, e.g. `keys.0.keyClass: Invalid option`. */
export function describeIssues(error: z.ZodError): string {
  const issue = error.issues[0]
  if (issue === undefined) return 'Invalid payload'
  const path = issue.path.map(String).join('.')
  return path ? `${path}: ${issue.message}` : issue.message
}

/** Parses a body with a schema; a failure is already the 400 response. */
export async function parseBody<T>(
  request: Request,
  schema: z.ZodType<T>,
  maxBytes: number,
): Promise<{ ok: true; value: T } | { ok: false; response: Response }> {
  const body = await readJsonBody(request, maxBytes)
  if (!body.ok) return body
  const parsed = schema.safeParse(body.value)
  if (!parsed.success) return { ok: false, response: apiError('invalid_payload', describeIssues(parsed.error)) }
  return { ok: true, value: parsed.data }
}
