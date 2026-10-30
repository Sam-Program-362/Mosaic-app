# Architecture

## Adopted development baseline
Native single-module Android application, Kotlin 2.1.20, Compose/Material 3
(BOM 2025.04.01), AGP 8.9.2, Gradle 8.11.1, JDK 17. Application/namespace
`dev.mosaic.app`, min API 26, target/compile API 35. These versions are a pinned
compatible baseline, not an assertion of latest versions. Identity and SDK targets
are provisional until product/release approval.

Implemented layout:
- `app/src/main/java/dev/mosaic/app/MainActivity.kt`: lifecycle-aware preference
  collection and activity/system-bar setup.
- `ui/MosaicApp.kt`: typed screen shell, top header and drawer/menu navigation only
  (no bottom bar or navigation rail), searchable communities directory, local search,
  empty states and Settings.
- `ui/theme/`: centralized semantic colors and Material theme mapping.
- `data/preferences/`: application-context Preferences DataStore; Light/Dark theme
  preference persists. No system theme mode. Fresh/unknown values default to Dark
  to follow the inspected reference. This adaptation passed CI run 38543107645
  (67 tests, lint, schema consistency, packaged privacy and debug APK); device acceptance
  for the refreshed screens remains pending.
- Version catalog centralizes dependencies. Gradle wrapper checked in; Material icon
  extended artifact is included for the new local playback/theme controls.

The shell's destination survives activity recreation through `rememberSaveable`.
Back closes the drawer or returns non-Home screens to Home; Home delegates to Android.
Room stores folder/media/saved-copy metadata; copied bytes stay in Mosaic's private
files directory. Media players are owned by visible compositions, not by the ViewModel
or application singleton.
Backup is disabled to avoid implicit cloud transfer of future local media metadata/copies.

