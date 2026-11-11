# Changelog

All notable changes to this project are documented here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses [Semantic Versioning](https://semver.org/).

## [Unreleased]

## [1.2.0] - 2026-11-11

### Added

- History: quick periods Today, Yesterday, This week, Last week, This month and Last month next to Day, 7 days and 30 days. Previous and Next move by a whole week or month for a quick period.
- History: a comparison with the period before, a Highlights card (daily average, busiest day, peak hour) and a list of days under the chart; selecting a day opens it.
- Nothing is sent to the server for these. The wire format and the API are unchanged.

## [1.1.1] - 2026-11-09

### Changed

- Documentation catches up with 1.1.0: the `streamtrack-tv` npm README and changelog list the PIN reset and security event schemas, the API reference covers `POST /api/v1/pin-reset/start`, `/poll` and `/events`, and the guides describe the History screen, the PIN reset page and PIN troubleshooting.
- The upgrade guide now says that moving from 1.0.0 to 1.1.0 or later installs a new app: remove the old one, then pair and set a PIN again.
- `test:pack` fails when an export of `streamtrack-tv` is not named in its README.
- The APK has no code change. Only its version differs from 1.1.0.

## [1.1.0] - 2026-11-09

### Added

- PIN lock on the TV: a D-pad keypad, 4 to 8 digits, asked on every start and when the app returns from the background, with a growing wait after wrong tries. Turn it on, change it or turn it off in Settings. The app is hidden from the recent apps list.
- Forgot PIN: the TV shows a code and a QR code, the signed-in owner confirms it at `/pin-reset` on the dashboard and the TV clears the PIN. New endpoints `POST /api/v1/pin-reset/start` and `/poll`.
- Security event log: the TV records when the app is opened and what happens at the lock (wrong PIN, wait, Forgot PIN, reset, PIN changes), uploads it to the new `POST /api/v1/events`, and the dashboard lists it on a new Security page. The PIN is never recorded. The Privacy screen shows the pending events.
- History screen on the TV with day stepping, 7 and 30 day ranges and an app filter.
- `streamtrack-tv` package: schemas for PIN reset and security events.

### Changed

- The Android package is now `com.jantursky.streamtracktv`. It installs as a new app: remove the old one, then pair and set a PIN again.
- Setup screen: the adb command and its QR code sit beside the permission card instead of inside it, so the list no longer jumps while you scroll, and the adb command is shown when the system accessibility settings cannot be opened.

## [1.0.0] - 2026-11-08

### Added

- Android TV app: counts remote key presses by class, reads foreground app time from usage stats, tracks screen-on time, stores hourly buckets in Room and syncs them on a configurable schedule. Pairs with the dashboard through a code and QR, and shows the exact next upload on a privacy screen.
- Web dashboard: overview, calendar with compare, apps and categories, remote usage, heatmap, devices, household members, goals and limits, settings, read-only share links, weekly digest email and a PWA manifest.
- Ingest API under `/api/v1` with device tokens, rate limits and idempotent upserts, plus CSV and JSON export.
- Seeded demo mode at `/demo` that needs no account.
- `streamtrack-tv` package with the sync, pairing and error schemas.
- Documentation site, one-click Vercel and Supabase deployment, CI and signed APK releases.

### Security

- Request schemas reject unknown fields, so a payload with a raw key code or extra data fails validation.
