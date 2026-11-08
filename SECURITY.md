# Security policy

## Supported versions

Only the latest release gets fixes: the Android app, the web dashboard and the `streamtrack-tv` package.

## Reporting a vulnerability

Please don't open a public issue. Use GitHub's [private vulnerability reporting](https://github.com/jantursky/StreamTrackTV/security/advisories/new), or email jan.tursky@gmail.com with a description and, if you can, steps to reproduce.

You'll get an answer within a week. Once a fix is released, the advisory is published with credit, unless you'd rather stay anonymous.

## Things to know

- The TV app needs the accessibility service to see key presses. It logs only a key class (dpad, select, back, home, media, volume or other) and a timestamp bucket. It never logs key codes, typed text or screen content.
- Usage stats are package names and durations. They don't include what was on screen.
- Each device token is 256 random bits. The server stores only its SHA-256 hash and compares hashes in constant time. A revoked device stays revoked; pair it again instead.
- Pairing codes have six characters without look-alikes, last 10 minutes and work once. The TV collects its token with a separate poll token that is also stored only as a hash and handed out once. Starting, polling and confirming are rate limited.
- The sync endpoint accepts key classes only. A payload with raw key codes, or any field the schema doesn't know, is rejected.
- Row level security covers every table. Signed-in users can read and delete their own rows. Buckets are written only by the sync function, which runs with the secret key after the device token is verified.
- The Supabase secret key is read only in server code and never reaches the browser.
- Pages send a nonce-based Content Security Policy with `strict-dynamic`, plus HSTS, `X-Frame-Options: DENY`, `nosniff` and a strict referrer policy. API responses send `default-src 'none'`.
- Export, deleting device data and deleting the account need a signed-in session. CSV exports neutralize cells that start with `=`, `+`, `-` or `@`.
- Share links are 256-bit tokens, stored hashed, read-only, expiring and revocable. Shared pages show no goals, household members or settings and are not indexed.
- Request bodies are strict: a field the schema doesn't know is rejected. Share links last at most 30 days, devices that never synced are removed after a day, and the post-login redirect only accepts local paths.
- Retention runs daily and deletes buckets older than the user's setting (365 days by default).
- Rate limits live in Postgres, so they hold across serverless instances. They don't stop a distributed attack.
- Email delivery and account recovery are Supabase Auth features. Their limits and templates are set in the Supabase dashboard.
- The Android app is distributed as a signed APK, outside Google Play. Check the SHA-256 file next to the APK on the release page before you install it.
