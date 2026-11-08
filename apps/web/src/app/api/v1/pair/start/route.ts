import { clientAddress, deviceApi, disabled, guarded } from '@/lib/http/route'
import { startPairing } from '@/lib/pairing/pairing'

export function POST(request: Request) {
  return guarded(async () => {
    const api = deviceApi()
    if (api === null) return disabled()
    return startPairing(request, clientAddress(request), { store: api.pairing, limiter: api.limiter, now: api.now })
  })
}
