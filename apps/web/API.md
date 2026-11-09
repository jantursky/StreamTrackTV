# StreamTrack TV API

All endpoints live under `/api/v1` and speak JSON. The request and response shapes are exported by the
[`streamtrack-tv`](../../packages/schema) package, which both the Android app and this server use to
validate them.

## Conventions

- Every response carries `Cache-Control: no-store` and a `default-src 'none'` Content Security Policy.
- Every non-2xx response has the `ApiError` shape:

  ```json
  { "error": "invalid_payload", "message": "keys.0.keyClass: Invalid option" }
  ```

  | `error`           | Status     | Meaning                                                       |
  | ----------------- | ---------- | ------------------------------------------------------------- |
  | `invalid_payload` | 400 or 413 | The body is missing, not JSON, too large or fails the schema  |
  | `unauthorized`    | 401        | Missing, unknown or revoked credentials                       |
  | `not_found`       | 404        | Unknown resource, or the device API is off on this deployment |
  | `rate_limited`    | 429        | Too many requests in the window; see `Retry-After`            |
  | `server_error`    | 500        | Unexpected failure; the details stay in the server log        |

- Request bodies are strict: a field the schema does not name fails with `400 invalid_payload`. Response
  bodies may gain fields in later versions, so clients should ignore fields they do not know.
- Limits count hits in fixed windows. Every `429` carries `Retry-After` with the seconds until the
  current window ends.
- A demo-only deployment (no Supabase configured, or `DEMO_ONLY=1`) answers the device endpoints with
  `404 not_found`. `GET /health` still works and reports `"mode": "demo"`.

## Where the secret key is used

The Supabase secret key bypasses row level security, so only these server paths use it:

- the device API: `sync` (looks up the token hash and calls `ingest_sync`), `pair/start` and `pair/poll`
- confirming a pairing code (`pairDevice`), since pairing codes are not readable by users
- the read-only share page `/s/<token>`, which has no session and reads one user's data by the link
- the cron jobs (`apply_retention` and the digest, which also reads user emails)
- account deletion (`auth.admin.deleteUser`)

Everything else in the dashboard, including export, uses the signed-in user's session.

## Device endpoints

### `GET /api/v1/health`

No authentication.

```json
{ "status": "ok", "schemaVersion": 1, "mode": "full", "serverTime": "2026-11-08T20:00:00.000Z" }
```

### `POST /api/v1/pair/start`

The TV asks for a code to show on screen. No authentication.

Request (`PairStartRequest`), at most 4 KiB:

```json
{ "deviceName": "Living room TV", "model": "Chromecast with Google TV" }
```

`201 Created` (`PairStartResponse`):

```json
{ "code": "K7Q2MX", "pollToken": "<43 base64url characters>", "expiresAt": "2026-11-08T20:10:00.000Z" }
```

- The code has 6 characters from `ABCDEFGHJKLMNPQRSTUVWXYZ23456789` and is valid for 10 minutes.
- Limit: 10 requests per client address per hour.

### `POST /api/v1/pair/poll`

The TV polls with the token from `pair/start` until the user enters the code on `/pair`.

Request (`PairPollRequest`): `{ "pollToken": "..." }`.

`200 OK` (`PairPollResponse`), one of:

- `{ "status": "pending" }`: the code has not been confirmed yet.
- `{ "status": "claimed", "deviceToken": "<43 base64url characters>" }`: returned exactly once. The TV
  stores the token and sends it with every sync. It is valid until the device is revoked.
- `{ "status": "expired" }`: the code ran out, or the token was already collected. Start over.

An unknown poll token answers `404 not_found`. The TV polls every 5 seconds and waits for `Retry-After`
on `429`. Limits per 10-minute window: 240 polls per poll token (twice the 5-second rate, so retries fit)
and 960 per client address, which also caps guessing poll tokens. After confirmation the TV has another
10 minutes to collect the token.

If the response with the device token is lost, the next poll answers `expired` and the TV pairs again
with a new code. The device created for the lost token never syncs, and the retention job deletes it a day
later.

