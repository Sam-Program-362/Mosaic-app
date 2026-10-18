# Next-session startup prompt — Mosaic (pre-merge, 2026-10-19)

Paste this into a fresh agent session:

---

You are taking over the **Mosaic** Android project in a fresh session. Do not
rely on any prior conversation. Work only from the repository and the user's
instructions in this session.

## Start here, in this order
1. `AGENTS.md`
2. `context/project-overview.md`
3. `context/architecture.md`
4. `context/code-standards.md`
5. `context/ai-workflow-rules.md`
6. `context/ui-context.md`
7. `context/progress-tracker.md`
8. `docs/session-handoff.md`

Then inspect the actual repository state: current branch, `git status`,
recent commits, tags, GitHub Releases, and PR #4. The handoff records the
last verified state, but verify it — do not assume it is still current.

## Known anchor facts (verify, don't re-derive)
- Current stable release: **v0.2.1** (versionName 0.2.1 / versionCode 3),
  permanent signing certificate; v0.2.0 remains published and unchanged.
  Released source is the tag target; later branch commits do not change it.
- Development branch `arena/a0a1b100-mosaic-app` additionally contains five
  user-approved improvements, all in **PR #4** (open, not merged): launcher
  icon from `mosaic_logo.svg`, viewer-only image/GIF zoom, tap-to-hide
  viewer chrome, temporary edge-swipe chronological browsing, and
  long-press title copy. Automated evidence: 154 tests passing in GitHub
  Actions; the user reports their physical-device testing of the installed
  signed candidate is complete and successful. Never present user-reported
  results as agent-run tests, and never present deferred checks as passed.
- Phase 7 device acceptance is **Scheduled/Deferred**. Do not start it,
  resume it, or mark items passed unless the user asks.
- Core invariants: offline-first, source media immutable, folder access
  explicit and non-recursive, no broad storage access, no runtime
  networking, no speculative video preloading, session audio is in-memory
  only and starts muted on every fresh process.

## Your first response
Summarize briefly: what Mosaic is, the current stable release, the
development improvement set and its verification status, that PR #4 is
open and awaiting the user's merge decision, and the approval boundary
below. **Then stop and wait for the user's instructions.** Do not merge
PR #4 unless the user explicitly tells you to (and then use a merge
commit, never squash, so the tagged release commits stay in `main`
history). Do not begin any new feature, refactor, tag, release, or version
change without the user's explicit approval of a specific task.

---
