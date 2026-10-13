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
No playback resources exist yet. Room now stores folder/media metadata (not bytes).
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

## Remaining planned architecture
Room already stores folder/access status and source media metadata; it will also
store community membership and Saved-copy destination records as those slices are
implemented. Media bytes remain outside Room.
SAF operations belong in `data/source/`; reconciliation/copy orchestration in
`data/repository/`, never composables. Coroutines/Flow offload provider/file I/O.
Room is now installed; add image/GIF loader and Media3 only with their implementation
slice. Room exports schemas under `app/schemas`; retain generated versions and add
explicit migrations when changing the database. No WorkManager or background scanning without a later need.

## Mandatory invariants
- Explicit read-only selected-folder access and direct-child enumeration only.
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

Unresolved: final identity/SDK policy, missing timestamp ordering fallback,
community renaming and custom artwork. Static folder artwork default: first supported
static image by case-insensitive filename; neutral initials when none exists.
