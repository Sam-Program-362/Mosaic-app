# Mosaic

An offline Android local-media browser with a Reddit-inspired feed structure and
its own identity. **Phase 2 adds safe folder indexing**, not yet a complete media browser.

## Current implementation

- Native Kotlin / Jetpack Compose / Material 3 project and Gradle wrapper.
- Home, community drawer, app menu, Saved/folder placeholders, and Settings.
- Persisted light/dark appearance using Preferences DataStore.
- Native folder picker, retained read grants, direct-child media scanning.
- Room metadata, refresh/resume reconciliation, unavailable-folder retention/retry.
- No runtime network, broad storage permissions, accounts, or telemetry.

Media post cards, community feeds/custom communities, playback, and independent
Saved copies are **not implemented yet**. Home currently reports indexing counts.
Manage folders supports Add folder, refresh and original-folder access reselection.
Only explicit selections are scanned, and only their direct supported media children.
Failed/incomplete scans retain previously indexed metadata; originals are read-only.

## Build

Use JDK 17 and Android SDK platform 35 / build tools 35.0.0. Open this repository
in Android Studio, or set `ANDROID_HOME` to your SDK installation (alternatively
use an untracked `local.properties` containing `sdk.dir=...`).

```sh
./gradlew testDebugUnitTest
./gradlew lint
./gradlew assembleDebug
```

Debug output: `app/build/outputs/apk/debug/app-debug.apk`.
Release output: `app/build/outputs/apk/release/app-release-unsigned.apk`.
Gradle/dependency installation requires development-time internet; runtime does not.

The verification workflow runs tests, lint, and a debug build on pushes and PRs.
The existing manual/tagged release workflow builds and externally signs the release
APK using the existing GitHub signing secrets. Do not commit keystores or credentials.
The core Phase 2 tests, lint and debug build passed on the development branch.
See the progress tracker for actual run results and final regression status.

## Provisional project configuration

Working name: Mosaic. Namespace/application ID: `dev.mosaic.app`. Minimum API 26
(Android 8.0), compile/target API 35. These are reversible development defaults,
**not finalized public identity or release-target approval**. The user delegated routine technical choices; dependency versions
are pinned in `gradle/libs.versions.toml` (no claim that they are the latest).

See [the simple phone check](docs/phase-2-device-check.md) for installation and
manual acceptance steps.

See `AGENTS.md` and the six `context/` files for requirements, boundaries, actual
verification status, and the next phase.
