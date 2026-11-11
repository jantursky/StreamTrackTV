# StreamTrack TV for Android TV

The TV side of StreamTrack TV. It records screen-on time, foreground time per app and remote key presses per hour, shows them on the TV, and uploads them to the server you pair it with. Nothing goes anywhere else.

- Compose for TV, D-Pad first, dark by default
- Android 8.0 (API 26) and newer, built against API 37
- Distributed as an APK on GitHub Releases; accessibility-based apps do not fit Google Play

## Build

Requirements: JDK 17 or newer and the Android SDK (platform 37). Point `ANDROID_HOME` at the SDK or put `sdk.dir` in `local.properties`.

```sh
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease      # minified with R8
./gradlew check                # unit tests, lint, detekt, ktlint, coverage gate
```

The version lives in `gradle.properties` (`APP_VERSION_NAME`, `APP_VERSION_CODE`); CI and the release workflow read it from there.

To prefill the server address on the pairing screen, set it at build time:

```sh
./gradlew assembleRelease -PDEFAULT_SERVER_URL=https://stats.example.com
```

### Release signing

A release build signs with the keystore named by these environment variables. When `ANDROID_KEYSTORE_PATH` is not set, it falls back to the debug key so local release builds still install.

| Variable                    | Meaning                 |
| --------------------------- | ----------------------- |
| `ANDROID_KEYSTORE_PATH`     | Path to the `.jks` file |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password       |
| `ANDROID_KEY_ALIAS`         | Key alias               |
| `ANDROID_KEY_PASSWORD`      | Key password            |

The release workflow takes four repository secrets. It decodes `ANDROID_KEYSTORE_BASE64` into a file and sets `ANDROID_KEYSTORE_PATH` to it; the other three are passed through unchanged.

| Secret                      | Content                                     |
| --------------------------- | ------------------------------------------- |
| `ANDROID_KEYSTORE_BASE64`   | The `.jks` file, base64-encoded on one line |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password                           |
| `ANDROID_KEY_ALIAS`         | Key alias                                   |
| `ANDROID_KEY_PASSWORD`      | Key password                                |

Create the keystore once and keep it, with its passwords, somewhere safe outside the repository. Updates only install over an APK signed with the same key, so losing it means every user has to uninstall first.

```sh
keytool -genkeypair -v -keystore streamtrack-release.jks -storetype JKS \
  -keyalg RSA -keysize 4096 -validity 10000 -alias streamtrack

# One line of base64 for the ANDROID_KEYSTORE_BASE64 secret
base64 < streamtrack-release.jks | tr -d '\n' > streamtrack-release.jks.b64     # macOS and Linux
```

To try the release signing locally, point the variables at the file:

```sh
ANDROID_KEYSTORE_PATH=$PWD/streamtrack-release.jks ANDROID_KEYSTORE_PASSWORD=... \
  ANDROID_KEY_ALIAS=streamtrack ANDROID_KEY_PASSWORD=... ./gradlew assembleRelease
```

## Run on a TV or the emulator

Emulator: in Android Studio's Device Manager, create a device from the **TV** category (1080p) with a recent Google TV image.

Real TV: turn on developer options and network debugging, then:

```sh
adb connect 192.168.1.50:5555
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

On first start the app asks for the server address, shows a 6-character code and a QR code, and waits until you confirm the code in the web app. The device token is stored encrypted with a key from the Android Keystore.

### Grant the permissions with adb

The **Setup** screen links to each settings page. Some TVs hide these pages; the app then shows the matching command, and you can run them yourself:

```sh
PKG=com.jantursky.streamtracktv

# Usage access: foreground time per app and screen-on time
adb shell appops set $PKG GET_USAGE_STATS allow

# Remote counter (accessibility service). This replaces the list of enabled services;
# if others are enabled, read the list first and append ours with a colon.
adb shell settings get secure enabled_accessibility_services
adb shell settings put secure enabled_accessibility_services $PKG/.collector.KeyPressService

# Battery optimization exemption, so syncing continues in standby
adb shell dumpsys deviceidle whitelist +$PKG
```

### Demo data

Debug builds have **Settings → Load demo week**. It replaces local data with a generated week (a binge, a TV left on, several apps) so every screen can be previewed without real use. Release builds do not contain it.

Debug builds also accept a plain `http://` server on the local network, for example `http://10.0.2.2:3000` from the emulator. Release builds require `https://`.

### History

The **History** tab reads the same hourly buckets that sync uses, so it needs no extra permission and sends nothing. Choose a quick period (**Today**, **Yesterday**, **This week**, **Last week**, **This month** or **Last month**) or a rolling **Day**, **7 days** or **30 days**. Weeks start on the first day of the week of the TV's language, and a period that has not ended stops at today. **Previous** and **Next** move one day, or one whole week or month for a quick period, never past today, and **Today** jumps back. Under the totals, a comparison shows how screen time, app time and remote presses differ from the period just before (the same weekdays of the week before, for a week in progress); it is left out when that period has no data. A **Highlights** card gives the daily average over days with data, the busiest day and the peak hour. The day view draws one bar per hour, with screen-on time behind and app time in front. The longer views draw one bar per day and list every day below the chart; select a day to open it. Select an app in the list to narrow app time to it, and select it again, or choose **Show all apps**, to clear the filter. Screen-on time and remote presses are not per app, so the filter leaves them as they are. Remote presses are listed by key class only. Apps excluded in Settings never appear.

### PIN lock

