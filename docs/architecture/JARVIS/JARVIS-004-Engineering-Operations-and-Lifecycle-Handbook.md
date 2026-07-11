# JARVIS-004 — Engineering, Operations & Lifecycle Handbook
### Engineering Specification | JARVIS OS Architecture Series
### Status: Draft for Owner Review | Constitution, JARVIS-001, JARVIS-002 & JARVIS-003 Compliance: Verified Throughout

*Fifth and final core document in the JARVIS architecture series. Subordinate to the Constitution, JARVIS-001, JARVIS-002, and JARVIS-003, in that order. Where those four documents specify *what* JARVIS is, this document specifies *how it stays that way* — through years of development, releases, incidents, and the ordinary erosion that happens to every system nobody deliberately maintains against.*

---

## Preface — The Document That Outlives Its Own Novelty

The first four documents are, in a real sense, exciting to write — they invent a constitution, a runtime, a theory of knowledge, a roster of agents. This one is not exciting, by design. It is the document that says what happens on the ordinary Tuesday eighteen months from now when nothing is being invented, something needs a routine dependency bump, and the only question that matters is whether the discipline established in the first four documents survives contact with a system that has stopped being new.

Every section below is written to be checked, not admired. Where a decision is genuinely just "good engineering hygiene" rather than a JARVIS-specific architectural choice, this document says so plainly rather than manufacturing false novelty — the previous four documents earned the right to be ambitious; this one earns its keep by being unglamorous and correct.

---

## 1. Purpose & Scope

**Objectives.** Specify the engineering and operational discipline governing JARVIS's entire lifecycle — development, testing, release, operations, security operations, maintenance, governance, and evolution — closing the loop the first four documents opened.

**Scope boundary.** In scope: how code is written and reviewed, how it's tested, how it's released, how the running system is watched and kept healthy, how secrets and credentials are operated day-to-day (distinct from their architecture, already specified), how technical debt and knowledge staleness are actively managed rather than passively hoped against, and how the whole series itself stays governed as it grows. **Out of scope:** any new architectural decision belonging to the previous four documents — this document implements their standards operationally; it does not add new ones. Where this document appears to introduce something new (for example, credential rotation cadence), it is filling in an operational detail those documents explicitly deferred, never overriding a decision they already made.

**Design rationale for being the "final core document."** The task explicitly designates this as the fifth and closing document in the core series. That closure is itself an architectural claim worth stating plainly: after this document, any further JARVIS-00X documents are extensions of a complete foundation (individual agent behavior specs, the deferred Permission/Approval/Audit Engine internals, a future Security deep-dive), not additional foundation. This document's Part IX (Implementation Roadmap) is written with that closure in mind — it is the one place in the whole series that looks back across all five documents at once.

---

## 2. Relationship to Constitution

This document operationalizes Article IV (Auditability) and Article V (Approval Before Consequence) most directly — Parts III and IV exist almost entirely to make sure those two Articles remain true under the friction of real operations (a 2am incident, a routine dependency update, a rollback under pressure) rather than only under the calm conditions in which they were first designed. Article VII (Amendment) governs Part VII directly: nothing in this document's governance sections claims any authority to amend the Constitution — they specify how *this document series* is kept current, which is a different, lesser thing.

## 3. Relationship to JARVIS-001

This document is the operational counterpart to JARVIS-001's architecture: where JARVIS-001 §17–19 specified Configuration, Secrets, and Dependency Management as architecture, this document's Part I gives them operating cadence. Where JARVIS-001 §26–27 specified Startup/Shutdown Sequences as architecture, this document's Part IV specifies what watching that sequence in production actually looks like.

## 4. Relationship to JARVIS-002

JARVIS-002's Confidence and Trust frameworks (§20, §23) are inert without an operational practice of actually checking calibration against outcomes over time — this document's §36 and §53 are that practice. JARVIS-002 §30's health checks (calibration, staleness) are specified as architecture there; this document specifies their monitoring cadence and response procedure here.

## 5. Relationship to JARVIS-003

JARVIS-003's sixteen-agent roster and operational scenarios are the direct subject of this document's testing (Part II), release (Part III), and maintenance (Part VI) sections — every reference to "an agent" throughout this document means a specific entry from JARVIS-003 Part I unless stated otherwise. JARVIS-003's Emergency Response scenario is formalized into this document's full Incident Response process (§38).

## 6. Engineering Philosophy

**Architectural decisions, extending JARVIS-001 §3 with lifecycle-specific values:**

