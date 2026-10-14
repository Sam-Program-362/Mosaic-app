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
- Only the current foreground video plays. MP4 starts muted and exposes play/pause,
  mute, seek/time/duration, double-tap ±10-second seeking and tap-to-hide/show controls
  in the viewer. Inactive videos show a local first-frame thumbnail.
- Feed photos fill the available media width and use their own decoded intrinsic aspect
  ratio, with a safe temporary fallback when dimensions are unavailable; photos are fit,
  not stretched or cropped to a video frame. Their established height is stable across
  later callbacks. The dedicated video/GIF frame policy remains separate and unchanged.
- Bounded nearby static-photo prefetch remains local. The production video-preload manager,
  neighboring-video buffering, offscreen video-frame prefetch, speculative offscreen video
  posters, and their diagnostic build toggle have been removed. Visible video posters,
  active-player preparation, controls, and first-rendered-frame overlay behavior remain.
- Manage folders supports add, refresh, access recovery, and confirmed **Remove
  from Mosaic**. Removal drops Mosaic's index/community and attempts to release its
  saved read grant; it never alters or deletes original media.
- Room metadata, direct-child SAF scanning, unavailable-folder retention/retry, and
  selective warm-resume reconciliation. A cold process start or explicit refresh still
  fully scans; a warm resume refreshes other entries while preserving the active window.
- Persistent Light/Dark appearance. No runtime network, broad storage permissions,
  accounts, or telemetry.

**Recent reference-inspired UI adaptation (CI-verified):** local metadata search for
  media and communities, relative media timestamps, updated feed/video controls, a
  searchable community directory, redesigned Settings/folder screens, and header/drawer-
  only navigation (no bottom bar or rail). Search is limited to indexed metadata.

**Still not implemented:** custom communities, comments, real votes, Saved-copy removal,
and production release/signing.

## Verification and phone evidence

The current focused change removes video-specific preloading from production and gives feed photos
full-width natural-ratio geometry while preserving the dedicated video frame and playback flow.
Its unit tests, lint, privacy/schema checks, debug APK build, and hosted CI are **pending**. Local
Gradle verification is blocked because this checkout has no Java executable and `JAVA_HOME` is
unset. Do not use older paired preload-mode APKs to accept this change; the current build has no
preload toggle. See the [Phase 5 device check](docs/phase-5-device-check.md) for the single-APK
phone sequence and exact acceptance criteria.

The user's earlier same-device comparison reported sticky photo scrolling and a crash with video
preloading enabled; with it disabled, photos felt smooth and video loading was fast enough. A
separate shift when a video began loading had also been reported. These are user observations, not
log-correlated attribution. No crash-resolution claim is justified by removing preloading alone.
Physical-device verification of photo proportions, video behavior, feed position stability, and
repeat-scroll crash behavior remains separate from CI.

Other physical-device checks remain open: GIF animation, sound/mute behavior, background/offscreen
stopping, Phase 4 paging/removal, and the latest theme/search/Settings/navigation UI. See the
[Phase 4 phone check](docs/phase-4-device-check.md) and [Phase 5 copy check](docs/phase-5-device-check.md).

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
