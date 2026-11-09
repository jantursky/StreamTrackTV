import { handleEvents } from '@/lib/events/events'
import { deviceApi, disabled, guarded } from '@/lib/http/route'

export function POST(request: Request) {
  return guarded(async () => {
    const api = deviceApi()
    if (api === null) return disabled()
    return handleEvents(request, { store: api.events, devices: api.ingest, limiter: api.limiter, now: api.now })
  })
}