## 2026-10-30 — Portable backup and restore (format v1)
Settings gains **Backup & Restore**: an offline, user-controlled export/import of the
library configuration (folder labels + portable IDs, custom communities + portable
memberships, Light/Dark theme) as a checksummed ZIP defined by
`docs/backup-format-v1.md`. The format is independent of the Room schema (v3 and the
future PR #6 v4 use the same contract) and deliberately excludes Saved copies, media
index rows, raw SAF/provider URIs, and persisted URI permissions.
- `data/backup/`: `BackupFormat` (constants/limits/typed errors), `BackupJson` (minimal
  strict bounded parser, unknown fields ignored), `BackupModels` (manifest/settings/
  folders/communities parse + serialize + semantic validation), `BackupArchiveWriter`
  (streams entries, computes SHA-256/size, writes manifest last), `BackupArchiveReader`
  (allowlisted entry names only, never extracts to disk, bounded entry count/size/total,
  verifies manifest checksums before parsing JSON, rejects traversal/duplicates/
  unexpected entries/unsupported versions), `BackupRestorePlanner` (pure Stage-2 mapping:
  portable ID → newly selected tree URI, read-grant and duplicate-target validation),
  `BackupStorage` (ContentResolver byte boundary with a hard read cap) and
  `BackupThemeStore` (the single persisted preference behind a boundary).
- `data/repository/LibraryBackupRepository` owns export (folder/community/theme snapshot →
  ZIP → SAF destination, success only after the stream closes), Stage-1 read/validate, and
  `restore()`: plan → `LibraryRepository.restoreLibrary` → theme. Room commits first in one
  transaction; a later theme failure is reported as `themeRestored = false` — never claimed
  atomic. Saved copies are never read or written.
- `LibraryRepository.restoreLibrary` runs under the existing operations mutex: register
  each reconnected tree (failures are skipped and reported, never redirected), release
  grants of folders being removed first (same ordering as `removeFolder`), one
  `LibraryStore.replaceLibraryConfiguration` transaction (delete communities, delete
  folders absent from the restore, upsert restored folders as Unavailable, insert
  communities with members filtered to actually-registered folders), then rescan each
  reconnected folder (failed scans keep Unavailable and are reported). On failure before
  the transaction commits, newly taken grants are released again and the library is
  untouched.
- `feature/settings/BackupViewModel` runs the stage machine
  (Idle → Validating → Previewed → Reconnecting → Restoring → Finished) with explicit
  cancellation/error states; `BackupRestoreSection` renders the Settings card (content,
  Saved-exclusion and reconnection notices) plus preview/reconnect/confirm/result/error
  dialogs through the existing SAF `CreateDocument`/`OpenDocument`/`FolderPickerContract`
  launchers. Preview and reconnection never mutate data; only the explicit final
  confirmation commits.

## Phase 2 implementation
- `core/model/`: immutable metadata/selection models and typed scan outcomes.
- `data/source/`: SAF picker/grant retention, direct-child reader/scanner and MIME typing.
- `data/local/`: Room 2.7.1 entities/DAO/database/store, schema export via KSP.
- `data/repository/`: injectable store/source boundary, serialized selection/scanning.
- `feature/folders/`: lifecycle-scoped ViewModel and folder/index-summary UI.
- `MosaicApplication`: application-context database/repository composition root.
Folder identity is the selected tree URI; media identity is `(folderId, documentId)`.
Available/Unavailable status and failure reason persist. Successful scans replace
one folder's media and status atomically; failures update status only. Room upsert
avoids deleting dependent rows during reselection. No destructive migration fallback.
Persist only READ permission from actual result flags; nonpersistent access is clearly
labelled. Same-tree reselection restores access without duplicate identity. Cold-start,
warm-resume and explicit refresh scans are serialized; no background service/WorkManager.
A warm resume retains the current and bounded nearby static-photo rows and feed epoch/order
while reconciling other entries. A cold process start and explicit refresh fully reconcile.
Null/missing-required-column/loading/error cursors never authorize deletion.

## Phase 3 implementation
`feature/feed/` owns pure feed ordering/count/title/artwork/visibility rules, lazy
post UI, local image/GIF/video-frame previews and composition-scoped MP4 players.
Coil 3.1.0 uses Compose/GIF/video-frame modules without a network fetcher; an
application-context ImageLoader provides bounded shared caching. Static photo/avatar
requests explicitly select a static decoder; only GIF previews opt into animation.
Feed photos fill the available media width and derive height from the decoded/oriented
intrinsic image ratio; a temporary safe fallback covers missing/invalid dimensions, and
the first valid ratio locks the composed photo's height. `ContentScale.Fit` preserves the
whole image without distortion/crop. The video/GIF reserved-frame policy remains separate.
Nearby retention/prefetch selects static photos only. There is no production video-preload
manager, neighboring-video buffering, offscreen video-frame prefetch, or speculative
offscreen-video poster decoding. A local video poster is requested when its card/page is
visible; the active visible player prepares on activation. Media3 creates at most one inline
player, muted by default and disposed offscreen/inactive. The active player retains its
play/pause, mute, seek/time/duration, viewer double-tap ±10-second seek, and tap-to-hide/show
controls. Its poster remains over the `TextureView` surface until the first rendered frame.
Room joined projections filter Available status before UI; no schema version change.
ViewModel combines available metadata + scope + sort + session epoch off main thread.
Random rank/count seed depends on epoch + length-prefixed folder/document identity;
unchanged metadata/ordinary recomposition cannot reshuffle it. Newest unknown dates
sort last, ties by case-insensitive name then identity. Artwork chooses first static
image case-insensitively per folder, never a GIF/video. Viewport geometry uses the
preview's bounds, not merely card visibility; menus/drawer/background disable playback.
All functional playback controls are distinct from decorative, noninteractive counts.
Phase 5 adds an enabled Save copy action in feed cards and viewer chrome.

## 2026-10-15 focused feed-media geometry and preload removal — CI verified; limited user phone report, targeted checks open
The former experimental build toggle and video-preload manager/lifecycle are deleted, not kept
as an opt-in. Nearby static-photo prefetch remains active; inactive video posters are visible-
only and player preparation remains activation-only. Feed photo dimensions are independent of
video layout: natural height is available media width divided by the first valid decoded intrinsic
ratio, with a square safe placeholder only until dimensions are known and a stable established
height thereafter. Invalid ratios are ignored. Stable LazyColumn media keys remain unchanged, and
media preparation/first-frame callbacks do not call the feed scroll API. The explicit
session/sort/search reset remains the only feed programmatic scroll. User-reported prior crash/
stickiness is not treated as resolved.

Tests cover image orientation/ratios and delayed dimensions, natural photo heights, full-width
Compose measurement, poster visibility, static-photo-only prefetch, reserved video-frame and
first-frame fallback behavior, playback controls, and photo/scroll-position stability. Code commit
`a4264dd` passed CI run
[38624705844](https://github.com/Sam-Program-362/Mosaic-app/actions/runs/38624705844): 96 tests
(0 failures/errors/skips), lint, debug APK build, packaged-permission privacy checks, Room schema
consistency, and the production source guard. Artifact `mosaic-debug-and-reports` ID `11923577241`
contains the APK/checksum. The user subsequently reported on 2026-10-15:
`"Scrolling is okay, didn't crash. Basically it's okey."` This is limited user evidence of acceptable
scrolling and no crash during that test; the user considers it okay enough to proceed. The
APK/device/sequence are not identified. Targeted geometry, video-position, repeat-scroll and broader
feature acceptance remain open. This does not prove the earlier crash's cause or resolution. See
the [fresh-session handoff](../docs/session-handoff.md) for limits and next steps.

## Phase 4 implementation
`feature/feed/MediaViewer.kt` receives a copied `FeedSnapshot` and tapped item index;
vertical paging therefore follows the exact opening order even if the live Room feed
later changes. The originating `FeedScreen` stays composed underneath, with semantics
hidden and playback disabled, preserving feed scroll position on Back. Only the current
RESUMED viewer page activates GIF/video playback; inactive pages do not retain players.
Viewer-only system-bar hiding records/restores previous visibility. The viewer session
is transient UI state, not persistent media/library state. The current Phase 4 follow-up
uses `AnimatedVisibility` with a restrained scale/fade enter and reverse exit. A retained
snapshot keeps the outgoing viewer composed through close; during both transitions the
feed stays hidden from semantics, muted from playback, and scroll-stable. CI run
38511038772 passed 60 tests, lint, schema, packaged privacy and debug build with the final
motion implementation/test. Earlier test-timing failures are resolved. Phone verification
remains open.

Manage folders now offers an explicit confirmation before removing a selection.
Repository operations serialize removal with refresh/add. `SafFolderSource` attempts
to release only Mosaic's persisted read grant; Room deletes the folder row and its
media metadata through the existing cascade. If grant release cannot be confirmed,
metadata is still removed and UI warns the user. No scan is triggered and no original
file is written, copied, moved, renamed or deleted. No schema/table version changed.

Phase 4 CI passed 53 tests, lint, Room schema consistency, packaged privacy checks
and debug APK. Physical viewer/paging and permission-release acceptance is pending.

## Phase 5 implemented — Saved copies
`SavedCopyRepository` opens the selected source URI read-only and streams it on
`Dispatchers.IO` into a unique UUID-named file under the app-private `filesDir/saved-media`
directory. It first writes a `.partial` file, rejects empty copies, flushes and renames
before inserting a Room v2 `saved_copies` row. Ordinary failures remove incomplete or
unrecorded destination files; metadata is inserted only for a completed copy. The row
stores the display name, MIME type, kind, save time, size and private filename, not the
source URI. Saved feed items are reconstructed with a stable app-managed file URI, so
removing a source folder or losing its SAF grant does not affect the copy. Every Save
invocation produces another UUID/file; no toggle or deduplication. Feed/viewer UI expose
Save, and Saved uses the same local media card/viewer at newest-save-first order.
Removal of saved copies and custom communities remain out of scope.

Room database version 2 adds only the Saved table/index with an explicit v1→v2
migration; existing folder/media data is retained. CI run 38469093930 passed 59 tests,
lint, tracked-schema consistency, APK privacy and debug build. Its exact KSP-generated
v2 schema was decoded from chunked CI annotations into source; no schema JSON was
hand-authored. Viewer motion passed CI run 38511038772. The reference-inspired UI follow-up
passed run 38543107645 (67 tests, lint, schema, APK privacy and debug build). Phone
acceptance for viewer, permissions, Saved copies, theme, search and refreshed controls
remains pending.

## Historical video and warm-resume follow-up — later superseded
The initial `nearbyMediaWindow` selected a bounded neighboring photo/video set; Coil
prefetched local images/video frames and a composition-scoped Media3 `DefaultPreloadManager`
buffered one second for neighboring video sources (not extra playing players). Inactive
MP4 cards requested a local frame thumbnail. `VideoPreview` retained position/play-when-ready
state across lifecycle deactivation and provided the playback/seek controls described above.

`ResumeRefreshPolicy` treats a new ViewModel/process as cold: full scan and a new feed
epoch. Later ON_RESUME scans keep the existing feed epoch/order and pass current plus
preloaded `MediaKey`s to the repository. A successful Room transaction retains those
existing rows (including their old metadata) while reconciling all other scanned rows;
failed scans still retain the whole prior index. No schema version change, background
service, network access or source writes were introduced. The prior implementation passed
CI run 38556928293 at `a11fde2` (74 tests, lint, schema consistency, packaged privacy and
debug build; artifact `11890760322`). That run predates the correctness/performance pass below.

## Historical video transition/performance pass — CI verified, device acceptance pending
At that checkpoint, the source chose the dominant visible media item by viewport overlap across
photos, GIFs and videos. Playback requires that all-media anchor to be animated, then
selects the most-visible animation that meets the 50% visible-fraction threshold; this lets
an eligible landscape video take over when a taller portrait edge is the anchor but falls
below threshold. A photo anchor still suppresses playback. Candidate ranking/current indices
use actual positions in the current Home or frozen viewer order. The bounded window considers
the nearest photo and nearest video independently on each side (max five including the
center), so a photo/GIF cannot hide a nearby video. Manager entries are removed/re-ranked
only after their active player releases its source; stale positions are reconciled against
the latest snapshot. The existing one-second Media3 `LOADED_FOR_DURATION_MS` target remains
unchanged pending same-device measurements.

A cached video poster overlays player preparation until Media3's rendered-first-frame event;
a `TextureView` surface keeps that Compose fallback composited over the player. Player/preload
errors end the spinner and show an actionable local-file/format message.
Debug-only `MosaicMedia` diagnostics log anchor/candidate positions, submission, observable
I/O start, preload completion/error, playback handoff, buffering/ready, activation-to-frame
latency and player errors without local paths or per-frame output. A build-time diagnostic
property, `-PmosaicVideoPreloading=false`, bypasses the Media3 nearby-video preloader, nearby
Coil video-frame prefetch and video-card poster decodes outside the viewport; active playback,
displayed inactive-video posters and static-photo prefetch are retained. At this prior baseline
build the default was enabled; the latest source uses disabled-by-default pending device evidence.
There is no runtime setting. Feed-only debug probes summarize visible row/media geometry and aggregate frame
interval/UI-thread CPU values once per scroll burst. Tests cover viewport and mixed-window
cases, position/rank updates, protected active entries, thumbnail/error state, manager source
handoff and cache keys. The manager test uses a local-style content URI but
does not decode/play a representative MP4. No schema, permission, network, source-media,
copy or player-count scope changed. Final CI run 38575809505 at `50132da` passed 83 tests,
lint, schema consistency, packaged privacy checks and debug APK build (artifact `11899788396`).
Physical-device scrolling/playback and before/after startup measurement remain unverified.

The user now reports an on-device A/B: video preloading enabled made photo scrolling feel
sticky; with it disabled, photos felt smoother, but the feed still shifted when a visible video
began loading. The disabled build has not crashed during the user's test. This is user evidence
that video-preload work contributes to photo-feed jank at aggregate level, but it does not isolate
Media3 buffering, nearby Coil video-frame prefetch, or offscreen poster decoding; it does not
explain the separate video-triggered shift or establish crash safety.

At that historical checkpoint, the source kept preloading disabled by default and required
explicit `-PmosaicVideoPreloading=true` to opt in. Static-photo prefetch, visible inactive-video
posters, and active playback remained unchanged. `FeedScreen` derived visible media positions from
`LazyListState.layoutInfo` plus preview geometry relative to each keyed row. It avoids storing
root coordinates in Compose state during scroll, and selection outputs remain stable item keys.
A per-feed bounded aspect-ratio cache seeds later compositions; asynchronous image/thumbnail/video
size callbacks can refine that cache and the video surface's content ratio, but cannot resize the
already-composed row. Unknown first-visit dimensions use a reserved 4:3 container; decoded content
continues to fit without stretching. The loading overlay and poster remain over the same reserved
container. This deliberately prioritizes stable measured geometry; user/device acceptance of the
fallback is still pending.

Debug events now include row-local preview geometry, full visible-row before/after snapshots on
remeasurement, first-visible key/index/offset, active-selection transitions, the explicit feed
`scrollToItem` trigger/result, aspect-ratio source/cache changes, player preparation, overlay
transitions and first-frame events, plus coarse frame/UI-thread CPU summaries. Only the explicit
scope/sort/session/search reset calls `scrollToItem`; no media activation/ratio/first-frame path
requests feed re-anchoring. The prior controlled A/B build passed run
[38595248869](https://github.com/Sam-Program-362/Mosaic-app/actions/runs/38595248869) at `742caf3`
(88 tests in both modes; artifact `11909443268`) but predates these geometry changes. Current
source passed run
[38607618253](https://github.com/Sam-Program-362/Mosaic-app/actions/runs/38607618253) at `cacd82d`:
93 tests with 0 failures/errors/skips in both preload modes, lint, both debug APK builds, schema
consistency and packaged privacy checks; artifact `11916277533`. The first geometry commit's CI
run `38607037687` caught a diagnostic variable-scope compile error, fixed before the green run.
A physical-device mixed portrait/landscape fast-scroll test remains necessary; no root cause or
crash resolution is claimed until device evidence is captured and reviewed.

## Retained first-frame loading correction; historical manager/crash instrumentation
`videoFrameFallback` now only exposes the loading overlay while the player is loading,
not failed, and has not rendered its first frame. The thumbnail remains until that first
rendered-frame callback; a failure suppresses loading and surfaces the existing error state.
The regression test enumerates all eight combinations of first-frame/loading/failure, with
an explicit post-frame buffering case.

For repeated-scroll investigation, debug-only diagnostics count allocated-but-unreleased
ExoPlayer instances, each session's managed source count, active entries, pending preload
requests and manager invalidation calls. TextureView attach/detach order, player cleanup,
manager invalidation, and manager release failures are logged with sanitized identifiers
and exception types only; no media URI/path is logged. Source review finds active manager
sources are protected until player release and then reconciled, while manager candidates are
bounded by the existing window. `VideoPreview` creates its player in `remember` and releases
it from `DisposableEffect`; the new paired tokens can show if an allocation has no release
(including an abandoned composition), but no such imbalance has been measured. Rapid
decoder/player churn and repeated invalidations are possible behaviors to measure, not
established causes. CI run [38581751120](https://github.com/Sam-Program-362/Mosaic-app/actions/runs/38581751120)
at `a9703cb` passed 84 tests, lint, schema consistency, packaged privacy and debug APK build
(artifact `11903168202`). No physical-device reproduction, crash log or native tombstone is
available; crash resolution is not claimed. Exact logcat, PID/stall discrimination, and
bugreport steps are in `docs/phase-5-device-check.md`.

## Historical portrait/landscape fast-scroll handoff follow-up — CI verified, device pending
The user reports that slow smooth scrolling makes the glitch subtle, while fast swipes expose
it near a repeatable point relative to video height. The difficult crash has been observed
around portrait/landscape videos near opposing viewport edges, and a single fast pass can
sometimes suffice. These remain user observations; no crash logs or cause are available.

The Home feed height formula is shared with a pure helper. Debug `preview geometry` records
capture source (thumbnail/player size), old/new aspect ratio and resulting height. `feed selection`
logs include viewport, anchor and selected active item (position/kind/hashed key), visible
overlap, preview height and relative top. Fractions are sampled in 10% buckets rather than
per frame. Player allocation/activation/state/first-frame/release records include paired tokens,
hashed identity, current player state, failure and first-frame status. No private URI/path is
logged.

A tall animated anchor below 50% visibility no longer masks another eligible visible animation;
a photo anchor still suppresses playback. The existing preload window and one-second target
are unchanged. Tests cover 9:16 and 16:9 feed heights, each size near both viewport edges,
forward/reverse fast-scroll selection, a both-below-threshold gap, and manager-source protection
through a portrait-to-landscape handoff. CI run
[38586848533](https://github.com/Sam-Program-362/Mosaic-app/actions/runs/38586848533) at
`ce78b37` passed 87 tests, lint, schema consistency, packaged privacy and debug APK build
(artifact `11905094822`). This is pure geometry/manager verification, not a device fling or
MP4 decode. At the time of that historical run, no physical-device sequence or crash evidence
was available; no crash cause or resolution was claimed. The later 2026-10-15 limited user scroll
report is recorded in the current section above and does not retroactively validate this run or
prove crash resolution. Exact targeted sweep and capture instructions are in
`docs/phase-5-device-check.md`.

## Phase 6 implemented — custom communities
Room v3 adds `custom_communities(id, name, createdAtMillis)` and
`custom_community_members(communityId, folderId)` with a composite primary key and CASCADE foreign
keys to both communities and folders. Explicit `MIGRATION_2_3` is chained after `MIGRATION_1_2`; the
KSP-generated `3.json` schema is tracked. `LibraryStore`/`RoomLibraryStore` expose communities with
their sorted member folder IDs; `LibraryRepository` (under its existing mutex) validates trimmed
non-blank names, requires at least one already-selected folder at creation, de-duplicates member
IDs, allows one folder in many communities, permits empty membership on later edits, and deletes
only the community row (FK cascade removes memberships). Folder removal relies on the same folder
FK cascade, so member media/Saved copies are untouched. `LibraryViewModel` resolves a selected
custom community to its member-ID set and builds the union with `buildFeedForFolders`, which filters
the available-only projection and de-duplicates by stable media key; unavailable members therefore
contribute nothing while retained. `CommunityDirectoryScreen` provides create/edit/delete dialogs
and custom rows alongside folder communities; the drawer lists custom communities by stable ID.
Artwork is provisional: first available member folder artwork, else initials. Final CI run
`38678674913` at `69d34df` passed 103 tests, lint, APK privacy, schema consistency and debug build
(earlier green run `38678154965` at `63d6a27` is historical); physical-device acceptance remains
open (`docs/phase-6-device-check.md`).

## Remaining planned architecture
Room stores folder/access status, source media metadata, Saved-copy metadata and custom-community
membership. Media bytes remain outside Room.
SAF operations belong in `data/source/`; reconciliation/copy orchestration in
`data/repository/`, never composables. Coroutines/Flow offload provider/file I/O.
Room, Coil and Media3 are now installed for implemented slices. Room exports schemas under `app/schemas`; retain generated versions and add
explicit migrations when changing the database. No WorkManager or background scanning without a later need.

## Mandatory invariants
- Explicit read-only selected-folder access and direct-child enumeration only. An
  explicit removal releases Mosaic's saved read grant where possible and deletes
  local index metadata only; source content is immutable.
- Folder enumeration has distinct successful-empty / successful-content / failed
  outcomes. Reconcile stale items only on success; retain unavailable metadata.
- URI/document-based stable identities prevent filename collisions.
- Automatic communities own one folder; custom communities own explicit memberships.
- Stable feed/viewer session ordering; display-only counters are not persisted votes.
- Every save streams into a unique persistent app-managed file, with safe cleanup
  on copy/database failure. Render copies from destination, never source URI.
- Source writes/deletes/renames/moves forbidden. Runtime network dependencies forbidden.
- Visible/foreground-only playback with lifecycle-scoped player/decoder ownership.
- Do not retain activities, open streams, or player instances in inappropriate scopes.

Unresolved: final identity/SDK policy and custom artwork. Custom-community naming/membership policy
was approved for Phase 6 (see above). Static folder artwork default: first supported
static image by case-insensitive filename; neutral initials when none exists.

Offline packaging guard: remove inherited INTERNET/ACCESS_NETWORK_STATE permissions
with manifest tools:node="remove". CI checks built APK, not just source XML. Muted
previews don't request audio focus; unmuted video enables Media3-managed focus.
Detach the TextureView from the player before releasing it on disposal.

## Session-wide MP4 audio (shipped in v0.2.1)
`core/playback/VideoAudioSession.kt` holds the one in-memory MP4 mute preference
for the application process: it defaults to muted, exposes Compose snapshot
state, and is never persisted (no DataStore/SharedPreferences/Room), so a fresh
process starts muted while Activity recreation, recomposition, navigation, and
background/foreground cycles within the process preserve the user's choice.
`MosaicApplication` owns the single instance; `MainActivity` passes it through
`MosaicApp` into `FeedScreen` and the viewer overlay, down to `VideoPreview`
(composables without an application-provided instance fall back to a local
remembered session, used only by previews/tests). A player applies the current
preference at creation and again in its setup effect before `prepare()` —
`applyVideoAudioPreference` sets volume (0f/1f) and Media3 audio-focus handling
(muted previews never request focus) — and a session-keyed effect propagates
later changes to the existing player. The sound control toggles the shared
session and applies it to its player immediately; its icon/label read the
shared state. Player creation, ownership, visibility gating, and disposal are
otherwise unchanged. Tests: `VideoAudioSessionTest` (unit) and
`VideoAudioSessionUiTest` (Robolectric Compose wiring); device audio checks
are in `docs/session-audio-device-check.md`.

## Viewer interaction state and chronological browsing (post-v0.2.1, PR #4)
The fullscreen viewer (`MediaViewer` over a frozen `FeedSnapshot`) owns all
ephemeral interaction state locally; nothing is persisted or shared with
the top-level feed:
- `chromeVisible` (immersive interface toggle) is remembered per opened
  snapshot and starts visible. The same state is passed into image/GIF and
  video surfaces; video playback controls are separate composables above
  the surface and hide/return with the chrome without touching playback or
  the audio session.
- Image/GIF zoom lives in `ZoomableImageViewport`, keyed by the media
  request so state never survives an item change. One gesture loop
  arbitrates pinch/pan (zoomed), confirmed single tap (chrome toggle),
  double tap (zoom), and horizontal swipes: a horizontal drag is claimed
  only at scale 1 with directional intent, and only when chronological
  browsing is active or the gesture began in the near-edge zone just
  inside the system-gesture inset. Vertical drags are never consumed, so
  the `VerticalPager` remains the single vertical navigation owner — there
  is deliberately no nested pager.
- Chronological browsing state (`ViewerTimeline`) is also viewer-local,
  anchored to the random-feed page that activated it. The sequence
  (`buildChronologicalSequence`) filters the already-open snapshot to
  `MediaKind.Image`, sorts by indexed `modifiedAtMillis` with deterministic
  tie-breakers, and is constructed lazily on `Dispatchers.Default` only
  after the first accepted gesture. The pager item displays the timeline
  destination instead of the random item, so only that destination is ever
  requested/decoded — no sequence preparation or neighbour preloading
  happens on viewer entry. Vertical paging or exiting clears the state.
Tests: `MediaZoomTest`, `MediaZoomUiTest`, `MediaViewerChromeUiTest`,
`MediaViewerTimelineUiTest`, `ChronologicalBrowsingTest`,
`TitleCopyUiTest`; long-press title copy (`CopyableTitleText`) shares one
implementation between feed cards and the viewer header.
