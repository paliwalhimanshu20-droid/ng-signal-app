# JARVIS-003 — Specialist Agents & User Experience
### Engineering Specification | JARVIS OS Architecture Series
### Status: Draft for Owner Review | Constitution, JARVIS-001 & JARVIS-002 Compliance: Verified Throughout

*Fourth document in the JARVIS architecture series. Subordinate to the Constitution, JARVIS-001, and JARVIS-002, in that order of authority. This document does not redefine the Orchestration Layer (JARVIS-001), the Evidence/Confidence/Trust frameworks (JARVIS-002), or the Approval/Audit contracts (JARVIS-001 §15–16). It populates those frameworks with the actual agents that will run under them and specifies how the owner experiences the resulting system.*

---

## Preface — What This Document Adds

The first three documents built a constitution, a machine to route and gate work, and a theory of how that machine knows things and trusts its parts. None of them named a single specific agent. This document is where JARVIS stops being an architecture and starts being a system someone can actually say "good morning" to.

Every agent specified in Part I is a concrete instantiation of JARVIS-002 §15's Base Agent Specification — nothing here introduces a new structural category of agent, only specific domain instances of the one category that document already defined. Every operational scenario in Part V is a concrete walk through JARVIS-001 §9's request lifecycle and JARVIS-001 §15's approval gating, with named agents instead of abstract "task nodes." Where this document repeats a term from the prior three, it is invoking that document's definition, never redefining it.

---

## 1. Purpose & Scope

**Objectives.** Specify the initial roster of specialist agents, the multi-agent operational patterns between them, the owner-facing experience layer, the external ecosystem they integrate with, and the end-to-end scenarios that tie all of it together.

**Scope boundary.** In scope: each named agent's mission, boundaries, and constitutional posture (not its internal algorithms — an agent's actual trading heuristics or code-review technique is implementation, not architecture, and stays out of this series entirely, consistent with JARVIS-002 §1's boundary against domain-specific logic). Also in scope: the Interface Layer's concrete shape (voice, chat, dashboard, briefings) as distinct from JARVIS-001's abstract "Interface Layer" box. **Out of scope:** Permission Engine, Approval Engine, and Audit Ledger internals (still reserved for a future JARVIS-00X), and any specific third-party service's API details (those belong in integration-specific technical documentation, not architecture).

**Design rationale.** Sixteen named agents plus a full user-experience layer is, deliberately, the largest surface area any document in this series has covered — because it is the first document where the abstract framework meets the owner's actual daily reality, and that reality does not decompose as cleanly as the layers above it did. Where a genuine architectural question doesn't have a clean answer, this document says so rather than forcing a tidy resolution the operational reality won't actually support.

---

## 2. Relationship to Constitution

Every agent specified below inherits Article II's floor without exception and without a mechanism for override. The "Approval Requirements" field in every agent specification (Part I) is a per-agent restatement of JARVIS-001 §11's tiering, never a competing scheme — an agent that appears to need a different approval model has surfaced a genuine tiering gap in JARVIS-001, not license to invent a bespoke process here (see §21, Future Agent Expansion Framework, for how such gaps get escalated back into the core specifications rather than patched locally).

## 3. Relationship to JARVIS-001

This document is a **consumer** of JARVIS-001's routing (§13), approval calls (§15), and audit writes (§16) — never a redefiner. Where an agent's "Collaboration Rules" field describes it working with another agent, that collaboration always executes through JARVIS-001's Orchestrator; no agent specification below implies or permits a direct channel.

## 4. Relationship to JARVIS-002

Every agent's "Trust Level," "Required Capabilities," and "Failure Modes" fields are instances of JARVIS-002's Trust Model (§20), Capability Framework (§18), and calibration health monitoring (§30) respectively. Where this document assigns a specific starting trust tier to a named agent, that assignment is a Tier 2 governance decision under JARVIS-002 §16's lifecycle — stated here as the initial registry value, not a permanent one.

---

## 5. Operational Philosophy

**Architectural decisions, stated as principles:**

1. **Named agents are disposable; the framework is not.** Any agent in Part I could be redesigned, merged, split, or retired without touching the Constitution, JARVIS-001, or JARVIS-002 — this is the direct payoff of the layered series, and this document is written to be the most replaceable one in it.
2. **The owner experiences one system, never a roster.** Every UX section in Part III is designed against the Blueprint's founding claim that "JARVIS is the only interface the owner interacts with" — a design that requires the owner to know which of sixteen agents to address has already failed this principle, regardless of how well each agent works individually.
3. **Operational convenience never outranks the approval tiering.** Every scenario in Part V is written to show the tiering working, including where it adds friction — a scenario document that quietly shows every example going smoothly would be marketing, not architecture.
4. **Every agent's boundary is a boundary against the other agents, not just against the owner.** The riskiest failure mode at this layer isn't an agent overstepping with the owner — JARVIS-001/002's approval and permission machinery already constrains that hard. It's two agents' boundaries overlapping just enough that a task quietly gets handled by whichever one happened to claim it first.

---

# PART I — SPECIALIST AGENT ECOSYSTEM

*Every agent below is specified against the same eleven fields for direct comparability. Trust Level values (Provisional / Standard / Elevated) are JARVIS-002 §20 registry defaults at launch, subject to that document's continuous recomputation — not fixed ratings.*

## 6. Engineering Agent

- **Mission:** Reduce manual investigation load across the entire codebase and its CI/CD surface — the Blueprint's explicit first priority (Blueprint, "First Priority" section).
- **Responsibilities:** Repository inspection, workflow/log inspection, failure investigation, PR preparation, code-path comparison, evidence-graded fix proposals (JARVIS-002 §22–24).
- **Boundaries:** Never merges or deploys its own proposals (that authority sits with the owner via Approval, not with this agent regardless of confidence). Does not own GitHub-account-level actions, deployment execution, or database schema changes directly — those are delegated to child-domain agents (§7–9) under JARVIS-001 §13's Engineering-as-root domain tree.
- **Inputs:** Repository state, workflow logs, prior audit entries for related past investigations (Episodic Memory, JARVIS-002 §7).
- **Outputs:** Investigation reports (JARVIS-002 §25 standard), scoped fix proposals with Blueprint Principle 12's six required fields.
- **Trust Level:** Standard at launch — this project's own investigation history already demonstrates a working evidentiary discipline for this domain, unusual for a first-launch agent, justifying starting above Provisional while still short of Elevated pending a full probation cycle (JARVIS-002 §16).
- **Required Capabilities:** Read access to repository/workflow/log surfaces; proposal-drafting; explicitly **not** direct write/execute capability — proposals only.
- **Approval Requirements:** Investigation and reporting are Tier 0. Any proposed code change is Tier 2 minimum, Tier 3 if it touches anything in JARVIS-001 §25's threat-mapped components.
- **Collaboration Rules:** Delegates GitHub-specific actions to the GitHub Agent (§7), deployment actions to the Deployment Agent (§8), schema actions to the Database Agent (§9) — always through the Orchestrator, per JARVIS-002 §21.
- **Failure Modes:** Proposing a fix broader than the confirmed root cause (mitigated by JARVIS-001 §29's smallest-change discipline, enforced at review); investigating with stale knowledge (mitigated by JARVIS-002 §10/§30's staleness monitoring).
- **Future Evolution:** Long-term candidate to absorb increasingly autonomous *investigation* (never execution) as its calibration health (JARVIS-002 §30) proves out over years, per Blueprint Phase 1.

## 7. GitHub Agent

