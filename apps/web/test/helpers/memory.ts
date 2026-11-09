import type { SecurityEvent } from 'streamtrack-tv'
import type { EventsStore } from '../../src/lib/events/events'
import type { DeviceCredential, IngestBatch, IngestStore, RateLimiter } from '../../src/lib/ingest/ports'
import type { NewPairing, PairingRecord, PairingStore } from '../../src/lib/pairing/pairing'
import type { NewPinReset, PinResetRecord, PinResetStore } from '../../src/lib/pin-reset/pin-reset'

/** Counts hits per key; `limitOverride` lets a test force the limiter closed. */
export class MemoryLimiter implements RateLimiter {
  readonly hits = new Map<string, number>()
  hit(key: string, limit: number): Promise<boolean> {
    const count = (this.hits.get(key) ?? 0) + 1
    this.hits.set(key, count)
    return Promise.resolve(count <= limit)
  }
}

/** Upserts by bucket identity like the SQL function, so idempotency can be checked. */
export class MemoryIngestStore implements IngestStore {
  readonly devices: DeviceCredential[] = []
  readonly rows = new Map<string, number>()
  readonly seen = new Map<string, string>()

  deviceByTokenHash(tokenHash: string): Promise<DeviceCredential | null> {
    return Promise.resolve(this.devices.find((d) => d.tokenHash === tokenHash) ?? null)
  }

  ingest(deviceId: string, batch: IngestBatch): Promise<void> {
    for (const r of batch.usage) this.rows.set(`${deviceId}|${r.day}|${r.hour}|app|${r.packageName}`, r.foregroundMs)
    for (const r of batch.keys) this.rows.set(`${deviceId}|${r.day}|${r.hour}|key|${r.keyClass}`, r.count)
    for (const r of batch.screen) this.rows.set(`${deviceId}|${r.day}|${r.hour}|screen`, r.screenOnMs)
    this.seen.set(deviceId, batch.timezone)
    return Promise.resolve()
  }
}

/** Skips events it already holds, like the unique key in SQL. */
export class MemoryEventsStore implements EventsStore {
  readonly events = new Map<string, SecurityEvent>()

  ingest(deviceId: string, events: readonly SecurityEvent[]): Promise<number> {
    let added = 0
    for (const event of events) {
      const key = `${deviceId}|${event.eventId}`
      if (this.events.has(key)) continue
      this.events.set(key, event)
      added++
    }
    return Promise.resolve(added)
  }
}

interface StoredPairing extends PairingRecord, NewPairing {
  userId: string | null
  tokenHash: string | null
}

export class MemoryPairingStore implements PairingStore {
  readonly pairings: StoredPairing[] = []
  /** Codes `create` should report as taken, to exercise the retry. */
  collisions = 0
  private nextId = 1

  create(pairing: NewPairing): Promise<boolean> {
    if (this.collisions > 0) {
      this.collisions--
      return Promise.resolve(false)
    }
    this.pairings.push({
      ...pairing,
      id: `pairing-${this.nextId++}`,
      deviceId: null,
      deliveredAt: null,
      userId: null,
      tokenHash: null,
    })
    return Promise.resolve(true)
  }

  byPollTokenHash(hash: string): Promise<PairingRecord | null> {
    return Promise.resolve(this.pairings.find((p) => p.pollTokenHash === hash) ?? null)
  }

  byCodeHash(hash: string): Promise<PairingRecord | null> {
    return Promise.resolve(this.pairings.find((p) => p.codeHash === hash) ?? null)
  }

  claim(pairingId: string, userId: string): Promise<string | null> {
    const pairing = this.pairings.find((p) => p.id === pairingId)
    if (pairing?.deviceId !== null) return Promise.resolve(null)
    pairing.deviceId = `device-for-${userId}`
    pairing.userId = userId
    return Promise.resolve(pairing.deviceId)
  }

  deliver(pairingId: string, tokenHash: string): Promise<boolean> {
    const pairing = this.pairings.find((p) => p.id === pairingId)
    if (pairing?.deliveredAt !== null) return Promise.resolve(false)
    pairing.deliveredAt = new Date().toISOString()
    pairing.tokenHash = tokenHash
    return Promise.resolve(true)
  }
}

interface StoredPinReset extends PinResetRecord, NewPinReset {}

/** Owners by device id, so `confirm` can check ownership like the SQL function. */
export class MemoryPinResetStore implements PinResetStore {
  readonly requests: StoredPinReset[] = []
  readonly owners = new Map<string, string>()
  private nextId = 1

  create(request: NewPinReset): Promise<boolean> {
    if (this.requests.some((r) => r.codeHash === request.codeHash && r.deviceId !== request.deviceId)) {
      return Promise.resolve(false)
    }
    const kept = this.requests.filter((r) => r.deviceId !== request.deviceId || r.consumedAt !== null)
    this.requests.splice(0, this.requests.length, ...kept)
    this.requests.push({ ...request, id: `reset-${this.nextId++}`, confirmedBy: null, consumedAt: null })
    return Promise.resolve(true)
  }

  byPollTokenHash(hash: string): Promise<PinResetRecord | null> {
    return Promise.resolve(this.requests.find((r) => r.pollTokenHash === hash) ?? null)
  }

  byCodeHash(hash: string): Promise<PinResetRecord | null> {
    return Promise.resolve(this.requests.find((r) => r.codeHash === hash) ?? null)
  }

  confirm(requestId: string, userId: string): Promise<boolean> {
    const request = this.requests.find((r) => r.id === requestId)
    if (request?.confirmedBy !== null || this.owners.get(request.deviceId) !== userId) return Promise.resolve(false)
    request.confirmedBy = userId
    return Promise.resolve(true)
  }

  consume(requestId: string): Promise<boolean> {
    const request = this.requests.find((r) => r.id === requestId)
    if (request?.confirmedBy === null || request?.consumedAt !== null) return Promise.resolve(false)
    request.consumedAt = new Date().toISOString()
    return Promise.resolve(true)
  }
}

export function jsonRequest(url: string, body: unknown, headers: Record<string, string> = {}): Request {
  return new Request(url, {
    method: 'POST',
    headers: { 'content-type': 'application/json', ...headers },
    body: typeof body === 'string' ? body : JSON.stringify(body),
  })
}
