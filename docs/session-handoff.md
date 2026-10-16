# Fresh-session handoff — Mosaic (refreshed 2026-10-15, after v0.2.1)

This is the current-state handoff for a new agent session. It contains only
verified facts, clearly labelled user-reported results, and unresolved items.
**No new feature, refactor, merge, tag, or release is authorized by this
document.** The next agent reads this, summarizes its understanding, and
waits for the user's next development instructions (see
`docs/next-session-prompt.md` for the ready-to-paste startup prompt).

## Read first (required order)

1. `AGENTS.md`
2. `context/project-overview.md`
3. `context/architecture.md`
4. `context/code-standards.md`
5. `context/ai-workflow-rules.md`
6. `context/ui-context.md`
7. `context/progress-tracker.md`
8. This handoff (`docs/session-handoff.md`) — it supplements, never replaces
   or reorders, the list above.

## Current stable release (verify before quoting)

- **Mosaic v0.2.1** — https://github.com/Sam-Program-362/Mosaic-app/releases/tag/v0.2.1
  - Tag `v0.2.1` → source commit `e5ee42d15211b1da13321dfdf9bc977528060531`
  - Asset: `Mosaic-v0.2.1-release.apk`, 14,941,999 bytes
    (https://github.com/Sam-Program-362/Mosaic-app/releases/download/v0.2.1/Mosaic-v0.2.1-release.apk)
  - APK SHA-256: `ce659516918de0a4a0fa34f1dccd305fd44ae6b9fce7a9e5641f36659aaa56eb`
  - Signing certificate SHA-256:
    `9de4becd705feef3ef536032ff3f6245a9e2bf7fc7380be9562aa5fc704c9d6a`
    (the permanent release certificate; only this public fingerprint is ever
    recorded — never keys, passwords, or encoded keystores)
  - App: `dev.mosaic.app`, versionName 0.2.1, versionCode 3, minSdk 26,
    target/compile SDK 35. versionCode 3 > v0.2.0's 2 with the same
    certificate, so v0.2.1 updates v0.2.0 in place.
- **Previous stable release v0.2.0** — tag `v0.2.0` → commit
  `af58727f7d34b882e29b6e336041c4cc963f4c16`, asset `Mosaic-release.apk`.
  Published and unchanged; do not modify either release or its assets.

## Repository state (verify live; do not assume)

- Repository: `Sam-Program-362/Mosaic-app`. Development branch:
  `arena/a0a1b100-mosaic-app`, branched from `main` baseline `42ae102`
  (PR #3). **`main` does not contain Phase 6, the audio fix, or either
  release tag target** — Phase 6 and everything after it lives on the
  session branch; merging needs separate user approval.
- At this refresh, the branch HEAD was `93e2d87` (documentation record of
  the v0.2.1 publication). The commits made for this context refresh follow
  it and are documentation-only. **Distinguish three things: the live branch
  HEAD (check `git ls-remote`), the `v0.2.1` tag target `e5ee42d` (the
  released source), and documentation commits after the tag (they do not
  change the released app).**
- Relevant commits on the branch: `a4264dd` (preload removal + photo
  geometry), `69d34df` (final Phase 6 custom communities), `af58727`
  (v0.2.0 released source), `e8b017a` (session-wide MP4 audio), `cd6836d0`
  (version bump to 0.2.1/3), `e5ee42d` (v0.2.1 signed-build source = tag
  target).
- This sandbox may hold a stale local checkout with unrelated uncommitted
  changes matching older branch states. Preserve any uncommitted work; do
  not reset or overwrite it. The remote branch is authoritative for pushed
  state. The sandbox has no Java/Android SDK, so Gradle cannot run locally;
  hosted GitHub Actions is the build/test evidence.
- If a `google_drive/` directory ever appears in the checkout, it is user
  data: never stage, modify, or delete it.

## What the app is (shipped)

Mosaic (working name) is a private, offline-first Android app for browsing
user-selected local photos, GIFs, and MP4 videos in a compact
Reddit-inspired feed (layout reference only — no Reddit branding, accounts,
comments, or real votes). Shipped core:

- Native SAF folder picker, persisted read-only grants, **direct-child
  indexing only**; nested folders need separate selection.
- Home feed combining all selected folders; one automatic community per
  folder; **custom communities** (create/rename/browse/edit membership/
  delete; duplicate names allowed with stable-ID identity; a folder may join
  several communities; ≥1 folder at creation, empty allowed later;
  unavailable members retained; folder removal cascades only membership
  rows; deleting a community never touches folders, media, or Saved copies).
- Random/Newest ordering (stable within a session epoch), refresh,
  **metadata-only search** (filenames, folder labels, MIME/media kind; media
  bytes are never inspected).
- Feed photos at full width with decoded intrinsic aspect ratio (safe
  fallback until known, then locked); the immersive viewer pages vertically
  through a frozen snapshot of the originating feed and returns to the same
  scroll position.
- **MP4:** starts muted on every fresh process (see audio below); visible
  inactive videos show a local poster; players prepare only on activation;
  no production video preloading exists and must not be reintroduced;
  bounded static-photo prefetch only. Controls: play/pause, mute/unmute
  (session-wide), seek with elapsed/duration, double-tap ±10 s, tap to
  show/hide controls; poster until first rendered frame.
- **Save copy:** every tap creates another independent app-managed copy that
  keeps rendering after its source folder is removed; copies never point at
  the source URI. Saved-copy *removal* is not implemented (out of v1 scope).
- Confirmed **Remove from Mosaic** (drops index/community, attempts to
  release the saved read grant, never alters originals), searchable
  community directory, header/drawer navigation only (no bottom bar/rail),
  Settings with Light/Dark (default Dark), unavailable-folder retention and
  recovery. Room v3 (`folders`, `media`, `saved_copies`,
  `custom_communities`, `custom_community_members`) with explicit chained
  migrations `MIGRATION_1_2` + `MIGRATION_2_3`, no destructive fallback;
  generated schemas `1.json`–`3.json` are tracked and must come from
  builds, never be hand-authored.

## Session-wide MP4 audio (shipped in v0.2.1) — completed

- `core/playback/VideoAudioSession.kt` is the single in-memory mute
  preference for the application process: default muted, **never persisted**
  (no DataStore/SharedPreferences/Room/saved state), owned by
  `MosaicApplication` and threaded `MainActivity → MosaicApp →
  FeedScreen/MediaViewer → LocalMediaPreview/VideoPreview`.
- Every player applies the current preference (volume + Media3 audio-focus
  handling; muted players never request focus) at creation and again before
  `prepare()`; a session effect propagates later changes to existing
  players; the existing sound control toggles the shared state and shows
  "Unmute video"/"Mute video" accurately. Unmute/mute therefore applies
  across Home, folder and custom communities, Saved, and the viewer until
  the user changes it or the process ends; Activity recreation,
  recomposition, navigation, and background/foreground within the process
  preserve it. Visibility/lifecycle gating and player cleanup are unchanged.

## Verification evidence (keep these categories separate)

- **Automated (CI):** release-source Verify Android runs
  [38733046620](https://github.com/Sam-Program-362/Mosaic-app/actions/runs/38733046620)
  and [38733379491](https://github.com/Sam-Program-362/Mosaic-app/actions/runs/38733379491)
  — **116 tests, 0 failures/errors/skips** (103 pre-audio + 9
  `VideoAudioSessionTest` + 4 `VideoAudioSessionUiTest`), lint, debug APK,
  packaged-permission privacy checks. Phase 6 was verified by run
  `38678674913` at `69d34df` (103 tests). The v0.2.1 signed build (run
  `38733379486`) and publish verification (run `38733884128`) confirmed
  `apksigner verify`, the certificate above, SHA-256 above, and `aapt2`
  badging (versionCode 3 / versionName 0.2.1).
- **User-reported device results (not agent-run):** (a) the signed audio-fix
  candidate **passed physical-device testing perfectly** for the
  session-wide audio behaviour — the basis for authorizing v0.2.1;
  (b) earlier, on a debug build: "Scrolling is okay, didn't crash.
  Basically it's okey." — limited to acceptable scrolling and no crash in
  that unspecified test; (c) still earlier: folders survived restart, Home
  photos display, folder feeds isolate, videos play.
- **Not established by any evidence:** the historical crash's cause or
  resolution (no logcat/tombstone exists), itemized Phase 4/5/6 acceptance,
  on-device Room v2→v3 migration over prior debug data (impossible in place:
  debug and release certificates differ), provider-specific edge cases, or
  hardware codec behaviour beyond the user's reports. Never present CI or
  app-launch success as device acceptance.

## Build, CI, and signing workflows (no credentials here)

- `.github/workflows/verify.yml` — push/PR verification
  (`testDebugUnitTest lint assembleDebug` + privacy/schema/source guards);
  ignores `context/**`, `docs/**`, `README.md`, `AGENTS.md` changes.
- `.github/workflows/phase6-signed-build.yml` — the established signed
  build: `assembleRelease` signed with `r0adkll/sign-android-release@v1`
  from the four repository secrets (`KEYSTORE_BASE64`, `KEY_ALIAS`,
  `KEYSTORE_PASSWORD`, `KEY_PASSWORD` — names only; values live in GitHub
  and with the user's private keystore backup), artifact-only, verifies
  with `apksigner` in-runner. Used for the v0.2.0 build, the audio
  candidate, and the v0.2.1 build.
- `.github/workflows/build.yml` — tag-triggered (`v*`) rebuild-and-release
  workflow. **Warning:** it rebuilds from the tag; both published releases
  instead attached the pre-verified signed artifact via one-time publish
  workflows using `GITHUB_TOKEN` (whose tag does not re-trigger it):
  `publish-tested-release.yml` (v0.2.0) and `publish-v021-release.yml`
  (v0.2.1). Do not improvise a different release sequence without user
  approval.
- `.github/workflows/signing-investigation.yml` — completed one-time
  diagnosis (proved branch-scoped debug-key caches caused an install
  conflict); historical.
- Versioning convention: Gradle `versionName` in `app/build.gradle.kts`,
  tags `v<versionName>` (e.g. `v0.2.1`), release title `Mosaic v<version>`.
  Never reuse a tag; never publish without explicit user authorization.

## Archive

Project context is archived in Google Drive at `Projects/Mosaic/`
(https://drive.google.com/drive/folders/1kCMVh3-BKXxAHL0cNMs8V2XRRG_z7hfC),
indexed by `ARCHIVE-INDEX.md` there. The Phase 7 checklist is preserved at
`Scheduled/SCHEDULED - Phase 7 Device Acceptance Testing.md` in that
archive. The repository remains the live source of truth; the archive is a
dated snapshot. Never upload keystores, encoded signing material,
passwords, or tokens to Drive or anywhere else.

## Scheduled tests, limitations, and open decisions

- **Phase 7 device acceptance: Scheduled / Deferred (user, 2026-10-15).**
  Full test cases are in the Drive `Scheduled/` document and its sources
  (`docs/phase-6-device-check.md`, `docs/phase-4-device-check.md`,
  `docs/phase-5-device-check.md`); the audio-specific sequence is in
  `docs/session-audio-device-check.md` (device-verified per user report
  above). No Phase 7 item is passed; do not resume the checklist
  automatically.
- Open decisions: final branding/trademark and package identity; release
  SDK policy; merging the session branch to `main`; provisional
  custom-community artwork (first-member artwork else initials, no editor);
  Dark as reference-inspired default theme.
- Explicitly out of scope unless the user approves otherwise: Saved-copy
  removal, comments/real votes, accounts, cloud sync, analytics, runtime
  networking, broad storage access, additional media formats, video
  preloading, source-media writes of any kind.

## Approval boundary and next step

The v0.2.1 release completes the last authorized task. **Next immediate
action: none.** The next agent should (1) read the files in the order
above, (2) inspect the live repository/GitHub state, (3) briefly summarize
the shipped app, current release, evidence status, and open items, and
(4) wait for the user to describe the next development idea, evaluating it
with the user before proposing any implementation. Do not treat any
historical phase, the deferred checklist, or this handoff as approval to
start work.
