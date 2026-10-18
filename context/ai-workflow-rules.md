# AI workflow rules

The agent working in the checkout implements code when explicitly asked, as in
the Phase 1 request. Planning-only prompts must never claim implementation.

Read `AGENTS.md` and all six context files in their listed order before changes.
Inspect the actual tree; do not invent existing APIs, files, or completed work.
Work in small verifiable slices, preserve the safety invariants, and avoid speculative
features/dependencies/refactors. Ask questions only for material blockers; infer
ordinary navigation conventions from the Reddit Android reference.

Clearly distinguish requirements, recommendations, provisional defaults, approved
choices, implemented source, and verified behavior. Record meaningful defaults
and unresolved decisions rather than silently describing them as confirmed.

After meaningful work update `progress-tracker.md`; inspect all six context files
and synchronize affected requirements/architecture/UI/standards. Report changed
files, actual checks/results, blocked verification, unresolved items, and next task.
Never claim a test/build/commit/workflow/device run without evidence.

No accounts, backend, runtime internet, analytics, broad storage scanning, comments,
real voting, source writes, or speculative media formats. No secrets in source/chat.
Use repository workflows for CI; release signing remains external via GitHub secrets.
Do not create releases or push/tag merely to bypass a blocked local toolchain unless
requested. Development dependency downloads are separate from offline runtime.

## Current session authorization (2026-10-14)
The user delegated routine technical choices and explicitly approved pushing this
work to the current session branch to run GitHub verification and produce a debug
APK. This is not authorization to merge main, tag or publish a production release.

Record user-reported phone results as user evidence, not agent-run device tests.
The user reports Phase 3 photos appear in Home, folder feeds show only their folder's
media, and videos play. This does not verify GIFs, mute/audio focus, offscreen or
background stop behavior, or every storage edge case. Phase 4 CI is not phone
acceptance: actual vertical swiping/back/scroll restoration and confirmed removal
still need device feedback. Phase 5 CI run 38469093930 passed migration/copy/UI tests,
lint, packaged privacy and debug APK build; the exact generated v2 schema is committed
with d267f16. Earlier runs 38509513570 and 38510281049 exposed a timing issue in the
new animation test; the final test isolates reverse-close retention. CI run 38511038772
passed 60 tests, lint, packaged privacy, schema and debug APK build with the final motion
change. The Drive-reference UI adaptation at `b667dcc` passed CI run 38543107645: 67
tests, lint, schema consistency, packaged APK privacy and debug APK. Its UI/theme/search/
video-control behavior remains unverified on a physical phone. The run's debug APK and
reports are artifact 11883232388. No merge or release was made. Keep custom communities
and other future features explicit; a visible label alone is not proof.

The user's 2026-10-14 phone report says photos are smooth, video loading/advance feels
sticky, and inactive video currently shows a paused placeholder. The then-current implementation
added bounded neighbor preloading and local video-frame thumbnails; its historical CI result is
recorded above. Treat the observations as user-reported evidence, not agent-run device tests.

## Current focused production request (2026-10-15)
The user superseded the previous suggestion of keeping video preloading disabled by default or
behind a diagnostic toggle. Permanently remove or safely bypass all video-specific preload paths:
neighboring-video buffering, offscreen video-frame prefetch, and speculative offscreen-video
poster decoding. Do not leave the old manager/lifecycle APIs or a diagnostic Gradle toggle in the
production app. Preserve bounded static-photo prefetch, visible video posters, active-player
preparation on activation, playback controls/interactions, the dedicated video frame/aspect policy,
viewer behavior, and the first-rendered-frame overlay correction.

Feed photos must fill available media width and use decoded/oriented intrinsic dimensions for
height without distortion, crop-to-video-frame, or arbitrary aspect clamps. Handle unknown/invalid
and delayed dimensions with a safe temporary placeholder; establish the first valid ratio and keep
height stable thereafter. Preserve stable item keys/navigation; media loading/first-frame events
must not change photo geometry or cause unnecessary anchor corrections. Add tests for photo
orientation/dimensions/Fit behavior and delayed dimensions, video presentation/playback invariants,
feed position stability, production absence of video preload, normal static-photo prefetch and
visible-video posters. Keep the patch focused.

The earlier user report was: preload-enabled scrolling felt sticky and the app crashed; preload-
disabled photo scrolling was smooth and video loading fast enough. This is user evidence, not
log-correlated causality or proof that the crash is fixed. Never claim crash resolution because
preloading was removed. Unit tests, lint, privacy/schema checks, debug APK generation, CI status,
and physical-device acceptance must be reported separately. A development APK is required for
phone verification; no merge to main or release is authorized.

The implementation at code commit `a4264dd` passed GitHub CI run `38624705844`: 96 tests
(0 failures/errors/skips), lint, debug APK, packaged privacy and Room schema checks, plus the
production-preloader source guard. Artifact `mosaic-debug-and-reports` ID `11923577241` contains
the APK and checksum. The sandbox cannot copy the artifact from GitHub's storage host, so provide
the user the workflow artifact link.

The user then reported on 2026-10-15: `"Scrolling is okay, didn't crash. Basically it's okey."`
This is limited user phone evidence: acceptable scrolling and no crash during that test; the user
considers it okay enough to proceed. It is not agent-run or exhaustive, and the tested APK, device,
sequence, and logs were not supplied. Targeted geometry/video/position and repeated-scroll checks
remain open. Do not say no phone feedback has occurred, do not claim full device acceptance, and do
not claim the historical crash is fixed. See the [fresh-session handoff](../docs/session-handoff.md)
for current status and the proposed next slice.

## Current approval boundary (after v0.2.1, 2026-10-15)
The dated authorizations above were one-off approvals for their stated tasks
and are complete; they do not carry forward. The audio implementation, its
signed candidate, the Drive context archive, and the v0.2.0 / v0.2.1 GitHub
Releases were each separately authorized and are finished. As of the current
stable release **v0.2.1**, no new feature, refactor, merge to `main`, tag, or
release is authorized. The next agent waits for the user's next development
instructions, evaluates the ideas with them, and only then proposes an
implementation task for approval. Continue to distinguish automated CI
evidence, user-reported device results, and unperformed scheduled checks in
every report.

## Current approval boundary (pre-merge, 2026-10-19)
The post-v0.2.1 improvement tasks the user authorized after that boundary
(launcher icon, viewer zoom, immersive chrome, chronological browsing,
title copy) are implemented, CI-verified, and — per the user's report —
device-tested successfully; they are complete and authorize nothing
further. **PR #4 is open and must not be merged by an agent**: the merge is
the user's explicit decision, and when they give it, it must use a merge
commit (not squash) so the tagged release commits remain in `main`
ancestry. No new feature, refactor, tag, release, or version change is
authorized. Phase 7 acceptance remains Scheduled/Deferred and must not be
marked passed on the basis of the improvement-set device testing.
