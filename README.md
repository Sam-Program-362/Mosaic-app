# Mosaic

An offline Android local-media browser with a Reddit-inspired feed structure and
its own identity. Phase 5 adds independent Saved copies to the immersive viewer and
safe folder removal; this is still a development app, not a complete first release.

## Current implementation

- Native Kotlin / Jetpack Compose / Material 3 app and Gradle wrapper.
- Home and automatic folder communities; exact folder-feed isolation.
- Random/Newest local ordering, refresh, and decorative noninteractive counts.
- Local photo/GIF/MP4 previews, plus a full-screen vertical viewer that opens at
  the tapped post and uses a frozen copy of the originating feed order.
- Every **Save copy** tap creates another independent app-managed copy; Saved items
  remain local and use the same viewer. Saved-copy removal is not implemented.
- Viewer playback is limited to the current foreground page; MP4 starts muted and
  exposes play/pause and mute/unmute controls. Back returns to the originating feed.
- Manage folders supports add, refresh, access recovery, and confirmed **Remove
  from Mosaic**. Removal drops Mosaic's index/community and attempts to release its
  saved read grant; it never alters or deletes original media.
- Room metadata, direct-child SAF scanning, refresh/resume reconciliation, and
  unavailable-folder retention/retry.
- Persistent Light/Dark appearance. No runtime network, broad storage permissions,
  accounts, or telemetry.

**Unverified UI follow-up in the current branch:** searchable local-media/community
  metadata, revised feed/video controls, a searchable community directory, updated
  Settings and folder-management screens, and header/drawer-only navigation (no bottom
  bar or rail). Verify this source with CI before treating it as passing.

**Still not implemented:** custom communities, comments, real votes, Saved-copy removal,
and production release/signing.

## Verification and phone evidence

[Latest verified viewer-motion CI build](https://github.com/Sam-Program-362/Mosaic-app/actions/runs/38511038772)
passed **60 tests (0 failures/errors/skips), lint, schema consistency, debug APK build,
and packaged APK privacy checks**. The APK has no network or broad storage/media
permissions. This predates the unverified UI follow-up above.

The user reports from the Phase 3 phone check that photos appear in Home, selecting
a folder shows only its media, and videos play. GIF animation, sound/mute behavior,
background/offscreen stopping, Phase 4 paging, and folder removal have not yet been
confirmed on-device. See the [Phase 4 phone check](docs/phase-4-device-check.md).

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
APK using existing GitHub signing secrets. Do not commit keystores or credentials.

## Provisional project configuration

Working name: Mosaic. Namespace/application ID: `dev.mosaic.app`. Minimum API 26
(Android 8.0), compile/target API 35. These are reversible development defaults,
**not finalized public identity or release-target approval**. The user delegated routine technical choices; dependency versions
are pinned in `gradle/libs.versions.toml` (no claim that they are the latest).

See `AGENTS.md` and the six `context/` files for requirements, safety boundaries,
verification details, and later work.
