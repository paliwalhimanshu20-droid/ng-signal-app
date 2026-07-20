# ASDP-001 — Autonomous Software Delivery Platform: Feasibility Assessment & Phased Roadmap
### Integration Specification | Subordinate to the Constitution, JARVIS-001–004, AUTH-001/002
### Status: Draft for Owner Review — **read this before any code lands on Phases 3+**

## Why this document exists before any Phase 3+ code

Seventeen phases were specified. Several of them describe things that are genuinely impossible on a stock, non-rooted Android phone, no matter how the code is written — not "hard," not "needs a library," but blocked by the Android OS itself at a level no app-level code can get around. Writing code against those phases without saying so first would mean shipping something that either silently does less than the phase describes, or (worse) needs Termux/root to work at all despite this brief's own "the owner should never need... Termux" requirement. This document says, plainly, which phases are real, which need reframing, and which aren't possible as written — then Phase 1 (Package Intake) ships as real, working code in this same delivery, because it's the one phase with zero dependency on the open questions below.

## The two hard walls, stated first

**1. There is no on-device Gradle/Android-SDK/JDK build toolchain reachable from a normal app.** Phase 7/8 ask for "build locally on the Android device" as the primary path, falling back to GitHub Actions only when local build isn't possible. In reality, the reverse is true: a regular Android app runs in an OS-enforced sandbox that does not permit spawning an arbitrary process tree, and there is no bundled JDK + Android SDK + Gradle distribution designed to run *inside* another app's own process (this is different from, say, Termux, which is its own full Linux userland the OS treats as a separate, user-consented environment — exactly what this brief rules out). Building a real embedded toolchain would mean shipping multiple gigabytes of JDK/SDK/Gradle inside JARVIS itself, with no precedent I'm aware of for a consumer app doing this, and even then Android's process sandboxing makes it doubtful `ProcessBuilder`/`Runtime.exec()` could actually invoke it. **Verdict: not feasible as "the primary path" on stock Android.** GitHub Actions is not the fallback — it's the only realistic path, for every project type, every time. Phase 8 should be re-scoped as "always remote," not "adaptive."

**2. APK installation cannot be silent, ever, for a normal app.** Phase 10/11 describe JARVIS asking for owner approval, then installing. That owner-approval step in JARVIS's own UI is real and buildable — but it is *not* the only gate. Android's `PackageInstaller`/`ACTION_VIEW`-with-`REQUEST_INSTALL_PACKAGES` APIs always show a second, OS-level system confirmation dialog that no app (short of being the device owner via MDM enrollment, which JARVIS is not) can suppress. **Verdict: feasible, but "Install APK" is always a two-step approval — JARVIS's own screen, then Android's own dialog — not fully silent/autonomous.** Worth stating up front so this isn't a surprise later.

## Phase-by-phase feasibility

