import type { SupabaseClient } from '@supabase/supabase-js'
import { z } from 'zod'
import type { EventsStore } from '../events/events'
import type { IngestStore, RateLimiter } from '../ingest/ports'
import type { PairingStore } from '../pairing/pairing'
import type { PinResetStore } from '../pin-reset/pin-reset'
import { parseRow, unwrap } from './query'

/** Postgres unique violation. */
const UNIQUE_VIOLATION = '23505'

export function supabaseRateLimiter(admin: SupabaseClient): RateLimiter {
  return {
    async hit(key, limit, windowSeconds) {
      const result = await admin.rpc('hit_rate_limit', { p_key: key, p_limit: limit, p_window_seconds: windowSeconds })
      return z.boolean().parse(unwrap(result))
    },
  }
}

export function supabaseIngestStore(admin: SupabaseClient): IngestStore {
  return {
    async deviceByTokenHash(tokenHash) {
      const row = parseRow(
        z.object({ id: z.string(), user_id: z.string(), token_hash: z.string() }),
        await admin
          .from('devices')
          .select('id, user_id, token_hash')
          .eq('token_hash', tokenHash)
          .is('revoked_at', null)
          .maybeSingle(),
      )
      return row && { id: row.id, userId: row.user_id, tokenHash: row.token_hash }
    },
    async ingest(deviceId, batch) {
      unwrap(
        await admin.rpc('ingest_sync', {
          p_device_id: deviceId,
          p_timezone: batch.timezone,
          p_usage: batch.usage,
          p_keys: batch.keys,
          p_screen: batch.screen,
        }),
      )
    },
  }
}

export function supabaseEventsStore(admin: SupabaseClient): EventsStore {
  return {
    async ingest(deviceId, events) {
      const result = await admin.rpc('ingest_events', { p_device_id: deviceId, p_events: events })
      return z.number().int().parse(unwrap(result))
    },
  }
}

const pairingRow = z.object({
  id: z.string(),
  device_name: z.string(),
  expires_at: z.string(),
  device_id: z.string().nullable(),
  delivered_at: z.string().nullable(),
})

const toPairing = (row: z.infer<typeof pairingRow> | null) =>
  row && {
    id: row.id,
    deviceName: row.device_name,
    expiresAt: row.expires_at,
    deviceId: row.device_id,
    deliveredAt: row.delivered_at,
  }

const PAIRING_COLUMNS = 'id, device_name, expires_at, device_id, delivered_at'

export function supabasePairingStore(admin: SupabaseClient): PairingStore {
  return {
    async create(pairing) {
      // An old, unused row may still hold the same code hash; it no longer matters once expired.
      unwrap(
        await admin
          .from('pairing_codes')
          .delete()
          .eq('code_hash', pairing.codeHash)
          .lt('expires_at', new Date().toISOString())
          .is('device_id', null),
      )
      const { error } = await admin.from('pairing_codes').insert({
        code_hash: pairing.codeHash,
        poll_token_hash: pairing.pollTokenHash,
        device_name: pairing.deviceName,
        model: pairing.model,
        expires_at: pairing.expiresAt,
      })
      if (error?.code === UNIQUE_VIOLATION) return false
      if (error) throw new Error(error.message)
      return true
    },
    async byPollTokenHash(pollTokenHash) {
      return toPairing(
        parseRow(
          pairingRow,
          await admin.from('pairing_codes').select(PAIRING_COLUMNS).eq('poll_token_hash', pollTokenHash).maybeSingle(),
        ),
      )
    },
    async byCodeHash(codeHash) {
      return toPairing(
        parseRow(
          pairingRow,
          await admin.from('pairing_codes').select(PAIRING_COLUMNS).eq('code_hash', codeHash).maybeSingle(),
        ),
      )
    },
    async claim(pairingId, userId) {
      const result = await admin.rpc('claim_pairing', { p_pairing_id: pairingId, p_user_id: userId })
      return z.string().nullable().parse(unwrap(result))
    },
    async deliver(pairingId, tokenHash) {
      const result = await admin.rpc('deliver_pairing', { p_pairing_id: pairingId, p_token_hash: tokenHash })
      return z.boolean().parse(unwrap(result))
    },
  }
}

const pinResetRow = z.object({
  id: z.string(),
  device_id: z.string(),
  expires_at: z.string(),
  confirmed_by: z.string().nullable(),
  consumed_at: z.string().nullable(),
})

const toPinReset = (row: z.infer<typeof pinResetRow> | null) =>
  row && {
    id: row.id,
    deviceId: row.device_id,
    expiresAt: row.expires_at,
    confirmedBy: row.confirmed_by,
    consumedAt: row.consumed_at,
  }

const PIN_RESET_COLUMNS = 'id, device_id, expires_at, confirmed_by, consumed_at'

export function supabasePinResetStore(admin: SupabaseClient): PinResetStore {
  return {
    async create(request) {
      const result = await admin.rpc('start_pin_reset', {
        p_device_id: request.deviceId,
        p_code_hash: request.codeHash,
        p_poll_token_hash: request.pollTokenHash,
        p_expires_at: request.expiresAt,
      })
      return z.boolean().parse(unwrap(result))
    },
    async byPollTokenHash(pollTokenHash) {
      return toPinReset(
        parseRow(
          pinResetRow,
          await admin
            .from('pin_reset_requests')
            .select(PIN_RESET_COLUMNS)
            .eq('poll_token_hash', pollTokenHash)
            .maybeSingle(),
        ),
      )
    },
    async byCodeHash(codeHash) {
      return toPinReset(
        parseRow(
          pinResetRow,
          await admin.from('pin_reset_requests').select(PIN_RESET_COLUMNS).eq('code_hash', codeHash).maybeSingle(),
        ),
      )
    },
    async confirm(requestId, userId) {
      const result = await admin.rpc('confirm_pin_reset', { p_request_id: requestId, p_user_id: userId })
      return z.boolean().parse(unwrap(result))
    },
    async consume(requestId) {
      const result = await admin.rpc('consume_pin_reset', { p_request_id: requestId })
      return z.boolean().parse(unwrap(result))
    },
  }
}
