# Changelog

All notable changes to this project are documented here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses [Semantic Versioning](https://semver.org/).

## [Unreleased]

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
