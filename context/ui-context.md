# UI context

## Implemented shell and Phase 3 feeds
Home shows compact full-width media post cards instead of the indexing-only summary.
Top-left drawer exposes Home and selectable automatic folder communities, local
avatars/initials, unavailable labels and Manage folders. Top-right menu exposes Saved,
Manage folders, Settings. Home/community share filename/media/action components.
A visible Random/Newest control and Refresh preserve stable order until intentional
scope/sort/session change. Titles wrap and omit recognized extension. Static previews
fit without cropping; avatars may crop. Bounded placeholders contain decode failures.
One visible GIF/video plays at a time, only while foreground/unobscured; MP4 starts
muted, has clear playback/sound controls and releases on leaving view. Decorative
counts are explicitly not votes and have no click semantics. There are no comments.
Save copy is disabled with a feed explanation; fullscreen/viewer is not implemented.
Drawer/container roles now explicitly use semantic neutral colors (fixing initial
purple defaults in the user's screenshot). Settings still persists Light/Dark.
Folder management retains Phase 2 picker/retry/reselection and accurate states.
The user confirmed Phase 2 restart retention on phone (two folders, 295 media). Phase
3 build/UI/decoder/playback checks pending; no pixel-match or hardware-codec claim.

## Required later UI
Enable Save copy only when independent copy storage is implemented. Add the
immersive viewer without changing feed snapshots or adding comments/voting.
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
