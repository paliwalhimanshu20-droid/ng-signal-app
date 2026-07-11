# JARVIS-001 — Foundation & Core Architecture
### Engineering Specification | JARVIS OS Architecture Series
### Status: Draft for Owner Review | Constitution Compliance: Verified Throughout

*Authored as the second document in the JARVIS architecture series, subordinate in all respects to "JARVIS OS — Constitution & Master Architecture Blueprint v1.0" (hereafter "the Constitution" or "the Blueprint"). Where this document is silent, the Constitution governs. Where any reader perceives a conflict between the two, the Constitution wins and this document is wrong.*

---

## Preface — Scope of This Document's Authority

This document has no power to create, waive, or reinterpret constitutional law. Its only job is to answer a narrower question than the Blueprint asked: **given the six-layer architecture, the seven Articles, and the tiered Approval Engine the Constitution already established, how is the JARVIS Core itself engineered so that every one of those commitments is structurally true — not just documented, but load-bearing in the design?**

Every section below carries the same internal discipline: state the objective, state the reasoning, state the decision, state what was rejected and why, state what could still go wrong. A specification that only shows its conclusions is a specification nobody can safely modify in year six without the original architect present. This one is built so they don't need to be.

---

## 1. Purpose & Scope

**Objectives.** Define precisely what "JARVIS Core" is as an engineering artifact, and draw its boundary against everything it is not — specialist agents, individual integrations, the knowledge base's domain content.

**Design rationale.** The Blueprint's six layers (Interface, Orchestration, Agent, Knowledge/Memory, Execution/Integration, Security/Audit) describe *responsibilities*, not a build order. Without a document narrowing "JARVIS Core" to a specific subset of those layers, every future engineering effort risks either duplicating orchestration logic inside individual agents (violating the Blueprint's Section 4 warning about layer 2 accumulating domain logic) or under-building the Core so agents end up talking to each other directly (violating Article VI and Blueprint Section 7).