| Phase | Verdict | Note |
|---|---|---|
| 1. Package Intake | **Buildable as specified.** Shipped this round. | ZIP reading, checksum, structure/language detection, traversal-safety checks -- all standard `java.util.zip` + Android Storage Access Framework, no special permission beyond picking a file. |
| 2. Engineering Intelligence | **Buildable**, heuristic-based (file/dependency signatures), not a real language-server-grade analysis. | Detecting "this has a `pubspec.yaml`, it's Flutter" is pattern matching, not compilation -- accurate for common cases, will misclassify unusual project layouts. Should say so in its own output, not claim certainty it doesn't have. |
| 3. Repository Intelligence | **Buildable via GitHub REST API** (search repos, compare via Contents/Git Trees API), reusing the real `GitHubStatusProvider`/token infrastructure already in this app. | No local `git clone` -- see the git note below. |
| 4. Deployment Preview | **Buildable**, contingent on Phase 3/6 existing first. | |
| 5. Owner Approval | **Buildable today, almost for free** -- this app already has a real, working approval state machine (`ApprovalRepository`, Sprint 9) that every one of the listed actions (repo creation, push, APK install, rollback, etc.) can route through unchanged. | |
| 6. Deployment Engine ("safely extract... commit... push") | **"push" needs reframing.** No local git binary is reachable on stock Android (same reasoning as the build-toolchain wall above) -- "commit and push" becomes "build a tree of changed files and call GitHub's Contents API / Git Data API (create blobs, a tree, a commit, update the ref) once approved." This is a real, well-documented GitHub API surface and requires no local git at all -- and it's exactly the pattern this ecosystem's own `ng-signal-app` already uses today (`signal_log.csv` pushed via the Contents API, per that project's own established pattern) — so it's not a new idea, it's the existing convention. | |
| 7. Intelligent Build Engine | **Buildable** as "detect which GitHub Actions workflow template fits" -- see the build-toolchain wall; there is no "local" branch of this decision. | |
| 8. Adaptive Build Strategy | **Re-scoped: always remote (GitHub Actions).** No adaptive decision to make once local build is ruled out. | |
| 9. Artifact Intelligence | **Buildable** -- GitHub Actions' own artifact-upload/download REST API returns exactly this. | |
| 10. Device Deployment | **Buildable, with the two-step-approval caveat above stated in the UI itself**, not left implicit. | |
| 11. Automated Validation | **Partially feasible.** "Installed successfully, correct package/version" -- yes, `PackageManager` confirms this directly. "Launches without an immediate crash" -- approximately, by launching via intent and checking the process is still alive after a short window; a real crash-log read (`logcat`) requires a permission normal apps don't have on modern Android, so this is an approximation, not a guarantee, and should be labeled as such in the report rather than asserted as fact. | |
| 12. Executive Deployment Report | **Buildable**, once 3–11 exist. | |
| 13. Timeline Integration | **Already exists and already works** -- this app's `AuditRepository` + `ExecutiveTimeline` (Sprint 13) is exactly the "Timeline-First Executive Memory System" this phase describes. Every deployment event this feature produces should be an `AuditRepository.record(...)` call, the same mechanism Connections/Approvals/Tools already use -- not a second, parallel timeline system. | |
| 14. Rollback Center | **Buildable via the GitHub API** (revert via a new commit restoring prior tree state, reinstall a previously-downloaded artifact) once 3–10 exist. No local git means no local `git revert` -- same reframing as Phase 6. | |
| 15. Watch Tower Integration | **Already exists and already works** -- `WatchTowerOrchestrator`/`WatchTowerAgents` (Sprint 12) is real, with the same "convene requires explicit approval, never runs a specialist unprompted" governance this brief itself asks for. | |
| 16. Security | **Directly achievable using patterns already established in this codebase**: never-execute-archive-contents (Phase 1 ships this), path-traversal validation (Phase 1 ships this), credentials-never-logged (established convention since `GoogleAuthManager`), approval-gated writes (`ApprovalRepository`, already real). | |
| 17. Vision | Achievable **as a GitHub-API-driven, remote-build pipeline with a two-step install confirmation** -- not as a fully local, silent, zero-OS-interaction system. Worth the owner reading this reframing before Phase 3+ work begins, since it changes what "autonomous" honestly means here. | |

## What ships this round: Phase 1, for real

`Package Intake` — the one phase with zero dependency on any of the open questions above. Real Storage Access Framework file picker, real ZIP parsing (`java.util.zip`, no third-party dependency), real SHA-256 checksum, real directory-traversal and unsafe-path detection (rejecting `..`, absolute paths, and symlink-like entries before anything is ever extracted -- and Phase 1 never extracts anything, only reads metadata, exactly as specified: *"Never execute anything inside the archive"*), real project-type heuristics (presence/absence of marker files: `build.gradle(.kts)`, `pubspec.yaml`, `package.json`, `requirements.txt`/`pyproject.toml`, `pom.xml`), and a real `AuditRepository.record(...)` call for "ZIP Imported" + "Engineering Analysis" -- Phase 13's first two timeline events, wired to the actual existing Timeline system, not a placeholder.

## Recommended sequencing for Phases 2–17

1. **Phase 2 + 3 + 6 together** (Engineering Intelligence -> Repository Intelligence -> Deployment Engine-via-Contents-API): the smallest slice that gets a real file change onto GitHub, which is the actual value this whole feature is for.
2. **Phase 5 (Approval)**: mostly wiring existing `ApprovalRepository` into the new flow, not new governance.
3. **Phase 4 (Preview) + 12 (Report)**: UI over what 2/3/6 already produce.
4. **Phase 7 + 9 (remote build + artifact intelligence)**: GitHub Actions REST API.
5. **Phase 10 + 11 (install + validation)**: the two-step-approval install, with validation's limits stated honestly in its own output.
6. **Phase 14 (rollback)**: once 6 exists, rollback is "the same Contents API pattern, in reverse."
7. **Phase 15 (Watch Tower)**: wire in last -- it's real and ready, but has the least urgency of anything on this list.

Each numbered group above is sized to be its own reviewable delivery, not a single 17-phase drop.

---

# Addendum: Code Review RC-001–RC-007 (abstractions added before any Phase 2+ logic)

A review of the Phase 1 delivery required three architecture seams to exist **before** Phase 2/3/6/7 are coded, not retrofitted after -- reasonable, since Phase 1 itself never touches GitHub Actions, the GitHub API, or repository operations at all, so there was nothing "hard-coded" to fix yet, only a decision about how the *next* round should be built. All three now exist, each with exactly one real implementation (per the review's own "today only [current] should be implemented" instruction -- no unused GitLab/Bitbucket/Azure DevOps stub classes):

- **`BuildEngine`** (RC-001) -- `GitHubActionsBuildEngine` is the only implementation. Current engine: **GitHub Actions**. Future supported engines, not yet implemented: **Local Build Engine** (blocked on-device per this document's own feasibility wall until that changes), **Cloud Build Engine**, **Enterprise Build Engine**, **Custom Build Engine**. Mission Control will depend on this interface once Phase 7/8 build its UI -- never on GitHub Actions directly.
- **`DeploymentEngine`** (RC-002) -- `GitHubApiDeploymentEngine` is the only implementation, using GitHub's real Git Data API (blob -> tree -> commit -> ref update) because Android has no accessible Git CLI (this document's own reframing of Phase 6, now implemented for real). Future deployment providers -- GitLab, Bitbucket, Azure DevOps, self-hosted Git -- can be added without changing Deployment Center's higher-level logic once it's built.
- **`RepositoryProvider`** (RC-003) -- `GitHubRepositoryProvider` is the only implementation. Repository selection UI (Phase 3, not yet built) will read `providerName` rather than hardcoding "GitHub," so it's provider-agnostic from the start.
- **RC-004 (Build Queue state machine)**: `BuildState` enum now exists on `BuildEngine` with exactly the states requested (Queued through Cancelled). Live-progress UI is deferred until Phase 7/8 actually builds Mission Control's build screen -- the state machine is ready for it now rather than needing to be invented at that point.
- **RC-005 (APK install UX copy)**: adopted verbatim as the planned Phase 10 copy --

  > "Your build has completed successfully. Android will now display its official installation screen. Please approve the installation there. I'll continue verification automatically once installation finishes."

  Not implemented yet (Phase 10 itself isn't built), but locked in now so Phase 10 doesn't need a separate UX pass later.

All four engine classes share one real, reusable HTTP client (`GitHubApiClient`), reusing the existing `GitHubTokenStore` credential (the same Personal Access Token `GitHubStatusProvider` already uses) rather than inventing a second GitHub credential store.

