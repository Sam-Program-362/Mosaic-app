# Decision record — multi-GIF feed playback, static thumbnails and playback lifecycle

Date: 2026-10-31. Scope: feed + viewer GIF presentation only. No change to `applicationId`,
signing, versioning, Room schema, backup format v1, saved copies, MP4/audio policy, or any new
dependency.

This record states each engineering problem, the options that were genuinely available in this
codebase, the choice that was made, why, and what it costs. It exists so the trade-offs can be
revisited without re-deriving them.

---

## 1. Simultaneous GIF playback in the feed

**Problem.** The feed had exactly one "active preview" slot (`activePreview` /
`playbackPreviewForAnchor` over all non-`Image` media). It was introduced for MP4: one ExoPlayer,
one muted player at a time. GIFs were classified into the same set, so at most one GIF could ever
animate, and every other GIF rendered a label instead of media.

**Options.**
1. Keep one slot and extend it to "the most visible GIF" only. Rejected: it directly contradicts
   the requirement, and users would still see static GIFs beside animating ones.
2. Give each GIF its own player/controller (ExoPlayer, Media3 frame extraction, or a hand-rolled
   animation loop). Rejected: violates "GIFs are images", adds decoders/audio sessions, and would
   break the single-ExoPlayer invariant.
3. Encode GIFs to MP4/cached filmstrips. Rejected: transcoding, extra files, changed filenames.
4. Keep exactly one MP4 slot, and select GIFs independently by measured visibility (chosen).

**Chosen.** `videoKeys` (kind `Video`) still feed `activeVideoPreview` /
`playbackVideoForAnchor`, unchanged semantics: single slot, 0.5 minimum visible fraction, anchor
must itself be a video. `eligibleGifKeys` selects *every* GIF whose preview is meaningfully inside
the measured viewport. There is no cap on the number of animating GIFs; the cap is whatever the
viewport can actually show.

**Why.** GIF presence cannot influence MP4 playback (the bug the old shared slot caused: a GIF
could outrank a video for the single slot). Selection uses measured geometry that already existed
(`visibleMediaFromRows`, `VisibleMedia`, `visibleFraction`), so nothing depends on LazyColumn
composition.

**Trade-off.** N visible GIFs mean N animated drawables plus N still-bitmap layers. All are
bounded by viewport height and released when the card leaves the viewport or playback stops; a
device with several simultaneous large GIFs will do proportionally more decode work than the old
single-GIF behaviour. That is the direct cost of the requested behaviour, not an accident.

---

## 2. Static thumbnail for an inactive GIF

**Problem.** An inactive GIF must show a real still frame derived from the original local GIF
bytes — never blank/black, never the text "GIF · playback paused". `AnimatedImageDecoder`,
`GifDecoder` and `StaticImageDecoder` cannot produce a paused single frame (they either animate or
produce nothing), so a new path was needed.

**Options.**
1. Keep drawing the animated drawable but never start it (a "paused animation" layer). It is not
   guaranteed to paint before the first frame is advanced, and the previous implementation proved
   the failure mode (blank card).
2. Decode once with the animated decoder and extract frame 0 from the drawable. No public API
   gives a frame from `AnimatedImageDrawable`/`Movie`; would need reflection or a private
   `AnimatedImageDrawable` state read.
3. `ImageDecoder.decodeBitmap` (API 28/29+) for GIF stills. Rejected: makes the still path
   API-dependent, needs a second platform-specific implementation, and still cannot serve API 26/27.
4. `BitmapFactoryDecoder` (chosen): Skia's GIF codec returns exactly one non-animated frame — the
   first frame — on every supported API level.

**Chosen.** `GifPreview` composes two Coil image layers over the same original content URI:
- a still layer (`createGifStillFrameRequest`, `BitmapFactoryDecoder`, cache identity
  `"$uri@$modified:gif-still-frame"`) that is **always** composed and owns the accessibility
  description;
- an animated layer (`createAnimatedGifRequest`, no explicit decoder so the loader's animated
  decoder resolves it, cache identity `"$uri@$modified:true"`) composed **only** while the card is
  eligible.

**Why.** Both layers are decoded from the original bytes through Coil's image pipeline: no copy, no
rename, no transcode, no thumbnail file, and `MediaKind.Gif` / content detection are untouched. The
still layer does double duty: it is the paused thumbnail *and* the backdrop while the animation
decodes, so start/stop transitions cannot flash blank. When the animated layer leaves the
composition, Coil's painter stops the underlying `Animatable` drawable and draws nothing, so the
still layer is immediately visible again.

**Trade-off.** While a GIF animates, the still bitmap is also resident (a second decode of the same
source at view size). In exchange, pause/stop is instant and blank-free. The still bitmap *is*
memory-cached (immutable, shareable), so scrolling back to a paused GIF does not re-decode; the
animation itself is not memory-cached by Coil (animated images are non-shareable by design), so
re-entering a GIF restarts its decode — bounded to eligible cards and inherent to animated
drawables.

---

## 3. GIFs stay images: never ExoPlayer, Media3, or MP4

**Problem.** The feature must not turn GIFs into video, and existing MP4 behaviour must not change.

**Options.** (a) reuse `VideoPreview`/ExoPlayer for GIFs — rejected, it introduces an audio session,
a video decoder, a second player and defeats the "GIFs are images" model; (b) generate MP4 or
pre-rendered frame caches — rejected, requires transcoding and extra files; (c) keep the Coil image
pipeline (chosen).

**Chosen.** GIF requests are built only by `createAnimatedGifRequest` / `createGifStillFrameRequest`.
The animated request deliberately sets **no** decoder factory, so the single animation decoder
registered on `localImageLoader` (`GifDecoders.animatedFactory()`) is authoritative; the still
request pins `BitmapFactoryDecoder`. Decoder selection lives in one object, `GifDecoders`, so the
application loader and the feed requests cannot drift apart.