**Architectural decision.** JARVIS Core is defined as: the Interface Layer, the Orchestration Layer in full, and the *core-facing contracts* of the Permission Engine, Approval Engine, Audit Ledger, and Agent Registry (i.e., how the Core calls into them — not their internal implementation, which belongs to the Security & Audit layer's own specification, a future JARVIS-00X document). Specialist agents, the Knowledge Store's content, and individual Integration Gateway connectors are explicitly **out of scope** for JARVIS-001 and will each receive their own numbered document.

**Alternatives considered.**
- *Option A — "Core" means everything except agent-specific logic.* Rejected: this would fold the Permission/Approval/Audit engines' internals into this document, producing an unmaintainably large single specification and blurring the sharp seams the Blueprint explicitly demands (Section 4).
- *Option B — "Core" means only the Interface Layer.* Rejected: too narrow: it would leave the Orchestration Layer's design — the single most architecturally consequential component in the system — without a governing document at all.

**Advantages of the chosen scope.** Keeps this document reviewable end-to-end by a human in one sitting; establishes a clean seam for JARVIS-002 (Permission & Approval Engine internals) and JARVIS-003 (Agent Registry & Framework) to build on without rework.

**Disadvantages / risks.** A reader looking for "how does the Approval Engine actually decide a tier" will not find it here — only how the Core *calls* it. Mitigated by explicit forward-references to the documents that will carry that content, so this specification never silently pretends to be complete when it isn't.

**Scalability considerations.** A narrowly-scoped Core document scales better across a 10-year series than a monolithic one — each subsystem's document can be revised on its own cadence without triggering a full re-review of everything else.

**Future evolution.** As JARVIS-002 through JARVIS-0XX are written, this document's forward-references become real citations. This document itself should be revisited whenever the Core's boundary changes — that revision is a Tier 2 governance action (Section 22 of the Blueprint), not a constitutional amendment.

---

## 2. Relationship with the Constitution

**Objectives.** Make constitutional compliance falsifiable, not just claimed.

**Design rationale.** A document that says "this complies with the Constitution" without a mechanism to check that claim is asserting confidence without evidence — exactly what Article III forbids JARVIS itself from doing. This specification should not be permitted to hold itself to a lower evidentiary standard than it holds the system it describes.

**Architectural decision.** Every major section below ends its reasoning by naming the specific Article or Blueprint section it implements. Additionally, JARVIS Core's bootstrap sequence (Section 26) includes an explicit **constitution-compatibility check** as its first step — the Core refuses to reach a ready state if it cannot verify which constitution version it is operating under. This turns "we comply with the Constitution" from a design intention into a runtime-enforced precondition.

**Alternative considered.** Treating constitutional compliance as a one-time design review rather than a runtime check. Rejected: a design review catches compliance at the moment of writing; it does nothing to catch a future deployment accidentally running against a stale or unversioned constitution, which is precisely the kind of silent drift Blueprint Section 12 warns about.

**Risks.** A compatibility check can become a rubber stamp if the version identifier is trivial to satisfy. Mitigation: the compatibility check must validate structural presence of all seven Articles, not merely a version number string — a constitution missing Article II entirely should fail the check even if its version string is "correct."

---

## 3. Engineering Philosophy

**Objectives.** Establish the handful of engineering values that should resolve any design disagreement not explicitly settled elsewhere in this document.

**Architectural decisions, stated as principles:**

1. **Sharp seams over convenient shortcuts** — restated from Blueprint Section 4; the single most commonly violated principle in systems like this, and the one most worth over-stating here.
2. **Boring technology, novel governance** — the constitutional and approval machinery is where this system should be genuinely innovative. The plumbing underneath (how a task is queued, how a log line is written) should use the most proven, well-understood patterns available, precisely so that engineering attention stays focused on the parts that are actually novel and actually risky.
3. **Evidence before elegance** — a design that looks clean on a whiteboard and cannot be verified in production is a liability, not an asset (Article III, applied to the engineering process itself, not just JARVIS's runtime outputs).
4. **Fail closed, never fail silent** — any component uncertain about whether an action is authorized must refuse it, not guess in the owner's favor. A misfire that blocks a legitimate action is an inconvenience; a misfire that permits an illegitimate one is a constitutional breach.
5. **Small blast radius by default** — every component should be built assuming its neighbors will eventually misbehave, and scoped so that misbehavior is contained rather than cascading.
6. **Build to be rebuilt** — from the Blueprint's closing note: everything below the Constitution is expected to be replaced at least once over ten years. Architecture that resists being rebuilt is architecture that will eventually be worked around instead, which is worse.

**Trade-off discussion.** Principle 4 (fail closed) has a real usability cost — an overly cautious Core will pester the owner with confirmations it doesn't strictly need, feeding directly into the approval-fatigue risk the Blueprint already names in Section 11. This document resolves that tension the same way the Blueprint does: fail-closed governs Tier 2/3 uncertainty; Tier 0/1 uncertainty may default to proceeding-with-disclosure rather than blocking, because the cost of being wrong at that tier is low and the cost of constant friction is real.

---

## 4. System Overview

**Objectives.** Provide a single reference diagram of JARVIS Core's internal composition, consistent with — and subordinate to — the Blueprint's six-layer diagram.

```
                         ┌─────────────────────────┐
                         │      INTERFACE LAYER      │
                         │  (voice, chat, briefing)  │
                         └────────────┬─────────────┘
                                      │  intent
                         ┌────────────▼─────────────┐
                         │   ORCHESTRATION LAYER      │
                         │  ┌──────────────────────┐  │
                         │  │ Intent Processor      │  │
                         │  ├──────────────────────┤  │
                         │  │ Task Planner           │  │
                         │  ├──────────────────────┤  │
                         │  │ Workflow Engine        │  │
                         │  ├──────────────────────┤  │
                         │  │ Agent Router           │  │
                         │  ├──────────────────────┤  │
                         │  │ Context Manager        │  │
                         │  └──────────────────────┘  │
                         └───┬─────────┬─────────┬────┘
                             │         │         │
                 ┌───────────▼──┐ ┌────▼────┐ ┌──▼────────────┐
                 │ Permission   │ │ Approval │ │ Audit Ledger  │
                 │ Engine (API) │ │ Engine   │ │ (write path)  │
                 │              │ │ (API)    │ │               │
                 └──────────────┘ └──────────┘ └───────────────┘
                             (contracts only — internals
                              specified in later documents)
```

**Design rationale.** The Orchestration Layer is deliberately drawn as five internally distinct components rather than one monolith, because each one answers a different question (what does the owner want; what steps does that require; in what order do they run; which agent does each step; what context does each step need) — collapsing them into one component is exactly the kind of convenience the Engineering Philosophy's Principle 1 warns against.

**Alternative considered.** A single "Orchestrator" component handling all five responsibilities internally, exposing only one external interface. Rejected on maintainability grounds: five responsibilities in one component means five reasons to change it, which is the textbook definition of a component that will eventually be feared rather than modified.

---

## 5. Repository Architecture

**Objectives.** Establish the logical (not literal, since no code is specified here) separation of concerns across the codebase that will eventually implement this document.

**Architectural decision.** A **polyrepo-leaning, registry-mediated** structure: JARVIS Core lives in its own repository; each specialist agent lives in its own repository; the Agent Registry (Blueprint Section 5) is the only place that knows all of them exist simultaneously. No agent repository may depend on another agent repository directly — any shared need routes through the Core's published contracts or the Knowledge Store.

**Alternatives considered.**
- *Monorepo.* Advantages: simpler dependency management, atomic cross-component changes, easier for a solo owner to navigate. Disadvantages: directly invites the layer-blurring risk the Blueprint warns about — a monorepo makes it trivial for an agent to reach into another agent's internals "just this once," and trivial things get done. **Rejected for the long term, though acknowledged as the more practical starting point — see the transitional note below.**
- *Full polyrepo with direct agent-to-agent dependencies.* Rejected outright: this is Article VI's confused-deputy risk given a build system, not just a runtime one.

**Transitional note, stated honestly.** Given the current single-owner, mobile-only, GitHub-web-UI development reality, a strict polyrepo from day one carries real practical friction. The recommended path is to **begin with a single repository, internally organized with the same sharp boundaries a polyrepo would enforce** (clearly separated top-level directories, no cross-boundary imports even though the tooling would allow them), with an explicit, planned migration to true polyrepo once agent count and contributor count justify the operational overhead. This is a disciplined trade-off, not a compromise of principle — the boundary is enforced by convention now and by tooling later, but it is never allowed to be absent.

**Risks.** A single-repo-with-internal-boundaries approach only works if the boundary is actually respected without tooling enforcement. Mitigation: Development Standards (Section 29) mandates that any change crossing a declared boundary requires explicit justification in its review, not silent approval.

---

## 6. Core Runtime Architecture

**Objectives.** Define the runtime shape of JARVIS Core — what kind of process it is, what state it holds, what it doesn't.

**Architectural decision.** JARVIS Core is a **long-lived orchestration process that is itself stateless between tasks.** Every task's working state (Section 14) is persisted to the Memory Layer, not held only in the Orchestrator's process memory. This directly implements the Blueprint Section 7 mitigation for the Orchestrator-as-single-point-of-failure risk: a stateless Core can be restarted, redeployed, or horizontally scaled without losing an in-flight task, because the task's state was never uniquely owned by one running process to begin with.

**Alternative considered.** A stateful Orchestrator optimized for low-latency in-memory task tracking. Rejected: faster in the short term, but reintroduces exactly the single-point-of-failure risk the Blueprint explicitly flagged as a named risk (Blueprint Section 21). Given this system is meant to run reliably for a decade, resilience is weighted above marginal latency.

**Scalability considerations.** Statelessness is what makes Blueprint Section 23's "technical scalability" axis achievable without a later rearchitecture — going from one Core instance to several (for load, for redundancy, or eventually for multi-owner governance per Section 23's flagged decision point) is a deployment change, not a design change, if this principle is honored from the start.

---

## 7. Bootstrap Process

**Objectives.** Define, in order, what must be true before JARVIS Core accepts a single instruction from the owner.

**Architectural decision — the bootstrap sequence:**

1. **Load and validate the Constitution reference.** Confirm the version, confirm structural presence of all seven Articles (Section 2 above). Failure here halts bootstrap entirely — a JARVIS Core that cannot verify its own constitutional grounding must not run at all, under any circumstance, including "just to check status."
2. **Establish Audit Ledger connectivity** before anything else that could produce an auditable event. A system that could act before it could record that it acted is a system with a built-in accountability gap.
3. **Initialize the Permission Engine's contract layer.** No capability grants are issued yet — this step only confirms the Engine is reachable and its schema matches what this Core version expects.
4. **Load the Agent Registry.** Every agent listed is checked for a valid, current lifecycle state (Blueprint Section 6) — an agent stuck in a stale or unreviewed state is loaded as *known but not routable* rather than silently excluded or silently trusted.
5. **Run a self-health check** (Section 22) covering every dependency touched in steps 1–4.
6. **Enter ready state**, and only now accept input from the Interface Layer.

**Design rationale for strict ordering.** Each step's success is a precondition for the next, and the ordering itself is a security property: constitutional grounding before audit capability, audit capability before any capability grants, capability infrastructure before agent awareness, agent awareness before traffic. Reordering these (for instance, accepting input before the Audit Ledger is confirmed reachable) would create a window where consequential actions are possible without a guaranteed record — unacceptable under Article IV regardless of how short that window might be.

**Risks.** A strict sequential bootstrap is slower to reach ready state than a parallelized one. **Mitigation:** steps 3 and 4 have no ordering dependency on each other and may run concurrently; the ordering constraint that matters is 1 → 2 → {3, 4} → 5 → 6, not full linear sequencing.

---

## 8. Lifecycle Management

**Objectives.** Distinguish the lifecycle of JARVIS Core itself from the lifecycle of the agents it manages (already defined in Blueprint Section 6).

**Architectural decision.** JARVIS Core has its own version lifecycle (proposed → staged → released → deprecated), tracked separately from any individual agent's lifecycle and from the Constitution's own version (Article VII). A Core release is a Tier 2 governance action; a Constitution amendment is always owner-initiated per Article VII, and a Core release must never be used as a vehicle to smuggle a constitutional change — this is stated explicitly because "we changed the code, and the code now behaves differently" is exactly the kind of soft reinterpretation Article VII exists to prevent.

**Compatibility rule.** Every Core release declares the minimum and maximum Constitution version it is compatible with. A Core version incompatible with the currently active Constitution fails bootstrap Step 1 rather than attempting a best-effort interpretation.

---

## 9. Orchestration Engine

**Objectives.** Define the Orchestration Layer's internal control flow, tying together Intent Processing, Task Planning, the Workflow Engine, Agent Routing, and Context Management into one coherent request lifecycle.

**Architectural decision — the request lifecycle:**

```
Owner Input
   → Intent Processing (Section 10)
   → Task Planning (Section 11)
   → Tier Classification (per task, per Blueprint Section 11)
   → Workflow Engine execution begins (Section 12)
        → for each task node:
             → Agent Routing (Section 13)
             → Context assembly (Section 14)
             → Approval gate check (Section 15) — blocks if Tier 2/3
             → Agent execution (delegated, outside Core's direct control)
             → Result returned to Workflow Engine
             → Audit write (Section 16)
   → Final result assembled → Interface Layer → Owner
```

**Design rationale.** This lifecycle is intentionally linear and auditable at every arrow — each transition is a point where the system could, in principle, be paused, inspected, or resumed by a different process entirely. That property is not incidental; it is what makes the stateless runtime decision in Section 6 actually implementable rather than aspirational.

**Alternative considered.** An event-driven, fully asynchronous orchestration model where task nodes react to events rather than being explicitly sequenced. Advantages: better throughput at high agent counts. Disadvantages: substantially harder to audit deterministically — Article IV requires reconstructing "what was proposed, what evidence supported it, who approved it, when" for every consequential action, which is materially easier to guarantee in a linear, checkpointed model than in a loosely-coupled event mesh. **Rejected for v1, revisit explicitly in Section 34 (Future Evolution) once agent count and throughput demands justify reopening this decision** — not before.

---

## 10. Intent Processing

**Objectives.** Convert raw owner input (voice or text) into a structured intent the Task Planner can act on, including a first-pass tier estimate.

**Architectural decision.** Intent Processing produces three outputs, never fewer: (1) a structured representation of what the owner appears to want, (2) a confidence score for that interpretation (Article III applied to the Core's own understanding of the owner, not just to agent outputs), and (3) an explicit ambiguity flag when confidence is insufficient to proceed to planning.

**Design rationale.** An intent processor that silently picks its best guess when genuinely uncertain is the Core-level equivalent of an agent fabricating information — Article III does not carve out an exception for "the system trying to be helpful." When ambiguity is flagged, the Workflow Engine's correct behavior is to ask a clarifying question through the Interface Layer before any task is planned, not to plan defensively and hope the approval gate catches the mistake later.

**Risk.** Over-conservative ambiguity flagging degrades usability, reintroducing friction the whole system is meant to reduce. **Mitigation:** ambiguity thresholds are tunable per Tier — a misread Tier 0 request costs little to get wrong and re-ask; a misread Tier 3 request must never proceed on a guess, so the threshold for "ask instead of assume" tightens as estimated tier rises.

---

## 11. Task Planning

**Objectives.** Decompose a structured intent into an ordered graph of executable tasks, each assignable to exactly one agent.

**Architectural decision.** The Task Planner produces a directed acyclic graph (conceptually — no implementation prescribed), where every node carries: the task description, its dependencies, its target domain (for routing), and a tier classification. **A task graph's overall tier is the maximum tier of any node within it** — this is a deliberate, conservative rule: a nine-step workflow that is entirely Tier 0 except for one Tier 3 step is a Tier 3 workflow as far as the owner's attention is concerned, never quietly presented as low-stakes because most of its steps were trivial.

**Alternative considered.** Per-node tier gating only, with no graph-level rollup. Rejected: this would allow a Tier 3 action buried in step seven of nine to reach its approval gate without the owner having been given any upfront signal that the overall request was ever going to touch something consequential — technically compliant with Article V, but a clear violation of its spirit, and exactly the kind of technically-legal-but-wrong outcome this document exists to prevent by being explicit rather than silent.

---

## 12. Workflow Engine

**Objectives.** Execute a task graph while correctly pausing at every approval gate and correctly resuming afterward, without losing state (Section 6).

**Architectural decision.** The Workflow Engine treats an approval gate as a **first-class suspend point**, not an exception or an interrupt. A workflow awaiting Tier 2/3 approval is not "running with a callback pending" — it is fully checkpointed, its state persisted, and it can survive a full Core restart while awaiting the owner's response. This is the direct, practical consequence of Section 6's statelessness decision: if it weren't true, statelessness would be a design intention that quietly stopped being true the moment a real approval gate was involved.

**Risks.** Long-lived suspended workflows accumulate over time if the owner never responds. **Mitigation:** every suspended workflow carries an explicit expiry policy, visible to the owner, after which it is marked stale (not silently retried, not silently cancelled) and surfaced in the next Daily Briefing (Blueprint Section 20) for explicit disposition.

---

## 13. Agent Routing

**Objectives.** Determine which specialist agent handles a given task node, resolving the domain-hierarchy ambiguity the Blueprint explicitly flagged as unresolved (Blueprint Section 6).

**Architectural decision — a strict domain tree, not a flat list.** Every agent declares exactly one parent domain in the Agent Registry (a root agent has no parent). Routing resolves to the most specific registered agent for a task's domain; if no agent is registered at that specificity, routing escalates to the nearest parent rather than guessing sideways across unrelated domains. Concretely, resolving the Blueprint's flagged ambiguity: **Engineering is a root domain; GitHub, Deployment, and Database are children of Engineering**, because each is a tool an engineering task reaches for, not an independent objective a task exists to pursue in its own right. **Research and Trading are independent root domains**, siblings of Engineering, because they represent genuinely distinct objectives the owner might pursue, not tools subordinate to engineering work.

**Design rationale.** This resolves the exact ambiguity the Blueprint named without touching the Constitution — domain hierarchy is an engineering decision the Constitution deliberately left open, and resolving it here, explicitly and with stated reasoning, is precisely what a document like this exists to do.

**Alternative considered.** A flat, non-hierarchical registry where every agent is a peer and routing is purely capability-matched. Rejected: this is what produced the original ambiguity — two peer agents with overlapping capability claims have no principled way to resolve a conflict except arbitrary priority ordering, which is fragile and undebuggable at scale.

**Risk.** A strict tree can misroute a genuinely cross-domain task (e.g., "investigate why the deployment broke the trading signals" spans Engineering and Trading). **Mitigation:** the Task Planner (Section 11) is explicitly permitted to decompose a single owner request into multiple task-graph branches routed to different root domains — the tree constrains *where a single task goes*, not *how many domains a single request may touch*.

---

## 14. Context Management

**Objectives.** Ensure every agent receives exactly the context a task requires — no more, no less — with full provenance.

**Architectural decision.** Context assembled for a task node is **scoped and provenance-tagged**: every fact included states whether it came from the owner's current instruction, from Episodic Memory, from the Knowledge Store, or from a prior task node's output in the same graph — directly implementing the Blueprint Section 8 distinction between "the owner decided X" and "an agent suggested X and the owner didn't object." An agent receiving context with unclear provenance is a design defect, not an acceptable simplification.

**Risk.** Over-scoping context (giving an agent less than it needs) causes task failure; under-scoping (giving more than it needs) violates least-privilege in spirit even when the Permission Engine's formal grants are correct. **Mitigation:** context assembly is task-specific, computed fresh per node from the task graph's declared dependencies, never inherited wholesale from a broader session.

---

## 15. Approval Framework Integration

**Objectives.** Specify precisely how the Orchestration Layer calls into the Approval Engine (Blueprint Section 11) — the contract, not the Engine's internals (reserved for a future JARVIS-002).

**Architectural decision.** Every task node's approval gate call passes: the task description, its tier, its evidence (Article III), and — for Tier 3 specifically — a **rendered consequence statement**, generated fresh per action rather than pulled from a static template, because a templated "this action is irreversible" loses exactly the specificity (Blueprint Section 11) that distinguishes real confirmation from reflexive assent. The Workflow Engine treats the Approval Engine's response as a hard gate: no path exists in the Orchestration Layer's design that allows a task to proceed on anything other than an explicit approval response for its classified tier.

**Design rationale for "no path exists."** This is stated as strongly as it is deliberately — an approval gate that can be bypassed under *some* internal condition (a retry storm, a timeout defaulting to proceed, a "trusted" agent skipping the check) is not a gate, it's a suggestion. The Engineering Philosophy's fail-closed principle (Section 3) means every failure mode of the approval-gate call itself — timeout, Engine unreachable, malformed response — resolves to **blocking the task**, never to proceeding without confirmation.

---

## 16. Audit Framework Integration

**Objectives.** Specify what the Orchestration Layer writes to the Audit Ledger, and when.

**Architectural decision.** A write occurs at every state transition named in Section 9's lifecycle diagram — not only at "action executed," but at intent received, plan produced, tier classified, approval requested, approval resolved, execution result, and workflow completion. This granularity is deliberate: Article IV requires reconstructing the *entire* chain, and a Ledger that only records final outcomes cannot answer "what was proposed" or "what evidence supported it" after the fact, which defeats the Article's purpose while technically maintaining *a* record.

**Distinction from Logging (see Section 20).** The Audit Ledger is a constitutional record; operational logs are a diagnostic tool. This document deliberately keeps them architecturally separate — collapsing them into one system risks exactly the failure this project has already lived through once: this project's own PR 8 investigation found that its diagnostic logging was silently unconfigured and produced nothing for weeks, precisely because logging was being asked to do a job (evidentiary record-keeping) it was never architected for. The Audit Ledger must never depend on the same configuration surface as operational logging, so a misconfigured logger can degrade diagnostics without ever silently degrading the constitutional record.

---

## 17. Configuration Framework

**Objectives.** Separate what changes per-environment from what must never change without governance.

**Architectural decision.** Three distinct configuration classes: (1) **operational configuration** — timeouts, retry counts, log verbosity — changeable freely, Tier 0 governance; (2) **structural configuration** — agent registry entries, domain hierarchy, tier thresholds — Tier 2 governance, reviewed and audited; (3) **constitutional references** — the Constitution version this Core instance targets — changeable only through the Article VII amendment process, never through ordinary configuration deployment. Class 3 is deliberately *not* stored alongside classes 1 and 2, specifically so that a routine configuration deployment can never accidentally (or maliciously) alter which constitution is in effect.

**Risk.** Three configuration classes add operational complexity versus one unified config file. **Mitigation:** the complexity is the point — it makes an attempted Article VII bypass structurally harder to execute by accident, and structurally visible if attempted deliberately.

---

## 18. Secrets Management

**Objectives.** Ensure credentials for external integrations are never held longer, or more broadly, than a task requires.

**Architectural decision.** Secrets are issued by the Permission Engine as **task-scoped, expiring grants** — an extension of the Blueprint Section 10 principle applied specifically to credentials. No agent holds a standing secret; every credential use is requested fresh for the task at hand and expires with it. The Integration Gateway (Blueprint Section 18) is the only component that ever presents a live credential to an external system — agents receive a capability to act, never the underlying secret itself, wherever the integration pattern allows that separation.

**Alternative considered.** Long-lived, per-agent standing credentials, refreshed periodically. Rejected: this is the exact asymmetry the Blueprint's Permission Engine section warns about — credentials that are simple to grant and painful to revoke will only ever expand in practice. Task-scoped expiry makes revocation the *default* behavior (a grant that isn't renewed simply lapses) rather than something that has to be actively remembered and performed.

---

## 19. Dependency Management

**Objectives.** Treat external libraries and integrations as trust-tiered inputs, consistent with Blueprint Section 17's plugin framework.

**Architectural decision.** Any external dependency — a library, an SDK, an integration — is reviewed and trust-tiered before adoption, with the same rigor as a third-party plugin, because from a supply-chain-risk perspective it *is* one. Dependency updates affecting anything touching Tier 2/3 code paths require the same review discipline as a Core release (Section 8), not a routine, low-friction bump.

**Risk.** This is more overhead than typical dependency hygiene. **Mitigation:** the elevated scrutiny applies specifically to dependencies in the trust-critical path (Permission Engine, Approval Engine, Audit Ledger, Integration Gateway) — dependencies used only in low-stakes, Tier 0 tooling do not require the same ceremony, keeping the overhead proportional to actual risk.

---

## 20. Logging Architecture

**Objectives.** Provide operational diagnostics distinct from, and never a substitute for, the Audit Ledger (Section 16).

**Architectural decision.** Structured, provenance-tagged operational logs, explicitly scoped as a **diagnostic aid for engineers**, not a constitutional record. Every log entry is attributable to a specific task-graph node and correlates to that node's Audit Ledger entries, but the reverse dependency must never exist — the Audit Ledger must never require the logging system to be correctly configured in order to be complete.

**Case study, stated explicitly because it is real and instructive.** This project's own experience investigating why historical research generation produced no data is the direct justification for this section's strictness: a diagnostic logging layer that depended on configuration nobody had verified was silently absent for an extended period, and the resulting blind spot delayed root-causing a production issue by several investigation rounds. That failure was entirely in the *diagnostic* layer — no constitutional record was ever at risk, because none existed for that subsystem yet. This document ensures that when JARVIS Core exists, the equivalent failure could degrade troubleshooting speed, but could never degrade the Audit Ledger's completeness, because the two systems do not share a dependency.

---

## 21. Observability

**Objectives.** Make the Core's internal behavior inspectable in aggregate, not just reconstructible after the fact via the Audit Ledger.

**Architectural decision.** Metrics and tracing correlate to task-graph execution (throughput, tier distribution over time, approval response latency, agent routing distribution) and are treated as a health signal, not a governance record — this keeps Observability's purpose distinct from both Logging (Section 20, diagnostic) and Audit (Section 16, evidentiary), a third clearly separated concern rather than a third name for the same thing.

**Future evolution.** As agent count grows (Blueprint Phase 2 onward), observability data is the primary input to detecting the "agent-level degradation" risk named in Blueprint Section 19 — a Research Agent whose confidence scores are technically well-formed but trending toward unjustified certainty is a pattern only visible in aggregate, not in any single audited action.

---

## 22. Health Monitoring

**Objectives.** Extend Blueprint Section 19 into a concrete Core-level responsibility.

**Architectural decision.** Three distinct health checks, run on independent schedules: **Core self-health** (are the five Orchestration components and their contract connections to Permission/Approval/Audit functioning), **pipeline integrity** (is every Tier 2/3 path still actually routing through an approval gate — a direct, periodic adversarial self-check, not just an assumption that Section 15's design is being honored in the running system), and **agent health** (is a given specialist agent's output pattern still consistent with its declared trust tier, surfaced from Observability data).

**Design rationale for pipeline integrity as its own check.** Every other health check asks "is this working." Pipeline integrity asks "has something found a way around a control that's supposed to always apply" — a fundamentally more adversarial question, and one worth checking on its own schedule specifically because a bypass, if it existed, would not necessarily show up as a conventional failure anywhere else.

---

## 23. Error Handling Strategy

**Objectives.** Define what happens when something inside the Orchestration Layer itself fails, as distinct from an agent reporting a failure.

**Architectural decision.** Errors are classified into two categories with entirely different handling: **recoverable operational errors** (a timeout, a transient integration failure) retry according to task-appropriate policy and are logged; **constitutional-boundary errors** (an approval gate unreachable, an audit write failing, a permission check returning an ambiguous result) **halt the affected task immediately and unconditionally**, regardless of retry policy, because Section 3's fail-closed principle applies with zero exceptions to anything touching Articles III through VI's guarantees.

**Risk.** Conflating the two categories, even briefly during implementation, would be a serious constitutional risk. **Mitigation:** the classification is made structurally explicit in this document precisely so it cannot be quietly blurred later — any future engineer or coding agent extending this system has this document's explicit instruction that these two error classes are never unified into one generic "error" handling path.

---

## 24. Recovery Strategy

**Objectives.** Define how the system recovers from a failure without producing an unaudited or partially-executed consequential action.

**Architectural decision.** Recovery is Audit-Ledger-driven: on restart, the Workflow Engine reconciles in-flight task graphs against their last recorded Ledger state (Section 16's granular write points make this possible) rather than trusting any in-memory state that might have survived a crash. Any task found mid-execution at a point past its approval gate but before a confirmed completion is treated as **unresolved, not retried automatically** — resuming a Tier 2/3 action without re-confirming its state is a rollback decision the owner makes, per the Constitution's Prepare→...→Rollback pipeline, not one the Core makes unilaterally on its own initiative.

**Rationale.** Automatic retry of a possibly-already-executed irreversible action is a textbook way to cause real harm in the name of resilience. This document deliberately trades a small amount of automated convenience for a guarantee that recovery never becomes a second, unaudited execution path.

---

## 25. Security Foundation

**Objectives.** Translate Blueprint Section 12's threat model into concrete Core-level architectural properties.

**Architectural decision, mapped directly to the Blueprint's named threats:**
- *Compromised session* → addressed structurally by Section 15's per-action, freshly-rendered consequence statements (never satisfiable by a stolen session's reflexive "yes").
- *Confused deputy* → addressed structurally by Section 13's strict domain-tree routing plus the categorical absence of any agent-to-agent direct channel anywhere in this design (Section 9's lifecycle has no such path).
- *Compromised/malicious integration* → addressed structurally by Section 18's task-scoped secret expiry and Section 19's elevated dependency review for anything in the trust-critical path.
- *Scope creep via convenience* → addressed structurally by Section 17's three-tier configuration separation and Section 8's explicit Core/Constitution version-decoupling.
- *Silent constitutional drift* → addressed structurally by Section 2's runtime compatibility check and Section 16's exhaustive audit granularity.

