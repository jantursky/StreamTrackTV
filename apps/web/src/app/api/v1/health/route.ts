import { SCHEMA_VERSION } from 'streamtrack-tv'
import { isDemoOnly } from '@/lib/env'
import { jsonResponse } from '@/lib/http/api'

export function GET() {
  return jsonResponse({
    status: 'ok',
    schemaVersion: SCHEMA_VERSION,
    mode: isDemoOnly() ? 'demo' : 'full',
    serverTime: new Date().toISOString(),
  })
}
