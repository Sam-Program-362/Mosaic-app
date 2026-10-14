# Progress tracker

## Current phase
Phase 5 — independent Save copy and Saved-feed implementation in progress.
Date: 2026-10-14. Phase 4 baseline CI run 38463132363 passed 53 tests, lint, schema,
APK privacy and debug build at commit 02085fb. Phase 5 CI iterations 38466413293 and
38467526012 exposed an unattached Robolectric provider NPE; run 38467045590 caught a
provider-stub import issue. Those were fixed. Green run 38468360143 passed 59 tests,
lint, schema consistency, APK privacy and debug build. Follow-up run 38469093930 also
passed those checks; its exact generated Room v2 schema was decoded from four CI
annotation chunks (6,024 bytes, identity hash `9c5465636830273745be40f054484124`).
Schema and viewer expand/fade work were committed and pushed. Two early CI runs
(38509513570, 38510281049) exposed an unreliable entrance assertion in the new UI test;
the final test isolates reverse-close retention. Green CI run 38511038772 at 1c4cd8d
passed 60 tests (0 failures/errors/skips), lint, schema consistency, packaged APK
privacy checks and debug build. The viewer motion is CI-verified, but phone acceptance
remains pending. User-reported Phase 3 evidence confirms Home photos, folder isolation
and video playback; GIF, mute, offscreen/background playback, viewer motion/paging and
removal are not yet confirmed on-device. This is not a complete first release.

## Drive-reference UI adaptation follow-up — source in progress
After the verified viewer commit `b916c31`, the current session branch has a
UI-adaptation slice awaiting CI: Dark fallback, indexed-metadata search and relative-time helpers,
feed-card/video-control updates, a searchable local community directory, Settings and
folder-management redesign, and top-header/drawer routing only. The explicit user
constraint is honored in source: no bottom navigation and no navigation rail. Added
Compose tests for Settings/directory/feed search and unit tests for search/time buckets.
The Drive ZIP was inspected; it remains untracked and must not be committed. GitHub run
38523338581 compiled main Android Kotlin source but failed compiling test sources because
the new tests explicitly imported `assertExists`/`assertDoesNotExist`, which are member
methods in this Compose API. Those imports were removed. Run 38523840274 compiled main
and test Kotlin but one relative-time test failed: its 36-hour-old timestamp was before
the Unix epoch, correctly taking the helper's invalid-date path. The test's fixed `now`
was moved forward. Run 38524387833 compiled main/tests and ran 67 tests, but the new
Settings UI test still failed at its first library-stat assertion. The stat component now
exposes an accessible `Label: count` description, and the test targets that description
instead of visible label text. Another CI run is pending. No passing full test/lint/APK/
privacy check or phone result exists for this slice yet. `git diff --check`
passes. Local Gradle is blocked because this sandbox has no Java. Continue using the
authorized GitHub workflow only on `arena/b0f3e3cf-mosaic-app`; this remains unverified
source, not a passing release.

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