**Settings → PIN lock** asks for a 4 to 8 digit PIN, entered on an on-screen keypad that works with the D-pad. Only a salted PBKDF2 hash is stored, on the TV. Once a PIN is on, the app asks for it on every start and whenever it leaves the screen and comes back, including after opening system settings from **Setup**. Three wrong tries are free; after that the keypad waits 30 seconds, doubling up to 15 minutes. The wait survives restarts and clock changes. Change or turn off the PIN in Settings (both ask for the current PIN). The app is also hidden from the recent apps list.

#### Security log

The lock and the PIN settings write to a bounded log in the app's Room database (table `security_events`, newest 500, oldest dropped first, sent or not). Each sync run uploads the unsent ones, oldest first, in batches of 100 to `POST /api/v1/events` with the device token, and marks them sent only after the server accepts them. A failed upload, an offline TV or a server without the route keeps them for the next run and never fails the usage sync; a batch the server refuses as invalid is halved and a single refused event is set aside, like usage rows. Events carry a random `eventId`, so a batch whose answer got lost is not stored twice. `app opened` is written once per process, not on every return from the background. If the TV is unpaired the log stays on the TV. Clearing the app's data deletes events that were not uploaded yet. The dashboard shows them on its Security page.

**Forgot PIN** on the lock screen shows a short code and a QR code. Open the link (`/pin-reset`) on the web, sign in as the account that owns this TV and confirm the code; the TV polls, clears the PIN and unlocks. This needs a paired TV and a server that has the `pin-reset` endpoints and migration. If the TV is not paired or the server is unreachable, the only way back in is **Settings → Apps → StreamTrack TV → Clear data** (or `adb shell pm clear com.jantursky.streamtracktv`), which also removes local history and pairing.

## Architecture

```
app        Application, activity, wiring (AppGraph), manifest, icons, release config
ui         Compose for TV screens, theme from design/DESIGN.md, view models
sync       Ktor client for /api/v1, sync engine, WorkManager sync worker, pairing flow
collector  Accessibility key counter, UsageStatsManager reader, boot receiver, permission checks
data       Room (hourly buckets with sent flags, app labels), DataStore settings, encrypted token
core       Pure Kotlin: models, hour splitting, aggregation, goals, binge and idle detection,
           wire DTOs (mirror of packages/schema), payload mapping, demo week generator
```

How data moves:

1. The accessibility service turns each key press into a key class and counts it in memory; every minute the counts are added to `key_buckets`.
2. Every 15 minutes (and when the dashboard opens) the collector reads the usage events of yesterday and today, rebuilds foreground sessions and screen-on intervals, splits them at local hour boundaries and stores **absolute** values per hour. A row whose value changes is marked unsent.
3. The sync worker runs on the chosen interval (15 minutes to 24 hours, exponential backoff). It sends unsent rows to `POST {server}/api/v1/sync` with `Authorization: Bearer <deviceToken>` in batches of at most 2000 rows per kind and 900 KiB of JSON, then marks exactly the sent values as sent. Because the server upserts absolute values, a repeated upload changes nothing.
4. A `401` removes the token and the TV returns to pairing. A `429` with `Retry-After` schedules one more sync after that delay; without it, and on `5xx` and network errors, the worker retries with backoff. A `400` or `413` halves the batch and tries again; a single row that is still refused is set aside, counted on the Privacy screen, and only sent again if its value changes. Other `4xx` stop the run.

Usage events have no record of a power loss. When the TV boots, or the screen turns on while it was already on, the open app session and screen-on interval end at the last event seen before, so a TV that lost power is not counted as on until the next boot.

Pairing polls every 5 seconds (120 polls in a code's 10 minutes; the server allows 150) and waits out a `Retry-After`. A code's lifetime is measured on the TV's clock from the moment it arrived, so a TV with a wrong clock still shows it for the full 10 minutes. After six unclaimed codes, an hour, the TV stops asking and waits for the user.

The wire format is defined in `packages/schema` (`SCHEMA_VERSION` 1). The Kotlin DTOs in `core` mirror it field by field, and `core/src/test/resources/contract/` holds sample JSON in the same shape that the contract tests decode and re-encode.

## Privacy

Collected, per local hour:

- foreground time per app, with the app's display name
- screen-on time
- number of remote presses per key class: D-Pad, select, back, home, media, volume, other
- the TV's time zone, sent with each upload
- security events, each a type and a time: app opened, lock screen shown, wrong PIN (with the wrong tries so far), wait started (with its seconds), unlocked, Forgot PIN opened, PIN reset confirmed, PIN turned on, changed or off

Never collected:

- which key was pressed beyond its class; key codes are discarded in the accessibility service
- typed text, screen content, window titles or screenshots; the service cannot read window content and receives no accessibility events
- what is watched inside an app
- the PIN, any digit typed on the lock screen or the PIN hash
- location, microphone, camera, contacts, accounts or advertising IDs

The **Privacy** screen on the TV shows the exact JSON of the next upload and the exact JSON of the security events waiting to go with it. Excluded apps (Settings → Apps) are neither recorded nor uploaded, and their stored hours are deleted. App data is excluded from cloud backup and device transfer.

## Tests and quality gates

```sh
./gradlew test             # JVM and Robolectric tests of every module
./gradlew lint             # Android lint, warnings included
./gradlew detekt           # static analysis, config in config/detekt/detekt.yml
./gradlew spotlessCheck    # ktlint formatting (spotlessApply fixes it)
./gradlew koverVerify      # line coverage of core, data, sync and collector logic, at least 85 %
./gradlew connectedCheck   # Room tests on a connected device or emulator
```

The coverage gate leaves out UI and code that only talks to the framework (the accessibility service, receivers, schedulers, Keystore); those are covered by the Compose UI tests and manual checks.
