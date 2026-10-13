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
Phase 5 source enables Save copy on feed cards and viewer chrome; each tap requests a
new independent app-managed copy. Saved opens a newest-first local feed with the same
media card/viewer behavior and an explanatory empty state. Viewer taps refer to the
current item in its frozen feed snapshot. Tapping a preview opens a full-screen vertical
pager at that item, frozen to the exact feed snapshot/order. Current page alone may play
GIF/video while RESUMED; Android Back and accessible toolbar Back close the viewer. The
feed stays composed beneath it with playback and accessibility semantics disabled,
preserving the original scroll position. Viewer hides system bars and restores their
prior visibility on exit. Phase 5 source is not yet build/test/device verified.
Drawer/container roles use semantic neutral colors (fixing purple defaults in the
user's initial screenshot). Settings persists Light/Dark. Folder management retains
picker/retry/reselection and now has a confirmed **Remove from Mosaic** action. This
removes local index/community and attempts to release Mosaic's saved read grant; it
never changes original media. Failed permission release shows a warning.

The user confirmed Phase 2 restart retention (two folders, 295 media) and reports in
the Phase 3 phone check that photos display in Home, folder selection shows only its
media, and videos play. GIF, video mute/offscreen/background behavior and Phase 4
viewer/removal have not yet been confirmed on-device. Phase 4 CI passed 53 tests,
lint, schema and APK privacy checks. First Phase 5 CI compiled source/tests but one
Robolectric source-open test failed; the test was adjusted to cover empty-stream cleanup
and awaits rerun. No pixel-match, physical paging, provider permission-release,
hardware-codec or Phase 5 device-acceptance claim.

## Remaining UI scope and Phase 5 verification
Phase 5 source implements independent Save requests in feed and viewer, and a Saved feed
built only from app-managed copies. Verify repeated taps create separate copies, Saved
items render/play after original removal or lost access, and failures leave no record or
partial destination file. The save-result snackbar states success or that the original
was left unchanged. Saved-copy removal is out of scope. Custom community creation
names/selects/reviews folder membership; custom artwork policy remains unresolved.
Local deterministic folder artwork uses first static image by case-insensitive name,
with neutral initials otherwise. Existing no-folder, unavailable, decode/missing-media,
and empty states remain. Device acceptance and Phase 5 CI are pending.

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
