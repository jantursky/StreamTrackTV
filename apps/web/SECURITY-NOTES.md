# Security notes for the dashboard

Bullets for the "Things to know" section of the repository's `SECURITY.md`, plus the details behind them.

## Things to know

- Device tokens are 256 random bits. The server stores only their SHA-256 hash and compares hashes in
  constant time. A revoked device cannot be reactivated; pair it again instead.
- Pairing codes have 6 characters without look-alikes (no I, O, 0 or 1), last 10 minutes and work once. The
  TV collects its token with a separate poll token, which is also stored only as a hash and is handed out
  once. Starting, polling and confirming are rate limited.
- The sync endpoint accepts key classes only. A payload with raw key codes, or any field the schema does
  not name, is rejected: every request schema is strict.
- Row level security covers every table. Signed-in users can read and delete their own rows; buckets are
  written only by the sync function, which runs with the secret key after the device token is verified.
- The Supabase secret key is read only in server code and is never sent to the browser. It is used for
  the device API (sync and pairing), confirming pairing codes, the read-only share page, the cron jobs and
  account deletion; everything else runs with the user's session under row level security.
- Pages send a nonce-based Content Security Policy with `strict-dynamic` and no inline styles, plus HSTS,
  `X-Frame-Options: DENY`, `nosniff` and a strict referrer policy. API responses send `default-src 'none'`.
- Export, deleting device data and deleting the account require a signed-in session.
- Share links are 256-bit tokens, stored hashed, read-only, revocable and valid for at most 30 days, which
  the database enforces. Shared pages show no goals, household members or settings, a link for one device
  names only the apps used on it, and search engines are told not to index them.
- Retention is enforced daily: buckets older than the user's setting (365 days by default) are deleted,
  and so are devices that never synced within a day of pairing.
- Time zones are stored per device (`devices.timezone`, as reported by the TV) and per account
  (`settings.timezone`, from the browser at sign-up or first visit, or chosen in settings).

## Details

**Tokens.** `generateToken` draws 32 bytes from `crypto.randomBytes` and encodes them as base64url (43
characters). Lookups go by the hash; `digestsEqual` then compares the stored and computed digests with
`timingSafeEqual`, so a match does not depend on string comparison timing. Revocation clears the hash in a
trigger, which makes it final even for direct database updates.

**Pairing.** A code is 6 characters from a 32-character alphabet (about 10^9 combinations). Confirming is
limited to 10 attempts per user per 10 minutes and starting to 10 per client address per hour. Polling is
limited per client address (960 per 10 minutes) before the poll token is looked up, and per known poll
token (240 per 10 minutes, twice what a TV polling every 5 seconds needs), so made-up tokens do not create
counter rows. Claiming and delivering lock the pairing row, so two users cannot claim the same code and the
device token is issued once. A token lost on its way to the TV is never repeated; that device never syncs
and the retention job removes it a day later.

**Input.** Every API body is read with a byte cap (1 MiB for sync, 4 KiB for pairing) before it is parsed,
then validated with the schemas of the `streamtrack-tv` package. Server actions validate their forms with
zod. Database rows read by the dashboard are validated again before they are rendered.

**Output.** React escapes everything it renders, and no component uses `dangerouslySetInnerHTML`. The
digest email builds HTML by hand, so every user-controlled value goes through `escapeHtml`. CSV exports
prefix cells starting with `=`, `+`, `-`, `@`, tab or carriage return with an apostrophe to stop formula
injection.

**Sessions.** `@supabase/ssr` keeps the session in `SameSite=Lax` cookies; the strict CSP is what keeps
scripts from reading them. The proxy refreshes it on each request and sends anonymous visitors of `/app`,
`/pair` and `/reset-password` to the login page. Server code checks the user with `getClaims`, which
validates the JWT, instead of trusting the cookie. The `next` target after login must resolve to a path on
the same origin: control characters and backslashes are refused, because browsers drop tabs and newlines
and read `\` as `/`, which would turn `/<TAB>/evil.example` into `//evil.example`.

**Migrations.** The build migrates only for production (or when `MIGRATE_ON_PREVIEW=1`), so a preview
branch cannot change the production schema. Without a CA the connection is encrypted but the server
certificate is not verified, because Supabase's certificates chain to Supabase's own root CA; set
`DATABASE_CA_CERT` or `sslmode=verify-full` with `sslrootcert` to verify it.

**Scheduled jobs.** The cron endpoints compare the `Authorization` header with `CRON_SECRET` in constant
time and refuse every call when the secret is not set.

**Not covered.** Email delivery and account recovery are Supabase Auth features; their rate limits and
templates are configured in the Supabase dashboard. The rate limits above are kept in Postgres, so they
hold across serverless instances but are not a defense against a distributed attack.
