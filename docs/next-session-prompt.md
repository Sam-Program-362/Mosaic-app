# New-session startup prompt — Mosaic

Copy everything below the line into a fresh agent session. It is
self-contained and assumes no knowledge of any previous conversation.

---

You are working on **Mosaic**, a private, offline-first Android app
(Kotlin/Jetpack Compose) for browsing user-selected local photos, GIFs, and
MP4 videos. Repository: `Sam-Program-362/Mosaic-app`. Development branch:
`arena/a0a1b100-mosaic-app`. Current stable release: **Mosaic v0.2.1**
(GitHub Release `v0.2.1`).

Before proposing or changing anything:

1. Read the repository's agent instructions in `AGENTS.md`.
2. Read the six context files in exactly this order:
   `context/project-overview.md`, `context/architecture.md`,
   `context/code-standards.md`, `context/ai-workflow-rules.md`,
   `context/ui-context.md`, `context/progress-tracker.md`.
3. Read the current session handoff: `docs/session-handoff.md`.
4. Inspect the actual current state before relying on any document:
   local `git status`/branch (preserve any uncommitted work — do not reset
   or overwrite it), the live remote branch HEAD, tags and GitHub Releases
   (the tag target is the released source; later commits may be
   documentation-only), and the Gradle version in `app/build.gradle.kts`.
5. Briefly summarize back to me: the shipped application and its current
   release, what the verification evidence does and does not establish
   (automated CI vs my user-reported device results vs unperformed
   scheduled checks), the known limitations and open decisions, and the
   approval boundary below.
6. Then **wait**. I will describe the next development idea; help me
   evaluate it before selecting or starting any implementation task.

Boundaries until I approve a specific task: do not implement features,
refactor, change version metadata or signing configuration, merge to
`main`, create tags or releases, or resume the deferred Phase 7
device-acceptance checklist (it is Scheduled in the project archive, not
passed and not automatically restarted). Never modify or delete original
media files, broaden folder access, add networking/accounts/analytics, or
handle signing keys, passwords, or secret values. Keep source media
immutable, folder access explicit and non-recursive, and runtime operation
offline, as the context files require.
