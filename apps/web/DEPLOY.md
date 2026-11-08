# Deploying the dashboard

The dashboard is a Next.js app in `apps/web`, backed by Supabase (Postgres and Auth). Vercel runs it,
applies the database migrations during the build and calls the two scheduled jobs.

## One click

[![Deploy with Vercel](https://vercel.com/button)](https://vercel.com/new/clone?repository-url=https%3A%2F%2Fgithub.com%2Fjantursky%2FStreamTrackTV&root-directory=apps%2Fweb&project-name=streamtrack-tv&repository-name=streamtrack-tv&integration-ids=oac_VqOgBHqhEoFTPzGkPd7L0iH6&env=CRON_SECRET,NEXT_PUBLIC_SITE_URL&envDescription=CRON_SECRET%20protects%20the%20scheduled%20jobs.%20NEXT_PUBLIC_SITE_URL%20is%20the%20public%20origin%20used%20in%20emails%20and%20share%20links.&envLink=https%3A%2F%2Fgithub.com%2Fjantursky%2FStreamTrackTV%2Fblob%2Fmain%2Fapps%2Fweb%2F.env.example)

The button:

1. copies the repository to your GitHub account,
2. creates a Vercel project with `apps/web` as the root directory,
3. installs the Supabase integration (`integration-ids`), which creates a Supabase project and sets
   `NEXT_PUBLIC_SUPABASE_URL`, `NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY`, `SUPABASE_SECRET_KEY` and
   `POSTGRES_URL_NON_POOLING`,
4. asks for `CRON_SECRET` (generate it with `openssl rand -hex 32`) and `NEXT_PUBLIC_SITE_URL` (for
   example `https://streamtrack-tv.vercel.app`, no trailing slash).

The first production build runs `scripts/migrate.mjs`, which applies `supabase/migrations` in order and
records them in `public.schema_migrations`. Later builds apply only new files. See
[Migrations](#migrations) for preview builds and TLS.

Then finish the Auth settings below.

## Supabase Auth settings

In the Supabase dashboard, under **Authentication**:

1. **URL Configuration**: set **Site URL** to your `NEXT_PUBLIC_SITE_URL` and add
   `https://<your domain>/auth/confirm` to **Redirect URLs**. Add preview domains too if you want sign-up
   to work on preview deployments.
2. **Sign In / Providers → Email**: keep **Confirm email** on.
3. **Emails → SMTP Settings**: the built-in mailer sends only a few emails per hour. Set up your own SMTP
   (Resend works) before you invite anyone.
4. **Emails → Templates** (recommended): the default links use the PKCE flow, which only works in the
   browser where the user signed up. Links that carry a token hash work anywhere. Change the link in
   - **Confirm signup** to
     `{{ .SiteURL }}/auth/confirm?token_hash={{ .TokenHash }}&type=email&next=/app`
   - **Reset password** to
     `{{ .SiteURL }}/auth/confirm?token_hash={{ .TokenHash }}&type=recovery&next=/reset-password`

`/auth/confirm` accepts both kinds of link.

## Optional: weekly digest

Set `RESEND_API_KEY` and `DIGEST_FROM` (an address on a domain you verified in Resend). Without them the
digest job does nothing. Users turn the digest on in **Settings**.

## Scheduled jobs

`apps/web/vercel.json` declares both jobs. Vercel sends `Authorization: Bearer $CRON_SECRET` with each call.

| Path                  | Schedule (UTC) | Job                                                                                                     |
| --------------------- | -------------- | ------------------------------------------------------------------------------------------------------- |
| `/api/cron/retention` | `17 3 * * *`   | Deletes data older than each user's retention period, and devices that never synced a day after pairing |
| `/api/cron/digest`    | `0 7 * * 1`    | Sends the weekly digest                                                                                 |

The Hobby plan runs crons at most once a day and at an approximate time, which both jobs tolerate.

## Manual setup

1. Create a Supabase project. From **Project Settings → API Keys** copy the project URL, the publishable
   key and a secret key. From **Connect** copy the direct connection string (port 5432).
2. Import the repository in Vercel and set **Root Directory** to `apps/web`. `vercel.json` sets the
   install command (`cd ../.. && bun install --frozen-lockfile`) and the build command
   (`cd ../.. && bun run build`, which builds the schema package and then the app).
3. Add the environment variables from [`.env.example`](./.env.example): at least
   `NEXT_PUBLIC_SUPABASE_URL`, `NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY`, `SUPABASE_SECRET_KEY`,
   `DATABASE_URL` (the direct connection string), `NEXT_PUBLIC_SITE_URL` and `CRON_SECRET`.
4. Deploy, then apply the Auth settings above.

## Migrations

- **Production only.** The build migrates when `VERCEL_ENV` is unset (a local or CI build) or
  `production`. Preview builds skip it and say so in the build log: the integration gives previews the
  production database, and a branch must not change its schema before it is promoted. Set
  `MIGRATE_ON_PREVIEW=1` on a preview environment that has a database of its own.
- **TLS.** Supabase signs its database certificates with its own root CA, which is not in Node's trust
  store. Without that CA the runner encrypts the connection but does not verify the certificate (what
  libpq's `sslmode=require` does too). To verify it, download the CA from **Database → Settings → SSL
  Configuration** and set its PEM text as `DATABASE_CA_CERT`, or point `sslrootcert` in the URL at the
  file. `sslmode=verify-full` or `verify-ca` always verifies, against the system store when no CA is
  given. `sslmode=disable` turns TLS off; `localhost` connects without TLS unless the URL asks for it.

To run migrations without a deployment, for example against a staging database:

```sh
DATABASE_URL=postgres://... bun run --filter web migrate
```

The files are plain SQL, so `bunx supabase db push` applies them as well; don't mix both tools on one
database, since each keeps its own history.

## Demo-only deployment

Set `DEMO_ONLY=1` and leave out the Supabase variables. The site then serves the landing page and the
seeded demo at `/demo`. Sign-in pages say that accounts are off, `/app` redirects to `/demo` and the device
API answers `404`.

## Local development

```sh
bun install
cp apps/web/.env.example apps/web/.env.local   # fill in, or leave Supabase empty for the demo
bun run dev
```

For a local Supabase stack, run `bunx supabase start` from the repository root and use the URL, keys and
database URL it prints.

The database tests in `apps/web/test/db` run the migrations and SQL functions against a real Postgres
(15 or newer). They are skipped unless `TEST_DATABASE_URL` points at a throwaway database whose name
contains `test`; its `public` and `auth` schemas are dropped first.

```sh
TEST_DATABASE_URL=postgres://postgres:postgres@localhost:5432/streamtrack_test bun run --filter web test
```

In GitHub Actions a `postgres:17` service with `POSTGRES_DB: streamtrack_test` is enough.