1. **Reversibility bias in engineering practice, not just in the Approval tiering.** JARVIS-001 §11 already tiers *owner-facing actions* by reversibility. This document extends the same bias to *engineering practice itself* — prefer changes that are cheap to undo (feature flags, staged rollout) over changes that are fast to ship but expensive to reverse, as a default engineering habit, not only where the Constitution requires it.
2. **Documentation is not a deliverable, it's a dependency.** A code change that isn't reflected in the relevant JARVIS-00X document (or that reveals the document needs updating) is not finished — extending JARVIS-001 §37 and JARVIS-002 §36's "re-check the document" requirement from a release gate into a daily engineering habit.
3. **Boring operations, exactly like boring technology.** JARVIS-001 §3 already argued for boring technology so novelty stays concentrated in governance. This document argues the same for operations: the deploy process, the monitoring dashboard, the backup routine should all be the least interesting parts of this system, on purpose.
4. **Solo-operator honesty.** This document does not pretend JARVIS is built and operated by a large team. Every process specified below is written to be genuinely executable by a single owner-engineer working primarily from a mobile GitHub web interface — a beautifully specified process nobody can actually run is worse than a humble one that gets run every time.

---

# PART I — DEVELOPMENT

## 7. Repository Standards

**Objectives.** Give JARVIS-001 §5's transitional single-repo-with-boundaries model concrete operating standards.

**Architectural decision.** Directory structure mirrors the domain tree established in JARVIS-001 §13/JARVIS-003 §13 — a top-level directory per root domain (Engineering, Research, Trading, ProjectOS, and JARVIS Core itself), with child agents nested beneath their parent. Every directory carries a short ownership note naming which JARVIS-00X document governs it. Cross-boundary imports are not tooling-blocked at this stage (consistent with JARVIS-001 §5's honest acknowledgment of current constraints) but are treated as an automatic review flag — any change touching two domain directories at once requires explicit justification in its review, restated here as a standing repository standard rather than a one-time note.

**Alternative considered.** Enforcing boundaries via automated tooling (import linters, path restrictions) from day one. **Advantage:** removes reliance on manual review discipline. **Disadvantage:** meaningful tooling investment the current mobile-only workflow may not support well. **Decision:** deferred, matching JARVIS-001 §5's own transitional stance — revisit once repository size or contributor count justifies the tooling investment, tracked as a Technical Debt Management item (§49).

## 8. Branching Strategy

**Objectives.** Choose a branching model matching both engineering soundness and the project's actual, mobile-constrained working reality.

**Architectural decision.** Trunk-based development with short-lived feature branches and a protected default branch — every change lands on the default branch through a reviewed pull request, never a direct push, matching JARVIS-002 §22's GitHub Agent boundary (branch/PR creation Tier 1; push to protected branch Tier 2 minimum).

**Alternative considered.** Long-lived environment branches (dev/staging/main). Rejected: adds merge overhead disproportionate to a single-owner project's actual release cadence, and long-lived branches are a well-known source of the exact "stale knowledge, documented-vs-actual divergence" risk this project has already experienced once (the Upstox investigation) in a completely different context — the same failure mode (two things that were supposed to be in sync silently drifting apart) is worth avoiding architecturally wherever it can recur.

## 9. Coding Standards

**Objectives.** State the standards specific to a constitutionally-governed system, not generic style rules (language-specific style is explicitly out of scope per the task).

**Architectural decision.** Three standards, restated from architecture into engineering practice: (1) JARVIS-001 §29's smallest-change discipline applies to every commit, not only fixes; (2) any code implementing a Tier 2/3-adjacent path (JARVIS-001 §25's threat-mapped components) carries an explicit comment naming which Article or JARVIS-00X section it implements, so a future reviewer never has to reverse-engineer intent from behavior; (3) no component may silently swallow an error into a default value in any path touching evidence, confidence, or approval logic — restated deliberately, because this project's own logging investigation (JARVIS-001 §20's case study) is a direct, lived example of a silent-failure pattern that took several investigation rounds to catch specifically because nothing forced it to be loud.

## 10. Documentation Standards

**Objectives.** Specify how the JARVIS-00X series itself is written and kept internally consistent, extending JARVIS-003 §17's Documentation Agent mandate into an actual standard.

**Architectural decision.** Every document in the series follows the template established by JARVIS-001 through JARVIS-003: stated scope, explicit relationship to every document above it, and the ten-element reasoning template (objectives, rationale, decision, alternatives, advantages, disadvantages, risks, mitigations, scalability, future evolution) for every major architectural claim. A document or section that only states a conclusion without this reasoning is incomplete, regardless of how correct the conclusion turns out to be — consistent with this document's own Preface.

## 11. Version Control

**Objectives.** Give JARVIS-001 §31's three-identifier versioning model (Constitution, Core, Agent) concrete VCS practice.

**Architectural decision.** Three independent tag namespaces: `constitution-vX.Y` (moved only under Article VII), `core-vX.Y.Z` (JARVIS-001-governed releases), and `agent-{name}-vX.Y.Z` per specialist agent (JARVIS-002 §16 lifecycle-governed). A single commit may bump more than one, but the tags themselves stay separate — collapsing them into one repository-wide version number would recreate exactly the ambiguity JARVIS-001 §31 was written to prevent.

