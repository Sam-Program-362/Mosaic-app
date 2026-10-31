# Fresh-session handoff — Mosaic (refreshed 2026-10-31)

This handoff records only verified facts, clearly labelled user-reported results, and unresolved
items. **No merge, tag, release, or version bump is authorized by this document.**

## Required reading order
1. `AGENTS.md`
2. `context/project-overview.md`
3. `context/architecture.md`
4. `context/code-standards.md`
5. `context/ai-workflow-rules.md`
6. `context/ui-context.md`
7. `context/progress-tracker.md`
8. This handoff. Then verify the live repository state instead of assuming these facts are current.

## Current change — Multi-GIF feed playback, static thumbnails, playback lifecycle (2026-10-31)
**Status: implemented and CI-verified on `arena/855dea2c-mosaic-app`; PR #8 not pushed to, nothing
merged, no tag/release, no change to `applicationId`, signing, version, Room schema, backup v1,
saved copies or MP4/audio policy.**

- Commits `a149508`, `46d5ae6`, `7ae0b7c`, `b28ec5a`; parent/head of PR #8
  `84f870f90f425255a038a00582f2b998a3579064`. Fast-forwarding PR #8's branch to `b28ec5a` (or
  cherry-picking this series) publishes exactly this feature — no merge is authorized by this doc.
- Verified run `41192764867`: SUCCESS, 289 tests, 0 failures, 0 errors, 2 skipped; lint,
  `assembleDebug`, Room-schema check and packaged-APK privacy guard passed.
  https://github.com/Sam-Program-362/Mosaic-app/actions/runs/41192764867
- Feed: every GIF whose measured preview is `>= 12%` inside the viewport animates at the same time
  (retained down to `6%` while already animating), with no GIF cap; MP4 keeps its own single slot
  (`activeVideoPreview` / `playbackVideoForAnchor`, 0.5 fraction, anchor must be a video). One gate,
  `animatedPlaybackAllowed(eligible, playbackEnabled, transientlyBlocked, lifecycleResumed)`, stops
  both when playback is disabled, a sort transition is running, or the app is backgrounded.
- Inactive GIFs render a real first frame: `GifPreview` keeps an always-on `BitmapFactoryDecoder`
  still layer (frame 0 of the original bytes, all API levels) under an animated layer that only
  exists while eligible. The `"GIF · playback paused"` label is removed. Cache identities are
  `"$uri@$modified:gif-still-frame"` and `"$uri@$modified:true"`; nothing is copied, renamed or
  transcoded and `MediaKind.Gif` is unchanged.
- Viewer animates only the current resumed page; zoom, chrome, chronological browsing, Save copy
  and `VerticalPager(beyondViewportPageCount = 0)` are unchanged.
- Decoder choice centralized in `core/media/GifDecoders.kt` (`AnimatedImageDecoder` API 28+,
  `GifDecoder`/`Movie` below; the API-28 call sits in a `@RequiresApi(28)` function so lint can
  prove the guard). `NearbyPhotoPrefetcher` stays static-photo-only (no GIF prefetch).
- Tests: `GifPlaybackEligibilityTest`, `GifFeedPlaybackUiTest`, `GifFrameDecodingTest` over the
  committed fixture `app/src/test/resources/media/two-frame.gif` (real 64x48, 2 frames: red then
  blue). The still-frame decode is asserted to be the **first** frame on API 35 and API 26.
- Limits: Robolectric cannot decode animated drawables (`ImageDecoder.decodeDrawable` and `Movie`
  both unsupported), so 2 cases are `assumeTrue`-guarded device checks — several simultaneous GIFs,
  scroll-in/out start/stop, background/resume, thumbnail-preserving pause, and the API 26/27
  `Movie` path still need a physical device. **No device testing was performed.**
- Engineering rationale and trade-offs: `docs/gif-playback-decision-record.md`.

## Previous change — Backup & Restore (PR #7) integrated into PR #6 (2026-10-31)
**Status: implemented on an intermediate branch; intermediate PR open, not merged.** No merge of
this PR or of PR #6, no tag, no release, and no change to `applicationId`, signing, version or the
format-v1 contract. `main`, PR #7 and PR #6's branch are untouched.

Branching:
- Working branch **`feature/pr6-backup-v1-integration`**, created from the verified PR #6 head
  `7b252bc3ccacf16c116984a859332e20fa05abaa`
  (`fix/gif-detection-and-accessible-saved-copies`) — deliberately **not** from `main`.