**Trade-off.** GIF behaviour now depends on the image loader configuration; the decoder wiring is
therefore asserted in tests (`animatedRequestNeverPinsTheStillFrameDecoder`,
`animatedFactorySelectsTheImageDecoderPathOnApi28AndAbove`) instead of being an inline
`Build.VERSION.SDK_INT` check.

---

## 4. Older-API path (minSdk 26)

**Problem.** `ImageDecoder`/`AnimatedImageDrawable` exist only from API 28; API 26/27 can only use
`android.graphics.Movie`.

**Options.** (a) raise minSdk — out of scope; (b) drop animation below 28 — violates the
requirement; (c) keep both decoders behind one factory (chosen).

**Chosen.** `GifDecoders.animatedFactory()` returns `AnimatedImageDecoder.Factory()` when
`Build.VERSION.SDK_INT >= P` and `GifDecoder.Factory()` (Movie-backed) otherwise. The API-28 call
site lives in its own `@RequiresApi(28)` function so the guard is provable to lint. Still frames use
`BitmapFactory` on every API level, so the thumbnail path is identical on 26 and 35.

**Trade-off.** Two animation implementations must both keep working; Robolectric cannot decode
either animated drawable, so both animation paths carry an explicit device check (see the tracker)
while the still-frame path is verified in CI on API 26 and 35.

---

## 5. Visibility threshold and churn

**Problem.** "Meaningfully visible" needs a number. Too low → off-screen slivers and edge cards
animate (wasted decoding, visible jank); too high → GIFs sitting in the viewport stay static. A
single threshold also makes GIFs start/stop repeatedly while scrolling across it.

**Options.**
1. Any overlap > 0 (1 px). Rejected: a card clipped to a sliver would decode and animate.
2. 50% (the MP4 slot rule). Rejected for GIFs: a tall GIF that fills the screen may never be 50%
   visible relative to its own height, and two GIFs would rarely both qualify.
3. A percentage of the *viewport* rather than the item (e.g. "covers >10% of the screen"). Rejected:
   `visibleFraction` already defines a consistent, tested notion of visible area; a second notion
   would make diagnostics and tests disagree with the video rule.
4. `visibleFraction >= 0.12` to start, `>= 0.06` to keep (chosen).

**Chosen.** `MIN_VISIBLE_GIF_FRACTION = 0.12f`, `RETAIN_VISIBLE_GIF_FRACTION = 0.06f`, both applied
to the existing `visibleFraction` (overlap ÷ min(item height, viewport height)), plus
`overlap > 0`. `GifAnimationEligibility` remembers the previous set so an already-animating GIF
uses the lower threshold; the state is recreated per feed session (folder, community, sort, epoch,
search).

**Why 12%.** On a phone viewport this is roughly 60–110 px of a feed card — enough that a card
peeking in from the bottom does not animate, and low enough that a card that dominates the screen
always does. 6% keep is exactly half of that, a band wide enough that normal fling overshoot and
small scroll jitter cannot toggle a decoder. The band is deliberate hysteresis, not a second
opinion about visibility.

**Trade-off.** A GIF between 6% and 12% visible keeps animating if it was animating (or stays
static if it was not). That asymmetry is intentional, and it is covered by tests at both exact
boundaries.

---

## 6. Resource bounding

**Problem.** Simultaneous playback must not become speculative decoding or leaked resources.

**Chosen.**
- No GIF prefetch: `NearbyPhotoPrefetcher` remains static-photo-only (`createPhotoPrefetchRequest`
  still `require(kind == Image)`), so animation work happens only for cards that are actually
  visible.
- Animated layers exist only while eligible: leaving the set (or disabling playback, sorting,
  backgrounding) removes the composable, which stops the drawable and frees the decoder.
- Hysteresis bounds start/stop churn to at most one transition per threshold crossing.
- The viewer animates exactly one page (`pageActive`: current page **and** lifecycle resumed) and
  `VerticalPager(beyondViewportPageCount = 0)` is unchanged, so neighbouring pages are neither
  composed nor animating.
- Still frames use the same memory cache the rest of the feed already uses; no new cache, no
  disk-cache entries for thumbnails, no files written.

**Trade-off.** No warm-up: a GIF that scrolls into view begins decoding when it becomes eligible,
so its first frames may appear a moment later. Prefetching GIFs was explicitly rejected as
speculative decoding; the still frame covers the gap so the card is never blank.

---

## 7. Verification strategy

| Layer | What proves it |
| --- | --- |
| Selection math | `GifPlaybackEligibilityTest` — exact 12%/6% boundaries, multi-select, ordering, off-screen, sliver, zero-height viewport, hysteresis transitions, scroll changes, MP4 independence, photo/GIF separation, the shared playback gate |
| Feed/viewer rendering | `GifFeedPlaybackUiTest` — still frame instead of a paused label, animated layer on the eligible path, several simultaneous animations, off-screen cards still, scroll out/in, global pause keeps thumbnails, `.jpg`-named GIF keeps its title and animates, static photos and MP4 cards unaffected, viewer current page animates |
| Decoding | `GifFrameDecodingTest` — the committed real two-frame GIF decodes to its **first** frame through the production still factory on API 35 **and** API 26; the animated and still requests have distinct cache identities and decoder wiring; the API-28+/pre-28 factory branch is asserted on the emulated platform |
| Device-only | Animated drawable decoding cannot be produced by Robolectric (both `AnimatedImageDrawable` and `Movie` paths are unsupported there), so two cases are guarded by JUnit assumptions and must be re-run on a device (API 26/27 and API 28+) |
