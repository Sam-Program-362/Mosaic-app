# Progress tracker

## Current phase
Phase 3 — Home/folder feeds, sorting and inline local media implemented in source.
Date: 2026-10-14. Phase 3 CI verification pending. Phase 2 previously passed 30 tests,
lint, schema consistency and APK build; user has now confirmed retained folders on
restart on a real phone. No complete first release or Phase 3 device playback verification.

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
  non-action decorative count accessibility; Save copy remains explicitly disabled.
- Coil 3 local photo/GIF loading, no network module. Fit previews, stable bounded
  placeholders and contained decode errors. Media3 MP4 starts muted, loops and offers
  Pause/Play/Mute/Unmute. Only one visible animation, based on preview bounds, runs
  while RESUMED and no drawer/menu obscures it. Offscreen/inactive players released.
- Semantic Material container/drawer tokens override purple defaults seen in screenshots.
- Pure feed/session/visibility/title/artwork tests, available-only Room regression,
  and Compose/Robolectric post/empty-feed accessibility smoke tests added.
- CI caches its development signing key for subsequent APK updates. Initial upgrade
  from the earlier uncached debug key may require uninstall/reselect; originals safe.

## Decisions
User delegated routine technical decisions on 2026-10-14. Retain the Kotlin/Compose/
Material 3 baseline, JDK 17, AGP 8.9.2 / Gradle 8.11.1; add Room 2.7.1 + Kotlin-matched
KSP 2.1.20-1.0.32, lifecycle ViewModel and test-only Robolectric. Phase 3 adds Coil
3.1.0 (Compose/GIF only, no network module), Media3 1.6.1 and Compose test tooling. Working Mosaic / `dev.mosaic.app`, min 26,
compile/target 35 are still development defaults, not final public/release identity.
Light default, explicit Light/Dark only. Unknown MIME values excluded rather than
extension-guessed. Missing/nonpositive timestamps remain null for future sorting.
No saved files, original writes, recursive queries or runtime network permissions.
Playback is composition/foreground/visibility scoped, never retained in the ViewModel.

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
- User verified Phase 2 folder persistence on their phone; Phase 3 actual media
  rendering/playback/lifecycle behavior is not yet device-verified.
- Phase 3 CI run: pending.

## Still unimplemented and next phase
Immersive viewer/fullscreen/vertical paging, independent Saved copies, custom
community membership/header artwork, folder deselection and full save-error states.
After Phase 3 build verification, test Home/folder membership, sorting, local
photo/GIF/MP4 rendering and stop/mute behavior on phone. Then add the immersive
viewer using a frozen snapshot of the originating feed; implement real Saved copies
in a later slice, never pretend the disabled Save action works.

## Open decisions
Final branding/package/release SDK policy; community rename/duplicate-label policy,
custom artwork. Missing timestamp fallback is now implemented: unknown dates last,
then case-insensitive filename and stable identity. Shuffle-per-refresh and extension
hiding remain documented defaults rather than separately finalized UX decisions.
