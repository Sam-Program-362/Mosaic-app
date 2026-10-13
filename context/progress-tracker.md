# Progress tracker

## Current phase
Phase 2 — selected folders and safe local indexing implemented in source.
Date: 2026-10-14. Android CI verification is being initiated; no first-release
completion or physical-device verification is claimed.

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
  transaction rollback and upsert preservation. These await CI results below.
- Room schema export configured; generated schema must be retained after CI.

## Decisions
User delegated routine technical decisions on 2026-10-14. Retain the Kotlin/Compose/
Material 3 baseline, JDK 17, AGP 8.9.2 / Gradle 8.11.1; add Room 2.7.1 + Kotlin-matched
KSP 2.1.20-1.0.32, lifecycle ViewModel and test-only Robolectric. No image loader or
Media3 until media rendering phase. Working Mosaic / `dev.mosaic.app`, min 26,
compile/target 35 are still development defaults, not final public/release identity.
Light default, explicit Light/Dark only. Unknown MIME values excluded rather than
extension-guessed. Missing/nonpositive timestamps remain null for future sorting.
No saved files, original writes, recursive queries, network access or media players.

## Verification and GitHub authorization
- The user explicitly approved pushing to the session development branch to run
  GitHub tests/builds and create a debug test APK. No merge or release authorized.
- CI results: pending. Update this section with exact run/results after completion.
- Local Android compilation/device testing remains unavailable in this sandbox.
- No physical-device folder/provider behavior is claimed verified.

## Still unimplemented
Post cards/media rendering, Random/Newest and decorative counts, interactive
community feeds/custom membership/artwork, viewer/paging/playback lifecycle,
independent Saved copies, folder deselection, and full decode/save error states.

## Next phase and manual acceptance
Run/install CI debug APK; select a folder containing supported media + a nested
folder; ensure count includes only direct media. Select an empty folder. Add/delete
source media and refresh. Restart to check grant retention. Revoke/detach source and
retry/reselect to check unavailable retention. Test picker cancellation, duplicate
selection, themes and landscape/font scaling. CI cannot replace real provider checks.
Then implement shared compact post cards, community feed isolation/sorting and
local image/GIF/video rendering in a separate incremental slice.

## Open decisions
Final branding/package/release SDK policy; community rename/duplicate-label policy,
custom artwork, missing timestamp sort fallback. Shuffle-per-refresh and extension
hiding remain documented defaults rather than separately finalized UX decisions.
