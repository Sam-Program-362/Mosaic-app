# Project overview

## Product
Mosaic is a working name for a private, offline-first Android phone application
that presents user-selected local photos, animated GIFs, and MP4 videos in a
compact Reddit-inspired feed. Reddit is an interaction reference, not branding
or proprietary assets to copy.

## Required first-release behavior (incrementally implemented; see status below)
- Native SAF folder picker; persist read grants where supported; no broad storage access.
- Index direct children only. Nested directories require separate explicit selection.
- Provider-recognized static images, GIFs, and MP4 only. Exclude unsupported media safely.
- Each folder creates a community named after it. Custom communities combine
  explicitly assigned selected folders without moving/copying source content.
- Home combines eligible folders; community feeds isolate membership correctly.
- Random and Newest sorting. Random order/counts regenerate at launch/refresh,
  not ordinary recomposition. Missing modification timestamps need consistent fallback.
- Filename-based titles; retain full names, use URI/document identity rather than
  filenames as unique IDs. Hiding recognized extensions is a provisional default.
- Compact post hierarchy: community, filename title, aspect-preserving media,
  local-time metadata, decorative up/down counts, Save copy. No comments or interactive votes.
- Search only indexed filename/title, folder label, MIME type, or media-kind metadata;
  never inspect media bytes or use a network service.
- Immersive vertical viewer follows a snapshot of the originating feed order.
  Photos fit; GIF/video autoplay only when visible/active; MP4 starts muted with
  sound toggle. Stop/release inactive and off-screen media. A bounded current-plus-one-
  per-side window retains the current item plus the nearest photo and nearest video
  in up to three feed positions on either side (at most five candidates). Image/video
  frames preload independently; short video buffers stay bounded. Inactive MP4 cards
  show a cached frame thumbnail rather than a paused placeholder.
- Active video offers play/pause, mute/unmute, seek timeline and elapsed/duration,
  double-tap ±10-second seeking, and tap-to-hide/show player controls.
- Every Save invocation makes another collision-safe app-managed persistent copy.
  Copies render independently after source deletion/access loss. Failures clean
  incomplete copies/records; never alter original media.
- Full refresh at a cold process start and explicit refresh; warm background resume
  rescans/reconciles other items while retaining the current and bounded preloaded
  media IDs/feed session. Reconcile deletions only after successful scans. Failed
  scans are not empty scans. Keep unavailable folder communities/metadata and recover
  when access returns.
- Home, searchable communities directory, and navigation through the header/drawer
  only (no bottom navigation or navigation rail); drawer/menu expose Saved, folders,
  communities, and Settings. Consistent Light/Dark themes and loading/empty/error/
  unavailable states remain required.

## Safety and scope
No runtime backend, internet permission, accounts, analytics, crash uploads,
advertisements, social features, comments, real voting, cloud sync, unrestricted
storage scanning, or source writes/deletions. No permanent background scanner.
Saved removal, additional media formats, custom artwork controls, and tablet-specific
multi-pane design are not required in v1.

## Status and decisions
Phase 1/2 passed CI (30 tests, lint, schema and debug APK); the user confirmed
folder records/access survived a phone restart. Phase 3 CI passed 45 tests, lint,
schema, packaged privacy and debug APK checks. The user reports on-phone that photos
display on Home, folder selection isolates that folder's media, and videos play.
GIF, mute, offscreen/background playback behavior have not been confirmed on-device.
Phase 4 implements a frozen-order full-screen viewer and confirmed folder removal;
its CI passed 53 tests, lint, schema, packaged privacy and debug APK checks. Phase 4
phone acceptance remains pending, and the user has now asked for a smoother grow/fade
viewer transition based on supplied screenshots. Phase 5 Save copy/Saved-feed, Room v2
migration and feed/viewer actions passed CI run 38469093930 (59 tests, lint, tracked
schema consistency, packaged privacy and debug APK build). The exact KSP-generated v2
schema is committed. Current viewer motion passed CI run 38511038772 at 1c4cd8d: 60
tests, lint, schema consistency, APK privacy and debug build. Earlier attempts exposed
and resolved an entrance-test timing issue. The Drive-reference UI adaptation passed CI
run 38543107645 at `b667dcc` (67 tests, lint, schema consistency, packaged APK privacy
and debug build); the debug APK and reports are available as CI artifact 11883232388.
The reference-inspired Dark fallback is a provisional default. A 2026-10-14 follow-up
implements bounded nearby image/video preloading, inactive-video frame thumbnails,
Reddit-inspired playback/seek controls and warm-resume media retention. CI run
38556928293 at `a11fde2` passed 74 tests, lint, schema consistency, packaged privacy
checks and debug APK build; artifact `mosaic-debug-and-reports` (ID `11890760322`) is
available. The initial run's missing decoder import was fixed before this green run.
Physical-device acceptance for this follow-up and the earlier refreshed UI/search remains
pending. The 2026-10-14 correctness/performance pass changes viewport anchoring, actual
snapshot-position ranking/reconciliation, active-source removal timing, first-frame poster
fallback, error semantics and debug diagnostics. Final CI run 38575809505 at `50132da` passed
83 tests, lint, schema consistency, packaged privacy checks and debug APK build (artifact
`11899788396`). No before/after device measurement, actual MP4 decode, or real scrolling /
playback evidence is available; CI does not establish that transitions are smoother. Custom
communities remain planned. This is not first-release completion.
Kotlin/Compose/Room/Coil/Media3 are the adopted technical baseline under delegated
routine technical decisions. See the tracker for exact verification evidence.
Working identity `Mosaic` / `dev.mosaic.app`, min API 26 and target/compile 35 remain
provisional. Public naming/trademark review, release SDK policy, folder-community
renaming/duplicate label policy, and custom-community artwork remain unresolved.