- Latest remote `main` before starting: `9bd46bb681ee3f9c1e3e4de0608e516cfbcda039` (the merged
  PR #7). It was merged **into** the working branch with a merge commit, so every backup file is
  the merged PR #7 implementation rather than a re-creation.
- Intermediate PR **#8**, base **`fix/gif-detection-and-accessible-saved-copies`** (PR #6's
  branch, not `main`): https://github.com/Sam-Program-362/Mosaic-app/pull/8.

Integration detail:
- The only code conflict was `MosaicApplication.kt` (both sides added composition-root imports).
  It now exposes the PR #6 saved-copy graph (`MediaStoreSavedCopyStorage` /
  `SafSavedCopyStorage`, `SavedCopyRepository`, `SavedCopyFolderPreferences`) and the PR #7
  `LibraryBackupRepository` (`DataStoreBackupThemeStore`, `ContentBackupStorage`, `BuildConfig`
  version fields).
- No Room conflict: PR #7 changed no entity or schema version, so PR #6's Room v4
  (`MIGRATION_3_4`, committed `4.json`) is unchanged and the backup layer runs on v4 exactly as
  it ran on v3. No migration was edited and no destructive fallback was added.
- `SettingsScreen` keeps the PR #6 *Saved copies* card and gains PR #7's optional
  `backupSection` slot; `MosaicApp`/`MainActivity` wire `BackupViewModel` alongside
  `LibraryViewModel`.
- `docs/backup-format-v1.md`, the `data/backup/` package, `LibraryBackupRepository`,
  `LibraryRepository.restoreLibrary`, the `LibraryStore`/`RoomLibraryStore` restore methods, the
  settings backup UI and both fixtures are **byte-identical to `main`**. Format v1 therefore
  imports unchanged; the release-exported archive the user already holds stays valid.
- PR #6 behaviour is preserved: content-sniffed `GIF87a`/`GIF89a` classification, animated GIF
  playback paths, `Download/Mosaic/Saved` MediaStore copies on API 29+, the persisted SAF folder
  on API 26–28, unique copies with failure cleanup, legacy private copies via the nullable v4
  column, and the v4 schema. Restore touches only folders/communities/theme; `saved_copies` is
  never read, deleted or rewritten.

Tests added — `app/src/test/java/dev/mosaic/app/data/repository/Pr7BackupFixtureIntegrationTest.kt`
(7 Robolectric cases) import the **committed PR #7 fixture**
`app/src/test/resources/backup/mosaic-backup-v1.zip` (unmodified, not regenerated) through the
merged stack over Room v4 and assert: the fixture reads as format v1; folder labels and custom
community IDs/names/memberships survive changed SAF tree URIs; the archived theme is applied;
a skipped folder drops only its own membership and an empty-member community stays empty;
duplicate-entry, broken-checksum, future-version and non-archive files change nothing;
a failed transaction rolls back and releases only the grants it acquired; legacy and
shared-destination saved-copy rows are preserved; the media index is rebuilt by rescanning,
including a content-detected GIF; and a post-restore export is still a readable v1 archive with
no URIs or saved-copy identifiers. All PR #6 and PR #7 tests remain and still run.