### `POST /api/v1/sync`

Uploads hourly buckets. Requires `Authorization: Bearer <deviceToken>`.

Request (`SyncPayload`), at most 1 MiB:

```json
{
  "schemaVersion": 1,
  "sentAt": "2026-11-08T20:00:00+01:00",
  "timezone": "Europe/Bratislava",
  "usage": [
    { "day": "2026-11-08", "hour": 19, "packageName": "com.netflix.ninja", "label": "Netflix", "foregroundMs": 1800000 }
  ],
  "keys": [{ "day": "2026-11-08", "hour": 19, "keyClass": "dpad", "count": 42 }],
  "screen": [{ "day": "2026-11-08", "hour": 19, "screenOnMs": 2400000 }]
}
```

- At most 2000 rows per kind. Durations are 0 to 3,600,000 ms per hour.
- Keys are reported by class only (`dpad`, `select`, `back`, `home`, `media`, `volume`, `other`). Any
  other field, such as a raw key code, typed text or a window title, fails validation.
- `timezone` is stored on the device (`devices.timezone`). Until the user picks a zone, the dashboard
  uses the zone of the device that synced last to decide which day is "today".
- Rows are absolute values per bucket (`bucketKey`), so sending the same hour again overwrites it.
  Duplicates within one payload keep the last row.
- `label` sets the display name of an app. Rows without a label keep the name the server already has.

`200 OK` (`SyncResponse`):

```json
{ "accepted": { "usage": 1, "keys": 1, "screen": 1 }, "serverTime": "2026-11-08T19:00:01.000Z" }
```

`401` means the token is unknown or the device was revoked; the TV should stop syncing and offer to pair
again. Limit: 60 syncs per device per hour.

### `POST /api/v1/events`

The TV sends new security events after each sync. Requires `Authorization: Bearer <deviceToken>`.

Request (`EventsRequest`), at most 32 KiB:

```json
{
  "schemaVersion": 1,
  "events": [
    {
      "eventId": "0b9e5d8e-6f1a-4c52-9d0e-3a7c1b2d4e5f",
      "type": "pin_wrong",
      "at": "2026-11-08T21:15:00+01:00",
      "value": 2
    }
  ]
}
```

- 1 to 100 events. `type` is one of `SECURITY_EVENT_TYPES`. `value` is optional, 0 to 100000.
- The server stores each `eventId` once per device, so a resent batch is harmless. An `at` more than one day ahead of the server clock is refused, and one invalid event refuses the whole batch with `400`.
- `200 OK` answers `{ "accepted": n }` with the number of events that were new. Limit: 60 requests per device per hour.

### `POST /api/v1/pin-reset/start`

A paired TV asks for a code to show when the user chooses Forgot PIN. Requires
`Authorization: Bearer <deviceToken>`. The request is an empty object (`PinResetStartRequest`).

`201 Created` (`PinResetStartResponse`):

```json
{ "code": "H4TW9N", "pollToken": "<43 base64url characters>", "expiresAt": "2026-11-08T20:10:00.000Z" }
```

- The code has the pairing code alphabet and is valid for 10 minutes. The owner confirms it at `/pin-reset`.
- A new start replaces an open one. Limit: 5 requests per device per hour.

### `POST /api/v1/pin-reset/poll`

The TV polls every 5 seconds with the token from `pin-reset/start`. Requires
`Authorization: Bearer <deviceToken>`.

Request (`PinResetPollRequest`): `{ "pollToken": "..." }`.

`200 OK` (`PinResetPollResponse`), one of `{ "status": "pending" }`, `{ "status": "confirmed" }` (returned
once, the TV clears its PIN) or `{ "status": "expired" }`.

A poll token that belongs to another device or does not exist answers `404 not_found`. Limit: 240 polls
per poll token per 10 minutes. Confirmation on the web is limited to 10 attempts per user per 10 minutes.

## Dashboard endpoints

### `GET /api/v1/export`

