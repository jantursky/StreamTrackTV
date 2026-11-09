import { deviceApi, disabled, guarded } from '@/lib/http/route'
import { startPinReset } from '@/lib/pin-reset/pin-reset'

export function POST(request: Request) {
  return guarded(async () => {
    const api = deviceApi()
    if (api === null) return disabled()
    return startPinReset(request, { store: api.pinReset, devices: api.ingest, limiter: api.limiter, now: api.now })
  })
}
