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
  decorative up/down counts, Save copy. No comments or interactive votes.
- Immersive vertical viewer follows a snapshot of the originating feed order.
  Photos fit; GIF/video autoplay only when visible/active; MP4 starts muted with
  sound toggle. Stop/release inactive and off-screen media.
- Every Save invocation makes another collision-safe app-managed persistent copy.
  Copies render independently after source deletion/access loss. Failures clean
  incomplete copies/records; never alter original media.
- Refresh at launch, resume, and explicit refresh. Reconcile deletions only after
  successful scans. Failed scans are not empty scans. Keep unavailable folder
  communities/metadata and recover when access returns.
- Home, community drawer, app menu (Saved, folders, Settings), consistent light/dark
  themes, accessible touch targets, loading/empty/error/unavailable states.

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
phone acceptance is pending. Phase 5 Save copy/Saved-feed source is now in progress:
private streaming copies, Room v2 migration, feed/viewer actions and tests are authored,
but no Phase 5 build/test/CI verification has run yet. Custom communities remain
planned. This is not first-release completion.
Kotlin/Compose/Room/Coil/Media3 are the adopted technical baseline under delegated
routine technical decisions. See the tracker for exact verification evidence.
Working identity `Mosaic` / `dev.mosaic.app`, min API 26 and target/compile 35 remain
provisional. Public naming/trademark review, release SDK policy, folder-community
renaming/duplicate label policy, and custom-community artwork remain unresolved.
