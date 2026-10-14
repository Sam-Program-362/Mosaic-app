# Progress tracker

## Current phase
Phase 5 follow-up — investigate a newly reported feed-scroll regression with a controlled
video-preloading A/B, feed geometry instrumentation and debug scroll timing. Diagnostic source
and workflow changes passed GitHub CI run [38595248869](https://github.com/Sam-Program-362/Mosaic-app/actions/runs/38595248869)
at `742caf3`: 88 tests in both preload modes, lint, paired debug APKs, Room schema and privacy
checks for both APKs. Artifact `mosaic-debug-and-reports` is ID `11909443268`. The first attempt
(38594927101) stopped before project compilation because GitHub could not resolve the KSP plugin;
the following push-triggered run passed. Physical-device comparison is still pending; the current
checkout has no attached 75-second recording, no `adb`/device, and no Java runtime for local
Gradle builds. Do not claim on/off results or a cause until matching phone logs exist.

The prior video-transition correctness/performance pass passed GitHub CI run 38575809505 at
`50132da` (83 tests, lint, schema/privacy checks and debug APK); physical-device behavior
remained unverified. The latest portrait/landscape fast-scroll follow-up passed CI run
38586848533 at `ce78b37` (87 tests, lint, schema/privacy checks and debug APK); hardware-fling
and crash evidence remain unavailable. Earlier phase/test/build history is retained below.

## New fast-scroll regression report — CI verified, device comparison pending
- In a new retest, the user reports the issue remains and the feed feels less consistent:
  photo-only “2D Images” still shifts/feels uneven, while “Anime Vid” still shows multiple
  “Loading video...” overlays during fast scrolling. The user reviewed a 75-second recording,
  but no recording file, exact offset, or device logs are available in the workspace. This is
  user-reported behavior, not an A/B result; it is not clear that the paired diagnostic APKs
  were used.
- Source-history comparison: `7a51b28` is the nearest pre-neighbor-preloading feed checkpoint,
  but no source revision is independently confirmed as “last known smooth.” In that checkpoint
  `FeedScreen` only stored preview bounds for animated media. `3c6e694` began storing bounds
  for all media and introduced nearby Coil image/video-frame prefetch; later `3769266` uses
  all-media viewport geometry for anchor/preload selection. This is a concrete source change and a
  plausible photo-feed invalidation cost, not proof of device jank. `3769266` also changed the
  preloader's foreground gate from `playbackEnabled && !sorting && RESUMED` to lifecycle-only;
  the hidden feed can therefore keep preloading beneath the viewer or sorting UI, a separate
  resource path from a normal foreground feed scroll. Ratio-driven sizing from async media
  dimensions already existed at `7a51b28`; stable media keys and the session-reset scroll effect
  also predate the preload changes.
- Build property `-PmosaicVideoPreloading=false` now disables the Media3 neighboring-video
  preloader, Coil prefetch of nearby video frames and poster decoding for composed video cards
  outside the viewport, while leaving active-video playback, currently displayed inactive-video
  posters, photo prefetch and feed layout/selection intact. The normal build defaults to enabled. Unit coverage checks the selection
  policy in both modes; CI run `38595248869` built/retained both debug APKs, asserted the
  enabled/disabled BuildConfig values, and ran unit tests in both modes.
- Debug-only feed instrumentation records scroll start/settle and idle position transitions,
  visible row keys/offsets/sizes, preview/card dimension changes, ratio-callback sources, and
  one frame-interval/UI-thread-CPU summary per scroll burst. No per-frame logging is added.
  Player readiness/first-frame events remain separate from layout records.
- `docs/phase-5-device-check.md` contains a repeatable same-order A/B protocol for the
  photo-only and mixed feeds. Final CI run [38595248869](https://github.com/Sam-Program-362/Mosaic-app/actions/runs/38595248869)
  at `742caf3` passed 88 tests in both preload modes, lint, both APK builds, generated schema,
  and privacy checks for both APKs; artifact `mosaic-debug-and-reports` is ID `11909443268`.
  The user reports the symptom still occurs on-device, but no controlled on/off comparison,
  raw log or root cause is established. Do not implement a layout/preloader fix before comparing
  enabled and disabled device traces.

## Video transition correctness/performance correction — CI verified, device pending
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

## First-frame loading overlay and repeated-scroll crash investigation — CI verified, device pending
- `showLoading` now requires `!hasRenderedFirstFrame` in addition to `loading && !failed`;
  error still stops loading and remains visible, while pre-first-frame fallback is unchanged.
  Tests enumerate all eight combinations and explicitly cover first-frame=true / failed=false /
  loading=true.
- Debug-only diagnostics now track player instances from creation through release, manager
  source counts, unresolved preload requests, invalidation calls, active-entry counts and
  TextureView attach/detach/release order. IDs are process-local or hashed; no URIs/paths are
  logged. Manager invalidation/create/release exceptions are logged by cause type and rethrown.
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

## Portrait/landscape fast-scroll and player-handoff investigation — CI verified, device pending
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
  See docs/phase-3-device-check.md for the historic feed check; Phase 4 phone acceptance remains pending.

## Still unimplemented and next acceptance
Custom community membership/header artwork and release-ready branding/SDK/signing
policy remain. Phase 5 Save-copy code and Room v2 migration passed CI run 38469093930;
the generated schema is captured locally, and the current viewer-motion follow-up still
needs verification. Phone acceptance is separate: use `docs/phase-4-device-check.md`
for viewer expansion/close, paging, return-position, playback-stop and removal, then
verify Save from feed and viewer, repeated independent copies, app restart, and Saved
availability after source deletion/access loss using `docs/phase-5-device-check.md`.
Never equate source/CI success with phone acceptance.

## Open decisions
Final branding/package/release SDK policy; community rename/duplicate-label policy,
custom artwork. Missing timestamp fallback is now implemented: unknown dates last,
then case-insensitive filename and stable identity. Shuffle-per-refresh and extension
hiding remain documented defaults rather than separately finalized UX decisions.
