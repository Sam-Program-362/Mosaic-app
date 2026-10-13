# UI context

## Implemented shell and Phase 2 folders
Home shows the indexing summary (selected folders, available supported file count),
unavailable warning, Add folder when empty, Refresh and Manage folders routes. Top-left
hamburger opens the community drawer (including standard drawer gesture); Home,
selected-folder labels, unavailable indicators and Manage folders are present.
Labels are visible metadata, not working community feed controls yet. Top-right app menu
routes to Saved, Manage folders, Settings; no account/profile screens.
Non-Home screens have a back control returning to Home. Drawer/back state is handled.
Saved explains independent copies but does not yet display or save media.
Manage folders launches the native picker through Add folder, shows loading/scan
progress, distinguishes accessible empty folders from unavailable ones, retains last
indexed counts on failed scans, and offers per-folder refresh and same-tree access
reselection. Read grants that cannot persist are labelled. Picker cancellation
changes nothing; different-tree recovery selection is rejected. No mock media or
misleading completed-feed/save states. Provider I/O stays outside composables.
Settings offers persisted Light/Dark selection and save-error feedback. No system
mode. Fresh/invalid theme preference defaults to Light. Scrollable layouts and
Scaffold insets accommodate landscape, font scaling, and edge-to-edge system bars.
System sans/Material 3 type scale is used; icons have accessible labels.
Android compilation/lint and source/database tests passed in CI; no real-phone
UI, picker or font/landscape acceptance is claimed. See `docs/phase-2-device-check.md`.

## Required later UI
Compact width-spanning Reddit-inspired feed, not grid or oversized promotional cards:
community/avatar + metadata, filename title, aspect-preserving preview, decorative
up/down counts, accessible Save copy. No comments. Stable loading placeholders.
Home/community share post components; Random/Newest and refresh are discoverable.
Community drawer retains unavailable communities with text state and recovery.
Custom community creation names/selects/reviews folder membership. Local deterministic
folder artwork uses first static image by case-insensitive name; neutral placeholder
otherwise. Custom artwork policy unresolved.
Immersive viewer has vertical paging of originating feed snapshot, fit photos,
visible-only GIF/video autoplay, muted video with accessible sound toggle and Back.
Provide no-folder, accessible-empty, unavailable-folder, slow-provider, decode/missing
media and source-safe save-failure states. Saved reuses media/feed/viewer conventions.

## Central semantic colors
Defined in `ui/theme/MosaicTheme.kt`; values are starting points, not pixel-verified
Reddit colors or final branding.

| Token | Light | Dark |
|---|---|---|
| background | #DAE0E6 | #0E1113 |
| surface | #FFFFFF | #181C1F |
| surfaceRaised | #FFFFFF | #24292D |
| textPrimary | #1A1A1B | #F2F4F5 |
| textSecondary | #576F76 | #A6ADB2 |
| divider | #E5E7E8 | #343A3F |
| accent | #FF4500 | #FF4500 |
| link | #0079D3 | #4FBCFF |
| error | #C62828 | #FF6B6B |
| mediaBackdrop | #F3F5F6 | #050606 |

Accent is for controls, not large blocks or apparent active votes. Filled accent
controls use a dark foreground in both themes for contrast. Use 4dp spacing
grid; approximately 0–4dp post corners, 8dp controls, 12–16dp overlays. Screen
headers ~20–24sp; titles ~16–18sp; body ~14–16sp; metadata ~12–14sp. System scaling,
48dp functional targets, non-color state cues and accessible focus order matter.
Initial local mosaic-tile launcher mark is a placeholder, not finalized branding.
