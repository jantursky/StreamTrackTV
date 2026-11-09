<p align="center">
  <img src="https://raw.githubusercontent.com/jantursky/StreamTrackTV/main/.github/assets/banner.svg" alt="StreamTrack TV: private usage analytics for Android TV" width="420">
</p>

# streamtrack-tv

Typed sync payload and pairing contract between the StreamTrack TV Android app and its web dashboard. Zod schemas and TypeScript types, nothing else.

```sh
npm install streamtrack-tv
```

```ts
import { syncPayloadSchema, type SyncPayload } from 'streamtrack-tv'

export function parseSync(body: unknown): SyncPayload {
  return syncPayloadSchema.parse(body)
}
```

What it exports:

**Sync**

- `syncPayloadSchema`, `syncResponseSchema` for `POST /api/v1/sync`. Rows are hourly buckets with absolute values, so sending the same hour again overwrites it.
- `usageBucketSchema`, `keyBucketSchema`, `screenBucketSchema` for the rows inside a payload, and `bucketKey()` to build the key that identifies one bucket.

**Pairing**

- `pairStartRequestSchema`, `pairStartResponseSchema`, `pairPollRequestSchema`, `pairPollResponseSchema` for pairing a TV with a dashboard account.

**PIN reset**

- `pinResetStartRequestSchema`, `pinResetStartResponseSchema` for `POST /api/v1/pin-reset/start`, sent by a paired TV that shows Forgot PIN.
- `pinResetPollRequestSchema`, `pinResetPollResponseSchema` for `POST /api/v1/pin-reset/poll`, answered with `pending`, `expired` or `confirmed`.

**Security events**

- `eventsRequestSchema`, `eventsResponseSchema` for `POST /api/v1/events`, and `securityEventSchema` for one event.
- `SECURITY_EVENT_TYPES`, the list of event types the TV can record at its lock screen. An event never carries anything typed.

**Errors and constants**

- `apiErrorSchema` for every non-2xx response.
- `KEY_CLASSES`, `SCHEMA_VERSION` and `PAIR_CODE_ALPHABET`, the characters of a pairing code.
- `LIMITS`, the numeric limits the API enforces and the app respects: rows per payload, string lengths, the pairing code length and lifetime, the PIN length (`pinMinLength`, `pinMaxLength`), the PIN reset lifetime (`pinResetTtlSeconds`) and the event limits (`maxEventsPerBatch`, `maxEventValue`, `maxStoredEvents`, `maxEventFutureMs`).

Every schema has an inferred TypeScript type with the same name without `Schema`, for example `SyncPayload` or `PinResetStartResponse`.

Request schemas are strict: a field they don't list, such as a raw key code, fails validation. Response schemas ignore unknown fields, so a client can read answers from a newer server.

Remote keys are reported by class only (`dpad`, `select`, `back`, `home`, `media`, `volume`, `other`). A raw key code fails validation.

Part of [StreamTrack TV](https://github.com/jantursky/StreamTrackTV). ESM only, Node 20 or newer. [MIT](LICENSE)