**Design rationale for this mapping.** A security foundation section that lists generic best practices without tying each one back to a named, specific threat is decoration, not architecture. Every decision above already exists elsewhere in this document — this section's actual contribution is making the coverage explicit and checkable, so a future reviewer can verify no named threat from the Blueprint was left unaddressed.

---

## 26. Startup Sequence

*(Formal restatement of Section 7's bootstrap process, presented here as the canonical operational sequence for deployment documentation purposes.)*

1. Constitution load & structural validation — halt on failure.
2. Audit Ledger connectivity established.
3. Permission Engine contract verified.
4. Agent Registry loaded, each entry lifecycle-checked.
5. Self-health check across steps 1–4.
6. Ready state entered; Interface Layer begins accepting input.

No step is skippable in any deployment context, including local development — a "development mode" that bypasses constitutional validation "just for testing" is precisely the kind of exception that becomes permanent. If a lighter-weight development configuration is genuinely required, it must use a clearly-labeled development Constitution reference, never a skipped check.

---

## 27. Shutdown Sequence

**Objectives.** Ensure no in-flight consequential action is left in an ambiguous state when JARVIS Core stops.

**Architectural decision.**
1. Stop accepting new intent from the Interface Layer.
2. Allow in-flight Tier 0/1 tasks to complete or checkpoint cleanly within a bounded grace period.
3. Any Tier 2/3 task awaiting approval is checkpointed (per Section 12, this should already be durable) — never force-completed, never force-cancelled.
4. Final Audit Ledger writes confirming Core shutdown state, including the explicit list of any tasks left suspended.
5. Process terminates only after step 4's write is confirmed.

**Rationale.** Symmetrical with Section 24's recovery philosophy — a clean shutdown and a crash should leave the system in the same reconcilable state, because Recovery Strategy is defined entirely in terms of reconciling against the Ledger, not in terms of trusting that shutdown was "graceful."

---

## 28. Scalability Strategy

**Objectives.** Extend Blueprint Section 23's two-axis framing (technical vs. governance scalability) into concrete Core design commitments.

**Technical scalability.** Directly enabled by Section 6's stateless runtime and Section 9's checkpointed, linear-but-parallelizable request lifecycle: additional Core instances can be added behind the Interface Layer without redesign, because no instance uniquely owns any task's state.

**Governance scalability.** Explicitly **not** solved by this document. Multi-owner governance remains the Blueprint's flagged Phase 5 decision point (Blueprint Section 24), and nothing in this Core design should be read as pre-committing to a specific multi-owner model — Section 17's configuration framework and Section 15's approval-gate design are both single-owner-shaped today, deliberately, pending that explicit future decision.

---

## 29. Development Standards

**Objectives.** Translate Blueprint Section 14's Engineering Framework into standards specific to building the Core itself.

**Architectural decision.** Every change to JARVIS Core follows: Investigate → Propose (Risk/Confidence/Evidence/Impact/Rollback/Approval, per Blueprint Principle 12) → Confirm scope → Implement the smallest change that addresses the confirmed need → Validate with evidence, not assertion → Present the actual diff → Await approval at the tier appropriate to what's being touched (a change to Section 15's approval-gate logic is never less than Tier 2, regardless of how small the diff looks) → Merge → Audit.

