# Progress tracker

## Current status — v0.2.1 is the current stable release; waiting for the user's next instructions
**Current published stable release: Mosaic v0.2.1**
(https://github.com/Sam-Program-362/Mosaic-app/releases/tag/v0.2.1) —
versionName 0.2.1 / versionCode 3, tag `v0.2.1` on source commit
`e5ee42d15211b1da13321dfdf9bc977528060531`, asset
`Mosaic-v0.2.1-release.apk`, SHA-256
`ce659516918de0a4a0fa34f1dccd305fd44ae6b9fce7a9e5641f36659aaa56eb`, permanent
release certificate `9de4becd705feef3ef536032ff3f6245a9e2bf7fc7380be9562aa5fc704c9d6a`
(full record in the session-audio section below). Previous stable release
v0.2.0 (tag `af58727`) remains published and unchanged. Working branch:
`arena/a0a1b100-mosaic-app` (branched from `main` baseline `42ae102`; `main`
does not yet contain Phase 6, the audio fix, or either release tag target).
Documentation commits after the `v0.2.1` tag target are context-only and do
not change the released source — always verify the live branch HEAD
separately from the tag target. The session-wide MP4 audio fix is implemented
and, per the user's report, passed physical-device testing; automated release
verification was 116 tests, 0 failures/errors/skips, plus lint and privacy
checks (sources below). The Phase 7 device-acceptance checklist is
**Scheduled / Deferred — not passed**. Remaining open decisions: final
branding/package identity, release SDK policy, whether/when to merge the
session branch to `main`, and provisional custom-community artwork. **Next
immediate action: none in progress. Wait for the user's next development
instructions and evaluate their ideas before selecting the next
implementation task; no next phase is approved or started.**

## Pre-merge state (2026-10-19) — PR #4 open; user device testing confirmed complete
All post-v0.2.1 improvement work is on `arena/a0a1b100-mosaic-app` and in
**PR #4** (`arena/a0a1b100-mosaic-app` → `main`), which is **OPEN, MERGEABLE,
not merged**. Branch head at this refresh: `8c67941c8b288efa39e5dc5400f9ab946fe7882c`;
`main` is unchanged at `42ae102`. Five improvement categories are complete
(detail in the sections below): (A) external launcher icon from the
user-provided `mosaic_logo.svg`, (B) viewer-only image/GIF pinch zoom,
(C) immersive tap-to-hide viewer chrome, (D) temporary chronological
browsing in the viewer, (E) long-press title copy. Current stable release
remains **v0.2.1** (tag `e5ee42d`); these improvements are unreleased
development work — no version bump, tag, or release exists for them.
**User-reported device testing (not agent-run):** the user installed the
latest signed candidate and confirms testing of these improvements is
complete and successful. This is user-reported physical-device verification
of the improvement set, kept distinct from automated CI evidence; it does
not reclassify any Phase 7 checklist item, which remains
**Scheduled / Deferred — not passed**. Merge rules for when the user
decides: merge PR #4 with a **merge commit (never squash)**, so the tagged
release commits `af58727` (v0.2.0) and `e5ee42d` (v0.2.1) remain in `main`
ancestry. Latest signed candidate for the improvement set: run `39157385362`
at `8c67941`, artifact `mosaic-phase6-signed-apk` ID `12147372598`
(ZIP 14,378,055 bytes; GitHub-reported ZIP SHA-256
`6901c8e175dac7e6ab8067d92b6f160b3984aa28d06d348fdf8e1a4c0e8df8b7` — a ZIP
digest, not an APK digest; no APK checksum was produced), application ID
and version metadata unchanged (0.2.1 / code 3), permanently signed by the
established workflow with in-runner certificate verification.

## Final improvement phase before merge (2026-10-16) — branch only, PR #4 open, not merged
Two user-authorized improvements were implemented on
`arena/a0a1b100-mosaic-app` on top of the v0.2.1 documentation state. Neither
changes the published v0.2.1 release, version metadata, signing, or `main`.
- **External launcher icon (Improvement A):** the user-provided
  `mosaic_logo.svg` (kept at `app/icon-source/mosaic_logo.svg`) is now the
  launcher/APK icon: adaptive icon (`mipmap-anydpi-v26`) with the squircle
  artwork scaled into the 66dp safe zone over `@color/ic_launcher_background`,
  legacy + round raster mipmaps at mdpi–xxxhdpi, and `android:roundIcon` in
  the manifest. The old `@drawable/ic_mosaic` vector is no longer the launcher
  icon and is otherwise untouched; no in-app branding changed. Automated
  verification covers resource/manifest wiring (see tests below); visual
  confirmation on a real launcher is still a device check.
- **Viewer-only image/GIF zoom (Improvement B):** `ZoomableImageViewport`
  (`feature/feed/MediaZoom.kt`) wraps the shared `LocalImage` path only when
  `viewer = true`, i.e. inside the full-screen viewer's vertical pager (the
  inner feed). Pinch scales 1x–4x with clamped panning, double-tap toggles
  back to scale 1, single-finger drags at scale 1 are never consumed so pager
  scrolling is unchanged, and zoom state is keyed by the media request so it
  cannot leak between items. The top-level feed (`viewer = false`) and all
  video surfaces are behaviourally unchanged; no video playback, audio, or
  lifecycle code was touched.
- **Automated results:** Verify run `38862036033` at `0223477` succeeded —
  **131 tests, 0 failures/errors/skips** (116 prior + 7 zoom-math unit tests,
  4 zoom UI tests, 4 launcher-icon resource tests), lint, debug APK build,
  and privacy checks passed. A one-time compile-diagnostics workflow used
  during development was removed afterwards (`756787e3`).
- **PR #4** (`arena/a0a1b100-mosaic-app` → `main`) is open and updated with
  these commits; it must be merged with a merge commit (not squash) when the
  user decides. Phase 7 remains **Scheduled / Deferred — not passed**; none of
  its items are affected or claimed by this work. **Device testing:** the
  user later confirmed (2026-10-19) that testing of the installed signed
  candidate — including launcher appearance and pinch zoom — is complete
  and successful (user-reported evidence; see "Pre-merge state" above).

