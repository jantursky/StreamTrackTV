import { cronSecret } from '../env'
import { digestsEqual, sha256 } from '../security/tokens'

/**
 * Vercel Cron sends `Authorization: Bearer <CRON_SECRET>`. Without a configured secret every call is
 * refused, so the jobs cannot be triggered by anyone else.
 */
export function isCronRequest(request: Request, secret: string | null = cronSecret()): boolean {
  const header = request.headers.get('authorization')
  if (secret === null || header === null) return false
  return digestsEqual(sha256(header), sha256(`Bearer ${secret}`))
}