**Explicit standard.** Any change touching the components named in Section 25's threat mapping (approval gating, audit writes, permission checks, agent routing) requires a written statement of which named threat, if any, the change affects — not as bureaucracy, but because Section 25's value depends entirely on that mapping staying current as the system evolves.

---

## 30. Testing Philosophy

**Objectives.** Test constitutional compliance itself, not only functional correctness.

**Architectural decision.** Alongside conventional functional and integration testing, JARVIS Core requires **adversarial constitutional tests** as a first-class, permanent category: deliberate attempts to bypass a Tier 3 approval gate, to route a task outside the domain tree, to have an agent act on unresolved delegated authority, to have a Core restart silently auto-resume a suspended Tier 2/3 task. These tests exist specifically to keep Section 22's "pipeline integrity" health check honest — a control that has never been deliberately attacked in a test environment is a control whose real strength is simply unknown.

**Rationale.** Functional tests answer "does it work." Adversarial constitutional tests answer "does it hold when something tries to make it not work" — the second question is the one this entire document exists to make sure has a confident, evidenced answer, in keeping with Article III applied to the engineering process itself.

---

## 31. Versioning Strategy

**Objectives.** Keep Core versioning, Constitution versioning, and Agent versioning independently trackable and explicitly cross-referenced.

**Architectural decision.** Three separate version identifiers, never conflated: Constitution version (changes only via Article VII), Core version (changes via Section 29's standard development process, Tier 2 minimum), and per-agent version (Blueprint Section 6's lifecycle). Every Core release publishes its compatible Constitution version range (Section 8); every agent registration publishes its compatible Core version range. This three-tier compatibility chain is what makes Section 2's runtime compatibility check (and Section 7's bootstrap failure mode) meaningful rather than cosmetic.