- **Mission:** Serve as the Engineering Agent's exclusive interface to repository, workflow, and pull-request state.
- **Responsibilities:** Repository queries, workflow-run retrieval, PR creation/update at the Engineering Agent's direction, branch management.
- **Boundaries:** Never initiates an investigation or fix independently — it is a capability provider to Engineering, not a peer investigator, consistent with JARVIS-002 §13's domain-tree resolution (child of Engineering).
- **Inputs:** Explicit, scoped requests from the Engineering Agent, routed through the Orchestrator.
- **Outputs:** Raw repository/workflow data, PR objects — deliberately *not* interpreted findings; interpretation is Engineering's job, keeping this agent's evidence grade at "direct observation" (JARVIS-002 §22 item 1) rather than blending in inference.
- **Trust Level:** Standard — narrow, well-defined capability surface with low interpretive risk.
- **Required Capabilities:** Task-scoped GitHub API access (JARVIS-001 §18 pattern — credentials never held standing).
- **Approval Requirements:** Read operations Tier 0; branch/PR creation Tier 1; any push to a protected branch Tier 2 minimum, matching JARVIS-001 §32's deployment-adjacent discipline.
- **Collaboration Rules:** Receives delegated tasks only from Engineering Agent; never receives direct owner instructions.
- **Failure Modes:** Rate-limit exhaustion under investigation load (mitigated by request throttling, a Performance Optimization concern per JARVIS-002 §29's non-evidentiary optimization surface); stale credential scope (mitigated by JARVIS-001 §18's expiring grants).
- **Future Evolution:** Natural candidate for deeper CI/CD-native integration as the Deployment Agent (§8) matures alongside it.

## 8. Deployment Agent

