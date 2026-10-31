# Fresh-session handoff — Mosaic (refreshed 2026-10-19, pre-merge)

This is the current-state handoff for a new agent session. It contains only
verified facts, clearly labelled user-reported results, and unresolved
items. **No merge, feature, refactor, tag, or release is authorized by this
document.** PR #4 is open and awaiting the user's merge decision; do not
merge it unless the user explicitly instructs you to (see
`docs/next-session-prompt.md` for the ready-to-paste startup prompt).

## Required reading order
1. `AGENTS.md`
2. `context/project-overview.md`
3. `context/architecture.md`
4. `context/code-standards.md`
5. `context/ai-workflow-rules.md`
6. `context/ui-context.md`
7. `context/progress-tracker.md`
8. This handoff. Then verify the live repository state (branch, HEAD,
   tags, releases, PR #4) instead of assuming the facts below are current.

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

## Releases (unchanged by all recent work)
- **v0.2.1 — current stable.** https://github.com/Sam-Program-362/Mosaic-app/releases/tag/v0.2.1
  Tag → `e5ee42d`; asset `Mosaic-v0.2.1-release.apk` (14,941,999 bytes,
  uploaded); APK SHA-256
  `ce659516918de0a4a0fa34f1dccd305fd44ae6b9fce7a9e5641f36659aaa56eb`;
  versionName 0.2.1 / versionCode 3; permanent certificate SHA-256
  `9de4becd705feef3ef536032ff3f6245a9e2bf7fc7380be9562aa5fc704c9d6a`.
- **v0.2.0** unchanged: tag → `af58727`, asset `Mosaic-release.apk`.
- The post-v0.2.1 improvements below have **no version bump, tag, or
  release**. If they are released later, that is a separate user decision.

## Shipped in v0.2.1 (stable)
Offline-first local media browsing: explicit SAF folder selection with
direct-child indexing, Home + folder communities, Phase 6 custom
communities, Random/Newest feeds with metadata-only search, full-width
intrinsic-ratio photos, frozen-snapshot immersive viewer, Saved copies,
safe folder removal, themes, and session-wide MP4 audio (fresh process
muted; one in-memory shared mute state; never persisted).

## Development improvement set (branch/PR #4 only)
A. **Launcher icon** — from the user-provided `mosaic_logo.svg` (kept at
   `app/icon-source/mosaic_logo.svg`): adaptive icon with artwork in the
   66dp safe zone plus legacy/round per-density rasters; manifest icon +
   roundIcon updated. The in-app logo/branding is unchanged.
B. **Viewer image/GIF zoom** — pinch 1×–4× with clamped panning and
   double-tap toggle, only in the fullscreen viewer; top-level feed and
   videos unchanged.
C. **Immersive chrome toggle** — confirmed single tap on image/GIF (or a
   video surface, outside its controls) hides/restores the viewer header,
   footer, and video controls; GIF animation and video playback continue;
   control taps never toggle; reopening starts visible. System bars follow
   the viewer policy (hidden in viewer, restored on exit).
D. **Temporary chronological browsing** — deliberate near-edge horizontal
   swipe on a still photo (just inside the system gesture inset) builds
   the snapshot's photo sequence (oldest→newest by `modifiedAtMillis`,
   deterministic tie-breaks, anchor by stable key) lazily off the UI
   thread and moves straight to the adjacent photo; interior swipes then
   navigate one at a time; vertical paging or exit resets; only the
   displayed destination is ever loaded; snapshot order untouched.
E. **Long-press title copy** — feed-card and viewer-header titles copy the
   exact displayed `mediaTitle` text with a "Title copied" toast.

## Verification evidence (kept distinct)
- Automated (GitHub Actions): Verify run `39157086439` at `3a59ea0` —
  **154 tests, 0 failures/errors/skips**, lint, debug APK, privacy checks;
  later documentation/candidate commits re-verified green.
- Signed candidate: established `phase6-signed-build.yml` run `39157385362`
  at `8c67941`; artifact `mosaic-phase6-signed-apk` ID `12147372598`
  (ZIP 14,378,055 bytes; GitHub ZIP SHA-256
  `6901c8e175dac7e6ab8067d92b6f160b3984aa28d06d348fdf8e1a4c0e8df8b7` — ZIP
  digest, not an APK digest). Application ID and version metadata
  unchanged (0.2.1 / code 3); signed with the permanent certificate by the
  established workflow (in-runner apksigner/certificate verification).
- **User-reported device testing:** the user installed that candidate and
  confirms testing of the improvement set (launcher, zoom, chrome toggle,
  chronological browsing, title copy) is complete and successful. This is
  user-reported evidence, not agent-run testing.
- Phase 7 itemized device acceptance remains **Scheduled / Deferred — not
  passed**, and is not reclassified by the above.

## Build, signing, and archive references (no credentials)
- CI: `.github/workflows/verify.yml` (tests/lint/APK/privacy; ignores
  `context/**` and `docs/**` on push). Signed candidates use the
  established `phase6-signed-build.yml` (push-triggered by changes to its
  own file on this branch) with permanent signing from GitHub secrets;
  never print secret values or substitute other signing.
- Drive archive: `Projects/Mosaic/`
  (https://drive.google.com/drive/folders/1kCMVh3-BKXxAHL0cNMs8V2XRRG_z7hfC)
  with `ARCHIVE-INDEX.md`; refreshed 2026-10-19 for this pre-merge state.
  The deferred Phase 7 document lives in its `Scheduled/` subfolder.

## Open decisions / known follow-ups (none authorized to start)
- Whether and when to merge PR #4 (user's decision; merge commit only).
- Whether to cut a new release (e.g., v0.3.0) for the improvement set
  afterwards, with a version bump — separate explicit authorization needed.
- Post-merge cleanup candidates: the one-time workflows
  (`phase6-signed-build.yml`, `publish-tested-release.yml`,
  `publish-v021-release.yml`, `signing-investigation.yml`) are inert on
  `main` (session-branch-scoped triggers) and can be removed in a small
  follow-up; the signed-build trigger path would need adjusting if signed
  candidates are ever built from `main`.
- Long-standing open decisions from the tracker: final branding/package
  identity, release SDK policy, provisional custom-community artwork.
