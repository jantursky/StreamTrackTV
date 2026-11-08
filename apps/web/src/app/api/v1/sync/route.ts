import { handleSync } from '@/lib/ingest/sync'
import { deviceApi, disabled, guarded } from '@/lib/http/route'

export function POST(request: Request) {
  return guarded(async () => {
    const api = deviceApi()
    if (api === null) return disabled()
    return handleSync(request, { store: api.ingest, limiter: api.limiter, now: api.now })
  })
}
