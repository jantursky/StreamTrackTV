import { clientAddress, deviceApi, disabled, guarded } from '@/lib/http/route'
import { pollPairing } from '@/lib/pairing/pairing'

export function POST(request: Request) {
  return guarded(async () => {
    const api = deviceApi()
    if (api === null) return disabled()
    return pollPairing(request, clientAddress(request), { store: api.pairing, limiter: api.limiter, now: api.now })
  })
}
