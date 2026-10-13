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
- `ui/MosaicApp.kt`: small typed screen shell, drawer/menu, empty states, Settings.
- `ui/theme/`: centralized semantic colors and Material theme mapping.
- `data/preferences/`: application-context Preferences DataStore; light/dark
  theme preference persists. No system theme mode. Fresh/unknown values default to Light.
- Version catalog centralizes dependencies. Gradle wrapper checked in.

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
labelled. Same-tree reselection restores access without duplicate identity. Lifecycle
resume/explicit refresh scan serialized operations; no background service/WorkManager.
Null/missing-required-column/loading/error cursors never authorize deletion.

## Phase 3 implementation
`feature/feed/` owns pure feed ordering/count/title/artwork/visibility rules, lazy
post UI, local image/GIF preview and composition-scoped MP4 players. Coil 3.1.0 uses
Compose/GIF modules without a network fetcher; an application-context ImageLoader
provides bounded shared caching. Static photo/avatar requests explicitly select a
static decoder; only GIF previews opt into animation. Media3 1.6.1 creates at most one inline player for
visible foreground media, muted by default and disposed offscreen/inactive.
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
is transient UI state, not persistent media/library state.

Manage folders now offers an explicit confirmation before removing a selection.
Repository operations serialize removal with refresh/add. `SafFolderSource` attempts
to release only Mosaic's persisted read grant; Room deletes the folder row and its
media metadata through the existing cascade. If grant release cannot be confirmed,
metadata is still removed and UI warns the user. No scan is triggered and no original
file is written, copied, moved, renamed or deleted. No schema/table version changed.

Phase 4 CI passed 53 tests, lint, Room schema consistency, packaged privacy checks
and debug APK. Physical viewer/paging and permission-release acceptance is pending.

## Phase 5 source in progress — Saved copies
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
migration; existing folder/media data is retained. KSP's generated v2 schema must be
captured from CI and checked in. First CI compiled source/tests but the test task failed
on a Robolectric source-open NPE; a test now exercises empty-stream cleanup instead.
No passing unit-test/lint/privacy/APK or phone result is claimed yet.

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
Detach PlayerView before releasing player on disposal.
