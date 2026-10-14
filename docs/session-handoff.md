# Fresh-session handoff — 2026-10-15

This is the current-state handoff, not authorization to implement the next phase. The work in this
handoff is documentation-only; no application code, build behavior, merge, or release is in scope.

## One-minute status

- **Current development branch:** `arena/b0f3e3cf-mosaic-app`. Before this documentation update,
  local and remote branch head was `e8e2ad5` (`Keep verification README environment-neutral`). The
  latest application-source commit is `a4264dd9c51181eff91de436fd7d7651cd48acea`; later commits
  on the branch are documentation-only.

- **Latest source verification:** GitHub Actions run
  [38624705844](https://github.com/Sam-Program-362/Mosaic-app/actions/runs/38624705844) completed
  successfully against `a4264dd`: 96 tests, 0 failures/errors/skips; unit tests, lint, debug APK
  build, packaged-permission privacy checks, Room schema consistency, and the production
  video-preloader source guard all passed.

- **APK artifact:** [mosaic-debug-and-reports, artifact
  11923577241](https://github.com/Sam-Program-362/Mosaic-app/actions/runs/38624705844/artifacts/11923577241),
  containing the debug APK, `app-debug.sha256`, and reports. GitHub reports it unexpired; its
  recorded expiration is 2027-01-12. The checksum value has not been independently retrieved or
  compared here.

- **Latest phone evidence:** user-reported on 2026-10-15:
  `"Scrolling is okay, didn't crash. Basically it's okey."` Treat this only as acceptable scrolling
  and no crash during that user test. The user considers that result okay enough to proceed. It
  neither identifies the exact APK/test sequence nor proves the earlier crash was fixed.

- **Local build limitation:** this checkout has no `java` executable and `JAVA_HOME` is unset. Do
  not report local Gradle verification as run; the hosted CI result above is the available build
  evidence.

- **Untracked user data:** `google_drive/` is present in the working tree. Do not modify or stage it.

## Product, architecture, safety, and provisional decisions

Mosaic is the working name of a private, offline-first Android app for browsing photos, animated
GIFs, and MP4s from folders the user explicitly selects. The compact Reddit-inspired feed is a
layout reference only, not permission to copy Reddit branding or add social behavior.

**Non-negotiable safety boundaries:**

- Access media only through explicit, read-only Storage Access Framework folder grants. Index direct
  children only; a nested folder must be separately selected.
- Never write, delete, move, rename, or alter source media. Folder removal affects Mosaic's grant/index
  only. Save creates a new private app-managed copy.
- No runtime network/backend, internet permission, accounts, cloud sync, analytics, crash uploads,
  ads, comments, interactive votes, or unrestricted storage scanning. Decorative counts are not
  actions. No permanent background scanner.
- Do not log local paths or content URIs. Keep players and provider/file I/O in appropriate
  lifecycle/background scopes.

**Technical baseline:** single-module native Android app, Kotlin 2.1.20, Jetpack Compose/Material 3,
AGP 8.9.2, Gradle 8.11.1, JDK 17, Room 2.7.1/KSP, Coil 3.1.0, and Media3 1.6.1. The
package/namespace/application ID is `dev.mosaic.app`, `minSdk` is 26, and compile/target SDK are 35.
These are pinned development choices, not claims that versions are latest.

**Still provisional or unresolved:** public product name/trademark and package identity; final
release target/minimum SDK policy; production release/signing policy; custom-community naming,
duplicate-label, rename and membership rules; and custom-community artwork. Dark is the current
reference-inspired default with explicit Light/Dark settings (no system-theme mode); treat final
visual identity as provisional. Routine technical choices were delegated, but that did not finalize
public identity or release decisions.

## Current implementation and source map

- `MainActivity.kt`, `ui/MosaicApp.kt`, and `ui/theme/MosaicTheme.kt`: Compose app shell, persisted
  theme, Home/Saved/folder/community-directory/Settings navigation through the header and drawer
  (no bottom navigation or rail).
- `data/source/`: native folder picker, retained read grant, direct-child scanner and provider/MIME
  classification. `data/repository/` serializes folder registration, scan/reconciliation and removal.
  Successful scans reconcile; failed/incomplete scans do not become empty-folder deletions.
- `data/local/`: Room database version 2 for selected folders, indexed source-media metadata and
  app-managed Saved-copy metadata. The explicit v1→v2 migration and generated schemas are retained.
- `feature/folders/`: selected-folder management, automatic one-folder communities, searchable
  community directory, availability/retry states. There are **no custom-community entities or
  membership rows** today; the directory maps selected folders to automatic communities.
- `feature/feed/FeedSession.kt` and `FeedScreen.kt`: Home/community/Saved feed ordering,
  metadata-only search, feed cards and stable feed sessions. Random order is stable within its epoch;
  Newest puts unknown timestamps last with deterministic ties. Viewer receives a frozen feed snapshot.
- `MediaGeometry.kt`, `LocalMediaPreview.kt`, `MediaImageRequests.kt`, and
  `NearbyPhotoPrefetcher.kt`: photos use available media width and decoded/oriented intrinsic ratio
  for natural height. Unknown/invalid dimensions use a temporary safe fallback; the first valid ratio
  locks the composed photo's height. Nearby prefetch is local and static-photo-only (current item
  plus nearest static photos within the bounded window). Video/GIF retain their separate reserved-
  frame policy.
- The production video-preload manager, neighboring-video buffering, offscreen video-frame
  prefetch, speculative offscreen-video poster decoding, and the diagnostic Gradle toggle are
  removed. A visible inactive video may show its local poster; a Media3 player prepares only on
  activation. At most one inline video player is active; playback is muted by default and
  composition/lifecycle/visibility scoped. Existing controls, viewer behavior, TextureView/poster
  overlay through the first rendered frame, and failure UI remain.
- `feature/feed/MediaViewer.kt`: vertical pager over the exact opening feed snapshot, with viewer-only
  system-bar hiding/restoration and the feed kept underneath without playback.
  `SavedCopyRepository.kt` reads the selected source on I/O into a unique `.partial` file in
  app-private storage, completes/renames before inserting Room metadata, cleans ordinary failures,
  and stores no original source URI. Every Save action is a separate copy.
- `feature/settings/SettingsScreen.kt`: local theme and library settings. Search is restricted to
  already-indexed metadata; it does not scan file content.

No code changes are part of this handoff.

## User decisions and phone evidence

1. The user superseded the earlier disabled-by-default/optional-diagnostic video-preload proposal.
   Keep production video preloading removed; do not replace it with another speculative video
   mechanism. Static-photo prefetch, visible video posters, and player preparation on activation
   remain.
2. Feed photos must use their decoded/oriented intrinsic dimensions at full available width: no
   distortion, crop-to-video-frame, shared fixed photo height, or arbitrary photo ratio/height clamp.
   Handle invalid/late dimensions safely and keep established height stable. Preserve the separate
   video/GIF frame policy and viewer/playback behavior.
3. Earlier user-reported comparison: preloading enabled felt sticky and the app crashed; with it
   disabled, photos felt smoother and video loaded fast enough; a separate shift on video load was
   reported. This is user evidence, not log-correlated causality or proof that removing preloads
   fixed the crash.
4. Latest report, dated 2026-10-15, exactly:
   `"Scrolling is okay, didn't crash. Basically it's okey."` This is a **user observation**, not an
   agent-run test. It supports only that scrolling was acceptable and no crash occurred during that
   test, and that the user is comfortable proceeding. No exact artifact/commit, device/Android/
   refresh details, orientation/content sequence, duration/repetition count, offsets, logcat,
   PID/ANR evidence, or crash trace accompanied the report. Do not infer those details, a test of
   every acceptance item, the cause of the historical crash, or crash resolution.
5. Historical phone reports also say selected folders survived restart, Home displayed photos,
   folder feeds isolated content, and videos played. Those reports do not establish GIF,
   audio-focus/mute, background/offscreen, viewer/removal, Saved-copy, or provider-edge behavior.

The right status is **limited user-reported scroll feedback received; broad device acceptance
remains open**, not “no phone testing ever happened” and not “device acceptance complete.” CI is not
device testing.

## Repository, commits, CI, and artifact

At the start of this handoff the local branch had been aligned with the existing remote session
branch at `e8e2ad5`; the worktree was otherwise clean except `?? google_drive/`. Recent history
relevant to current source:

- `680c54c` — removed the video-preload production path and implemented natural intrinsic photo
  geometry.
- `a4264dd` — fixed aspect-ratio cache misses so unresolved lookups do not evict useful dimensions;
  this is the latest app-source commit.
- `0f5fd78` — documented the verified geometry change and APK.
- `e8e2ad5` — made README verification wording environment-neutral.

CI run [38624705844](https://github.com/Sam-Program-362/Mosaic-app/actions/runs/38624705844) is a
completed successful push workflow for branch `arena/b0f3e3cf-mosaic-app`, head SHA
`a4264dd9c51181eff91de436fd7d7651cd48acea`. Its one verification job passed 96 tests
(0 failures/errors/skips), `testDebugUnitTest`, lint, `assembleDebug`, APK permission privacy, Room
schema consistency, and the source guard. The artifact page lists artifact `11923577241` as
unexpired, about 21.2 MB, expiring 2027-01-12. Use the artifact page to retrieve it; the sandbox
could not fetch GitHub's artifact-storage host. Do not treat older paired preload-mode APKs or
earlier commits as the current acceptance build.

The local Gradle commands were blocked before Gradle startup because Java is unavailable (`java`
not found; `JAVA_HOME` unset). No agent-run emulator/physical-device test, crash reproduction,
device log, or crash-resolution evidence exists. No merge to `main` or release was made or
authorized.

## Remaining first-release work, limitations, and tests

**Feature gap:** custom communities are listed in the overview requirements but not implemented.
They should combine only explicitly assigned, already-selected folders and remain local/offline.
Saved-copy removal is explicitly out of scope for v1. Comments, votes, online/community services,
additional media formats, and custom artwork controls are not approved scope. Branding/package/SDK/
release choices still need product approval before production release.

**Known limitations/evidence gaps:**

- The latest user report does not name the APK or describe its exact scroll sequence.
  Portrait/landscape/square/panoramic fit, delayed-dimension locking, and height stability have no
  itemized device results.
- No same-item before/after offsets or repeatable device measurements exist for a feed jump during
  video activation/loading/size/first-frame events. The historically reported shift remains
  unverified against the latest code.
- The older crash has no supplied logcat, native tombstone, bugreport, PID/ANR trace, or identified
  cause. No crash-resolution claim is justified.
- Hardware decoding/playback, actual rendered first frames, visible/offscreen poster behavior, fast
  mixed-orientation flings and repeated-scroll stress remain distinct from CI unit/Compose/
  Robolectric checks. CI had no representative hardware MP4/device run.
- Device checks remain open for GIF animation, mute/audio-focus and background/offscreen stop
  behavior; viewer open/page/back/system-bar/scroll restoration; exact folder removal grant release
  and source immutability; repeated Save requests, restart, source deletion/lost access, and
  copy-failure cleanup; and refreshed theme/search/Settings/community-directory behavior.
- CI covers code-level scanner/repository/Room and migration cases, feed ordering/search/visibility/
  geometry, photo and video fallback semantics, playback state/control helpers, viewer/navigation,
  Saved-copy logic and Compose/Robolectric UI. It does not establish provider-specific picker/grant
  behavior, codecs, physical gestures, or all accessibility/display-size behavior.

Keep a short, scoped device follow-up available while proceeding: test the current artifact on a
named device/build, record APK checksum and device/Android/refresh details, inspect photo dimensions
and position stability around video activation, and repeat the prior mixed portrait/landscape scroll
sequence if available. If the crash recurs, capture full logcat and PID/ANR/tombstone/bugreport
evidence before attributing cause. If it does not recur, report only that the tested sequence did
not reproduce it.

## Recommended next engineering phase (proposal only)

The single user report is enough to proceed with planning; it is not enough to declare all media
acceptance complete. The highest-value remaining explicit first-release feature is **Phase 6: local
custom communities**, rather than another speculative player/preload rewrite. This is a
recommendation, not user approval or implementation. Keep the device follow-up above as a separate
acceptance task.

**Proposed scope:** create, list, and browse a custom community whose members are chosen from
folders already selected in Mosaic; show the union of media belonging to those explicit folder IDs
while preserving the existing feed, search, sort, snapshot, availability, and safety rules. Keep the
automatic per-folder communities. No new storage permission or picker flow, cloud/backend,
original-media operation, real votes/comments, or speculative artwork controls.

**Dependencies and policy decisions to settle first:**

- Decide community naming, duplicate labels, rename behavior, whether a selected folder may belong
  to multiple custom communities, whether an empty membership is allowed, and what happens when a
  member folder becomes unavailable or is removed. Keep artwork policy provisional unless approved.
- Add stable custom-community and membership data to the current Room v2 model, with an explicit
  v2→v3 migration chained after v1→v2, generated/committed KSP schema, and no destructive fallback.
  Represent membership by stable selected-folder IDs, never copied paths or filenames.
- Extend the repository/DAO with available-member feed queries and transactional membership
  maintenance. Removing a folder must not leave unsafe stale media exposure or alter the source;
  unavailable membership semantics should follow the decision above.
- Integrate creation/management and navigation into the current searchable directory/header/drawer
  without introducing a second navigation system. Preserve the current frozen feed snapshot and
  stable identity semantics.

**Proposed acceptance criteria:**

1. Create a custom community from two explicitly selected folders; it persists across process
   restart and displays only those folders' eligible indexed media. Existing Home and automatic
   folder communities remain unchanged and isolated.
2. Search, Random/Newest ordering, viewer snapshot/back restoration and unavailable/empty states work
   within the custom community. Membership edits update predictably without accidental reorder or
   cross-community leakage.
3. Removing a selected folder updates memberships according to the approved policy; original files
   remain present and untouched. No unselected folder or media becomes visible.
4. Works offline and adds no runtime network/broad-storage permission. Names, empty states, dialog
   cancellation and controls remain accessible at large font/touch-target sizes.

**Proposed tests:** pure model/query tests for stable membership, duplicate IDs, search/order and
empty states; DAO/repository tests for union/isolation, unavailable members, membership add/remove,
folder cleanup and transactions; v2→v3 migration test proving existing folder/media/Saved rows
survive and exact KSP schema consistency; Compose tests for create/cancel/manage/directory/drawer/feed
flows; then the full CI suite, lint, debug build and packaged-permission/schema guards. On-device
acceptance should include create/select/restart, folder unavailable/removal, offline browsing and
source-file integrity.

## Exact startup instructions for a new agent

1. Work in `/home/user/Mosaic-app` on the fixed branch `arena/b0f3e3cf-mosaic-app`. Start with:
   ```sh
   cd /home/user/Mosaic-app
   git status --short
   git branch --show-current
   git log -6 --oneline --decorate
   ```
   Do not stage or modify `google_drive/`; it is user data.
2. Read `AGENTS.md`, then the six context files **in exactly the order listed there**: project
   overview, architecture, code standards, AI workflow rules, UI context, progress tracker. After
   that ordered read, read this handoff. The handoff supplements but does not replace those
   requirements.
3. Recheck the live branch/CI state before changing anything; distinguish source commits from
   documentation commits, CI from user reports, and current results from historical records. Do not
   assume the phone report identifies a build or resolves the older crash.
4. For Android verification, use JDK 17 and Android SDK platform/build tools 35. Set `ANDROID_HOME`
   (or an untracked `local.properties` with `sdk.dir=...`) and run:
   ```sh
   ./gradlew testDebugUnitTest lint assembleDebug
   ```
   Debug output is `app/build/outputs/apk/debug/app-debug.apk`. In this checkout Java is currently
   unavailable, so those commands cannot start; use CI when authorized rather than claiming local
   success.
5. Do not implement the proposed custom-community phase as part of this handoff. Wait for a task
   explicitly authorizing implementation and resolve the policy decisions above before silently
   choosing them. No merge or release is authorized.
