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
The Phase 2 restart report does not verify Phase 3 playback or all storage edge cases.
Keep disabled future features explicit; never claim copies or fullscreen are working
just because their labels appear in the current feed.