---

## 32. Deployment Considerations

**Objectives.** Establish deployment discipline proportional to what's being deployed, consistent with Section 11's tiering philosophy applied to the Core's own releases.

**Architectural decision.** Core releases touching only Tier 0/1-adjacent components (Interface Layer wording, logging verbosity) may deploy with lightweight review. Releases touching anything named in Section 25's threat mapping require staged rollout with an explicit, tested rollback plan **before** deployment begins, per Blueprint Principle 12 — a rollback plan authored after a problem is discovered is not a rollback plan, it's an improvisation.

---

## 33. Operational Risk Analysis

**Objectives.** Extend Blueprint Section 21's risk register with risks specific to Core engineering.

| Risk | Source | Mitigation (this document) |
|---|---|---|
| Orchestrator single point of failure | Blueprint §7, §21 | Stateless runtime (§6), checkpointed lifecycle (§9, §12) |
| Approval gate silently bypassed | Novel to Core engineering | No-alternative-path design (§15), adversarial testing (§30), pipeline integrity health check (§22) |
| Audit Ledger incomplete due to logging conflation | Demonstrated by this project's own history | Structural separation of Audit vs. Logging (§16, §20) |
| Constitutional drift via configuration deployment | Blueprint §12 | Three-tier configuration separation (§17) |
| Domain-routing ambiguity causing misrouted tasks | Blueprint §6 (flagged, unresolved) | Strict domain tree, resolved explicitly (§13) |
| Automatic recovery causing unaudited duplicate execution | Novel to Core engineering | Ledger-reconciled, owner-confirmed recovery only (§24) |
| Secret over-retention | Blueprint §10, §12 | Task-scoped, expiring grants (§18) |

