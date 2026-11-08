import type { KeyBucket, ScreenBucket, UsageBucket } from 'streamtrack-tv'

export interface DeviceCredential {
  id: string
  userId: string
  tokenHash: string
}

export interface IngestBatch {
  timezone: string
  usage: UsageBucket[]
  keys: KeyBucket[]
  screen: ScreenBucket[]
}

/** Write side of device sync. Implemented over Supabase with the service role, and in memory for tests. */
export interface IngestStore {
  /** Active (not revoked) device whose token hashes to `tokenHash`. */
  deviceByTokenHash(tokenHash: string): Promise<DeviceCredential | null>
  /** Upserts all rows by bucket key and marks the device as seen, atomically. */
  ingest(deviceId: string, batch: IngestBatch): Promise<void>
}

/** Fixed-window counter shared by all server instances. */
export interface RateLimiter {
  /** Counts one hit for `key`; `false` once more than `limit` hits fall into the current window. */
  hit(key: string, limit: number, windowSeconds: number): Promise<boolean>
}
