# Contributing

Thanks for helping. Bug reports, docs fixes and pull requests are all welcome.

## Before you start

- For anything bigger than a small fix, open an issue first so we can agree on the approach.
- Don't include real viewing data, device tokens or Supabase keys in issues, screenshots or test fixtures.

## Prerequisites

- [Bun](https://bun.sh) 1.3 or newer, and Node.js 22.19 or newer (the version is in `.node-version`).
- JDK 17.
- The Android SDK, for the TV app. Android Studio installs it.

## Setup

```sh
bun install
bun run dev                  # web dashboard on http://localhost:3000
cd android && ./gradlew test # Android unit tests
```

Install the Playwright browsers once, before the end-to-end tests:

```sh
bunx playwright install chromium
```

## Where things live

| path                  | What it is                                                               |
| --------------------- | ------------------------------------------------------------------------ |
| `android/`            | The Android TV app.                                                      |
| `apps/web`            | The web dashboard and the ingest API.                                    |
| `apps/docs`           | The Blume docs site.                                                     |
| `packages/schema`     | The `streamtrack-tv` npm package: the sync payload and pairing contract. |
| `supabase/migrations` | SQL for the database, with row level security.                           |
| `design/`             | Architecture and design notes.                                           |

## Checks

```sh
bun run check       # format, lint, typecheck and unit tests with coverage
bun run build       # schema package and web app
bun run test:e2e    # Playwright tests of the web app
cd android && ./gradlew test lint koverVerify spotlessCheck
```

CI runs all of them on every pull request.

## Demo mode

Demo mode shows every dashboard screen with seeded data, so you can look at the UI without a TV or a Supabase project. Start the dev server with `bun run dev` and open `/demo`.

## Conventions

- TypeScript strict. Run `bun run format` before you commit. Prettier handles the formatting.
- Every behavior change comes with a test.
- Add a line under `## [Unreleased]` in `CHANGELOG.md` for anything users will notice.
- Changes to the sync payload or the pairing contract change `packages/schema`. Keep the Android app and the web app in step with them.
- Commit messages are short imperative sentences in sentence case, such as `Add pairing code expiry to the dashboard`. No period at the end.

## Pull requests

- Use the pull request template and tick every box that applies.
- Keep one change per pull request.
- Say what you tested, and how, in the description.

## Releasing

A release is a tag that matches the version in `packages/schema/package.json` and `APP_VERSION_NAME` in `android/gradle.properties`, with a section for that version in `CHANGELOG.md`. The release workflow builds the signed APK, creates the GitHub release and publishes the npm package.

Before the first release, set up these once:

- Repository secrets `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS` and `ANDROID_KEY_PASSWORD` (see `android/README.md`).
- A GitHub environment named `npm`.
- The `streamtrack-tv` package on npm. npm only lets you add a trusted publisher to a package that exists, so publish the first version by hand with `npm publish --access public` from `packages/schema`, then add the trusted publisher (this repository, workflow `release.yml`, environment `npm`) in the package settings. Later tags publish through the workflow.

## License

By contributing you agree that your contributions are licensed under the MIT license.
