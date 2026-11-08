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

- `syncPayloadSchema`, `syncResponseSchema` for `POST /api/v1/sync`. Rows are hourly buckets with absolute values, so sending the same hour again overwrites it.
- `pairStartRequestSchema`, `pairStartResponseSchema`, `pairPollRequestSchema`, `pairPollResponseSchema` for pairing a TV with a dashboard account.
- `apiErrorSchema` for every non-2xx response.
- `KEY_CLASSES`, `LIMITS`, `SCHEMA_VERSION` and `bucketKey()`.

Request schemas are strict: a field they don't list, such as a raw key code, fails validation. Response schemas ignore unknown fields, so a client can read answers from a newer server.

- `LIMITS` and `PAIR_CODE_ALPHABET` expose the numeric limits and the pairing code alphabet.

Remote keys are reported by class only (`dpad`, `select`, `back`, `home`, `media`, `volume`, `other`). A raw key code fails validation.

Part of [StreamTrack TV](https://github.com/jantursky/StreamTrackTV). ESM only, Node 20 or newer. [MIT](LICENSE)
