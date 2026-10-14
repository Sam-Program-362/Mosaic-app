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
sticky, and inactive video currently shows a paused placeholder. Source now has bounded
neighbor preloading, local video-frame thumbnails, playback/seek controls and warm-resume
media retention. CI run 38556928293 at `a11fde2` passed 74 tests, lint, schema consistency,
packaged privacy checks and debug APK build; artifact 11890760322 is available. Treat the
original observations as user-reported evidence, and leave the new behavior pending until
physical-device verification.