## Immersive inner-feed viewer (2026-10-16) — branch only, PR #4 open, not merged
One user-authorized improvement on `arena/a0a1b100-mosaic-app`: tapping media
in the fullscreen viewer (the inner feed) now hides/restores the viewer
interface (chrome). `MediaViewer` owns a session-local `chromeVisible` state
keyed by the opened snapshot, so reopening always starts with the interface
visible and nothing leaks to other screens. The header (Back, folder/title,
page count) and footer (hint, Save copy) fade out together; still images and
GIFs toggle via the zoom viewport's confirmed-single-tap callback (a double
tap still only zooms — no flicker, no double toggle — and GIF animation is
not gated by chrome state). Video surface taps route to the same toggle;
playback controls are separate composables above the surface so control taps
never toggle, controls hide/return with the chrome, and playback, position,
and the session-wide audio preference are untouched. System bars keep the
existing viewer policy (hidden while viewing, restored on exit). The
top-level feed is unchanged. Automated results: Verify run `39148933209` at
`16ce648` succeeded — **139 tests, 0 failures/errors/skips** (131 prior + 6
viewer-chrome UI tests + 2 tap-discrimination zoom tests), lint, debug APK,
and privacy checks passed. **Device testing:** the user later confirmed
(2026-10-19) that testing of the installed signed candidate — including
the chrome toggle on image/GIF/MP4 — is complete and successful
(user-reported evidence; see "Pre-merge state" above).

## Final feature additions before merge (2026-10-16) — branch only, PR #4 open, not merged
Two user-authorized features on `arena/a0a1b100-mosaic-app`; v0.2.1, `main`,
version metadata, and signing unchanged.
- **Temporary chronological browsing (inner feed):** in the fullscreen
  viewer, a deliberate horizontal swipe starting in the near-edge zone (just
  inside the system gesture inset) on a still photo builds — lazily, off the
  UI thread, only at that moment — the oldest-to-newest photo sequence from
  the opened snapshot (`modifiedAtMillis`; missing timestamps first, then
  case-insensitive filename, then stable key; anchor found by stable key)
  and moves straight to the adjacent photo. While active, horizontal swipes
  anywhere move one photo; boundaries stay put. Only the displayed
  destination is requested (no sequence preparation or neighbour preloading
  on viewer entry). Vertical paging or exiting clears the state and requires
  a fresh edge swipe; the vertical pager position and snapshot order are
  never modified. Zoomed pans, pinch, double-tap, GIF, and MP4 surfaces never
  claim the gesture; chrome visibility does not affect browsing.
- **Long-press title copy:** feed-card and viewer-header titles copy their
  exact displayed `mediaTitle` text on platform long-press (clipboard +
  "Title copied" toast). Card title tap still opens the viewer; viewer
  long-press does not toggle chrome.
- **Automated results:** Verify run `39157086439` at `3a59ea0` succeeded —
  **154 tests, 0 failures/errors/skips** (139 prior + 7 chronological unit
  tests, 5 timeline UI tests, 3 title-copy UI tests), lint, debug APK, and
  privacy checks passed. **Device testing:** the user confirmed
  (2026-10-19) that testing of the installed signed candidate
  (run `39157385362`, source `8c67941`) — including edge-swipe
  chronological browsing on real photos and long-press title copy — is
  complete and successful (user-reported evidence; see "Pre-merge state"
  above).

## Session-wide MP4 audio control — CI verified; user reports device verification passed; v0.2.1 release preparation

**User-reported device verification (not agent-run):** after the signed
device-test candidate below was delivered, the user tested it on a physical
device and reports that the test **passed perfectly** for the session-wide MP4
audio behaviour (fresh-launch muting, session-wide unmute/mute across videos
and surfaces, and reset after a fresh launch, per
`docs/session-audio-device-check.md`). Record this as user evidence; the
agent did not run a physical device, and it does not complete unrelated
device-acceptance work (the Phase 7 checklist remains scheduled). On the
strength of that report the user has authorized publishing this change as
release **v0.2.1** (versionName 0.2.1, versionCode 3 — an in-place update over
v0.2.0 with the same permanent release certificate). Release verification and
publication results are appended to this section when they complete; the
v0.2.0 release remains unchanged.

Implements the user's final core adjustment on top of the published v0.2.0
release (which remains unchanged): MP4 audio is now a single session-wide
preference instead of an independent mute flag per video card. A new
process-scoped, in-memory `VideoAudioSession`
(`core/playback/VideoAudioSession.kt`, default muted, never persisted to
DataStore/SharedPreferences/Room) is owned by `MosaicApplication` and threaded
through `MainActivity` → `MosaicApp` → `FeedScreen`/`MediaViewer` →
`LocalMediaPreview`/`VideoPreview`. Each player applies the current session
preference at creation and again in its setup effect *before* `prepare()`
(volume + Media3 audio-focus handling via `applyVideoAudioPreference`), a
session effect propagates later mute/unmute changes to existing players, and
the existing sound control now toggles the shared state (label/icon read the
shared state: "Unmute video" while muted, "Mute video" while unmuted). A fresh
process therefore starts muted; unmute/mute persists across feeds,
communities, scrolling, viewer open/return, recomposition, Activity
recreation, and background/foreground within the same process. Player
ownership, visibility/lifecycle gating, and cleanup are unchanged; no
dependencies, permissions, or persistence were added.

New tests: `VideoAudioSessionTest` (9 unit tests: fresh-process muted, silent
pre-`prepare` preference, shared toggle, new-player inheritance, re-mute,
consumer-recreation and fresh-process semantics) and
`VideoAudioSessionUiTest` (4 Robolectric Compose tests: accurate control
state, cross-surface update, inheritance by a newly composed surface,
recreated-surface inheritance).

