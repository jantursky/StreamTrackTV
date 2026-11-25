# Changelog

All notable changes to this project are documented here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses [Semantic Versioning](https://semver.org/).

## [Unreleased]

## [1.4.0] - 2026-11-25

### Changed

- No change to the schemas. The version moves with the app release 1.4.0, which adds on-screen reminders, typed limits and a bedtime window on the TV. Nothing is sent to the server that was not sent before.

## [1.3.0] - 2026-11-15

### Changed

- No change to the schemas. The version moves with the app release 1.3.0, which improves accessibility on the TV and the dashboard.

## [1.2.1] - 2026-11-11

### Changed

- No change to the schemas. The version moves with the app release 1.2.1, which fixes the History controls on the TV.

## [1.2.0] - 2026-11-11

### Changed

- No change to the schemas. The version moves with the app release, which adds History quick periods and insights on the TV.

## [1.1.1] - 2026-11-09

### Changed

- The README lists every export, including the PIN reset and security event schemas, and this changelog covers every release. No code change.
- `test:pack` now fails when a runtime export is not named in the README.

## [1.1.0] - 2026-11-09

### Added

- `pinResetStartRequestSchema`, `pinResetStartResponseSchema`, `pinResetPollRequestSchema` and `pinResetPollResponseSchema` for Forgot PIN (`POST /api/v1/pin-reset/start` and `/poll`).
- `securityEventSchema`, `eventsRequestSchema`, `eventsResponseSchema` and `SECURITY_EVENT_TYPES` for the security event log (`POST /api/v1/events`).
- `LIMITS` keys `pinMinLength`, `pinMaxLength`, `pinResetTtlSeconds`, `maxEventsPerBatch`, `maxEventValue`, `maxStoredEvents` and `maxEventFutureMs`.

## [1.0.0] - 2026-11-08

### Added

- Sync payload, pairing and error schemas with exported types.
- `KEY_CLASSES`, `LIMITS`, `SCHEMA_VERSION`, `PAIR_CODE_ALPHABET` and `bucketKey()`.