---

## 34. Future Evolution Strategy

**Objectives.** Name, explicitly, the decisions this document deliberately deferred rather than pretending they were resolved.

**Deferred decisions, stated openly:**
- **Event-driven orchestration** (Section 9) — reopen only once linear checkpointing demonstrably limits throughput at real agent-count scale, not preemptively.
- **True polyrepo migration** (Section 5) — reopen once contributor count or agent count makes the single-repo-with-conventions approach genuinely strained, not on a fixed calendar.
- **Multi-owner governance** (Section 28) — explicitly Blueprint Phase 5's decision, not this document's to make.
- **Cross-domain task-graph patterns beyond simple branching** (Section 13) — revisit once real usage shows the current model insufficient, rather than over-designing for a case that hasn't occurred yet.

**Rationale for deferring rather than deciding now.** Section 3's "build to be rebuilt" principle cuts both ways — over-architecting for scale this system doesn't yet have is its own form of premature commitment, just as risky as under-architecting. Naming these deferrals explicitly, with the condition under which each should be revisited, is how this document stays honest about its own limits rather than implying false completeness.

---

## 35. Implementation Roadmap

*(Scoped to JARVIS Core specifically — distinct from, and nested inside, Blueprint Section 24's ecosystem-wide phases.)*

- **Core Phase 0:** Bootstrap sequence, stateless runtime skeleton, Constitution compatibility check. No agents yet — this phase is complete when the Core can reach ready state and refuse to do so under a deliberately broken constitutional reference.
- **Core Phase 1:** Intent Processing, Task Planning, and the Workflow Engine for single-agent, single-task workflows only — proves the request lifecycle (Section 9) end-to-end at the smallest possible scope.
- **Core Phase 2:** Agent Routing against the domain tree (Section 13) with two or more registered agents — proves routing resolution and cross-domain branching.
- **Core Phase 3:** Full Approval Framework integration (Section 15) across all four tiers, including adversarial testing (Section 30) — this phase is not complete until the pipeline-integrity health check (Section 22) has run against a deliberately hostile test suite and passed.
- **Core Phase 4:** Recovery and shutdown discipline (Sections 24, 27) proven under deliberately induced crash scenarios, not just clean-path testing.
- **Core Phase 5:** Observability and Health Monitoring (Sections 21, 22) at multi-agent scale, feeding the Daily Briefing system (Blueprint Section 20).

Each phase gates the next — Phase 3 in particular must not begin meaningfully until Phase 2's routing is trustworthy, because approval-gate testing against an unreliable router would produce false confidence in exactly the subsystem this entire document treats as non-negotiable.

---

## 36. Acceptance Criteria

JARVIS Core is considered to meet this specification when, for each item below, the claim is demonstrated with evidence (Article III), not merely asserted:

- Bootstrap refuses to reach ready state against a structurally invalid Constitution reference.
- No code path exists — demonstrated, not just reviewed — by which a Tier 2/3 task executes without a corresponding recorded approval in the Audit Ledger.
- A Core process restart mid-task never results in a duplicated Tier 2/3 execution, demonstrated under induced crash testing.
- Domain routing correctly resolves every agent in the current registry to exactly one path in the domain tree, with no ambiguous cases silently defaulting.
- The Audit Ledger's completeness is demonstrated to be independent of operational logging configuration, by disabling logging entirely and confirming the Ledger remains fully populated.
- Every named risk in Section 33's table has a corresponding passing adversarial test (Section 30).

---

## 37. Definition of Done

A given Core release is "done," for purposes of this specification, only when all of the following are simultaneously true:

1. Constitutional compliance is demonstrated per Section 36, not asserted per this document's prose alone.
2. Every consequential change is traceable through the Audit Ledger to a specific owner-approved decision (Article IV, operationally verified).
3. Rollback has been tested for the release, not merely planned (Section 32).
4. All adversarial constitutional tests (Section 30) pass, including any newly added for this release's specific changes.
5. Version compatibility (Section 31) is published and internally consistent across Constitution, Core, and any affected agents.
6. This document itself has been re-checked against the release — if the release changed something this specification describes, this specification is updated in the same change, never left to drift silently out of sync with the system it governs.

---

## Closing Statement

This document translates seven Articles and a six-layer diagram into an engineering reality without adding a single new obligation the Constitution didn't already imply, and without quietly removing one either. Where a decision genuinely wasn't settled by the Constitution — domain hierarchy, repository shape, orchestration model — this document makes that decision explicitly, states what was rejected, and states the condition under which it should be revisited. That is the entire discipline this architecture series is built on, and this document is not exempt from being held to it by whatever comes next in the series.
