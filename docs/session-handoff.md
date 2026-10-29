# Fresh-session handoff — Mosaic (refreshed 2026-10-29)

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

## Repository and release state (verified 2026-10-29)
- Checked-out branch: `main`, HEAD `99c64503b874250e6777ec968c2c47e629b1b39c`
  ("Merge pull request #5 from Sam-Program-362/release/v0.3.0-version-bump").
- Latest tag: **v0.3.0**. `app/build.gradle.kts` has versionName `0.3.0` / versionCode `4`.
- PR #4 (session branch → main) and PR #5 (version bump) are **merged**. PR #2 is the only open
  pull request and is an unrelated old revert; do not merge it as part of this work.
- The older v0.2.1 sections in the context files are historical. No release was created, tagged,
  or altered for the 2026-10-29 change.

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