Downloads the signed-in user's buckets. Requires the session cookie; reads go through row level security.

| Parameter | Required | Value                                       |
| --------- | -------- | ------------------------------------------- |
| `from`    | yes      | `YYYY-MM-DD`                                |
| `to`      | yes      | `YYYY-MM-DD`, at most 366 days after `from` |
| `format`  | no       | `csv` (default) or `json`                   |
| `device`  | no       | Device id; all devices when left out        |

The response is an attachment named `streamtrack-<from>_<to>.<format>`.

**CSV** has the header `device_id,device,day,hour,kind,key,value`, one row per bucket, sorted by day, hour,
device name, kind and key:

- `kind` is `app`, `key` or `screen`.
- `key` is the package name for `app`, the key class for `key` and empty for `screen`.
- `value` is milliseconds for `app` and `screen` and a count for `key`.

Lines end with CRLF. Cells starting with `=`, `+`, `-`, `@`, a tab or a carriage return get a leading
apostrophe so spreadsheets do not run them as formulas.

**JSON** keeps the bucket kinds apart:

```json
{
  "exportedAt": "2026-11-08T20:00:00.000Z",
  "from": "2026-11-01",
  "to": "2026-11-07",
  "devices": [{ "id": "…", "name": "Living room TV", "model": "Chromecast HD" }],
  "usage": [
    { "deviceId": "…", "day": "2026-11-01", "hour": 20, "packageName": "com.netflix.ninja", "foregroundMs": 1800000 }
  ],
  "keys": [{ "deviceId": "…", "day": "2026-11-01", "hour": 20, "keyClass": "dpad", "count": 42 }],
  "screen": [{ "deviceId": "…", "day": "2026-11-01", "hour": 20, "screenOnMs": 2400000 }]
}
```

`devices` lists every device of the account, also when `device` narrows the buckets.

### Server actions

The dashboard changes data through Next.js server actions, not public endpoints. Each one checks the
session and validates its input with zod. Writes go through the session client, so row level security
applies, except where the table says the secret key is used.

| Action                               | Effect                                                                                                                                   |
| ------------------------------------ | ---------------------------------------------------------------------------------------------------------------------------------------- |
| `pairDevice`                         | Confirms a pairing code shown on the TV (10 attempts per 10 minutes); uses the secret key, since pairing codes are not readable by users |
| `updateDevice`                       | Renames a device and assigns it to a household member                                                                                    |
| `revokeDevice`                       | Revokes a device for good; its token stops working at once                                                                               |
| `deleteDeviceData`                   | Deletes all buckets of one device; needs the device name typed again                                                                     |
| `setAppCategory`                     | Moves an app to another category                                                                                                         |
| `addMember`, `deleteMember`          | Manages household members                                                                                                                |
| `addGoal`, `deleteGoal`              | Manages daily and weekly limits                                                                                                          |
| `saveSettings`                       | Time zone, language, retention and the weekly digest                                                                                     |
| `adoptTimeZone`                      | Sets the browser's time zone on the first dashboard visit, only while the account has none                                               |
| `createShareLink`, `revokeShareLink` | Read-only links at `/s/<token>` for 1, 7 or 30 days (the database refuses more), optionally for one device                               |
| `deleteAccount`                      | Deletes the user and, through cascades, every row they own; uses the secret key for the Auth admin call                                  |

## Scheduled jobs

Both run on Vercel Cron (see `vercel.json`) and require `Authorization: Bearer <CRON_SECRET>`.

| Endpoint                  | Schedule (UTC) | Effect                                                                                                                           |
| ------------------------- | -------------- | -------------------------------------------------------------------------------------------------------------------------------- |
| `GET /api/cron/retention` | Daily, 03:17   | Deletes buckets past each user's retention, devices that never synced a day after pairing, and expired codes, links and counters |
| `GET /api/cron/digest`    | Mondays, 07:00 | Emails last week's summary to users who opted in, through Resend                                                                 |

Without Supabase or `RESEND_API_KEY` the jobs answer `200` with a `skipped` reason.
