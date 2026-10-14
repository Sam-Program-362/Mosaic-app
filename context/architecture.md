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
A warm resume retains the current and bounded preloaded media rows and feed epoch/order
while reconciling other entries. A cold process start and explicit refresh fully reconcile.
Null/missing-required-column/loading/error cursors never authorize deletion.

## Phase 3 implementation
`feature/feed/` owns pure feed ordering/count/title/artwork/visibility rules, lazy
post UI, local image/GIF/video-frame previews and composition-scoped MP4 players.
Coil 3.1.0 uses Compose/GIF/video-frame modules without a network fetcher; an
application-context ImageLoader provides bounded shared caching. Static photo/avatar
requests explicitly select a static decoder; only GIF previews opt into animation.
Inactive videos decode a local first-frame thumbnail. The feed/viewer window keeps the
current item plus the nearest photo and nearest video within three feed positions on each
side (at most five candidates); image/frame prefetch is independent from video buffering.
Media3 1.6.1 `DefaultPreloadManager` buffers one second for eligible adjacent video sources.
These sources do not create extra playing players. Media3 creates at most one inline
player for visible foreground media, muted by default and disposed offscreen/inactive.
The active player exposes play/pause, mute, a seek slider and elapsed/duration; full-screen
video supports left/right double-tap ±10-second seeking and tap-to-hide/show controls.
Room joined projections filter Available status before UI; no schema version change.
ViewModel combines available metadata + scope + sort + session epoch off main thread.
Random rank/count seed depends on epoch + length-prefixed folder/document identity;
unchanged metadata/ordinary recomposition cannot reshuffle it. Newest unknown dates
sort last, ties by case-insensitive name then identity. Artwork chooses first static
image case-insensitively per folder, never a GIF/video. Viewport geometry uses the
preview's bounds, not merely card visibility; menus/drawer/background disable playback.
All functional playback controls are distinct from decorative, noninteractive counts.
Phase 5 adds an enabled Save copy action in feed cards and viewer chrome.

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

## Earlier video and warm-resume follow-up — CI verified; later correction pending
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

## Video transition correctness/performance pass — CI verified; device acceptance pending
The current source chooses the dominant visible media item by viewport overlap across
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

The current source keeps preloading disabled by default and requires explicit
`-PmosaicVideoPreloading=true` to opt in. Static-photo prefetch, visible inactive-video posters,
and active playback remain unchanged. `FeedScreen` now derives visible media positions from
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

## First-frame loading correction and crash-investigation instrumentation — CI verified, device pending
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

## Targeted portrait/landscape fast-scroll handoff follow-up — CI verified, device pending
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
MP4 decode. No physical-device sequence or crash evidence is available; no crash cause or
resolution is claimed. Exact targeted sweep and capture instructions are in
`docs/phase-5-device-check.md`.

## Remaining planned architecture
Room already stores folder/access status, source media metadata and Saved-copy
metadata; custom-community membership remains for a later slice. Media bytes remain
outside Room.
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

Unresolved: final identity/SDK policy, community renaming and custom artwork. Static folder artwork default: first supported
static image by case-insensitive filename; neutral initials when none exists.

Offline packaging guard: remove inherited INTERNET/ACCESS_NETWORK_STATE permissions
with manifest tools:node="remove". CI checks built APK, not just source XML. Muted
previews don't request audio focus; unmuted video enables Media3-managed focus.
Detach the TextureView from the player before releasing it on disposal.