- **Mission:** Execute approved deployment actions with maximum auditability and minimum blast radius.
- **Responsibilities:** Staged rollout execution, rollback execution, deployment-health verification pre- and post-release.
- **Boundaries:** Never decides *what* to deploy — that determination belongs to Engineering Agent proposals plus owner approval; this agent only executes an already-approved action.
- **Inputs:** An approved deployment task (post-Tier-2/3 gate), current system health signals (JARVIS-001 §22).
- **Outputs:** Deployment status, rollback confirmation if triggered, full audit trail per JARVIS-001 §32's staged-rollout discipline.
- **Trust Level:** Provisional at launch, regardless of Engineering Agent's own trust level — this is deliberately the highest-consequence agent in the Engineering domain, and JARVIS-002 §16's mandatory probation applies at maximum scrutiny here specifically.
- **Required Capabilities:** Scoped deployment-platform access, rollback trigger capability — both task-scoped, never standing.
- **Approval Requirements:** Every action is Tier 3 by default; no deployment action is ever demoted below Tier 3 regardless of how routine it appears, consistent with JARVIS-001 §32.
- **Collaboration Rules:** Executes only tasks originated by Engineering Agent proposals that have cleared approval; reports outcome back through the Orchestrator for audit and for Episodic Memory's outcome classification (JARVIS-002 §7).
- **Failure Modes:** Partial deployment leaving an inconsistent state (mitigated by JARVIS-001 §24's Ledger-reconciled recovery — never auto-retried); rollback itself failing (must escalate to owner immediately, never silently retry a failed rollback).
- **Future Evolution:** Trust tier is expected to rise slowly and only through demonstrated track record (JARVIS-002 §20) — this is the one agent in the roster where this document explicitly recommends resisting pressure to accelerate trust growth, given the asymmetry between routine successes and rare catastrophic failures.

## 9. Database Agent

- **Mission:** Own schema inspection, migration proposal, and data-integrity verification across JARVIS's own persistence layer and any specialist agent's domain databases.
- **Responsibilities:** Schema inspection, migration drafting, data-integrity checks, query-pattern analysis for performance concerns flagged by Health Monitoring.
- **Boundaries:** Never executes a schema migration without explicit approval; never has standing write access to production data.
- **Inputs:** Schema state, migration history, Database-relevant findings from Engineering Agent investigations.
- **Outputs:** Migration proposals (Blueprint Principle 12 fields, including rollback plan — non-negotiable for anything touching schema).
- **Trust Level:** Provisional — schema changes are structurally close to Deployment Agent's risk profile and are treated with comparable caution.
- **Required Capabilities:** Scoped, read-primary database access; write/migration-execute capability granted only per approved task.
- **Approval Requirements:** Inspection Tier 0; migration proposal Tier 1 (it's just a proposal); migration execution Tier 3, always.
- **Collaboration Rules:** Peer to GitHub and Deployment agents under Engineering; may be invoked directly by Research or Trading agents (§10–11) for their own domain schemas, always through the Orchestrator.
- **Failure Modes:** A migration proposal that's technically correct but operationally destructive under load (mitigated by requiring the same staged-rollout discipline as Deployment Agent, JARVIS-001 §32, for any migration affecting live data).
- **Future Evolution:** Long-term candidate to absorb automated data-integrity monitoring as a Tier 0 continuous capability, feeding Health Monitoring (§20) rather than only acting on request.

## 10. Research Agent

- **Mission:** Produce evidence-graded market and instrument research, per the Blueprint's NG Signal Pro priority (Blueprint, "NG Signal Pro Priority" section).
- **Responsibilities:** Historical intelligence synthesis, confidence-scored signal research, indicator-relationship study, probability-of-success analysis for defined setups.
- **Boundaries:** Never places or recommends an actual trade — that is Trading Agent's exclusive domain (§11); Research Agent's output is analytical, not actionable, a distinction this document treats as load-bearing, not stylistic.
- **Inputs:** Market data (via Integration Gateway, JARVIS-001 §18/25), Knowledge Store entries (JARVIS-002 §9), historical outcome data (Episodic Memory).
- **Outputs:** Research reports carrying full JARVIS-002 §22–23 evidence/confidence grading — never a bare conclusion.
- **Trust Level:** Standard, with calibration health monitoring (JARVIS-002 §30) weighted especially heavily here given the direct financial relevance of miscalibration in this domain specifically.
- **Required Capabilities:** Market-data read access, historical-intelligence-engine query access, no execution capability whatsoever.
- **Approval Requirements:** Research generation and reporting Tier 0/1 (informational); any output that would materially inform a Tier 2/3 trading decision is flagged as such at generation time, not left for the owner to infer.
- **Collaboration Rules:** Feeds Trading Agent's decision inputs (§11) but is never itself gated by Trading Agent's approval requirements — the two are peers under a shared root domain, not a dependency chain.
- **Failure Modes:** Overconfident output in low-liquidity or low-sample-size conditions (mitigated by JARVIS-002 §23's evidence-quantity factor, which structurally caps confidence on thin data); stale market-regime assumptions (mitigated by knowledge staleness monitoring, JARVIS-002 §30).
- **Future Evolution:** Primary candidate for the Blueprint's "study Natural Gas deeply before scaling to every instrument" directive — this document recommends Research Agent's own internal scope (not its architecture) narrow to Natural Gas specifically at launch, widening only once its calibration track record justifies it.

## 11. Trading Agent

- **Mission:** Translate Research Agent output plus owner risk parameters into concrete, approved trading actions.
- **Responsibilities:** Position sizing proposals, entry/exit recommendation, risk-parameter adherence checking.
- **Boundaries:** Never generates its own market research — consumes Research Agent's graded output exclusively, never substitutes its own analysis for it (a deliberate separation of "what does the evidence say" from "what should we do about it," preventing one agent from grading its own homework).
- **Inputs:** Research Agent output, owner-defined risk parameters (Semantic Memory, JARVIS-002 §8, approval-scoped), current position state.
- **Outputs:** Trade proposals with full Blueprint Principle 12 fields — every proposal is, by this document's default classification, **Tier 3**, without exception, given financial irreversibility.
- **Trust Level:** Provisional, held there deliberately longer than other agents' typical probation window — financial consequence justifies the most conservative trust posture of any agent in this roster.
- **Required Capabilities:** Read access to Research output and position state; proposal-only — this document recommends Trading Agent **never** be granted direct execution capability against a live brokerage connection, full stop, with actual order placement remaining a distinct, even-more-restricted capability reviewed separately if it is ever introduced at all.
- **Approval Requirements:** Tier 3, always, no exceptions, no learned-preference shortcut permitted (directly enforced by JARVIS-002 §11's rule that approval-scoped behavior can never be promoted by recurrence alone).
- **Collaboration Rules:** Consumes from Research Agent; reports outcomes back to Episodic Memory for both its own and Research Agent's calibration tracking.
- **Failure Modes:** Risk-parameter drift (mitigated by treating risk parameters as approval-scoped Semantic Memory, never silently adjustable); recommending against stale research (mitigated by requiring Research Agent's confidence figures to carry current recency scoring at proposal time, not cached).
- **Future Evolution:** Explicitly, this document does *not* recommend a future path toward autonomous execution — see §50 (Autonomous Assistance Boundaries) for why this is a standing architectural position, not an oversight.

## 12. ProjectOS Agent

- **Mission:** Coordinate cross-project state for the owner's broader engineering portfolio (of which NG Signal Pro is the current flagship, not the only instance).
- **Responsibilities:** Cross-project status synthesis, dependency tracking between projects, surfacing which project needs attention.
- **Boundaries:** Synthesizes; does not execute within any individual project — actual work in a given project routes to Engineering Agent (§6) and its children.
- **Inputs:** Status signals from Engineering Agent across all tracked projects, owner-stated priorities (Semantic Memory).
- **Outputs:** Cross-project briefings, priority recommendations, feeding directly into the Daily Briefing System (§34).
- **Trust Level:** Standard — synthesis and prioritization carry lower direct-action risk than execution-adjacent agents.
- **Required Capabilities:** Read access to Engineering Agent's status outputs across projects; no independent execution.
- **Approval Requirements:** Tier 0 for synthesis and briefing; any recommendation that would reprioritize owner attention in a way that affects standing schedules routes through Planner Agent (§14) rather than acting directly.
- **Collaboration Rules:** Sits structurally as a peer to Engineering Agent, consuming from it, never issuing it instructions — a synthesis layer, not a management layer, consistent with Constitutional Principle 3's ban on agents ranking above each other.
- **Failure Modes:** Synthesis quietly implying a decision was made about priority when none was owner-approved (mitigated by JARVIS-002 §24's requirement that recommendations remain advisory, never self-executing).
- **Future Evolution:** Natural home for the Blueprint's "Jarvis, investigate today's failures" cross-project voice command once Voice Experience (§32) matures.

## 13. Learning Agent

- **Mission:** Operate the Learning Framework's promotion pipeline (JARVIS-002 §11) as a dedicated agent rather than an implicit background process.
- **Responsibilities:** Pattern detection across Episodic Memory, candidate preference drafting, scope declaration (style vs. approval-affecting, JARVIS-002 §8).
- **Boundaries:** Never writes directly to Semantic Memory (JARVIS-002 §12's read/write separation is enforced *against this agent specifically* — it drafts candidates; only the governed promotion pipeline commits them).
- **Inputs:** Episodic Memory, existing Semantic Memory (to avoid redundant candidate drafting).
- **Outputs:** Candidate preference proposals, always carrying their supporting episodic evidence and scope declaration.
- **Trust Level:** Standard for style-scoped candidates; any approval-scoped candidate this agent drafts is treated as Provisional-grade scrutiny regardless of this agent's own overall trust tier, mirroring Deployment Agent's per-action override pattern (§8).
- **Required Capabilities:** Read access to Episodic and Semantic Memory; write access only to a candidate-proposal queue, never to active Semantic Memory directly.
- **Approval Requirements:** Style-scoped candidates Tier 1; approval-scoped candidates Tier 2 minimum, routed through Article V exactly as JARVIS-002 §11 specifies.
- **Collaboration Rules:** Feeds the Preference Engine (JARVIS-002 §12) indirectly, only through the governed pipeline — never a direct write path.
- **Failure Modes:** Over-fitting to a small number of episodic events as if they were a genuine pattern (mitigated by JARVIS-002 §11's recurrence requirement — a singular event is an anecdote, not a candidate).
- **Future Evolution:** Candidate to eventually surface *meta*-patterns (e.g., "the owner's risk tolerance for Research Agent recommendations appears to shift on high-volatility days") — explicitly deferred pending Intelligence Phase 5 maturity (JARVIS-002 §34).

## 14. Planner Agent

- **Mission:** Maintain the owner's task and priority landscape across all domains.
- **Responsibilities:** Task capture, priority sequencing, deadline tracking, surfacing conflicts between competing priorities.
- **Boundaries:** Does not execute tasks itself — a planning layer over Engineering, Research, Trading, and any future domain, never a doer.
- **Inputs:** Owner-stated tasks and priorities, ProjectOS Agent status (§12), Calendar Agent state (§15).
- **Outputs:** Prioritized task views, conflict flags, feeding the Daily Briefing (§34).
- **Trust Level:** Standard.
- **Required Capabilities:** Read/write access to a dedicated task-state store (distinct from Working/Episodic/Semantic Memory — task state is operational, not evidentiary, and this document recommends it be architected as its own lightweight store rather than overloaded onto Memory's three tiers).
- **Approval Requirements:** Tier 0/1 for planning operations; any auto-generated task that would itself carry Tier 2/3 consequence is flagged, never silently scheduled.
- **Collaboration Rules:** Consumes from ProjectOS and Calendar agents; peer relationship, no authority over either.
- **Failure Modes:** Priority conflicts silently resolved by recency (last-mentioned task treated as most important) rather than surfaced (mitigated by an explicit conflict-flagging requirement, mirroring JARVIS-002 §28's conflict-resolution philosophy applied to planning rather than evidence).
- **Future Evolution:** Natural integration point for a future capacity-modeling capability (how much can realistically fit in a day) — explicitly not specified here, deferred to real usage data.

## 15. Calendar Agent

- **Mission:** Own calendar state as the authoritative source of scheduled time commitments.
- **Responsibilities:** Event creation/update/deletion (at owner or Planner Agent direction), conflict detection, availability queries for other agents.
- **Boundaries:** Never schedules something the owner hasn't approved, even when Planner Agent's prioritization strongly implies it should.
- **Inputs:** Owner instructions, Planner Agent proposals, Calendar Integration (§44) external state.
- **Outputs:** Calendar state, availability responses to other agents' queries.
- **Trust Level:** Standard.
- **Required Capabilities:** Scoped calendar-integration read/write.
- **Approval Requirements:** Event creation/update Tier 1 (reversible, low external impact); deletion of an event with third-party attendees Tier 2 (affects a third party, per JARVIS-001 §11's rollup logic).
- **Collaboration Rules:** Serves availability queries to any agent; only accepts write instructions from the owner or from Planner Agent's already-approved proposals.
- **Failure Modes:** Double-booking from a race between two simultaneous scheduling requests (mitigated by treating calendar writes as serialized through the Orchestrator, never concurrent).
- **Future Evolution:** Primary integration point for eventual voice-driven scheduling ("Jarvis, move my 3pm").

## 16. Reminder Agent

- **Mission:** Own time- and condition-triggered reminders, distinct from Calendar's fixed-time events.
- **Responsibilities:** Reminder creation, trigger evaluation, delivery coordination with the Notification Framework (§35).
- **Boundaries:** Delivers reminders; does not itself decide what's worth reminding the owner about beyond what was explicitly requested or approved via Learning Agent's pipeline.
- **Inputs:** Owner instructions, condition state from other agents (e.g., a Health Monitor Agent condition triggering a reminder).
- **Outputs:** Triggered reminder events to the Notification Framework.
- **Trust Level:** Standard.
- **Required Capabilities:** Scoped write access to a reminder-state store; read access to whatever condition signals a conditional reminder depends on.
- **Approval Requirements:** Tier 0/1 — reminders are inherently low-stakes and reversible; this document explicitly does not recommend elevating reminder creation to a higher tier even for reminders about high-stakes topics, since the reminder itself carries none of the consequence of the thing it's about.
- **Collaboration Rules:** Receives conditional-trigger definitions from any agent; delivers exclusively through Notification Framework, never directly.
- **Failure Modes:** Missed trigger due to condition-evaluation failure (mitigated by Health Monitoring, §20, covering this agent's own liveness).
- **Future Evolution:** Natural candidate for Learning Agent-informed proactive reminders once that pipeline matures, always still gated at the point a reminder would imply a Tier 2/3 action rather than just surface information.

## 17. Documentation Agent

- **Mission:** Keep the architecture series and operational documentation synchronized with the system it describes, directly operationalizing JARVIS-001 §37 and JARVIS-002 §36's "re-check the document against the release" requirement.
- **Responsibilities:** Drift detection between specification and implementation, documentation drafting for owner review, changelog maintenance.
- **Boundaries:** Never authors a constitutional change — flags drift for owner review, per Article VII, never resolves it unilaterally.
- **Inputs:** Repository state, this entire architecture series, audit trail of recent changes.
- **Outputs:** Drift reports, draft documentation updates.
- **Trust Level:** Standard.
- **Required Capabilities:** Read access to repository and architecture documents; write access only to draft/proposal state, never to published specifications directly.
- **Approval Requirements:** Drift detection and draft generation Tier 0/1; publishing an update to an official architecture document is Tier 2 minimum, Tier 3 if the drift touches constitutional interpretation (in which case this agent's correct behavior is to escalate to the owner as a possible Article VII matter, not to draft a fix at all).
- **Collaboration Rules:** Reads from every other agent's outputs indirectly via the Audit Ledger; writes to none of them.
- **Failure Modes:** Treating a deliberate, approved architectural evolution as "drift" and flagging false positives (mitigated by cross-referencing the Audit Ledger for whether a given change was already itself approved, before flagging it as unreviewed drift).
- **Future Evolution:** Long-term owner of the entire architecture series' internal consistency as the document count grows well past the current four.

## 18. Communication Agent

- **Mission:** Draft and, where approved, send communications on the owner's behalf (email, messages) — the highest third-party-impact agent in the roster after Trading and Deployment.
- **Responsibilities:** Drafting, tone/style application via Preference Engine (JARVIS-002 §12), scheduled/triggered send execution.
- **Boundaries:** Never sends anything to a third party without explicit per-message approval at launch — this document does not recommend any standing "auto-send" capability for this agent regardless of message category, given irreversible third-party impact.
- **Inputs:** Owner instructions or draft requests from other agents (e.g., Deployment Agent needing an incident notification drafted).
- **Outputs:** Drafts for review; sent-confirmation audit entries.
- **Trust Level:** Provisional, with an explicitly extended probation period given third-party impact.
- **Required Capabilities:** Scoped access to email/messaging integrations (§43); drafting capability with no independent send authority.
- **Approval Requirements:** Drafting Tier 0/1; sending is Tier 2 minimum for internal/known recipients, Tier 3 for anything external or first-contact, consistent with JARVIS-001 §11's third-party-impact rollup.
- **Collaboration Rules:** Accepts draft requests from any agent; sends only what the owner has specifically approved, never a "similar enough" variant generated after approval.
- **Failure Modes:** Drafting in a tone that misrepresents the owner's intent (mitigated by Preference Engine's provenance-tagged application, JARVIS-002 §12 — the owner always sees what preference shaped the draft, not just the output); send-after-edit mismatch (the approved draft and the sent draft must be byte-identical, or the send requires re-approval).
- **Future Evolution:** Candidate for expanded scope only after a long, evidenced track record — this document deliberately keeps this agent's ambition narrow at launch.

## 19. Security Agent

- **Mission:** Own the adversarial-testing and threat-monitoring responsibilities named throughout JARVIS-001 §25/§30 and JARVIS-002 §31, as a dedicated agent rather than an implicit property of the system.
- **Responsibilities:** Running adversarial constitutional tests (JARVIS-001 §30) on a schedule, monitoring for the specific threats named in JARVIS-001 §25 and JARVIS-002 §31, flagging anomalies to Health Monitoring.
- **Boundaries:** Detects and reports; does not itself revoke another agent's permissions or trust tier — that remains a governance action per JARVIS-002 §20's asymmetric rule, even when this agent's finding is the trigger.
- **Inputs:** System-wide audit and observability data, the full named-threat list from prior documents.
- **Outputs:** Security findings with JARVIS-002 §22 evidence grading, escalations.
- **Trust Level:** Elevated — uniquely among this roster, this agent's core function requires broad read access across the system, and its findings are treated as high-priority by design; this elevated access is offset by this being the most heavily externally-audited agent's *own* behavior, per Future Evolution below.
- **Required Capabilities:** Broad, read-only observability and audit access across all other agents; explicitly no write/execute capability of any kind.
- **Approval Requirements:** Monitoring and reporting Tier 0; any recommended permission/trust revocation is drafted as a proposal routed to governance, never executed directly, regardless of this agent's own elevated trust tier.
- **Collaboration Rules:** Reads across every agent; writes to none; escalates directly to Health Monitor Agent (§20) and, for Tier 3-relevant findings, directly to the owner rather than only through routine briefing cadence.
- **Failure Modes:** False-positive fatigue degrading owner trust in its findings (mitigated by JARVIS-002 §22's evidence grading applied to its own output — a finding is reported at the confidence it actually warrants, not maximal alarm by default).
- **Future Evolution:** This document recommends this agent's own actions be subject to *external* audit review on a fixed cadence precisely because its elevated, broad read access makes it the single highest-value target for compromise in the entire roster — a recommendation for the Security Considerations section (§57) to formalize further.

## 20. Health Monitor Agent

- **Mission:** Operate JARVIS-001 §22 and JARVIS-002 §30's health-check frameworks as a dedicated agent, aggregating signals across Core self-health, pipeline integrity, per-agent health, calibration health, and knowledge staleness.
- **Responsibilities:** Continuous health-signal aggregation, threshold-based alerting, feeding the Daily Briefing (§34).
- **Boundaries:** Reports health; does not remediate — a degraded agent is flagged, never automatically restarted, retrained, or reconfigured by this agent unilaterally.
- **Inputs:** Observability data (JARVIS-001 §21), calibration and staleness signals (JARVIS-002 §30).
- **Outputs:** Health status reports, threshold-breach alerts.
- **Trust Level:** Standard.
- **Required Capabilities:** Broad read-only observability access, mirroring Security Agent's pattern but scoped to health rather than adversarial-threat data specifically.
- **Approval Requirements:** Tier 0 — reporting is inherently informational; this agent has no action capability requiring higher tiers by design.
- **Collaboration Rules:** Receives signals from every agent implicitly via Observability; peer to Security Agent, with a clearly divided mandate (health/degradation vs. adversarial/compromise) to avoid the two agents' findings becoming redundant or, worse, contradictory without a clear resolution owner.
- **Failure Modes:** Alert fatigue from over-sensitive thresholds (mitigated by tuning thresholds against Section 5 Principle 3's friction-proportional-to-stakes philosophy, applied to alerting rather than approval).
- **Future Evolution:** Primary data source for a future capacity-planning capability as agent count grows past what a human can casually track (Blueprint §23's technical scalability axis, made observable).

## 21. Future Agent Expansion Framework

**Objectives.** Specify how a seventeenth agent, or any agent beyond this initial roster, gets added without this document requiring a full rewrite each time.

**Architectural decision.** A new agent proposal must, at minimum: declare its domain-tree position (extending JARVIS-001 §13's tree, never inventing a parallel routing mechanism), complete the same eleven-field specification used throughout Part I, and pass through JARVIS-002 §16's full lifecycle including mandatory probation — no agent, regardless of who proposes it or how urgently it's needed, skips probation. If a new agent's needs reveal a genuine gap in JARVIS-001 or JARVIS-002 (a new tier concept, a new evidence type), that gap is escalated as a proposed amendment to *those* documents, never patched locally in this document or in the new agent's own specification — this document has no authority to extend the frameworks above it, only to populate them.

**Alternative considered.** A lightweight "fast-track" registration path for low-risk-seeming agents. Rejected: "seems low-risk" is exactly the kind of unverified assumption Article III's evidence discipline exists to prevent from governing a decision — every agent's actual risk profile is established through probation, not asserted at proposal time, regardless of how simple the agent's stated mission sounds.

---

# PART II — MULTI-AGENT OPERATIONS

*This Part operationalizes JARVIS-002's structural definitions (Communication Protocol §21, Multi-Agent Collaboration §27, Conflict Resolution §28) against the actual sixteen-agent roster above. It does not redefine those mechanisms.*

## 22. Multi-Agent Collaboration

**Objectives.** Show JARVIS-002 §27's synthesis-step model operating across named agents.

**Architectural decision.** The clearest recurring collaboration pattern in this roster is Research → Trading (§10–11): a strict producer/consumer relationship where synthesis is nearly trivial (Trading consumes, never blends its own analysis in) precisely because the domains were split specifically to avoid needing complex synthesis logic at all. The most complex pattern is Engineering's children (§7–9) collaborating on a single investigation spanning GitHub, deployment history, and schema state simultaneously — this is the case JARVIS-002 §27's full synthesis-step machinery is actually built for, and this document recommends it as the canonical test case when that machinery is first implemented (JARVIS-002 §34, Intelligence Phase 3).

## 23. Task Delegation

**Objectives.** Specify how a task moves from one agent to a child/peer agent within this roster, extending JARVIS-001 §13's routing.

**Architectural decision.** Delegation within this roster always flows from a broader-domain agent to a narrower one (Engineering → GitHub/Deployment/Database), never the reverse, and never sideways between unrelated domains without passing back through the Orchestrator's own routing decision. A Database Agent task originating from Trading Agent's domain (§11, if Trading ever needed schema work) is not "Trading delegating to Database" — it is a fresh Orchestrator-routed task, keeping JARVIS-001 §13's tree-based routing authoritative rather than letting agents establish their own informal delegation shortcuts.

## 24. Agent Coordination

**Objectives.** Address coordination for tasks with no clean hierarchical delegation path — the case Task Delegation (§23) deliberately doesn't cover.

**Architectural decision.** Cross-root-domain coordination (e.g., ProjectOS Agent's cross-project synthesis touching multiple Engineering-domain investigations at once) is handled entirely at the Orchestrator level via task-graph branching (JARVIS-001 §11), with ProjectOS Agent as a pure consumer of already-completed branch outputs — it does not itself coordinate other agents' execution order, avoiding the informal-authority risk Constitutional Principle 3 warns against.

## 25. Cross-Agent Evidence Sharing

**Objectives.** Specify how evidence graded by one agent (JARVIS-002 §22) is treated when consumed by another.

**Architectural decision.** Evidence retains its original grade and source attribution when it crosses an agent boundary — Trading Agent consuming Research Agent's output sees Research's own confidence figure and evidence grade, never a re-stated or laundered version presented as Trading's own finding. This is Article III applied specifically to the handoff point between agents, which is otherwise the single easiest place in the whole system for provenance to quietly get lost.

## 26. Conflict Resolution

**Objectives.** Show JARVIS-002 §28's evidence-tie resolution operating against a concrete roster case.

**Architectural decision.** The most likely real conflict in this roster: Health Monitor Agent (§20) and Security Agent (§19) both flagging the same agent's behavior with different severity assessments. Per JARVIS-002 §28, this is not resolved by one agent's trust tier outranking the other's (Security Agent's Elevated tier does not automatically win) — both findings, with their evidence grades, are surfaced together, because the two agents have deliberately divided, non-overlapping mandates (§20's Collaboration Rules) and a disagreement between them is genuinely informative, not noise to be collapsed.

## 27. Consensus Framework

**Objectives.** Address the case genuinely distinct from a two-agent conflict — where three or more agents' outputs need reconciling into one briefing item.

**Architectural decision.** Consensus is never majority-vote among agents — Constitutional Principle 3 already forbids agents ranking against each other, and a voting model would create exactly that ranking implicitly. Instead, each contributing agent's output retains its individual evidence grade through synthesis (§22), and the Daily Briefing (§34) presents a synthesized view *with* per-source attribution available on request, never a flattened "the agents agree" statement that discards which agents actually said what.

## 28. Escalation Framework

**Objectives.** Specify when a multi-agent situation stops being resolvable at this layer and requires owner involvement, beyond the specific evidence-tie case JARVIS-002 §28 already covers.

**Architectural decision.** Three escalation triggers, all routing directly to the owner rather than waiting for the next scheduled briefing: a genuine evidence-grade tie (JARVIS-002 §28), a Security Agent finding above a defined severity threshold (§19), and any situation where two agents' outputs would imply contradictory Tier 3 actions. All three bypass Notification Framework's normal batching (§35) — this is the one class of situation in this document where immediacy outranks the friction-reduction goals stated throughout Part III.

## 29. Human Approval Integration

**Objectives.** Confirm, at the multi-agent operational level, that JARVIS-001 §15's "no path exists" guarantee holds even when a task graph spans this document's full sixteen-agent roster.

**Architectural decision.** Restated deliberately, because it is the single most important guarantee in the entire series and this is the document where it's tested against real complexity: a task graph's overall tier (JARVIS-001 §11's max-of-nodes rule) governs regardless of how many agents contributed to it or how low any individual node's tier was. A nine-agent workflow with eight Tier 0 nodes and one Trading Agent Tier 3 node is a Tier 3 workflow, full stop, exactly as JARVIS-001 §11 already specified for the simpler case.

---

# PART III — USER EXPERIENCE

## 30. Conversation Model

**Objectives.** Define the shape of an owner-JARVIS exchange, extending JARVIS-001 §10's Intent Processing into an actual interaction pattern.

**Architectural decision.** Every exchange is modeled as: owner input → JARVIS-001 §10's structured intent (with confidence and ambiguity flag) → for high-confidence, unambiguous intent, direct task planning begins; for flagged ambiguity, a clarifying question is asked *before* any agent is invoked, never after a task has already partially executed. This ordering — clarify before act, not act and course-correct — is a direct, deliberate consequence of Article III applied to the conversation layer itself: proceeding on a guessed interpretation and hoping the approval gate catches a misunderstanding later is exactly the kind of unearned confidence this entire series exists to prevent.

**Alternative considered.** A more conversationally fluid model where JARVIS begins acting on a best-guess interpretation and adjusts based on owner reaction. Rejected: feels more natural in low-stakes cases, but has no principled way to stay contained to low-stakes cases — the conversational layer has no reliable way to know a request is high-stakes *before* interpreting it, so the safer default is asking whenever confidence is insufficient, regardless of stakes, with Section 5 Principle 3's friction-proportional-to-stakes philosophy applied at the *approval* layer rather than the *understanding* layer.

## 31. Chat Experience

**Objectives.** Specify the text-based interaction surface.

**Architectural decision.** Chat is the default, lowest-friction interface for Tier 0/1 interactions and for reviewing/approving Tier 2/3 proposals in detail — text is uniquely well-suited to JARVIS-001 §15's requirement that Tier 3 confirmations restate consequence specifically, because text can be read, re-read, and referenced back to in a way a fleeting voice exchange cannot. This document recommends chat remain the required channel for finalizing any Tier 3 confirmation even after Voice Experience (§32) matures, consistent with §16's channel-mismatch rule from JARVIS-002... *(cross-reference note: §16's rule actually lives in the Blueprint's own voice section — restated here as the UX-layer consequence of that rule, not a new rule)*.

## 32. Voice Experience

**Objectives.** Specify voice as an interaction surface, directly implementing the Blueprint's voice architecture principle.

**Architectural decision.** Voice can initiate any tier of request, including Tier 3 (Blueprint's own rule, restated for this document's UX layer). This document's specific contribution: voice-initiated Tier 3 requests trigger an explicit **channel handoff** — JARVIS confirms verbally that a confirmation has been sent to chat/dashboard, and the spoken exchange itself cannot complete the approval, even if the owner says "yes, confirmed" out loud. This isn't distrust of the owner's spoken intent; it's the same reasoning as §31: a channel suited for speed is structurally unsuited for the specific kind of deliberate friction Tier 3 requires.

**Risk.** This creates a jarring experience — "Jarvis, deploy it" answered with "sent to your phone for confirmation" rather than immediate action. **Mitigation:** this document recommends this friction be treated as a feature to surface transparently in onboarding, not softened — an owner who understands *why* voice can't complete high-stakes actions is more likely to trust the system's judgment than one who experiences it as an arbitrary limitation.

## 33. Dashboard Architecture

**Objectives.** Specify the persistent, glanceable surface distinct from the conversational chat/voice channels.

**Architectural decision.** The dashboard is a **read-mostly synthesis view** — current agent health (§20), pending approvals awaiting owner action, recent briefing history, and per-domain status (Engineering, Research/Trading, ProjectOS). It is explicitly not a channel for initiating new Tier 2/3 actions directly (those originate through conversation, per §30) — the dashboard's job is situational awareness, not command initiation, keeping its own risk profile deliberately low regardless of how much system state it surfaces.

## 34. Daily Briefings

**Objectives.** Fully specify the Blueprint §20 constraint ("a briefing is Tier 0 by construction, never a soft channel around approval") as an actual document structure.

**Architectural decision.** Every briefing item carries: which agent(s) produced it, its evidence grade (JARVIS-002 §22), and — for any item referencing an action — explicit confirmation that the action was itself already properly approved at whatever tier it required, with a link back to that approval's audit entry. A briefing item that cannot show this confirmation is not included, full stop; an unapproved action is never described in past tense in a briefing, regardless of how confident the originating agent was that the owner would have approved it.

## 35. Notification Framework

**Objectives.** Specify how and when JARVIS interrupts the owner outside the scheduled briefing cadence.

**Architectural decision.** Three notification classes: **batched** (Tier 0/1 items, held for the next briefing by default), **prompt** (Tier 2/3 approval requests, delivered as soon as the task is ready for owner review), and **immediate** (Part II §28's three escalation triggers exclusively). This tiering directly protects against the approval-fatigue risk JARVIS-001 §11 already names — most system activity never interrupts the owner at all, preserving the credibility of the notifications that do.

## 36. Multi-Device Experience

**Objectives.** Specify consistency requirements across however many devices/surfaces the owner uses.

**Architectural decision.** State is owned centrally (JARVIS-001 §6's stateless-Core, externally-persisted-state principle applies directly here) — no device holds authoritative state of its own. A Tier 3 confirmation initiated on one device and completed on another is fully supported by design, because the confirmation's state lives in the Core's persisted task graph (JARVIS-001 §12), never in any single device's local session.

## 37. Personalization

**Objectives.** Specify how Semantic Memory (JARVIS-002 §8) and the Preference Engine (JARVIS-002 §12) surface as an actual owner experience.

**Architectural decision.** Personalization is always disclosed, never invisible — restated deliberately from JARVIS-002 §12 because the UX temptation to hide "the system just knows you" behind a seamless experience is strong, and directly conflicts with Article III's provenance requirement. Every personalized default is inspectable on request ("why did you suggest this format?" always has an answerable response pointing to a specific Semantic Memory entry and its originating evidence).

## 38. Accessibility

**Objectives.** Ensure the tiered-friction model (§35) doesn't inadvertently create an accessibility barrier.

**Architectural decision.** Every Tier 3 confirmation's "consequence restatement" (JARVIS-001 §15) must be satisfiable through whatever channel the owner can most reliably use — text for a hearing-impaired owner, voice-plus-explicit-verbal-restatement for a vision-impaired owner interacting primarily by voice, with the channel-handoff principle (§32) adapted rather than treated as chat-only by default. The constitutional requirement (specific, non-generic confirmation) is fixed; the modality satisfying it is not.

---

# PART IV — EXTERNAL ECOSYSTEM

*Every integration below is a connector through JARVIS-001's Integration Gateway (JARVIS-001 §18/25) — this Part specifies each connector's scope and trust posture, not new gateway architecture.*

## 39. GitHub Integration

Primary surface for GitHub Agent (§7). Scoped, task-expiring credentials per JARVIS-001 §18. Trust posture: high reliability expectation given this project's own extensive operational history with it, but credential scope remains minimal-per-task regardless of that history — track record earns agent trust (JARVIS-002 §20), not credential scope expansion.

## 40. Streamlit Integration

Primary surface for Deployment Agent (§8) and the Dashboard (§33), where applicable. Given this project's own documented experience with Streamlit Cloud's deployment-sync behavior (redeploy timing not being instantaneous or fully observable from repository state alone), this document recommends the Health Monitor Agent (§20) treat "deployment committed" and "deployment live" as two distinct, separately-tracked states rather than assuming the former implies the latter — directly informed by that project history.

## 41. Upstox Integration

Primary surface for Research Agent (§10) and, at proposal-only scope, Trading Agent (§11). Given this project's own history of the documented API surface diverging from live enforced behavior (the historical-candle date-range case, cited repeatedly through JARVIS-002), this document recommends this integration specifically be held to the highest Knowledge Staleness monitoring sensitivity (JARVIS-002 §30) of any external integration in the roster.

## 42. Telegram Integration

Primary surface for Notification Framework's (§35) prompt and immediate classes, where the owner has configured it as a preferred channel. Treated as a delivery mechanism only — no inbound Telegram message is treated as a valid Tier 2/3 approval channel on its own, for the same reasoning as §32's voice-channel restriction (a fast, low-friction channel is structurally unsuited to high-stakes confirmation).

## 43. Email Integration

Primary surface for Communication Agent (§18). Outbound only at launch, per §18's conservative scope; inbound email parsing as a future intent-input channel is explicitly deferred, not designed here.

## 44. Calendar Integration

Primary surface for Calendar Agent (§15). External calendar as source of truth for third-party-visible commitments; JARVIS's own Calendar Agent state must reconcile against it, never override it silently — a conflict between JARVIS's internal state and the external calendar's actual state is itself a Health Monitoring signal (§20), not something Calendar Agent resolves unilaterally in its own favor.

## 45. Cloud Storage Integration

Reserved for Documentation Agent (§17) and any future agent needing durable artifact storage beyond the Knowledge/Memory stores' scope (JARVIS-002 §5–9). Explicitly not specified in further detail here — this document recommends this integration's actual design wait until a concrete agent need justifies it, rather than building speculative capacity.

## 46. MCP Integration Framework

Direct implementation of Blueprint §17's Plugin Framework: any MCP-style connector is registered as an agent (Section 14 above, "agent" as a role not an implementation category) at Provisional trust by default, regardless of the connector's own claimed maturity, with the same mandatory probation as any other new agent (§21).

## 47. Future External Services

No future service is pre-approved by category. Each new integration proposal follows §21's Future Agent Expansion Framework in full — this document deliberately keeps this section short precisely because its content should never grow; growth belongs in specific, reviewed proposals, not in an expanding "approved categories" list here.

---

# PART V — OPERATIONAL SCENARIOS

*Each scenario below is a concrete walk-through of JARVIS-001 §9's request lifecycle. Format: Participating agents, Information flow, Approval gates, Evidence requirements, Audit trail, Failure handling, Recovery strategy.*

## Morning Briefing

- **Agents:** ProjectOS, Planner, Health Monitor, Research (if market-relevant), Calendar.
- **Information flow:** Each agent's overnight output synthesized (§27 Consensus Framework) into one briefing (§34).
- **Approval gates:** None — Tier 0 by construction.
- **Evidence:** Every item traceable to its source agent and evidence grade (§34).
- **Audit trail:** Briefing generation itself is logged; each cited action already carries its own prior audit entry.
- **Failure handling:** A non-responsive agent's section is marked "unavailable," never silently omitted without explanation.
- **Recovery:** Missing sections backfill into the next cycle; no retroactive briefing edit.

## Engineering Investigation

- **Agents:** Engineering, GitHub, Database (as needed), Security (background monitoring).
- **Information flow:** Engineering Agent decomposes the investigation (JARVIS-002 §25), delegates evidence-gathering to GitHub/Database (§23 Task Delegation), synthesizes findings.
- **Approval gates:** None for investigation itself (Tier 0); any resulting fix proposal gates separately.
- **Evidence:** Full JARVIS-002 §22 grading; static vs. runtime evidence explicitly distinguished (§25).
- **Audit trail:** Full reasoning chain retained per JARVIS-002 §26 (Tier-proportional depth).
- **Failure handling:** Insufficient evidence is reported as such (JARVIS-002 §10's proposed-not-corroborated state), never padded with inference presented as fact.
- **Recovery:** N/A — investigation is non-consequential; failed investigations simply produce a lower-confidence report.

## Pull Request Review

- **Agents:** Engineering, GitHub, Security.
- **Information flow:** GitHub Agent surfaces PR content; Engineering evaluates against JARVIS-001 §29's smallest-change discipline; Security screens for the named threat categories (§19).
- **Approval gates:** Tier 2 minimum for merge (owner decision); Engineering's recommendation is advisory only (JARVIS-002 §24).
- **Evidence:** Diff-level evidence, direct observation grade (JARVIS-002 §22 item 1).
- **Audit trail:** Full recommendation-to-decision chain logged.
- **Failure handling:** A PR touching JARVIS-001 §25 threat-mapped components is automatically escalated to Tier 3 regardless of Engineering's own risk assessment (JARVIS-002 §24's cross-check rule).
- **Recovery:** Standard version-control revert, outside this document's scope but compatible with JARVIS-001 §24's Ledger-reconciled philosophy.

## Production Deployment

- **Agents:** Deployment, Engineering (originating proposal), Health Monitor.
- **Information flow:** Approved fix/feature → Deployment Agent executes staged rollout (JARVIS-001 §32) → Health Monitor verifies post-deploy state.
- **Approval gates:** Tier 3, always (§8).
- **Evidence:** Pre-deploy health baseline and post-deploy comparison, both direct observation grade.
- **Audit trail:** Full Prepare→Rollback pipeline logged per Constitutional Principle 13.
- **Failure handling:** Health Monitor degradation post-deploy triggers immediate escalation (§28), not a wait-for-next-briefing cycle.
- **Recovery:** Deployment Agent's own rollback capability (§8); a failed rollback escalates directly to the owner, never auto-retried.

## GitHub Issue Investigation

- Structurally identical to Engineering Investigation above, with GitHub Agent as the primary evidence source and the issue itself as the originating intent rather than a proactive Engineering Agent trigger.

## Trading Analysis

- **Agents:** Research only.
- **Information flow:** Market data → Historical Intelligence synthesis → graded confidence output.
- **Approval gates:** None — pure Tier 0/1 informational output, explicitly not a trading decision (§10's boundary).
- **Evidence:** Full JARVIS-002 §23 confidence computation, recency-weighted.
- **Audit trail:** Research output logged with full evidence chain for later calibration tracking (JARVIS-002 §30).
- **Failure handling:** Thin-sample conditions produce explicitly capped confidence, never omitted or silently smoothed over.
- **Recovery:** N/A — non-consequential by design.

## Market Research

- Broader-scope variant of Trading Analysis, potentially spanning multiple instruments; otherwise structurally identical.

## Project Planning

- **Agents:** Planner, ProjectOS, Calendar.
- **Information flow:** Owner priorities + ProjectOS status + Calendar availability → prioritized plan.
- **Approval gates:** Tier 0/1 for plan generation; any resulting calendar commitment gates per Calendar Agent's own rules (§15).
- **Evidence:** N/A in the JARVIS-002 §22 sense (planning is preference-driven, not evidence-driven) — but conflicts are still explicitly flagged (§14).
- **Audit trail:** Plan versions retained, not overwritten silently.
- **Failure handling:** Conflicting priorities surfaced, never silently resolved by recency (§14).
- **Recovery:** N/A.

## Daily ProjectOS Coordination

- **Agents:** ProjectOS, Engineering (per tracked project), Planner.
- **Information flow:** Per-project Engineering status → ProjectOS synthesis → Planner reprioritization input.
- **Approval gates:** None for synthesis; downstream actions gate per their own agent's rules.
- **Evidence:** Aggregated from each project's own audit trail.
- **Audit trail:** Synthesis logged distinctly from the underlying per-project entries it summarizes.
- **Failure handling:** A project with no recent Engineering Agent activity is reported as "stale," not silently dropped from the synthesis.
- **Recovery:** N/A.

## Multi-Agent Collaboration (Complex Case)

- The canonical Engineering-children case from §22 — full task-graph branching (JARVIS-001 §11), synthesis step (JARVIS-002 §27), and — if evidence genuinely conflicts — the tie-surfacing behavior of §26/JARVIS-002 §28.

## Emergency Response

- **Agents:** Whichever combination is relevant (typically Health Monitor + Security + Deployment for a production incident).
- **Information flow:** Immediate-class escalation (§28/§35) bypasses batching entirely.
- **Approval gates:** Emergency does not lower any Tier's approval requirement — Constitutional Principle 7 has no emergency exception, and this document explicitly does not invent one. What emergency status changes is *notification urgency* (§35), never *approval necessity*.
- **Evidence:** Same grading standard as any other scenario — urgency is not license for lower evidentiary rigor, it's the opposite: an emergency proposal with weak evidence is exactly the situation Article III's discipline matters most.
- **Audit trail:** Full trail, generated with the same rigor as any other Tier 3 action, produced under time pressure rather than exempted from production because of it.
- **Failure handling:** If Deployment Agent's rollback itself fails during an emergency, escalation goes directly and immediately to the owner — no automated fallback attempts a second unilateral action.
- **Recovery:** Standard recovery strategy (JARVIS-001 §24), explicitly not relaxed under emergency framing.

## Owner Approval Workflow

- The canonical instance of JARVIS-001 §15 in full: task reaches its gate → tier-appropriate rendered consequence statement generated → delivered via the appropriate channel per §31/§32/§38 → owner responds → Workflow Engine resumes or the task is marked declined, both outcomes fully audited (§16 above / JARVIS-001 §16).

---

# PART VI — LONG-TERM EVOLUTION

## 48. Personal AI Roadmap

Nested inside Blueprint §24 and JARVIS-001/002's own roadmaps: this document's roster (Part I) represents a *target* state, not a launch requirement — recommend sequencing roughly Engineering/GitHub first (matching the Blueprint's stated first priority), Research/Trading second (matching NG Signal Pro priority), Planner/Calendar/Reminder third (lowest individual risk, highest daily-life value), remainder as capacity allows.

## 49. Voice-first Operation

Explicitly a **future** state, not a launch assumption — §32's channel-handoff design is built to make voice-first viable eventually without requiring architectural rework, but this document does not recommend treating voice as the primary interface until Chat Experience (§31) and the approval framework have a substantial owner-facing track record first.

## 50. Autonomous Assistance Boundaries

**This is the section where this document draws its firmest line.** No agent in this roster — regardless of future trust-tier growth under JARVIS-002 §20's continuous recomputation — is ever recommended for a capability upgrade that removes a Tier 2/3 approval gate. Trust growth under this document's model changes *friction at lower tiers* and *speed of proposal-to-gate*, never *whether the gate exists*. This is stated as a standing architectural position specifically to resist the natural pressure, a decade from now, to quietly treat an extremely well-calibrated agent as no longer needing the gate — the gate is not a proxy for distrust of any specific agent's competence; it is Article V, and Article V does not have a competence exception.

## 51. Plugin Ecosystem

Extends §46 (MCP Integration Framework): a mature plugin ecosystem is a plausible multi-year outcome, but every plugin remains subject to §21's full agent-registration discipline indefinitely — this document does not recommend a lighter-weight "certified plugin" fast track even at ecosystem maturity, for the same reasoning §21 already gave against a fast-track at launch.

## 52. Third-party Agent Framework

If JARVIS ever hosts agents built by parties other than the owner (a genuinely open question, not assumed here), this document recommends that decision be treated as a Constitutional-adjacent question — closer to Blueprint §23's flagged multi-owner decision point than to an ordinary Tier 2 architectural choice — because a third-party-authored agent operating under this Constitution raises authority questions the current single-owner, single-author model doesn't have to answer yet.

## 53. Future Expansion Strategy

The general pattern this entire document establishes — specify against the existing framework, extend rather than redefine, escalate genuine framework gaps rather than patching around them locally — is itself the expansion strategy, intended to apply unchanged to whatever JARVIS-004 and beyond eventually cover.

---

# PART VII — ENGINEERING GOVERNANCE

## 54. Operational Risks

| Risk | Source | Mitigation |
|---|---|---|
| Two agents' domain boundaries overlapping in practice despite clean tree design | Section 5, Principle 4 | Domain tree (JARVIS-001 §13) treated as authoritative; overlaps escalated to §21's framework-gap process, never resolved informally |
| Trading Agent trust growth pressure over time | §11, §50 | Explicit standing position against gate removal regardless of trust tier |
| Briefing becoming a soft approval bypass | §34 | Structural requirement that every action-referencing item link to prior approval audit entry |
| Voice channel friction perceived as a bug rather than a feature | §32 | Explicit onboarding-transparency recommendation |
| Security Agent's own elevated access becoming the highest-value compromise target | §19 | Recommended external-audit cadence for this agent specifically |
| Documentation Agent flagging approved evolution as unauthorized drift | §17 | Cross-reference against Audit Ledger before flagging |

## 55. Scalability Analysis

Technical scalability (JARVIS-001 §28) is unaffected by this document — every agent here is a registry entry and a routed task target, not a load-bearing architectural component. Governance scalability is directly tested by this roster's size: sixteen agents is large enough that Constitutional Principle 3's "no agent ranks above another" and Article VI's "delegated authority must trace back" stop being easy defaults and start being actively-enforced design choices — which is precisely why Part II exists as its own Part rather than a subsection.

## 56. Performance Considerations

Consistent with JARVIS-002 §29's ordering (correctness and auditability never traded for speed): the only performance-sensitive pattern specific to this roster is GitHub Agent's rate-limit exposure under sustained Engineering Agent investigation load (§7), addressed there directly rather than as a system-wide concern.

## 57. Security Considerations

Extends JARVIS-001 §25 and JARVIS-002 §31 with roster-specific findings: Security Agent's own broad access (§19) and Communication Agent's third-party send capability (§18) are this roster's two highest-value targets, both already given elevated scrutiny in their individual specifications above; this section's contribution is naming them together as the two the owner should think about first if this document's threat model is ever formally re-audited.

## 58. Acceptance Criteria

- Every agent in Part I is demonstrated to reject a task outside its declared domain-tree position, not merely documented as scoped to it.
- Trading Agent is demonstrated, under adversarial testing (JARVIS-001 §30 pattern), to be structurally incapable of bypassing Tier 3 regardless of simulated urgency or simulated high-recurrence learned preference.
- A briefing item referencing an action is demonstrated to always resolve to a real prior approval audit entry, with a deliberately broken case (an unapproved action) confirmed to be excluded rather than included with a missing link.
- A voice-initiated Tier 3 request is demonstrated to require channel handoff completion, with a spoken "yes" alone confirmed insufficient.
- A genuine two-agent evidence tie (§26) is demonstrated to surface to the owner rather than resolve via trust-tier precedence.

## 59. Definition of Done

Part I through VII of this document are each "done" only when their respective acceptance criteria (§58, extended per-Part as needed) are met with evidence, and when this document itself has been re-verified against the prior three (Constitution, JARVIS-001, JARVIS-002) for drift — per the same standing obligation JARVIS-001 §37 and JARVIS-002 §36 already placed on themselves, now placed on this one as well, and intended to apply to every document this series produces from here forward.

---

## Closing Statement

The first three documents could be judged entirely on internal consistency — did the reasoning hold together. This one can also be judged against something harder: does the owner's actual morning, actual investigation, actual trade proposal, actually feel like the system the Constitution promised, friction included. Sixteen agents and a full operational layer are a lot of surface area for something to go quietly wrong in — which is exactly why every section above was built to point back at a specific document, a specific article, or a specific piece of this project's own history, rather than standing on its own authority. Nothing here is supposed to be trusted because this document says so. It's supposed to be checkable against everything that came before it, indefinitely, by whoever's reading it next.