**Verification (hosted CI; no Java in this sandbox):** code commit `e8b017a`
passed Verify Android run
[38729454211](https://github.com/Sam-Program-362/Mosaic-app/actions/runs/38729454211):
**116 tests, 0 failures/errors/skips** (103 pre-existing + 13 new), lint,
debug APK build, and packaged-permission privacy checks; the follow-up Verify
run at final branch HEAD `9e1e572` (docs/workflow-only commits after
`e8b017a`, run `38729816539`) also succeeded. Two earlier red runs were fixed
during development (`38728789426`: a `setMuted` property/function JVM
signature clash, renamed to `updateMuted`; `38729174291`: one UI test calling
`setContent` twice, rewritten). **Signed device-test candidate:** the existing
established-signing workflow (`.github/workflows/phase6-signed-build.yml`,
comment-only reuse note added) ran at HEAD `9e1e572` as run
[38729816541](https://github.com/Sam-Program-362/Mosaic-app/actions/runs/38729816541)
— all steps succeeded, including `assembleRelease`, signing with the permanent
release configuration, and in-runner `apksigner verify`. Artifact
`mosaic-phase6-signed-apk` ID `11969238543` (artifact ZIP 14,151,433 bytes,
expires 2027-01-13) contains `Mosaic-release.apk` with SHA-256
`19db3acda94688864dffa8ae3ff5530cba0c6acf775c0263204a715205918590`, signed with
certificate SHA-256
`9de4becd705feef3ef536032ff3f6245a9e2bf7fc7380be9562aa5fc704c9d6a` — the same
permanent release certificate as v0.2.0, so the candidate should install over
the v0.2.0 release in place (same versionName 0.2.0 / versionCode 2; identity
deliberately unchanged for a test candidate). **Physical-device audio
verification is still required** via `docs/session-audio-device-check.md` and
must not be inferred from unit/UI tests: real audio output, audio focus,
background/foreground and force-stop behaviour can only be confirmed on the
phone. No new release or tag was created by this task; v0.2.0 stays exactly
as published, and publication of a next version awaits the user's device
confirmation and separate authorization.

**Release v0.2.1 published (2026-10-15):**
[Mosaic v0.2.1](https://github.com/Sam-Program-362/Mosaic-app/releases/tag/v0.2.1),
tag `v0.2.1` on source commit `e5ee42d15211b1da13321dfdf9bc977528060531`
(version bump `cd6836d0` + the tested audio implementation at `e8b017a`).
Release-source Verify runs `38733046620` and `38733379491` both passed:
**116 tests, 0 failures/errors/skips**, lint, debug APK, privacy checks. The
signed APK was built fresh from that source by the established-signing
workflow, run
[38733379486](https://github.com/Sam-Program-362/Mosaic-app/actions/runs/38733379486)
(artifact `mosaic-phase6-signed-apk` ID `11971138920`), then re-verified
in-runner by publish run
[38733884128](https://github.com/Sam-Program-362/Mosaic-app/actions/runs/38733884128)
before attaching: asset `Mosaic-v0.2.1-release.apk`, 14,941,999 bytes,
SHA-256 `ce659516918de0a4a0fa34f1dccd305fd44ae6b9fce7a9e5641f36659aaa56eb`,
`apksigner verify` passed, certificate SHA-256
`9de4becd705feef3ef536032ff3f6245a9e2bf7fc7380be9562aa5fc704c9d6a` (matches
the expected permanent release certificate), and `aapt2` badging confirmed
`dev.mosaic.app` versionCode 3 / versionName 0.2.1 — so it updates v0.2.0 in
place. The release was created with `GITHUB_TOKEN`, so the tag did not
re-trigger `build.yml`; exactly one asset, built from the tagged source, is
attached. The v0.2.0 release and its asset remain unchanged. Direct APK:
https://github.com/Sam-Program-362/Mosaic-app/releases/download/v0.2.1/Mosaic-v0.2.1-release.apk

## Phase 7 — device acceptance and stabilization (Scheduled / Deferred; no items passed)
The user reports they have installed and opened the signed release APK and that it behaves as
expected. Record this narrowly as limited user-reported evidence: install/open/general behavior
only. It is not an agent-run test, does not identify itemized Phase 6 results, and does not prove
the Room v2→v3 migration ran over prior debug-build data (the release certificate differs from the
debug certificates, so an in-place data-preserving update from a debug install was not possible).
The user deferred this checklist on 2026-10-15 and considers the app satisfactory for
proceeding; it is preserved with full test cases in the Google Drive archive
(`Projects/Mosaic/Scheduled/SCHEDULED - Phase 7 Device Acceptance Testing.md`) and its
sources remain in `docs/phase-6-device-check.md` sections A–F plus the Phase 4/5 device
documents. Status of every item: **Scheduled / Deferred — pending user results if the
user chooses to resume it; do not resume it automatically**. No failing result has been
supplied and no code-level follow-up is justified yet; do not mark items passed from CI green or
from the app launching.

## Current phase — Phase 6 custom communities MVP, CI verified; device acceptance pending
Phase 6 implements local custom communities under the user's approved decisions: duplicate display
names allowed (stable community IDs are the only identity), at least one member folder required at
creation but empty membership allowed later, one folder may join multiple communities, unavailable
member memberships are retained and labelled, and folder removal cascades only its membership rows.
Room v3 adds `custom_communities` and `custom_community_members` through explicit `MIGRATION_2_3`
chained after `MIGRATION_1_2`; no destructive fallback. The CI-generated schema
`app/schemas/dev.mosaic.app.data.local.LibraryDatabase/3.json` (identity hash
`c9b1d0ba420ba8cf63969ce269c6c164`) was recovered from the green run's annotations, not hand-authored.
Feeds scope by member folder-ID set (`buildFeedForFolders`), preserving Random/Newest, metadata-only
search, viewer snapshots, photo geometry, and playback. Creation/management live in the searchable
community directory and drawer; no new picker, permission, network, or source-media operation exists.

Final verified commit `69d34df` passed CI run
[38678674913](https://github.com/Sam-Program-362/Mosaic-app/actions/runs/38678674913): 103 tests
(0 failures/errors/skips), lint, debug APK, packaged-permission privacy, Room schema consistency and
the video-preloader source guard; artifact `mosaic-debug-and-reports` ID `11947392238` is the
acceptance build. Historical Phase 6 runs: `38678154965` at `63d6a27` (artifact `11946751459`) was
the first green run for the Phase 6 code, before the generated-schema (`617dabd`, run `38678435518`)
and documentation commits; two still earlier runs were red and are not verification: `38677570560`
(one name-ordering test assertion) and `38677881453` (two Robolectric dialog/directory interaction
tests, since made deterministic).
Physical-device acceptance is pending; follow `docs/phase-6-device-check.md`. Local Gradle still
cannot start in this sandbox (no Java); hosted CI supplied verification. `main` baseline before this
phase was merge commit `42ae102` (PR #3), whose own CI run `38676173414` succeeded.

## Current phase — focused production change, CI verified; limited user phone report, targeted checks open
The 2026-10-15 user request supersedes the earlier disabled-by-default/diagnostic video-preload
approach. Production neighboring-video buffering, offscreen video-frame prefetch, speculative
offscreen-video poster decoding, and the preload manager/toggle have been removed. Bounded static-
photo prefetch and visible video posters remain; players prepare only on activation. Feed photos use
full available width and decoded/oriented intrinsic ratio for natural height, with safe
unknown/invalid-dimension fallback and a first-valid-ratio lock. The dedicated video/GIF frame
policy, playback controls/interactions, viewer, and first-rendered-frame overlay correction remain.

Verified code commit `a4264dd9c51181eff91de436fd7d7651cd48acea` passed CI run
[38624705844](https://github.com/Sam-Program-362/Mosaic-app/actions/runs/38624705844): 96 tests,
0 failures/errors/skips; unit tests, lint, debug APK build, packaged-permission checks, Room schema
consistency, and the production video-preload source guard passed. Artifact `mosaic-debug-and-reports`
(ID `11923577241`) contains `app-debug.apk`, `app-debug.sha256`, and reports. The artifact remains
available from the [run's artifact page](https://github.com/Sam-Program-362/Mosaic-app/actions/runs/38624705844/artifacts/11923577241); the sandbox cannot fetch GitHub's artifact-storage host. An earlier run found an LRU cache-miss regression; the fix is included in this verified
commit.

The user reported on 2026-10-15, exactly: `"Scrolling is okay, didn't crash. Basically it's okey."`
Interpret narrowly: scrolling was acceptable and no crash happened during that user test; the user
considers it okay enough to proceed. This is user-reported phone evidence, not an agent-run test.
The report gives no APK/commit, device/Android/refresh details, exact sequence, duration, orientations,
repeat count, offsets, logcat, or crash trace. It is not full device acceptance, does not prove the
cause of the earlier crash, and does not establish that the crash is fixed. The right status is
limited scroll feedback received; photo-geometry, mixed-video position/playback, repeated-scroll and
other feature checks remain open—not “no phone test occurred” and not “acceptance complete.”

Local `./gradlew testDebugUnitTest lint assembleDebug --stacktrace` cannot start because `JAVA_HOME`
is unset and no `java` executable exists; hosted CI supplied code/build verification. `google_drive/`
is untracked user data and must remain untouched/unstaged. See [the current-session handoff](../docs/session-handoff.md)
for repository state, remaining first-release work, a proposed next phase, and exact startup steps.

Earlier user evidence remains: preload-enabled scrolling felt sticky and the app crashed; with
preloading disabled, photo scrolling felt smoother and video loading was fast enough. A separate
video-loading position shift was reported. These are not log-correlated cause or crash-resolution
proof. The old geometry/A-B CI result at `38607618253` tested the previous two-mode source; do not use
it as verification for this change. Historical implementation and CI records follow.

## Historical controlled preload A/B build — CI verified
- Build property `-PmosaicVideoPreloading=false` bypasses the Media3 neighboring-video
  preloader, nearby Coil video-frame prefetch, and offscreen video-poster decoding while
  retaining static-photo prefetch, visible inactive-video posters, active playback, and feed
  selection. The build default was set to off at that historical stage; the later 2026-10-15
  change removed the video-preload property and manager entirely.
- The previous diagnostic source captured scroll start/settle, visible row keys/offsets/sizes,
  preview/card changes, ratio callback source, and one frame/UI-thread summary per scroll. It
  passed CI run [38595248869](https://github.com/Sam-Program-362/Mosaic-app/actions/runs/38595248869)
  at `742caf3`: 88 tests in both modes, lint, paired APKs, schema and privacy checks; artifact
  `11909443268`. That build predates the geometry fix below.
- Source-history comparison: `7a51b28` is the nearest pre-neighbor-preloading checkpoint, not
  independently confirmed as the user's last-known-smooth version. `3c6e694` began tracking
  bounds for all media and introduced nearby Coil image/video-frame prefetch; `3769266` uses
  all-media viewport geometry for anchor/preload selection. `3769266` also changed the
  preloader's foreground gate to lifecycle-only. These are source facts, not proof of device
  jank. Ratio-driven sizing, stable keys and the feed-session scroll reset predate those changes.

## Historical disabled-by-default geometry-stability change — CI verified, device pending
- The user reports: preload-enabled photo scrolling felt sticky; disabled photo scrolling felt
  smooth; with disabled preloading the feed still shifted when the next video loaded; the disabled
  build had not crashed during their tests. This supports an aggregate video-preload contribution
  to photo jank, but does not isolate Media3 vs. Coil vs. offscreen poster work or establish crash
  safety. The remaining video shift is a separate issue. No raw phone logs/offsets or video file
  have been supplied to the workspace.
- Source review confirms an asynchronous geometry path: previews began at 4:3, and image,
  video-thumbnail and Media3 video-size callbacks wrote the decoded ratio directly into the
  height-driving `ratio` state. That can remeasure a row after it enters the viewport. Loading
  text/controls already lived inside a fixed parent `Box`, so they do not by themselves change the
  outer row size. The `scrollToItem(0)` effect is keyed to folder/sort/epoch/search changes, item
  keys are stable media identities, and player activation has no scroll call. These mechanisms
  have not yet been correlated to a specific recorded jump.
- At that historical checkpoint, video preloading was disabled by default; visible media
  position used LazyList row offsets plus preview geometry relative to the row, avoiding root-
  coordinate snapshot writes on ordinary scrolling. A bounded per-feed ratio cache seeded later
  compositions, while each currently composed row retained its reserved height. This two-mode /
  default model and shared media reservation are superseded: feed photos now use natural intrinsic
  ratio, while the separate video/GIF reservation remains.
- Event diagnostics include full before/after visible rows on size changes, active-selection
  transitions, explicit programmatic scroll trigger/result, row-relative preview geometry, ratio
  source/cache changes, player preparation/loading-overlay/first-frame transitions, and coarse
  frame/main-thread-CPU summaries. CI run `38607618253` passed 93 tests in both preload modes,
  lint, both debug APK builds, schema consistency, and privacy checks. The physical-device
  mixed-orientation test remains required; no exact cause, fix, or crash resolution is claimed.

## Historical video transition correctness/performance correction — CI verified, device pending
- Home viewport anchoring uses the largest overlap across all media. Playback requires an
  animated anchor, then selects the most-visible animation meeting the 50% visible-fraction
  threshold; a tall sub-threshold animated edge cannot mask an eligible video. Photo anchors
  still suppress playback.
- The mixed window is current plus nearest photo and nearest video on each side within
  three feed positions (at most five). Feed/viewer requests and Media3 ranking use true
  positions in the current ordered snapshot; image/frame requests remain independent.
- Reconciliation removes/re-ranks changed candidates, but protects a player-active source
  until `player.release()`; its old rank is excluded while selecting other targets.
- The one-second Media3 loaded-duration target is unchanged pending device measurement.
  Active video keeps its cached thumbnail until `onRenderedFirstFrame`; inaccessible or
  unsupported media shows an error without an endless spinner.
- Debug-only `MosaicMedia` diagnostics cover anchors/candidates, submission, observable
  I/O start, completion/error, handoff, buffering/ready, activation-to-first-frame and
  player error, without paths or per-frame output.
- Regression coverage added for overlap/partial visibility, mixed bounds, rank/order
  changes, protected active entries, manager source handoff through local-style URIs,
  poster/error semantics and cache keys. A representative MP4 decode/preload integration
  test is not available: this checkout has no Java/Android SDK/device or MP4 fixture.
- `git diff --check` passed before documentation synchronization. Local
  `./gradlew testDebugUnitTest`, `./gradlew lint` and `./gradlew assembleDebug` each
  exited before Gradle startup: `JAVA_HOME` is unset and no `java` executable exists.
  GitHub run `38574064275` on `3769266` first failed Kotlin compilation (the TextureView
  adapter aspect-ratio parameter and an explicit `ConcurrentHashMap.containsKey` were fixed).
  The corrected source at `c8dbd55` passed run
  [38574662523](https://github.com/Sam-Program-362/Mosaic-app/actions/runs/38574662523):
  82 tests (0 failures/errors/skips), `testDebugUnitTest`, `lint`, `assembleDebug`, packaged
  privacy checks and Room schema check all passed. A further manager/player release-order
  regression was added after that first green run. Final run
  [38575809505](https://github.com/Sam-Program-362/Mosaic-app/actions/runs/38575809505) at
  `50132da` passed 83 tests (0 failures/errors/skips), `testDebugUnitTest`, `lint`,
  `assembleDebug`, packaged privacy and Room schema checks. Artifact `mosaic-debug-and-reports`
  is ID `11899788396`. Local Gradle commands still cannot start without Java. No before/after
  device startup measurement, MP4 decode run, or real scrolling/playback evidence is
  available. Device acceptance remains pending; do not claim the sticky transition fixed.

## Retained first-frame loading overlay correction and historical crash investigation
- `showLoading` now requires `!hasRenderedFirstFrame` in addition to `loading && !failed`;
  error still stops loading and remains visible, while pre-first-frame fallback is unchanged.
  Tests enumerate all eight combinations and explicitly cover first-frame=true / failed=false /
  loading=true.
- The historical debug diagnostics tracked player instances, manager-source/pending-request
  counts, invalidations, active entries, and TextureView attach/detach/release order. The video
  manager and its counters were removed by the current user request; debug-only player lifecycle,
  hashed-ID, and TextureView diagnostics remain. No paths/URIs are logged.
- Source review finds a bounded per-session candidate set, explicit active-source protection
  until player release, and both TextureView detach paths clearing the view-holder before player
  release. `VideoPreview` creates its player in `remember` and releases it from `DisposableEffect`;
  the new allocation/release tokens can reveal an unpaired allocation if composition is abandoned.
  Rapid activation can still cause decoder churn; repeated manager invalidations can occur as
  windows/handoffs change. These are hypotheses to measure, not an assigned crash cause.
- No device crash log, native tombstone, or repeated-scroll run is available here. `adb`, an
  Android device and a representative MP4 fixture are unavailable. Capture steps (full logcat,
  PID check, bugreport for native/ANR failures) are in `docs/phase-5-device-check.md`.
- For this new change, local `./gradlew testDebugUnitTest`, `./gradlew lint` and
  `./gradlew assembleDebug` each stopped before Gradle startup (`JAVA_HOME` unset; no `java`).
  Pushed commit `a9703cb` to the approved development branch. GitHub run
  [38581751120](https://github.com/Sam-Program-362/Mosaic-app/actions/runs/38581751120)
  passed 84 tests (0 failures/errors/skips), lint, `assembleDebug`, packaged privacy and
  Room schema checks; debug artifact `mosaic-debug-and-reports` is ID `11903168202`.
  This does not verify physical-device playback or resolve the reported crash.

## Historical portrait/landscape fast-scroll and player-handoff investigation — CI verified
- New user-reported observations: the glitch is subtle during slow scrolling but clear on fast
  swipes at a repeatable point relative to video height. Crash risk has been observed around
  portrait/landscape videos at opposing viewport edges, and one fast pass can be sufficient.
  No crash logs were supplied; this is not a confirmed cause or proof of resource pressure.
- The follow-up exposes the existing ratio-driven feed height as a pure helper and logs
  ratio/height changes from thumbnail and Media3 video-size callbacks. Viewport diagnostics now
  report anchor vs active item, feed position/kind/hash, overlap, preview size, relative top and
  visible fraction in coarse 10% buckets. Player lifecycle/state/first-frame logs include paired
  tokens and hashed IDs; logs remain debug-only with no path/URI or per-frame output.
- A targeted selection policy change avoids an empty handoff when a tall animated anchor is
  below 50% visibility but another eligible animation is visible. Photo anchors still suppress
  playback; the preload window and one-second target are unchanged. Regression tests cover
  ratio-derived portrait/landscape heights, each orientation at top and bottom, forward/reverse
  fast-scroll geometry, the threshold gap, and managed-source handoff until old-player release.
- This models visibility/selection and Media3 manager ownership, not a hardware fling or MP4
  decode. Local `./gradlew testDebugUnitTest` stopped before Gradle because `JAVA_HOME` is unset
  and no `java` executable exists. Pushed code commit `ce78b37`; CI run
  [38586848533](https://github.com/Sam-Program-362/Mosaic-app/actions/runs/38586848533) passed
  87 tests (0 failures/errors/skips), lint, `assembleDebug`, packaged privacy and Room schema
  checks. Debug artifact `mosaic-debug-and-reports` is ID `11905094822`. No device run/log exists;
  overlay/frame diagnosis and crash investigation remain separate, and no crash cause/resolution
  is claimed.

## Earlier video preload, controls and warm-resume follow-up — CI verified, phone pending
Prior source used a bounded current/neighbor media window, Coil local image/video-frame prefetch,
and Media3 one-second neighboring-video buffers. Inactive MP4 cards request a local
first-frame thumbnail. The player adds play/pause, mute, seek/time/duration, double-tap
±10-second seeking, tap-to-hide/show, and position/play-state retention across lifecycle
deactivation. Warm ON_RESUME keeps the feed epoch and Room rows for current/preloaded IDs
while refreshing other entries; a new process and explicit refresh remain full refreshes.
Unit/repository/Room/UI tests were added. No database schema or runtime network permission
changed. Initial GitHub run 38556461411 failed compilation because `MosaicApplication.kt`
lacked the `VideoFrameDecoder` import; this was fixed in `a11fde2`. Green CI run
[38556928293](https://github.com/Sam-Program-362/Mosaic-app/actions/runs/38556928293) at
`a11fde2` passed 74 tests (0 failures/errors/skips), lint, schema consistency, packaged
APK privacy checks and debug APK build. Artifact `mosaic-debug-and-reports` is ID
`11890760322`. Commits `3c6e694` and `a11fde2` are pushed to the session branch. Local
Gradle remains blocked because Java is absent; physical-device acceptance is still
pending. The user authorized development-branch CI/debug-APK verification; no merge or
release is in scope.

## Drive-reference UI adaptation follow-up — CI verified, phone check pending
The current session branch adapts the inspected Drive reference without copying the
web app wholesale: Dark fallback, indexed-metadata search and relative-time helpers,
feed-card/video-control updates, searchable local communities, Settings/library stats,
and redesigned folder-management cards. Navigation is through the top header and drawer
only—no bottom navigation or navigation rail. Search covers already-indexed names,
folder labels, MIME type, and media kind only; no content scan or runtime network was
added. Compose tests cover Settings, directory loading/unavailable rows and local feed
search; unit tests cover search and relative-time buckets.

CI iterations 38523338581, 38523840274, 38524387833 and 38542503093 exposed test import,
fixture and Settings assertion issues; each was corrected. Final run
[38543107645](https://github.com/Sam-Program-362/Mosaic-app/actions/runs/38543107645)
at commit `b667dcc` passed 67 tests (0 failures/errors/skips), lint, schema consistency,
packaged APK privacy checks and debug APK build. Artifact `mosaic-debug-and-reports`
(ID `11883232388`, about 21 MB) includes the debug APK and reports. The Drive ZIP was
inspected and remains untracked; do not commit it. Local Gradle remains unavailable
because Java is not installed. CI does not establish phone acceptance: the new UI/theme,
video controls and search still need device feedback. This is not a complete first
release; no merge or release was made.

## Repository inspection and Phase 1
The initial checkout contained README, ignore rules and a release workflow only.
Phase 1 added the Android/Compose foundation, Gradle wrapper/version catalog,
Home/drawer/menu/Settings shell, persisted Light/Dark themes, theme mapping tests,
verification CI, explicit SDK setup in release CI, and the six context files.
The sandbox has no Java or Android SDK; local Gradle test/lint/build commands are
blocked before startup. Static XML/TOML/YAML/wrapper/manifest checks passed.

## Phase 2 implemented
- Native ACTION_OPEN_DOCUMENT_TREE result contract retains actual returned flags;
  requests/persists read-only access without broad storage/network permissions.
- SAF source queries only direct children, classifies image/GIF/MP4 metadata,
  excludes directories/unsupported MIME types, closes cursors, handles lost access,
  null/malformed/loading/error cursors as failures rather than empty results.
- Typed scan results distinguish completed empty from failed/incomplete listing.
- Room stores selected folders and media metadata, using tree URI + document ID
  identities rather than filenames. Atomic successful replacement reconciles
  additions/deletions; failure retains index/labels/identity and marks unavailable.
- Application-scoped repository serializes operations. ViewModel survives activity
  recreation; lifecycle resume (including launch) and explicit refresh scan folders.
- Add folder, folder status/counts, per-folder retry, Home indexing summary,
  and same-tree access reselection are functional in source. Different-tree
  reselection is rejected rather than silently replacing a community.
- Drawer retains folder identities/labels and unavailable indicators. Actual
  community feeds/custom communities are next-phase work, not implemented yet.
- Temporary-only grants are labelled clearly; same-tree selections do not create
  duplicate records; cancellation is not converted to successful empty results.
- Added pure scanner/repository tests and Robolectric SAF/Room integration tests
  for direct-child queries, cursor closure, empty vs failed, permissions, incomplete
  metadata, identity collisions, recovery, successful deletion reconciliation,
  transaction rollback and upsert preservation. All 30 tests passed in the final CI run.
- Room schema export configured and actual CI-generated version 1 JSON retained
  in source; CI checks its semantic consistency. No hand-authored schema.

## Phase 2 user acceptance and Phase 3
User reports the selected folders stayed after restarting. Supplied screenshots
show two selected folders and 295 supported files. This establishes that selection,
index population and restart retention worked on that phone/provider. It does not
establish every deletion/unavailability/nested-folder edge case.

Phase 3 source now includes:
- Joined available-only Room feed metadata; no schema/table change or migration.
- Home combines available folders; selectable automatic folder communities isolate
  membership; unavailable communities remain visible and excluded from playback.
- Random uses stable ID-based ranking per explicit session epoch, avoiding repeats
  and accidental reshuffles from themes/menu/DB emissions. Refresh/resume starts a
  new epoch and regenerates decorative counts; sort changes keep counts unchanged.
- Newest uses modification time descending; missing timestamps last; deterministic
  filename/identity ties. Filename title hides recognized extension, keeps full metadata.
- Width-spanning compact post cards, local static artwork by deterministic filename,
  non-action decorative count accessibility; Phase 5 adds Save copy actions.
- Coil 3 local photo/GIF loading, no network module. Fit previews, stable bounded
  placeholders and contained decode errors. Media3 MP4 starts muted, loops and offers
  Pause/Play/Mute/Unmute. Only one visible animation, based on preview bounds, runs
  while RESUMED and no drawer/menu obscures it. Offscreen/inactive players released.
- Semantic Material container/drawer tokens override purple defaults seen in screenshots.
- Pure feed/session/visibility/title/artwork tests, available-only Room regression,
  and Compose/Robolectric post/empty-feed accessibility smoke tests added.
- CI caches its development signing key for subsequent APK updates. Initial upgrade
  from the earlier uncached debug key may require uninstall/reselect; originals safe.

## Phase 3 phone report and Phase 4 implementation
The user reports that photos display in Home, selecting a folder shows only its media,
and videos play. This is user phone evidence; it does not establish GIF decoding,
muted/unmuted playback, offscreen/background stop behavior, audio focus, or every
provider edge case.

Phase 4 source adds:
- Tapping a media preview captures a copied `FeedSnapshot` (exact visible ordering,
  sort/scope/epoch) and the tapped index; a vertical pager opens that exact item.
- The existing feed stays composed underneath while viewing, hidden from accessibility
  and with playback disabled, preserving feed scroll position. Only the current
  RESUMED viewer page activates GIF/video playback. Back button/system Back close it;
  temporary system-bar hiding restores the previous visibility.
- Manage folders presents an explicit cancel/confirm dialog for **Remove from Mosaic**.
  Serialized repository removal attempts to release only the persisted read grant, then
  Room deletes that folder record and cascades only local media metadata. Source media
  stays unchanged; if grant release cannot be confirmed, UI warns but still removes
  the folder index/community. No schema version change.
- Added tests for immutable feed-order snapshots, initial viewer page/exit and vertical
  swiping, cancellation/confirmation UI, folder-specific repository cleanup, failed
  grant release and Room cascade; test total now 53.
- New phone guide: `docs/phase-4-device-check.md`; Phase 3 guide marked historical.

Phase 4 CI iterations:
- Runs 38461010256 and 38461432410 exposed missing imports (first app source, then
  Compose UI test imports); these were corrected, not represented as passing.
- Run 38462313818 passed 52 tests plus lint/schema/privacy/APK.
- Final run 38463132363 at source commit 02085fb passed 53 tests, 0 failures/errors/
  skips; lint, schema consistency, packaged APK privacy and debug build passed.
  Artifact `mosaic-debug-and-reports`, ID 11846947024, size 14,242,550 bytes.
  https://github.com/Sam-Program-362/Mosaic-app/actions/runs/38463132363

## Phase 5 source and CI; device acceptance pending
- Each Save tap in feed cards and viewer starts a new request; Saved shows app-managed
  copies newest-first. Saved items can be viewed and copied again. Saved-copy removal
  and custom communities are excluded from this slice.
- `SavedCopyRepository` streams from the selected content URI into a UUID-named private
  file under `filesDir/saved-media`, first as `.partial`; Room metadata follows only
  after a nonempty stream is flushed and renamed. Ordinary failures clean temporary or
  unrecorded files. Saved rows do not retain source URIs; source media is read-only.
- Room v2 adds the Saved table/index with explicit v1→v2 migration and retains existing
  folder/media tables. Earlier CI found provider setup/import issues; they were fixed.
- CI run 38469093930 at commit 53314ef passed 59 tests, lint, tracked-schema
  consistency, packaged APK privacy and debug APK build. KSP's exact generated v2 schema
  was recovered from 4 chunked workflow annotations and committed at
  `app/schemas/dev.mosaic.app.data.local.LibraryDatabase/2.json` (6,024 bytes,
  identity hash `9c5465636830273745be40f054484124`).
- New Phase 4 visual follow-up adds reversible grow/fade motion for opening/closing the
  full-screen viewer and a UI transition test, following the user-supplied dark/orange
  reference without copying web-only functions. Runs 38509513570 and 38510281049 exposed
  an unreliable entrance assertion in the test; the final test isolates reverse-close
  retention. Run 38511038772 at 1c4cd8d passed all 60 tests, lint, schema consistency,
  packaged privacy checks and debug APK build. Phase 4/5 device acceptance remains
  pending.

## Decisions
User delegated routine technical decisions on 2026-10-14. Retain the Kotlin/Compose/
Material 3 baseline, JDK 17, AGP 8.9.2 / Gradle 8.11.1; add Room 2.7.1 + Kotlin-matched
KSP 2.1.20-1.0.32, lifecycle ViewModel and test-only Robolectric. Phase 3 adds Coil
3.1.0 (Compose/GIF only, no network module), Media3 1.6.1 and Compose test tooling. Working Mosaic / `dev.mosaic.app`, min 26,
compile/target 35 are still development defaults, not final public/release identity.
Dark default (reference-inspired provisional UI choice), explicit Light/Dark only.
Unknown MIME values excluded rather than extension-guessed. Missing/nonpositive timestamps
remain null for future sorting.
Saved-copy bytes live only in Mosaic's private files area; no source writes, recursive
queries or runtime network permissions. Playback is composition/foreground/visibility
scoped, never retained in the ViewModel.

## Verification and GitHub authorization
- The user explicitly approved pushing to the session development branch to run
  GitHub tests/builds and create a debug test APK. No merge or release authorized.
- Initial run 38447946849 failed at SDK setup, before compiling/testing app code.
- Switched to fixed ubuntu-24.04 runner's preinstalled SDK; updated core actions.
- Run 38448205363 (commit 2ea1bce): SUCCESS. `testDebugUnitTest`, `lint` and
  `assembleDebug` all completed successfully. Debug APK/reports artifact uploaded.
  https://github.com/Sam-Program-362/Mosaic-app/actions/runs/38448205363
- Run 38449127701 (commit cf908eb): SUCCESS, 29 tests, 0 failures/errors/skips;
  lint, debug APK build and generated schema consistency checks passed.
- One final conservative guard/test added: even an empty cursor must expose both
  document-ID and MIME columns before it can authorize deletion reconciliation.
- Final run 38449832366 (source commit 87f9815): SUCCESS, 30 tests;
  0 failures, 0 errors, 0 skipped. `testDebugUnitTest`, `lint`, `assembleDebug`
  and checked-in/generated schema consistency all passed. Debug APK/reports uploaded.
  https://github.com/Sam-Program-362/Mosaic-app/actions/runs/38449832366
- Final documentation-only commit does not change the verified Android source;
  docs-only changes are excluded from redundant push CI runs.
- Static manifest/XML/catalog/YAML/wrapper and shell/diff checks passed.
- Full CI log downloads are blocked by sandbox host restrictions; API step results
  and annotations establish the recorded outcomes. Workflow emits useful error
  annotations and test totals for future API-visible diagnosis.
- Local Android compilation/device testing remains unavailable in this sandbox.
- User verified Phase 2 folder persistence. Phase 3 user report confirms photos show
  on Home, folder feeds isolate correctly, and videos play; GIF, mute/audio focus and
  offscreen/background lifecycle behavior remain unverified.
- Phase 3 initial run 38454091013 / commit 1283b43 passed: 45 tests,
  0 failures/errors/skips; lint, schema consistency and APK build passed.
- Final hardening: removed Media3's inherited ACCESS_NETWORK_STATE (and defensively
  INTERNET) via manifest-merger rules; CI now checks actual APK permissions. Media3
  safely falls back to unknown network type without that permission.
- Silent videos request no audio focus until unmuted; surfaces detach before release.
- Static image/avatar requests explicitly use static decoders, avoiding accidental
  background animation of non-GIF formats or cached animated artwork.
- Final hardened run 38455304642 / source commit 8f11853: SUCCESS, 45 tests,
  0 failures, 0 errors, 0 skipped; lint, schema consistency, debug APK and packaged
  APK privacy guard all passed. No network or broad storage/media permissions.
  https://github.com/Sam-Program-362/Mosaic-app/actions/runs/38455304642
- Run 38454957427 was superseded/cancelled by the static-decoder hardening push;
  no failed test/build result is claimed for that cancelled run.
- Final doc-only commit does not change verified source; no merge/release created.
  At that historical checkpoint, Phase 4 phone acceptance remained pending. See
  `docs/phase-3-device-check.md` for the historic feed check; the later limited
  2026-10-15 scroll report is recorded in the current section above.

## Still unimplemented and next acceptance
Custom community membership is implemented in Phase 6 (see the current section above). Release-ready
branding/package/SDK/signing policy remains unresolved; custom-community artwork stays a provisional
default with no editor. Saved-copy removal is explicitly out of scope. Phase 5 Save-copy and
Room v2 migration, viewer, and focused photo-geometry changes are CI-verified, but their
hardware/provider behavior is not thereby accepted.

The latest user phone report (`"Scrolling is okay, didn't crash. Basically it's okey."`,
2026-10-15) is limited user evidence of acceptable scrolling/no crash during that test; it
is not a build-identified or exhaustive acceptance run and does not prove crash resolution.
Use `docs/phase-5-device-check.md` for current photo geometry, video activation, position and
crash-evidence procedure; use `docs/phase-4-device-check.md` for the historical viewer/folder
removal checklist and the Saved-copy flows for repeated copies, restart, source deletion/access
loss, and failure cleanup. GIF, audio focus/mute, background/offscreen playback, refreshed UI,
viewer restoration and provider/permission edge cases remain to be exercised on-device.

Phase 6 has since implemented the local custom-communities MVP under the approved policies in the
current section above; device acceptance remains open. Do not treat CI as phone acceptance or claim
the earlier crash is fixed.

## Open decisions
Final branding/package/release SDK policy and custom artwork. Custom-community naming/membership
policies were approved for Phase 6: duplicate names allowed (IDs are identity), at least one member
at creation, empty later allowed, multiple memberships per folder, unavailable memberships retained. Missing timestamp fallback is now implemented: unknown dates last,
then case-insensitive filename and stable identity. Shuffle-per-refresh and extension
hiding remain documented defaults rather than separately finalized UX decisions.