## 12. Dependency Management

**Objectives.** Give JARVIS-001 §19's trust-tiered dependency review an operating cadence.

**Architectural decision.** Dependencies touching the trust-critical path (Permission/Approval/Audit contracts, Integration Gateway) are reviewed on update, every time, regardless of how routine the update appears — no automatic-merge for this category. Dependencies outside that path may use lighter-weight, batched review. This is the direct operational form of JARVIS-001 §19's "proportional to actual risk" principle, made into an actual review policy rather than left as an intention.

## 13. Configuration Management

**Objectives.** Give JARVIS-001 §17's three-class configuration model (operational, structural, constitutional-reference) an operating home.

**Architectural decision.** Operational config (class 1) is freely editable by the owner directly. Structural config (class 2 — registry entries, tier thresholds) requires the Tier 2 review already specified in JARVIS-001 §17, tracked through the same PR process as code (§8). Constitutional references (class 3) are never edited as configuration at all — a change here is definitionally an Article VII event and routes accordingly, restated here specifically because configuration systems have a natural gravitational pull toward "just one more field," and this document exists partly to resist that pull operationally, not just architecturally.

## 14. Secret Management

**Objectives.** Give JARVIS-001 §18/JARVIS-002 §19's task-scoped, expiring secret model an operational home; full lifecycle and rotation cadence are detailed in Part V (§44–45) to avoid duplication here.

**Architectural decision.** No secret is ever committed to the repository, in any form, including in this document series' own examples — restated as an absolute, not a guideline, because "just this once, in a comment, for clarity" is exactly the kind of exception that becomes a real leak eventually.

## 15. Build Standards

**Objectives.** Ensure what gets deployed is exactly what was reviewed.

**Architectural decision.** Builds are reproducible from a specific, tagged commit (§11) with no manual, undocumented build-time steps — anything a build needs beyond the repository's own contents (secrets, environment-specific config) is injected at deploy time through the channels already specified in §13/§14, never baked into a build artifact. This closes a subtle gap: a "reviewed" PR is only meaningful if the artifact actually deployed is provably derived from exactly that reviewed state.

---

# PART II — QUALITY ENGINEERING

## 16. Testing Philosophy

**Objectives.** Restate and extend JARVIS-001 §30's adversarial-testing-as-first-class-category principle as the philosophy governing this entire Part.

**Architectural decision.** Every testing category below answers one of two questions: "does it work" (§17–20, §22) or "does it hold when something tries to make it not work" (§21, §23–25) — and per JARVIS-001 §30, the second category is never treated as optional or lower-priority than the first, even though it's typically the smaller share of a conventional test suite. For a constitutionally-governed system, a passing functional suite with no adversarial coverage is an unverified system, not a verified one.

## 17. Unit Testing

Standard practice, scoped per JARVIS-003's agent and domain boundaries (§13) — a unit test that requires crossing a domain boundary to pass is a signal the boundary itself may be misdrawn, not just a test smell.

## 18. Integration Testing

**Objectives.** Verify the contracts between components specified across the series — Orchestrator↔Permission Engine, Orchestrator↔Approval Engine, Agent↔Registry — actually hold, since JARVIS-001 explicitly scoped those engines' internals out (reserved for a future document) while still depending on their contracts throughout.