Verification (GitHub Actions only — this sandbox has neither a JDK nor an Android SDK, so no
local Gradle run is claimed): Verify Android run
[41180358698](https://github.com/Sam-Program-362/Mosaic-app/actions/runs/41180358698) on
`64454cd288217058b793bec8e2ab95a730cb6f5e` — **SUCCESS: 249 tests, 0 failures, 0 errors,
0 skipped**, plus `lint`, `assembleDebug`, the packaged-APK privacy check, the Room
schema-consistency step and the production video-preload source guard. Debug APK artifact
`mosaic-debug-and-reports` ID `13034239424`. The documentation commit `3db9c2f` also passed the
same workflow (run
[41180600345](https://github.com/Sam-Program-362/Mosaic-app/actions/runs/41180600345), artifact
`13034597181`). The first run on this branch
([41180098291](https://github.com/Sam-Program-362/Mosaic-app/actions/runs/41180098291),
`de78ba3`) failed one new assertion that expected the rolled-back grant to remain in place; the
rollback deliberately releases it, so the assertion was corrected in `64454cd`.

Remaining real-device limitations (unchanged by CI): importing the user's actual release-exported
backup on a phone, MediaStore/`Download/Mosaic/Saved` visibility and repeated unique copies,
Android 8–9 SAF saved-folder grants, animated GIF playback in Home and the viewer, and migrating
over real installed data. CI executes no platform SAF picker, no file manager and no real decoder.

## Repository and release state (verified 2026-10-29)
- Checked-out branch: `main`, HEAD `99c64503b874250e6777ec968c2c47e629b1b39c`
  ("Merge pull request #5 from Sam-Program-362/release/v0.3.0-version-bump").
- Latest tag: **v0.3.0**. `app/build.gradle.kts` has versionName `0.3.0` / versionCode `4`.
- PR #4 (session branch → main) and PR #5 (version bump) are **merged**. PR #2 is the only open
  pull request and is an unrelated old revert; do not merge it as part of this work.
- The older v0.2.1 sections in the context files are historical. No release was created, tagged,
  or altered for the 2026-10-29 change.

## Current change — portable backup/restore (PR #7, 2026-10-30)
**Status: implemented on a feature branch, PR open, not merged.** Branch
`feature/local-library-backup-restore` (base `main` at
`99c64503b874250e6777ec968c2c47e629b1b39c`), PR #7:
https://github.com/Sam-Program-362/Mosaic-app/pull/7. **PR #6 is untouched, still open at
`7b252bc3ccacf16c116984a859332e20fa05abaa`, and waiting.** Nothing was merged, tagged, or released.

What it adds (purpose: let the user export from the current release, uninstall, install the
PR #6 debug APK, and restore — Android refuses to install a different signing certificate
over the release):
- **Portable backup format v1**, specified in `docs/backup-format-v1.md` as a stable public
  contract: a ZIP with exactly `manifest.json` (format id/version, creation time, app version,
  per-entry SHA-256 + size), `settings.json` (theme only), `folders.json` (generated portable
  `backupId` + label — never URIs), `communities.json` (stable id, name, createdAt,
  `memberBackupIds`). Room-schema independent (Room v3 here; PR #6's Room v4 must use the same
  contract). Unknown JSON fields ignored; unknown ZIP entries, missing entries, checksum or
  size mismatch, traversal, duplicates, oversized/over-count input, malformed JSON and
  unsupported `formatVersion` are all clear errors with **no partial restore**.
- **Code:** new `data/backup/` package (format constants, strict bounded JSON parser,
  models + validation, archive writer/reader, restore planner, `BackupStorage`,
  `BackupThemeStore`),  `data/repository/LibraryBackupRepository`, `LibraryRepository.restoreLibrary`
  (register → single `replaceLibraryConfiguration` Room transaction → revoke removed
  folders' grants only after commit, with failures reported as `unreleasedFolderLabels`
  → rescan; any pre-commit failure, cancellation included, rolls back newly acquired
  grants only and never touches pre-existing folders' permissions),
  `LibraryStore.replaceLibraryConfiguration`,
  `feature/settings/BackupViewModel` (Idle → Validating → Previewed → Reconnecting → Restoring → Finished)
  and `BackupRestoreSection` (Settings card + preview/reconnect/confirm/result/error dialogs via
  `CreateDocument`/`OpenDocument`/`FolderPickerContract`). Wired through `SettingsScreen` (new
  optional `backupSection` slot), `MosaicApp`, `MainActivity`, `MosaicApplication`. **No Room
  schema change, no new dependencies, no permissions, no network.**
- **Semantics:** restore replaces the folder/community configuration with the archive's contents
  after explicit confirmation; skipped or failed folders drop only their own memberships
  (reported, never redirected); theme is written after the Room commit and a theme failure is
  reported as `themeRestored = false` — Room and DataStore are **not** claimed atomic. Saved
  copies are excluded in both directions and never touched.
- **Tests/fixtures:** `BackupArchiveTest` (round trip, manifest/versioning, unknown fields,
  corruption, traversal/duplicate/unexpected entries, limits, stream closure) with committed
  fixtures `app/src/test/resources/backup/{mosaic-backup-v1,duplicate-entries}.zip`,
  `BackupRestorePlannerTest`, `LibraryBackupRepositoryTest` (in-memory Room: round trip,
  URI/Saved-data exclusion, untouched-before-validation, skip/failure mapping, repeated-import
  determinism, transaction rollback + grant cleanup, theme outcome), `BackupViewModelTest`,
  `BackupRestoreSectionUiTest`, and a `SettingsScreenUiTest` slot assertion. The existing
  `LibraryRepositoryTest.FakeStore` gained `replaceLibraryConfiguration`.
- **Verification:** GitHub Actions "Verify Android" run
  [41123210618](https://github.com/Sam-Program-362/Mosaic-app/actions/runs/41123210618) on
  `3053e2a` — **SUCCESS: 212 tests / 0 failures / 0 skipped (32 classes), lint, assembleDebug,
  APK privacy check, Room schema check, and the production source guard all passed.** Three
  earlier runs (41051985076, 41053383466, 41054812203) failed on compile/test defects fixed in
  `a81f374`, `5e82ebe`, `3053e2a`; only the final run is claimed. After the read-only review
  fixes (writer total-size limit, cancellation-safe registration rollback, release-after-commit
  with reported failures), run
  [41130169375](https://github.com/Sam-Program-362/Mosaic-app/actions/runs/41130169375) on
  `169d561aad7f2518a7d8737fe43ec5d1df5d9d46` passed with **218 tests / 0 failures / 0 skipped
  (32 classes)**, lint, `assembleDebug`, APK privacy, Room schema and source guard. Details are
  in `context/progress-tracker.md`. Local Gradle cannot run in this sandbox (no JDK/Android
  SDK), so CI is the verification. Device-level SAF picker behavior remains unverified until
  phone testing.
- Docs updated: `README.md`, new `docs/backup-format-v1.md`, `context/architecture.md`,
  `context/code-standards.md`, `context/ui-context.md`, `context/project-overview.md`;
  `context/progress-tracker.md` and this handoff record the change.

## Repository and PR state (verified 2026-10-19)
- Working branch: `arena/a0a1b100-mosaic-app`, HEAD
  `8c67941c8b288efa39e5dc5400f9ab946fe7882c` (later documentation commits
  may sit on top; check live). `main` is unchanged at `42ae102` and is a
  direct ancestor of the branch (fast-forward/merge-commit clean).
- **PR #4** (`arena/a0a1b100-mosaic-app` → `main`) is **OPEN, MERGEABLE**,
  all checks SUCCESS. When the user decides to merge, it must be a **merge
  commit, not squash**, so tagged release commits `af58727` (v0.2.0) and
  `e5ee42d` (v0.2.1) remain in `main` ancestry.
- The local checkout in the sandbox is stale at the `main` commit with the
  branch's work present as uncommitted changes. Preserve it untouched;
  branch pushes have been made through the GitHub API.
## Current change — mislabelled GIFs and shared-folder Saved copies
**Status: implemented and verified by CI on a feature branch; pull request open, not merged.**
Branch `fix/gif-detection-and-accessible-saved-copies` (`35bf7d9`, after `f00d267`), PR #6 against
`main`: https://github.com/Sam-Program-362/Mosaic-app/pull/6. Nothing was merged, tagged or released.

Part A — GIF detection:
- `DirectChildScanner` now accepts an optional `MediaHeaderProbe`. `SafFolderSource` supplies one
  that reads at most 6 bytes (`GIF_SIGNATURE_BYTE_COUNT`) inside the existing IO scan, always
  closes the stream, and returns null for empty/unreadable content.
- Only provider-declared static images are probed. Exact `GIF87a`/`GIF89a` bytes promote the item
  to `MediaKind.Gif` with effective MIME `image/gif`; the stored filename and title are unchanged.
  Probe failure preserves the provider classification and never turns a completed scan into a
  failed/empty one. Static WebP and unsupported formats are unchanged; search is metadata-only.
- Rendering needed no new path: detected GIFs use the existing animated Coil request and
  feed/viewer playback lifecycle.

Part B — Saved copies:
- `SavedCopyStorage`/`SavedCopyWriter` (`data/source/SavedCopyStorage.kt`) abstracts destinations.
  API 29+ writes to MediaStore Downloads at `Download/Mosaic/Saved` with an `IS_PENDING` row.
  API 26–28 uses a one-time SAF tree chosen in Settings (`SavedCopyFolderPreferences`), persists
  read/write access to that tree only, creates `Mosaic/Saved`, writes `<name>.partial`, and
  renames after completion.
- `SavedCopyRepository` writes one unique copy per Save, derives destination MIME/extension from
  detected content (GIF bytes named `.jpg` become `.gif`), keeps `fullName` for the Saved title,
  rejects empty/short reads, publishes only after a complete copy, and deletes the destination if
  metadata insertion fails. The Saved feed reads `destinationUri`; legacy rows (null URI) keep
  resolving to `filesDir/saved-media`.
- Room v4 adds nullable `saved_copies.destinationUri` via explicit `MIGRATION_3_4`.
- `SavedCopyFolderContract` asks the tree picker for read + write + persistable/prefix flags, so
  the persisted grant used on Android 8/9 is actually requested; the library picker stays
  read-only.

## Verification evidence and limits
- **Generated:** `app/schemas/dev.mosaic.app.data.local.LibraryDatabase/4.json`, produced by the
  real Room KSP processor (`room-compiler` 2.7.1 with KSP 2.1.20-1.0.32 — the versions pinned in
  `gradle/libs.versions.toml`) run over the module's `data/local` and `core/model` sources with
  `room.schemaLocation` pointed at `app/schemas`. It is not hand-written. Versus `3.json` it changes
  only the version, the identity hash and the nullable `destinationUri` TEXT column, which matches
  `ALTER TABLE saved_copies ADD COLUMN destinationUri TEXT`.
- **Run in CI and passing** on `35bf7d9` (run
  [40862412986](https://github.com/Sam-Program-362/Mosaic-app/actions/runs/40862412986)):
  `./gradlew testDebugUnitTest` (178 tests, 0 failures, 0 errors, 0 skipped), `./gradlew lint`,
  `./gradlew assembleDebug` (debug APK built), the packaged-APK privacy check (no INTERNET,
  network-state, broad storage/media or MANAGE_EXTERNAL_STORAGE permission) and the
  schema-consistency step (committed `4.json` identical to the Gradle/KSP export).
- **Not run locally:** the sandbox has no Android SDK — `:app:testDebugUnitTest` stops at `SDK
  location not found` — and, after plugin resolution, only ~500 MB of the 3.9 GB disk was free with
  2 GB RAM, so the SDK plus Compose/Room/media3/Robolectric artifacts do not fit.
- Fixed during this work: `SafFolderSourceTest` registered its provider with
  `ShadowContentResolver.registerProviderInternal` only, which serves shadowed `query` calls but not
  the real `ContentResolver.openInputStream` transport, so the GIF probe always returned null there.
  `35bf7d9` attaches the provider and asserts the header read happened.
- **Supplementary evidence only:** a targeted `kotlinc` compile of `DirectChildScanner`,
  `SafFolderSource`, `SavedCopyStorage`, `SavedCopyRepository` and the scanner test passed against
  the API 35 `android.jar`; 14 `DirectChildScannerTest` cases and 3 bounded-header/export checks
  passed on the JVM. This does not cover Compose, Room, Robolectric, lint, APK packaging, or the
  real platform storage providers.
- **Not verified on a device:** animated GIF playback in Home/community/viewer, saved-copy
  playback, MediaStore visibility in a file manager, SAF folder behavior on Android 8–9, storage
  full/partial-write behavior, and migration over real installed data.
- Automated evidence from earlier work (v0.3.0 and older CI runs) does **not** cover this change.
- The user's earlier device reports remain user-reported evidence, not agent-run tests.

## Required next checks
1. Review and merge PR #6 when the user asks; merging is not authorized by this document.
2. Device checks below are the remaining unknown; CI cannot cover them.
4. On a device: save a real GIF whose bytes start with `GIF87a`/`GIF89a` but whose name ends in
   `.jpg`; confirm animation in Home and the immersive viewer, confirm `Download/Mosaic/Saved`
   visibility in a file manager, repeat Save for distinct copies, delete the source and confirm
   the copy still plays, and repeat on an Android 8/9 device with the one-time folder choice.

## Known limitations / open items
- `4.json` was generated outside Gradle (same Room/KSP versions); if CI ever regenerates a
  different file, replace it with the Gradle-generated one rather than editing JSON by hand.
- The Saved-feed row remains visible if a copy is deleted externally; it shows the existing
  "missing or cannot be decoded" state rather than removing the record.
- No cleanup/removal action for saved copies is part of this change.
- Earlier unresolved product decisions remain: final branding/package identity, release SDK
  policy, and provisional custom-community artwork.
- `google_drive/` is user data and must not be staged or modified.
