# StreamTrack TV architecture (draft)

Privacy-first usage analytics for Android TV. Self-hosted: one click deploys the web app and database to the user's own Vercel and Supabase accounts.

## Layout

```
android/              Kotlin TV app (Gradle, Compose for TV, Room, WorkManager, Ktor client)
apps/web/             Next.js dashboard + ingest API (Vercel)
apps/docs/            Blume docs site
packages/schema/      Zod schemas + TS types for the sync payload (shared by web and tests; contract doc for the Android DTOs)
supabase/migrations/  SQL schema, RLS policies
scripts/              samples check, screenshots, contract check
.github/              CI, release (signed APK + GitHub release), templates
```

## Android app

- Modules: `core` (models, aggregation, pure Kotlin), `data` (Room, repositories), `collector` (AccessibilityService, UsageStatsManager reader), `sync` (WorkManager worker, Ktor client, pairing), `ui` (Compose for TV).
- minSdk 26, target latest stable. Debug + release (signed via CI secrets) builds. Distribution: APK on GitHub Releases (accessibility apps are poor fits for Play).
- Keypress tracking: AccessibilityService with `canRequestFilterKeyEvents`; records only key class (dpad, select, back, media, other) and timestamp bucket. Never key codes beyond class, never text, never window content.
- App usage: UsageStatsManager events, converted to sessions per package; screen-on time from interactive/non-interactive events.
- Storage: Room, hourly buckets per (day, hour, package) plus key-class counts. Sync marks buckets as sent.
- Sync: periodic WorkManager (15 min to 24 h, configurable), idempotent upsert keyed by (device, day, hour, package), exponential backoff.
- Onboarding: permission checklist (usage access, accessibility, battery exemption) with deep links; pairing screen with code + QR.
- On-TV dashboard: today summary, top apps, keypresses, active windows, sync status, privacy screen showing the exact outgoing payload.
- Tests: JVM unit tests for aggregation and sync mapping, Room instrumented tests, Compose UI tests, contract test against `packages/schema` fixtures.

## Web app (Next.js)

- Auth: Supabase Auth, email + password (with email confirmation), row level security on every table. Server components read through the user's session.
- Views: overview, calendar (heatmap per day, compare two days/ranges), range filters (day, week, custom), per-app breakdown, categories, key-class split, hour-by-weekday heatmap, devices, profiles, goals and limits, settings (export, retention, delete all).
- Ingest API `POST /api/v1/sync`: device token (hashed in DB), zod validation, size limits, rate limit, idempotent upsert.
- Pairing: `POST /api/v1/pair/start` (TV gets code), user confirms in web, TV polls `POST /api/v1/pair/poll` and receives a scoped device token once.
- Other endpoints: export (CSV/JSON), delete device data, health.
- Stack: Next.js App Router, Tailwind v4 with the design tokens, Drizzle or typed SQL on Supabase Postgres, zod.

## Data model (Postgres)

`profiles` (user), `household_members` (optional person profiles), `devices` (name, token_hash, last_seen, member_id), `usage_buckets` (device_id, day, hour, package, foreground_ms), `key_buckets` (device_id, day, hour, key_class, count), `screen_buckets` (device_id, day, hour, screen_on_ms), `apps` (package, label, category override), `goals` (scope, metric, limit, period), `pairing_codes` (code_hash, expires_at, device_name, claimed_by).

## One-click deploy

Vercel Deploy Button with the Supabase integration: provisions the project, sets `NEXT_PUBLIC_SUPABASE_URL`, keys and `SERVICE_ROLE` env vars; migrations in `supabase/migrations` applied at build by a script (`scripts/migrate.mjs`, idempotent). A README section documents the manual path.

## Security and privacy notes (go to SECURITY.md)

- Device tokens random 256-bit, stored hashed, revocable per device.
- No raw key codes or text, no screenshots, no window content, no contact lists. Package names + durations only.
- RLS isolates users; service role only inside ingest route handlers.
- Payload limits, rate limits, retention job.
- Accessibility permission is sensitive: the app documents exactly what it reads and shows it on the TV.

## Ideas beyond the brief

Weekly email digest, per-app time limits with a dashboard alert, "binge" detection (continuous sessions over N hours), idle-screen-on detection (TV left on), app launch counts, night-time usage, shareable read-only link, PWA install, CSV import, i18n (en first), demo mode with seeded data so the public demo needs no TV.