**Architectural decision.** Integration tests are written against the *documented contract* (this series' own specifications), not against whatever the current implementation happens to do — a passing integration test against undocumented behavior is worthless the moment that behavior is "corrected" to match the spec and the test breaks for the right reasons. This inversion (spec as ground truth, implementation as the thing being checked) is deliberate and specific to a system this heavily governed by written architecture.

## 19. End-to-End Testing

Directly instantiates JARVIS-003 Part V's twelve operational scenarios as a standing E2E suite — every scenario specified there (Morning Briefing through Owner Approval Workflow) is required to have a corresponding automated or manually-executed test, reviewed on the same cadence as the scenarios themselves would need updating.

## 20. Regression Testing

**Objectives.** Use the Audit Ledger itself (JARVIS-001 §16) as a regression-testing asset, not only a compliance record.

**Architectural decision.** Historical audit entries — real past task graphs, their evidence, their outcomes — form a replay-based regression corpus: a Core or agent change is checked against how the system previously handled real historical situations, not only synthetic test cases. This is a natural, low-cost extension of Article IV's exhaustive audit-writing requirement into a second, engineering-facing use.

## 21. Security Testing

Consolidates the threat lists already named across JARVIS-001 §25, JARVIS-002 §31, and JARVIS-003 §57 into one standing test program, owned operationally by Security Agent (JARVIS-003 §19) once that agent exists, and by the owner directly before it does — this document does not invent new threats, only insists the ones already named across the series are actually, continuously tested against rather than documented once and assumed.

## 22. Performance Testing

Validates JARVIS-002 §29's stated ordering (correctness and auditability are never traded for speed) actually holds under load — a performance test that reveals a component silently skipping an evidence check or an audit write under high concurrency has found a constitutional defect wearing a performance defect's clothes, and must be triaged as the former.

## 23. Disaster Recovery Testing

Tests JARVIS-001 §24's Ledger-reconciled recovery strategy under genuinely induced failure (killed processes, corrupted state, simulated Streamlit Cloud unavailability) — not walkthrough review, actual induced failure — feeding directly into the full Disaster Recovery plan in §39.

## 24. Constitutional Compliance Testing

Formalizes JARVIS-001 §36 and JARVIS-003 §58's acceptance criteria into a **standing, recurring** suite rather than a one-time release gate — every criterion named in any prior document's Acceptance Criteria section is re-verified on a fixed cadence (tied to §47, Compliance Verification), not only at the moment it was first specified.

## 25. Adversarial Testing

Restates JARVIS-001 §30 in full and extends it with JARVIS-003's agent-specific adversarial cases named throughout Part I of that document — Trading Agent's Tier 3 bypass resistance, Deployment Agent's rollback-failure escalation, Voice's channel-handoff enforcement — each already named as an Acceptance Criterion in JARVIS-003 §58, formalized here as permanent, re-run adversarial test cases, not single verification events.

---

# PART III — RELEASE MANAGEMENT

## 26. Build Pipeline

Automated, triggered from a tagged commit (§11/§15) — no manual build-and-ship path exists for anything beyond local, clearly-labeled development iteration.

## 27. Continuous Integration

Every PR runs the full applicable test suite from Part II before merge is even offered as an option — this is standard practice, restated here specifically to note that "applicable" always includes Constitutional Compliance Testing (§24) for any PR touching a JARVIS-001 §25-threat-mapped component, never skipped for expediency.

## 28. Continuous Deployment

**Objectives.** Distinguish what can be genuinely continuous from what must always remain gated, directly extending JARVIS-003 §8's Deployment Agent design.

**Architectural decision.** Continuous *integration* (merge-time testing) is fully automatic. Continuous *deployment* is not, and is never recommended to become fully automatic for anything above Tier 1 — Deployment Agent's every action is Tier 3 by JARVIS-003 §8's own explicit design, and this document does not soften that into "continuous deployment with an approval webhook" or any similar automation-flavored workaround. The gate is the point.

## 29. Release Strategy

Staged rollout, per JARVIS-001 §32, with explicit health verification (JARVIS-003's Production Deployment scenario) between stages — restated here as the operating release strategy, not a new decision.

## 30. Versioning

Semantic versioning (`major.minor.patch`) applied within each of §11's three independent tag namespaces — major bumps reserved for changes that would break a documented contract (§18), never used casually for "this feels like a big change."

## 31. Rollback Procedures

Deployment Agent's rollback capability (JARVIS-003 §8) is the operational mechanism; this section's contribution is the standing rule that a rollback is *always* preferred over a forward-fix under time pressure — a hotfix written and reviewed under incident stress is a higher-risk artifact than reverting to a already-proven-good prior state, and Incident Response (§38) defaults to rollback-first accordingly.

## 32. Change Management

Every change is tier-classified using the same JARVIS-001 §11 rollup logic applied to owner-facing actions, applied here to the change itself: a change touching a Tier 3-relevant component requires Tier 3-equivalent review rigor regardless of how small the diff is, directly restating JARVIS-001 §29's development standard as a formal change-management policy.

## 33. Release Approval Process

For any change classified at Tier 2/3 equivalence (§32), the owner is the final approver, consistent with Article I/V — this document does not introduce any release-approval authority independent of the owner, including for routine-seeming releases, because JARVIS-003 §50 already established that trust growth never removes a gate, and a release-approval bypass would be exactly that removal in a different costume.

---

# PART IV — OPERATIONS

## 34. Monitoring

Restates JARVIS-001 §21's Observability layer as an operational practice: dashboards (JARVIS-003 §33) surfaced continuously, not only checked reactively during an incident.

## 35. Observability

Operational cadence for JARVIS-001 §21: metrics reviewed on a standing schedule (weekly at minimum, given single-owner capacity), not solely incident-triggered — the goal is catching a slow drift (JARVIS-002 §30's calibration/staleness signals) before it becomes an incident, which requires looking even when nothing appears to be wrong.

## 36. Health Monitoring

Operational SLA for JARVIS-003 §20's Health Monitor Agent: Core self-health and pipeline integrity checked continuously; calibration and staleness health checked on a slower, still-regular cadence appropriate to how quickly those signals genuinely change — a calibration score doesn't meaningfully shift hour to hour, and checking it as if it might is wasted attention that competes with genuinely urgent signals for the owner's limited review capacity.

## 37. Alerting

Directly instantiates JARVIS-003 §35's three notification classes (batched, prompt, immediate) as the operational alerting policy — given the single-owner reality, there is no separate "on-call engineer"; the owner *is* the escalation target for every class, which makes §35's batching discipline (protecting immediate-class credibility) operationally essential rather than a nice-to-have.

## 38. Incident Response

**Objectives.** Formalize JARVIS-003's Emergency Response scenario into a complete, named process.

**Architectural decision.** Severity levels map directly to JARVIS-001 §11's tiers: an incident affecting only Tier 0/1 territory is handled at normal operating pace; an incident touching Tier 2/3-relevant components triggers immediate-class notification (§37) and Deployment Agent's rollback-first posture (§31). Every incident, regardless of severity, produces a full audit trail with the same rigor as any other Tier-classified action — restated deliberately from JARVIS-003's Emergency Response scenario, because incident pressure is precisely when evidentiary rigor is most tempting to shortcut and most important not to.

## 39. Disaster Recovery

**Objectives.** Name JARVIS's actual, concrete disaster scenarios rather than treating "disaster recovery" abstractly.

**Architectural decision — named scenarios and recovery posture:**
- **Repository loss or corruption:** Given this project's own established architecture ("GitHub serves as the persistence layer" — a design decision predating this document, restated here as the reason this scenario is existential rather than routine), recovery depends entirely on GitHub's own durability. This document recommends this be named explicitly as an accepted, not mitigated, risk — see §40 for why a from-scratch mitigation isn't proportionate at current scale.
- **Streamlit Cloud unavailability or the deploy-sync issue this project already lived through:** Recovery is a manual reboot/redeploy trigger (already known from direct experience); this document recommends that manual trigger be a documented, rehearsed procedure, not tribal knowledge recreated from memory during an actual outage.
- **External API deprecation or behavior change (the Upstox case, generalized):** Recovery is JARVIS-002 §9's knowledge supersession mechanism plus a migration decision under §63 — named here as a recurring category of disaster this project has direct, lived evidence is not hypothetical.

## 40. Backup Strategy

**Objectives.** State plainly what is and isn't backed up, given the architecture's reliance on GitHub as persistence.

**Architectural decision.** Git history *is* the backup for everything committed — every research database snapshot, every configuration state, every piece of code. This document explicitly does **not** recommend building a separate backup system at current scale: doing so would duplicate GitHub's own durability guarantees for marginal benefit at real engineering cost, violating this document's own reversibility-bias and boring-operations principles (§6) by adding complexity without proportionate risk reduction. **This is revisited explicitly, not by default,** the moment JARVIS's data footprint or criticality grows past what a single git history can reasonably be trusted to protect alone — named as a Future Evolution trigger (§67), not designed against now.

## 41. Business Continuity

**Objectives.** Name the gap Article I's single-authenticated-owner model creates, honestly.

**Architectural decision.** This document does not solve owner unavailability (illness, incapacity, extended absence) — it names it as a real, currently-unaddressed continuity gap, directly connected to Blueprint §23's already-flagged multi-owner decision point. Given the Constitution's explicit single-owner framing, this document recommends this gap be revisited as part of that same future decision, not patched around locally here with an informal "someone else could approve things if needed" workaround — any such workaround would be a de facto Article I amendment implemented through operational policy rather than through Article VII, exactly the kind of soft constitutional reinterpretation the series has repeatedly warned against.

---

# PART V — SECURITY OPERATIONS

## 42. Operational Security

Day-to-day extension of JARVIS-001 §25 and JARVIS-003 §19/§57: routine hygiene (credential audits, dependency review per §12) performed on a standing cadence, not only in response to a finding.

## 43. Identity Management

**Objectives.** Specify what "identity management" means for a genuinely single-authenticated-owner system, per Article I.

**Architectural decision.** No user-account system beyond the single owner is specified or recommended at this stage — session security (device trust, authentication strength) is the actual identity-management surface that matters here, directly supporting §31's/JARVIS-001 §12's confirmation-integrity requirements (a compromised session is JARVIS-001 §25's first named threat). Multi-identity support is explicitly deferred to the same future decision point named in §41.

## 44. Credential Rotation

**Objectives.** Give JARVIS-001 §18's task-scoped-secret architecture an actual rotation cadence, closing the gap §14 deferred.

**Architectural decision.** Because secrets are already task-scoped and expiring by architecture, "rotation" in the traditional standing-credential sense is largely superseded — the operational practice that remains is periodic rotation of the *underlying* long-lived credentials the Permission Engine issues task-scoped grants from (e.g., the root GitHub token, the Upstox account credential), on a fixed calendar cadence regardless of whether any compromise is suspected, consistent with standard security hygiene and cheap given how few standing credentials this architecture actually requires.

## 45. Secret Lifecycle

Full lifecycle, closing §14's deferral: issued (task-scoped, JARVIS-001 §18) → used (Integration Gateway only, JARVIS-001 §18/25) → expired (automatic, at task completion) → the *expiry itself* audited (JARVIS-001 §16) — making secret lifecycle a fully auditable sequence, not only its issuance.

## 46. Audit Operations

**Objectives.** Specify the operational practice of actually reviewing the Audit Ledger, distinct from its architecture (JARVIS-001 §16).

**Architectural decision.** Periodic review (a standing cadence, not only incident-triggered) looking specifically for the pattern JARVIS-001 §12/§25 already named as a risk: a series of individually-justified small permission or scope extensions that, in aggregate, represent drift nobody explicitly approved as a whole. This is the single audit-review practice this document considers non-negotiable, because it's the one failure mode that is by definition invisible to any single audit entry examined in isolation.

## 47. Compliance Verification

Recurring execution of Constitutional Compliance Testing (§24) against the current live system, not only the specification — closing the loop between "the document says this is true" and "this is currently, actually true," on the same cadence as Audit Operations (§46).

## 48. Threat Response

Direct consumer of Security Agent's findings (JARVIS-003 §19): a finding above the severity threshold already named in JARVIS-003 §28 triggers Incident Response (§38) at the corresponding severity; below threshold, findings are tracked and reviewed at the next Audit Operations cycle (§46) rather than triggering immediate escalation for every minor finding, protecting the same alert-credibility principle §37 already established.

---

# PART VI — MAINTENANCE

## 49. Technical Debt Management

**Objectives.** Operationalize JARVIS-001's closing "build to be rebuilt" principle.

**Architectural decision.** A standing, lightweight technical debt register — not a formal ticketing bureaucracy disproportionate to a single-owner project, but an explicit, reviewed list, checked at the same cadence as §61's Continuous Improvement retrospective. §7's deferred repository-tooling decision is this document's first actual entry in that register, named explicitly rather than left implicit.

## 50. Architecture Review Process

**Objectives.** Specify how the JARVIS-00X series itself gets reviewed and amended going forward, extending Blueprint §22's Governance article operationally.

**Architectural decision.** Non-constitutional architectural changes (anything in JARVIS-001 through JARVIS-004, or future documents) follow the same Tier 2 governance process as any structural configuration change (§13) — proposed, evidenced, reviewed, approved, logged — distinct from, and lighter than, Article VII's constitutional amendment process, exactly as Blueprint §22 already specified. This document adds the operating cadence: a full cross-document consistency pass (checking for the kind of drift §10 warns against) at least annually, or immediately after any change significant enough to plausibly ripple across documents.

## 51. Refactoring Policy

**Objectives.** Reconcile JARVIS-001 §29's smallest-change discipline with the real, occasional need for a larger refactor.

**Architectural decision.** A refactor larger than a minimal fix is permitted only when explicitly proposed and justified as a refactor — never smuggled inside an unrelated fix's diff (directly restating JARVIS-001 §14's engineering framework), and only after the smallest-change alternative has been considered and explicitly rejected with stated reasoning, not skipped by default because the larger change felt more satisfying to make.

## 52. Documentation Maintenance

Operational cadence for JARVIS-003 §17's Documentation Agent: drift detection runs continuously in principle, reviewed and actioned at the same cadence as §50's architecture review — documentation maintenance is treated as a first-class, scheduled activity, not an afterthought squeezed in only when a document is noticed to be wrong.

## 53. Knowledge Base Maintenance

Operational cadence for JARVIS-002 §9/§10's supersession-based knowledge lifecycle: staleness health (JARVIS-002 §30) reviewed on a cadence proportional to each knowledge domain's actual volatility — the Upstox integration's knowledge (§41, this document's Disaster Recovery section) reviewed far more frequently than, say, a stable architectural fact about the Constitution's own structure, consistent with §36's principle of matching review frequency to how fast a signal genuinely changes.

## 54. Agent Lifecycle Maintenance

Operational cadence for JARVIS-002 §16's five-state lifecycle: every active agent's trust tier and probation status reviewed at the same cadence as §46's Audit Operations; deprecation is considered, not automatic, whenever an agent's calibration health (JARVIS-002 §30) shows sustained degradation — the decision to deprecate remains a Tier 2 governance action (§50), never triggered automatically by a health signal alone, consistent with JARVIS-002 §20's asymmetric trust-adjustment rule (automatic lowering of standing, but never automatic removal without review).

## 55. Infrastructure Maintenance

Names the platform dependencies this system actually has (GitHub, GitHub Actions, Streamlit Cloud, Upstox, and whatever future integrations join per JARVIS-003 §47) as a standing maintenance surface: each platform's own maintenance windows, deprecation notices, and status pages reviewed at the same cadence as dependency management (§12) — this project's own experience discovering Upstox's V2 deprecation notice *after* it had already caused a production issue is the direct, named justification for treating platform-status monitoring as routine maintenance rather than something only checked reactively once something breaks.

---

# PART VII — GOVERNANCE

## 56. Engineering Governance

Given the single-owner reality (§6, Principle 4), "engineering governance" is the owner applying the standards in Parts I–III consistently to their own work — this document does not invent a governance body that doesn't exist; it specifies the discipline a single owner-engineer holds themselves to, which is the only form of engineering governance actually available at this project's current scale, and is no less real for being self-applied.

## 57. Architecture Governance

Restates §50 as the standing process; this section's contribution is naming the trigger conditions explicitly: any new agent (JARVIS-003 §21), any new integration (JARVIS-003 §47), any change to a JARVIS-001 §25-threat-mapped component, and any accumulated technical debt register item (§49) reaching a self-defined severity all trigger an architecture review, rather than review happening only when someone happens to remember to schedule it.

## 58. Risk Management

**Objectives.** Consolidate the risk registers scattered across all four prior documents (Blueprint §21, JARVIS-001 §33, JARVIS-002 §32, JARVIS-003 §54) into one operationally reviewed master view, plus name the risks specific to this document's own lifecycle concerns.

**Consolidated view (by document of origin) plus new lifecycle-specific entries:**

| Risk | Origin | Operational owner (this document) |
|---|---|---|
| All risks named in Blueprint §21, JARVIS-001 §33, JARVIS-002 §32, JARVIS-003 §54 | Prior documents | Reviewed at §50's annual cross-document pass, not re-litigated here |
| Repository/GitHub as an unmitigated single point of failure | New — §39/§40 | Named, explicitly accepted at current scale, revisit trigger set (§67) |
| Owner unavailability with no continuity plan | New — §41 | Named, explicitly deferred to Blueprint §23's future multi-owner decision |
| Platform deprecation discovered reactively rather than proactively | New — §55, evidenced by the Upstox case | Mitigated by routine platform-status monitoring cadence |
| Technical debt accumulating invisibly without a register | New — §49 | Mitigated by the standing debt register |

## 59. Operational Metrics

**Objectives.** Define what is actually measured, distinct from what is merely monitored (§34–37).

**Architectural decision.** Core operational metrics: approval-request latency (is Tier 2/3 friction proportionate or accumulating unreasonably — a direct check on §37's alert-credibility concern), calibration trend (JARVIS-002 §30, tracked over time, not only point-in-time), incident frequency and severity distribution (§38), and technical debt register size/age (§49). Deliberately not tracked as a headline metric: raw task volume or agent count — this document explicitly rejects "more automated activity" as a success signal in its own right, reserving that judgment for §60.

## 60. Success Metrics

**Objectives.** Define what "JARVIS is working" actually means, tying back to the Blueprint's founding vision.

**Architectural decision.** Primary success metric, directly from the Blueprint's own stated first priority: measurable reduction in manual investigation time for engineering work (the exact goal Blueprint's "First Priority" section names). Secondary metrics: calibration accuracy trending stable-or-improving (never merely "high," per Intelligence Philosophy Principle 1's calibration-over-confidence stance), and — deliberately included as a genuine success signal rather than treated as pure overhead — approval-fatigue absence, measured qualitatively by whether the owner reports still reading confirmations carefully rather than reflexively approving them (JARVIS-001 §11's named risk, made into something this document actually checks for rather than only warns about).

## 61. Continuous Improvement

Standing retrospective cadence (recommended monthly, adjustable to actual capacity) reviewing §58's risk register, §49's debt register, and §59's metrics together — feeding candidate improvements into Learning Agent's pipeline (JARVIS-003 §13) where they're preference-shaped, and into §50's architecture review where they're structural.

---

# PART VIII — FUTURE EVOLUTION

## 62. Upgrade Strategy

Governed by §11/§30's versioning model: a Core or agent upgrade is evaluated against its declared compatibility range (JARVIS-001 §31) before deployment, with any incompatibility treated as a blocking finding, never a warning to be judged case-by-case under release pressure.

## 63. Migration Strategy

**Objectives.** Give the deferred Upstox V2→V3 decision (explicitly left un-implemented across several rounds of this project's own investigation) a general policy, rather than a one-off judgment call each time a migration question like this arises.

**Architectural decision.** A migration is proposed only with direct evidence that the current approach's risk has become disproportionate to migration cost — not preemptively, and not indefinitely deferred either. The V2→V3 case specifically: this document recommends it remain deferred until either V2 shows further behavioral degradation beyond the already-discovered date-range tightening, or a new capability genuinely requires V3 — named explicitly as the applied instance of this policy, closing the loop that investigation left open.

## 64. Legacy Compatibility

Deprecated agent versions (JARVIS-002 §16) retain their audit and episodic history indefinitely (Article IV doesn't expire) but are not required to remain routable — a defined, published deprecation window (tied to §30's versioning discipline) gives advance notice before a version stops being supported, rather than support silently lapsing.

## 65. Future AI Model Upgrades

**Objectives.** Address the case none of the prior four documents named explicitly: the underlying AI model or models powering JARVIS's agents improving in capability over time.

**Architectural decision.** A more capable underlying model is treated exactly like any other agent change under JARVIS-002 §16/§20 — it does not receive elevated trust automatically because it's "smarter"; it re-enters probation and rebuilds its calibration track record like any other agent change would, because JARVIS-002 §20 explicitly measures trust against *demonstrated outcome*, not *claimed or inherent capability*. This is stated as a standing position specifically because "the new model is obviously better" will be a genuinely true and genuinely tempting argument for skipping probation at some point in the next decade, and this document commits in advance to not accepting that argument when it comes.

## 66. Future Agent Expansion

No new content — this document defers entirely to JARVIS-003 §21's Future Agent Expansion Framework, which already fully specifies this. Restated here only to confirm no competing process exists.

## 67. Long-Term Sustainability

**Objectives.** Name the single-owner "bus factor" risk plainly, and state this document series' actual answer to it.

**Architectural decision.** This entire five-document series *is* the sustainability strategy for a solo-owner project — not a workaround for the absence of a team, but a genuine substitute for the tacit knowledge a team would otherwise hold collectively. A future engineer, or a future version of the owner returning after time away, or a future AI coding agent (named explicitly in the original task's own closing line) should be able to reconstruct correct JARVIS behavior from these five documents without the original author present. This document's own §40 (accept GitHub as the backup) and §41 (name owner-unavailability as an open gap) are honest admissions that sustainability isn't fully solved — but documentation-as-continuity, applied this rigorously, is the best available answer at current scale, and is itself named here as the thing to revisit if that scale ever genuinely changes.

---

# PART IX — IMPLEMENTATION ROADMAP

## 68. Development Phases

Consolidating every phase reference across all five documents into one master sequence:

- **Foundation:** JARVIS-001 Core Phases 0–5 (bootstrap through multi-agent health monitoring at scale).
- **Intelligence:** JARVIS-002 Intelligence Phases 0–5 (memory through learning), explicitly sequenced *after* Core Phase 1 proves the request lifecycle, per JARVIS-002 §34's own stated dependency.
- **Agents & Experience:** JARVIS-003's roster build-out (§48's suggested Engineering/GitHub → Research/Trading → Planner/Calendar/Reminder → remainder sequencing), dependent on Intelligence Phase 2 (Base Agent Specification + Lifecycle) being stable.
- **Operations:** This document's Parts I–VII, which are not a distinct "phase" so much as a discipline that should be present from Foundation Phase 0 onward — testing philosophy (§16) and documentation standards (§10) are not deferred to "later," they govern every phase above from the start.

## 69. Milestones

Directly reusing the acceptance criteria already specified in JARVIS-001 §36, JARVIS-002 §35, and JARVIS-003 §58 as the actual milestone-completion tests for the corresponding phases in §68 — this document does not invent new milestones where the prior three already specified exactly what "done" looks like for their own layer.

## 70. Acceptance Criteria

**For this document specifically:**
- Every process specified in Parts I–VII is demonstrated to be executable by a single owner-engineer using the project's actual current tooling (mobile, GitHub web UI) — a process that only works with tooling this project doesn't have is not accepted as complete.
- The consolidated risk register (§58) is demonstrated to actually reference, not duplicate, every risk named in the four prior documents.
- Constitutional Compliance Testing (§24) is demonstrated to run successfully as a *recurring* suite, not only as a one-time check at first implementation.
- §63's migration policy is demonstrated against the real, already-known Upstox V2/V3 case and produces the same recommendation stated in §63 when re-derived from the policy alone — proving the policy is actually usable, not just plausible-sounding.

## 71. Definition of Done

**The JARVIS-00X core architecture series, as a whole, is considered complete and implementation-ready when:**

1. All five documents (Constitution, JARVIS-001 through JARVIS-004) pass their own individually-stated Acceptance Criteria and Definition of Done sections.
2. This document's §70 criteria are independently satisfied.
3. A full cross-document consistency pass (§50) has been performed at least once across all five documents together, with any found drift resolved before this series is declared closed.
4. The owner has explicitly reviewed and approved the series as a whole — consistent with Article I/V, no document in this series, including this one, is ever self-certifying.

Once these four conditions hold, per the original task's own framing, the core JARVIS architecture series is complete. Everything built from this point forward is implementation against a finished foundation, or a new document extending it — never a document that has to re-justify the foundation itself again.

---

## Closing Statement

Five documents ago, this series started by asking what JARVIS is for. It ends here asking something much less glamorous and, over ten years, much more decisive: who checks the dependency update, who reads the incident report at 2am, who notices the knowledge base has gone quiet on a fact nobody's rechecked in eight months. Every other document in this series can be right in the abstract and still fail in practice if this one is ignored. That asymmetry — architecture is necessary, operations is what actually determines whether the architecture survives — is the entire reason this document exists, and the entire reason it was written to be checked rather than admired.
