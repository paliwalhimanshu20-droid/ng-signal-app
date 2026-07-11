# JARVIS-002 — Intelligence & Agent Framework
### Engineering Specification | JARVIS OS Architecture Series
### Status: Draft for Owner Review | Constitution & JARVIS-001 Compliance: Verified Throughout

*Third document in the JARVIS architecture series. Subordinate to "JARVIS OS — Constitution & Master Architecture Blueprint v1.0" (the Constitution) and to "JARVIS-001 — Foundation & Core Architecture" (the Core specification). Where this document is silent, both govern, in that order. Where any reader perceives a conflict, the Constitution wins over JARVIS-001, and JARVIS-001 wins over this document.*

---

## Preface — What This Document Adds

JARVIS-001 specified the machine that routes, gates, and audits work. It deliberately said nothing about *how JARVIS actually knows things* — what it remembers, what it's willing to call a fact, how confident it's entitled to be, and how a specialist agent earns the right to act at all. That gap is this document's entire purpose.

Everything here sits *inside* the boxes JARVIS-001 already drew. Context Management (JARVIS-001 §14) assembles what an agent receives; this document defines what's allowed to go into that assembly and why. Agent Routing (JARVIS-001 §13) decided *which* agent gets a task; this document defines what an agent *is* well enough to be routed to in the first place. Nothing below reopens a decision JARVIS-001 already made — where this document touches the same subject (agent lifecycle, for instance), it extends the existing decision with the detail JARVIS-001 explicitly deferred, and says so.

---

## 1. Purpose & Scope

**Objectives.** Define the intelligence layer — memory, knowledge, learning, evidence, confidence, reasoning — and the agent framework that intelligence operates through, as a coherent, non-duplicative extension of the Constitution and JARVIS-001.

**Scope boundary.** In scope: the structure and lifecycle of everything JARVIS knows or learns; the specification every agent must satisfy to be registrable; how agents communicate, are trusted, and are routed to (in more depth than JARVIS-001's routing contract required); how evidence and confidence are produced, checked, and reported; how multiple agents collaborate and how their outputs are reconciled when they conflict. **Out of scope:** the Orchestration Layer's request lifecycle (JARVIS-001 §9, unchanged here), the Approval/Audit Engine internals (reserved for JARVIS-004 or later), and any individual agent's domain-specific logic (Research Agent's actual trading heuristics, Engineering Agent's actual investigation techniques — each specialist agent's *behavior* is its own document; this one specifies the *contract* every agent must meet, nothing more).

**Design rationale for this boundary.** JARVIS-001 §1 already established the discipline of narrow, reviewable specifications over one sprawling document. Intelligence and Agents are genuinely one coherent subject — an agent without a memory/knowledge/evidence model to operate under isn't meaningfully different from a routing target with no epistemics — so combining them into one document, as instructed, is architecturally sound. Combining them with individual agents' domain logic would not be, and this document holds that line explicitly.

---

## 2. Relationship to the Constitution

**Objectives.** Restate, at the intelligence layer specifically, JARVIS-001 §2's principle that compliance must be falsifiable, not asserted.

**Architectural decision.** Article III (Non-Fabrication) is the constitutional article this entire document exists to make structurally true, not just observed. Every section from Memory through Reasoning below is, in effect, a different angle on the same question: *what has to be true about how JARVIS stores and processes information so that a confident-sounding wrong answer becomes structurally difficult to produce, rather than merely discouraged.* Where a design choice doesn't visibly serve that goal, it is either misplaced in this document or wrong.

**Article VI (delegated authority must trace back)** is the second load-bearing article here — Sections 21 (Communication Protocol) and 27 (Multi-Agent Collaboration) both exist substantially to keep that Article true as agent count grows past the two-or-three-agent scale where it's easy to take for granted.

---

## 3. Relationship to JARVIS-001

**Objectives.** Make explicit exactly where this document's authority starts and JARVIS-001's stops, since both touch agents and context.

| Concern | Owned by JARVIS-001 | Owned by this document |
|---|---|---|
| Agent routing decision (which agent gets a task) | §13 (domain tree resolution) | — (unchanged, referenced not redefined) |
| Agent lifecycle *state machine* | §8 (proposed→staged→released→deprecated, applied to Core) | §16 (the same pattern, specified fully for *specialist agents*, which §8 explicitly deferred) |
| Context *assembly mechanics* (scoping, provenance tagging at the orchestration level) | §14 | — (unchanged) |
| Context *content rules* (what counts as usable evidence, how confidence is computed) | — | §13, §22, §23 (this document) |
| Approval/Audit *call contract* | §15, §16 | — (unchanged; this document's agents are callers of that contract, not redefiners of it) |
| What an agent fundamentally *is*, structurally | Assumed, not specified | §15 (Base Agent Specification) |

**Rationale for this table.** A specification that touches an adjacent document's territory without stating precisely where the line falls invites exactly the kind of layer-blurring the Constitution's Section 4 warns about — this table exists so that a future engineer reading either document knows immediately which one to amend.

---

## 4. Intelligence Philosophy

**Objectives.** Establish the values specific to JARVIS's cognitive layer, extending JARVIS-001 §3's general engineering philosophy.

**Architectural decisions, stated as principles:**

1. **Calibration over confidence.** A system that is right 70% of the time and *says* 70% is more trustworthy than one that is right 85% of the time and says 99% — the second one will eventually cost more, because its errors arrive disguised as certainties. Every confidence figure this document's frameworks produce is judged on calibration, not on how reassuring it sounds.
2. **Provenance is not metadata, it's the claim itself.** "X is true" and "X is true, sourced from a 2025 document that may now be stale" are different claims, not the same claim with an extra tag — this document treats provenance as inseparable from any fact it governs, never as an optional annotation that could be stripped without changing the fact's meaning.
3. **Memory is evidence, not narrative.** Blueprint §8 already drew this line; this document holds it rigorously through the Learning Framework (§11) specifically, where the temptation to let a system's own summarized memory quietly become treated as ground truth is strongest.
4. **Agents earn trust; they are not issued it.** Extending JARVIS-001 §3's least-privilege-by-default and Blueprint §17's plugin trust tiers into a single, unified Trust Model (§20) that applies identically whether an agent is a first-party specialist or a third-party plugin — there is no architectural reason those two categories should be governed by different rules, and Section 20 explicitly rejects maintaining two.
5. **Reasoning must be inspectable, not just correct.** An agent that reaches the right conclusion through a process nobody can audit has produced a lucky guess wearing a conclusion's clothes, from a governance standpoint — Article IV doesn't distinguish between "wrong and untraceable" and "accidentally right and untraceable" as acceptable outcomes.

---

## 5. Memory Architecture

**Objectives.** Establish the memory subsystem's overall shape before specifying its three tiers individually in Sections 6–8.

**Architectural decision.** Memory is a service the Orchestration Layer and agents *query and write to* through a defined interface — it is not a shared database any component reaches into directly. This is a direct extension of JARVIS-001 §14 (context is scoped and assembled, never inherited wholesale) applied to memory's own storage, not just its use: if agents could query the Memory Store arbitrarily, context scoping at the orchestration level would be a formality agents could route around, which would make §14 a suggestion rather than a control.

**The three tiers** (Working, Episodic, Semantic — Blueprint §8) are architecturally separate stores with separate access patterns and separate decay/retention policies, not three views over one underlying table. This is restated from the Blueprint deliberately: the temptation to "simplify" this into one unified memory store with type tags is strong from an implementation-convenience standpoint and wrong from a governance standpoint, for the exact reasons given in Sections 6–8 below.

**Alternative considered.** A single unified memory store with a `type` field distinguishing working/episodic/semantic. Rejected: a unified store makes it trivial for a query to accidentally (or a compromised agent to deliberately) blend tiers with different trust levels into one result — e.g., treating an unconfirmed working-memory hypothesis with the same weight as a confirmed episodic fact. Separate stores make that blending an explicit, visible architectural choice at query time, not an accident of shared storage.

---

## 6. Working Memory

**Objectives.** Specify the shortest-lived, lowest-trust memory tier.

**Architectural decision.** Working memory holds only the current task graph's in-progress state (JARVIS-001 §12's checkpointed workflow state lives here, structurally) and is **never** treated as a source of confirmed fact by any agent other than the one actively executing the task it belongs to. It is cleared or archived into Episodic Memory (§7) at task completion — archival is a deliberate promotion step, not automatic, and it strips anything marked as a hypothesis that was never confirmed.

**Rationale for archival being a deliberate step, not automatic.** If working memory flowed into episodic memory unconditionally, every abandoned hypothesis, every "let me check if X" that turned out false, would silently become part of JARVIS's permanent history — indistinguishable, without careful re-reading, from things that actually happened. This is precisely the "Human turns vs. Assistant turns, decision vs. suggestion" distinction that has already proven necessary in practice; this document makes it a structural promotion gate rather than a hoped-for discipline.

---

## 7. Episodic Memory

**Objectives.** Specify the "what actually happened" tier, per Blueprint §8.

**Architectural decision.** Every episodic entry carries, non-optionally: what occurred, when, its source task graph (traceable back through the Audit Ledger via JARVIS-001 §16), and — critically — an explicit **decision/suggestion/hypothesis classification**. A record that JARVIS proposed an action is stored distinctly from a record that the owner approved it, which is stored distinctly from a record of the action's actual outcome. Collapsing these three into "what happened" is the single most common way a system like this drifts toward quietly overstating what it actually knows about its own history.

**Advantages.** This structure makes it possible to later ask "how often has JARVIS been right when it suggested X" as a genuinely answerable, evidenced question (feeding the Confidence Framework, §23) rather than a vibe.

**Disadvantages / cost.** Three-way classification is more storage and query complexity than a flat event log. **Accepted as necessary** — the alternative is an episodic memory that can't reliably distinguish its own track record from its own aspirations, which would quietly undermine Section 4's calibration principle from the inside.

---

## 8. Semantic Memory

**Objectives.** Specify the durable-preference tier, and its governance boundary against becoming policy.

**Architectural decision.** Semantic memory holds *derived* preferences — patterns extracted from Episodic Memory over time, never asserted directly by an agent as if they were observed facts. Blueprint §8 already established the core rule (promotion into anything that changes approval behavior requires Article V approval); this document adds the mechanical detail: a semantic-memory entry is versioned, carries the episodic evidence it was derived from, and carries an explicit **scope declaration** — is this preference about *style* (freely applicable) or does it touch *what counts as Tier 2/3* (requires the promotion gate)? An entry with an ambiguous scope declaration is treated as touching approval behavior by default (fail closed, per JARVIS-001 §3's Principle 4), never the reverse.

---

## 9. Knowledge Architecture

**Objectives.** Distinguish Knowledge (domain facts) from Memory (personal/operational history) at the same rigor Blueprint §9 established, and specify its internal structure.

**Architectural decision.** A knowledge entry is a tuple, conceptually: *claim, source type, confidence, version, supersession pointer*. The supersession pointer is the mechanism that makes falsifiability (Blueprint §9) concrete — when a new observation contradicts an existing entry, the old entry is not deleted (deletion would destroy the audit trail of "we used to believe X") but is marked superseded, pointing to the entry that replaced it and the evidence that triggered the replacement.

**This project's own history as the direct justification, again.** The Upstox historical-candle date-range investigation is the canonical example this document is built around: documented knowledge said "30-minute candles, one year of history." Runtime evidence said otherwise. A knowledge architecture without a first-class supersession mechanism would have two bad options when that happened — silently overwrite the old belief (destroying the record of what was documented and when it stopped being true) or keep both entries with no way to know which currently governs (recreating exactly the confusion the investigation had to resolve manually, across several rounds, before the actual limit was found). Supersession pointers are how that entire multi-round investigation becomes a single structural event instead of a recurring manual one.

---

## 10. Knowledge Lifecycle

**Objectives.** Specify how a knowledge entry moves from unverified to trusted to (possibly) superseded.

**Architectural decision.** Four states: **proposed** (an agent or investigation surfaced a candidate fact, not yet corroborated), **corroborated** (confirmed by direct observation or a source type the Evidence Framework, §22, rates highly), **active** (corroborated and currently governing), **superseded** (§9's mechanism). Promotion from proposed to corroborated requires evidence meeting the Evidence Framework's bar for the claim's stakes — a claim that will influence a Tier 3 decision (per JARVIS-001 §11's tier rollup) requires stronger corroboration than a claim that only affects a Tier 0 informational briefing.

**Alternative considered.** A simpler two-state model (unverified / verified). Rejected: it has no way to represent "this was true and now isn't," which is precisely the case that matters most for a system meant to run for a decade against external systems (APIs, documentation, market behavior) that will absolutely change underneath it.

---

## 11. Learning Framework

**Objectives.** Fully specify the preference/goal boundary the Blueprint named in principle (§15) but did not mechanize.

**Architectural decision — the promotion pipeline.** A candidate learned pattern moves: Episodic observation (repeated, not singular — a pattern needs recurrence to be a pattern, not an anecdote) → candidate semantic-memory entry, scope-declared per §8 → if style-scoped, applied directly with disclosure in the next relevant interaction; if approval-scoped, routed through Article V as a standing-policy proposal, exactly like any other consequential action, **never auto-applied regardless of how many times the pattern recurred.** Recurrence increases *confidence* that a pattern is real; it never substitutes for *approval* that the pattern should become policy. This is the concrete mechanism behind the Blueprint's abstract test ("does it change *how* JARVIS pursues a stated objective, or *what* JARVIS decides to pursue") — recurrence-driven confidence answers the "is this real" question; the promotion gate answers the "is this authorized" question, and this document refuses to let a strong answer to the first stand in for the second.

**Risk.** A learning system that requires explicit approval for every approval-scoped pattern change may feel slow relative to the Blueprint's "coordinate without me managing agents individually" vision. **Mitigation, consistent with JARVIS-001 §3's fail-closed-for-Tier-2/3 principle:** this is the correct trade-off, not a bug to be optimized away — a learning system that quietly self-modifies its own approval behavior based on accumulated pattern-matching is one of the more plausible paths toward the "independent goals" outcome Constitutional Principle 3 exists specifically to prevent, and the friction here is proportionate to that risk, not excessive caution for its own sake.

---

## 12. Preference Engine

**Objectives.** Specify how active semantic-memory preferences are actually applied to agent behavior at runtime, distinct from how they're learned (§11).

**Architectural decision.** The Preference Engine is a **read-only consumer** of Semantic Memory (§8) — it never writes preferences itself; writing is exclusively the Learning Framework's job, gated as §11 describes. At runtime, it supplies style/format/default preferences into Context Management's assembly (JARVIS-001 §14) as one provenance-tagged input among several, never as a silent override of an agent's default behavior — an agent receiving a preference always receives it *as* a preference, attributable and inspectable, not baked invisibly into its instructions.

**Rationale for the read/write separation.** Keeping the Preference Engine strictly read-only is what makes §11's promotion gate actually enforceable in practice — if the component that *applies* preferences could also silently *create* them under runtime pressure ("the owner seems annoyed, let me adjust"), the entire promotion pipeline would have a bypass built into its only consumer.

---

## 13. Context Intelligence

**Objectives.** Specify the reasoning layer *above* JARVIS-001 §14's mechanical context assembly — what makes assembled context good, not just correctly scoped.

**Architectural decision.** Context Intelligence is responsible for **relevance ranking and staleness filtering** within whatever JARVIS-001 §14 has already scoped and provenance-tagged: given a correctly-scoped set of candidate facts, which are actually relevant to this specific task, and are any of them from superseded knowledge entries (§9/§10) that should be excluded or flagged rather than presented as current. This is a genuinely separate concern from scoping — scoping answers "what is this agent allowed to see"; Context Intelligence answers "of what it's allowed to see, what's actually worth including, and is any of it known to be out of date."

**Risk.** A relevance-ranking layer is itself a place where silent bias or fabrication-adjacent behavior could creep in (e.g., quietly deciding a piece of context is "irrelevant" because it's inconvenient rather than because it's genuinely unrelated). **Mitigation:** every exclusion decision is itself logged with its stated reason, auditable exactly as any other Orchestration decision under JARVIS-001 §16 — an agent (or the owner) can always ask "what was filtered out of my context, and why."

---

## 14. Agent Framework

**Objectives.** Set the frame for Sections 15–20's detailed agent specification, extending Blueprint §6's four-part agent definition (domain, capability set, trust tier, lifecycle).

**Architectural decision.** This document treats "agent" as a role, not an implementation category — a first-party specialist, a third-party plugin (Blueprint §17), and a future capability not yet imagined are all *agents* from this framework's perspective, distinguished only by their trust tier (§20) and origin, never by a separate rule set. This is Intelligence Philosophy Principle 4 (§4) made structural: there is exactly one Base Agent Specification (§15), and everything that participates in JARVIS is measured against it identically.

---

## 15. Base Agent Specification

**Objectives.** Define the minimum structural contract any agent — first-party or third-party — must satisfy to be registrable at all.

**Architectural decision.** Every agent, without exception, declares: (1) a single domain position in the routing tree (JARVIS-001 §13); (2) an enumerable capability set (Blueprint §6, ceiling not grant, per JARVIS-001 §18's secrets pattern applied generally); (3) its evidence-sourcing behavior — does it produce claims it can source, and at what confidence-reporting standard (feeding §22, §23); (4) its own internal escalation behavior — what does the agent do when *it* is uncertain (must route to ambiguity handling consistent with JARVIS-001 §10's Core-level pattern, applied at the agent level); (5) a declared trust tier default (§20), which the Permission Framework (§19) may further scope down per-task but never up.

**Rationale for making "evidence-sourcing behavior" a structural requirement, not a quality aspiration.** An agent that cannot describe how it sources and grades its own claims cannot meaningfully participate in the Evidence Framework (§22) at all — this document treats that capability as a registration precondition, not a nice-to-have measured after the fact. An agent that can't meet it is not registered, regardless of how otherwise useful it might be.

---

## 16. Agent Lifecycle

**Objectives.** Fully specify the lifecycle JARVIS-001 §8 applied to Core versioning and explicitly deferred, for specialist agents.

**Architectural decision — five states:** **proposed** (a capability gap identified, an agent concept drafted, no registry entry yet) → **reviewed** (Base Agent Specification compliance checked, Tier 2 governance minimum per JARVIS-001 §29's development standards) → **provisioned** (registered with an initial, conservative trust tier — see §20's earned-trust principle — and a scoped permission ceiling, not yet routable for live owner tasks) → **active** (routable, its outputs feeding Observability per JARVIS-001 §21) → **deprecated** (routing disabled, historical audit and episodic records retained, never deleted — Article IV doesn't expire when an agent is retired).

**Explicit addition beyond JARVIS-001 §8's pattern:** a **probation sub-state within "active,"** required for every newly provisioned agent regardless of how thoroughly it was reviewed pre-launch — a fixed period (or task-count threshold) during which the agent's outputs are held to Tier-elevated scrutiny (a Tier 1 task from a probationary agent is treated with Tier 2 approval friction) before its declared trust tier is allowed to take full effect. This exists because review-time compliance and production behavior are not the same evidence, and Section 4's calibration principle applies to the trust *placed in an agent*, not only to the agent's own outputs.

---

## 17. Agent Registry

**Objectives.** Specify the Registry's structure in the detail JARVIS-001 §1 explicitly deferred to this document.

**Architectural decision.** The Registry is the single source of truth for: every agent's current lifecycle state (§16), domain position (JARVIS-001 §13), capability ceiling (§15/§19), current trust tier (§20), and version compatibility range (JARVIS-001 §31). It is queried by the Orchestration Layer at routing time (read-heavy, low-latency) and written to only through the lifecycle transitions in §16 (infrequent, governance-gated) — an access pattern asymmetry worth stating explicitly because it shapes how the Registry should be built: optimized for fast, frequent reads, with writes deliberately made to feel heavier, matching how rarely and carefully they should actually happen.

---

## 18. Capability Framework

**Objectives.** Specify what a "capability" concretely is, distinct from a "permission" (§19).

**Architectural decision.** A capability is a **declared category of action** an agent's design supports ("can read repository contents," "can propose trades," "can draft communications") — it is static, reviewed at registration and on any change (§16), and independent of any specific task. This is deliberately the ceiling JARVIS-001 §10's Permission Engine section already named; this document's contribution is specifying that capabilities are themselves evidence-graded: a capability an agent claims but has never actually exercised successfully carries a "declared, unproven" status distinct from "declared, demonstrated," feeding directly into Trust Model calculations (§20) rather than being taken purely on the agent's own self-report.

---

## 19. Permission Framework

**Objectives.** Specify how a capability (§18, static) becomes a live, task-scoped permission (dynamic), extending JARVIS-001 §18's secrets-specific pattern to permissions generally.

**Architectural decision.** Every permission grant is: task-scoped (expires with the task, per JARVIS-001 §18's already-established pattern), a strict subset of the requesting agent's registered capability ceiling, and requested — never self-issued by the agent claiming to need it. The granting authority is the Permission Engine (JARVIS-001's contract, this document's caller-side behavior); an agent has no mechanism to expand its own live permissions mid-task under any circumstance, including an circumstance the agent itself judges as urgent — urgency changes how fast an approval can be sought (JARVIS-001 §15's tiering already accounts for this), never whether it's sought at all.

---

## 20. Trust Model

**Objectives.** Unify first-party and third-party agent trust under one model, per Intelligence Philosophy Principle 4.

**Architectural decision — trust is earned, computed, and revocable, never assigned once.** An agent's effective trust tier is a function of: its declared trust tier at registration (§16, starting conservative), its probation-period performance (§16), its ongoing track record from Episodic Memory's decision/suggestion/outcome classification (§7 — did this agent's proposals, once approved, actually produce the stated outcome), and time-decay (a good track record from three years ago is weaker evidence than one from three months ago, consistent with Section 4's calibration principle applied to the agent itself, not just its individual claims).

**Alternative considered.** A static, manually-assigned trust tier reviewed only on major incidents. Rejected: this is trust-by-inertia — it under-reacts to gradual degradation (Blueprint §19's named risk: an agent whose outputs are technically well-formed but drifting toward unjustified confidence) and over-reacts only after something has already gone wrong, which is the wrong shape for a control meant to prevent problems rather than document them afterward.

**Risk.** A continuously recomputed trust score is more complex to reason about than a fixed tier, and could itself become a target for gaming (an agent, or its author, optimizing for the score rather than genuine reliability). **Mitigation:** the score is a *health monitoring* signal (feeding §30) that can *lower* effective trust automatically, but any *raising* of an agent's trust tier beyond its registered default requires explicit owner or governance review — asymmetric by design, matching JARVIS-001 §18's "revocation must be cheap, expansion must not be automatic" principle.

---

## 21. Communication Protocol

**Objectives.** Specify the message contract Blueprint §7 described in principle, in the detail needed for a multi-agent system to actually implement Article VI reliably.

**Architectural decision.** Every inter-agent message (always routed through the Orchestrator, per Blueprint §7 — this document does not reopen that decision) carries, non-optionally: a **provenance chain** back to the originating owner instruction (Article VI, made concrete as an actual field, not a principle to be remembered), the sender's current trust tier at time of sending (§20, a snapshot — trust can change between when a message is sent and when it's acted on, and the receiving agent should reason about the trust level *at time of the claim*, not at time of use), and an **evidence/confidence envelope** (§22/§23) for any factual claim the message contains. A message missing any of these three is rejected by the Orchestrator before delivery — restated from the Blueprint deliberately, because this document is where "rejected" becomes a specific, checkable structural requirement rather than a described intention.

---

## 22. Evidence Framework

**Objectives.** Make "evidence" a graded, checkable concept rather than a word used loosely, extending Article III into an operational standard.

**Architectural decision — evidence source types, ranked, not treated as equivalent:**

1. **Direct observation** (a live execution result, a runtime response actually captured) — highest grade.
2. **Documented source** (official documentation, a specification) — high grade, but explicitly *demoted relative to direct observation when the two conflict* — this ranking is the direct, structural lesson of the Upstox investigation already cited in §9: a document said one thing, live behavior said another, and live behavior was correct. This ranking exists so that resolution isn't rediscovered by judgment each time; it's a standing rule.
3. **Inference from corroborated knowledge** (reasoning from active, corroborated knowledge-store entries) — medium grade, always labeled as inference, never presented with the confidence of direct observation.
4. **Pattern/precedent from Episodic Memory** — medium-low grade, and explicitly bounded: a repeated past outcome is evidence about *tendency*, never treated as evidence of a *current* fact without corroboration, especially for anything time-sensitive.
5. **Unsourced or self-reported by the claiming agent with no supporting grade above** — lowest grade, and specifically the grade that triggers the ambiguity-flagging behavior described in §15's base specification and JARVIS-001 §10's Core-level pattern.

**Design rationale.** A flat "evidence: yes/no" model cannot represent the actual situation this project has already lived through — evidence existed (documentation) and was wrong, while a lower volume of a higher-grade type (a handful of live 400 responses) was right. Grading by source type, with an explicit conflict-resolution rule favoring direct observation, is what makes that outcome the system's *default* behavior rather than something a human investigator had to work out fresh under time pressure.

---

## 23. Confidence Framework

**Objectives.** Specify how a confidence figure is computed from graded evidence (§22), so that "confidence" stops being a number an agent asserts and becomes a number the system can check.

**Architectural decision.** Confidence is a function of: the grade of the strongest supporting evidence (§22), the *quantity* of corroborating evidence at that grade or above, and *recency* (per §10's knowledge lifecycle and §9's supersession model — evidence for a claim that hasn't been rechecked in a long time decays in confidence even if nothing has actively contradicted it, because the absence of contradiction is not the same as active confirmation). A claim resting on a single, old, low-grade source is never permitted to be reported at high confidence, structurally — this isn't a review checklist item, it's a computation the Confidence Framework performs before a number is ever surfaced.

**Alternative considered.** Letting each agent self-report confidence based on its own internal reasoning, with the framework only auditing after the fact. Rejected: this is exactly the "confident-sounding wrong answer" failure mode Section 4 names as the thing this entire document exists to prevent — auditing after the fact catches miscalibration once it's already been presented to the owner, which is one investigation-cycle too late.

---

## 24. Decision Framework

**Objectives.** Specify how an agent moves from graded evidence and computed confidence to an actual recommendation, consistent with Blueprint Principle 12's required fields (Risk Level, Confidence Score, Evidence, Impact, Rollback Plan, Approval Requirement).

**Architectural decision.** Every agent recommendation is required to populate all six of Blueprint Principle 12's fields before it can enter the Orchestration Layer's tier-classification step (JARVIS-001 §11) — an agent producing a recommendation missing any field is treated as producing an incomplete output, routed back for completion, never passed through with a gap silently defaulted. Risk Level and Approval Requirement are cross-checked against the Orchestrator's own independent tier classification (JARVIS-001 §11) — **an agent's self-assessed risk level is advisory input to that classification, never a substitute for it**, closing a potential gap where an agent could under-state its own action's stakes to reduce friction on itself.

---

## 25. Investigation Framework

**Objectives.** Formalize, at the agent-capability level, the discipline Blueprint §13 already named as already-operating practice.

**Architectural decision.** "Investigate" is treated as a first-class capability category (§18) with its own evidence-standard floor: an investigation-capable agent's output must trace every claim to §22's grading, must explicitly enumerate what remains unverified rather than omitting it, and — directly extending the pattern this project has repeatedly used successfully — must distinguish *static analysis* (code/documentation review) from *runtime evidence* (execution results) as different evidence grades (§22 items 2 and 1 respectively), never blending them into one undifferentiated "investigated and confirmed" claim.

---

## 26. Reasoning Framework

**Objectives.** Specify what "inspectable reasoning" (Intelligence Philosophy Principle 5) requires structurally.

**Architectural decision.** Any multi-step reasoning chain an agent produces on the way to a recommendation is retained as a **traceable sequence of intermediate claims**, each individually evidence-graded (§22), not collapsed into a single opaque "conclusion" with the intermediate steps discarded. This is what makes an agent's reasoning auditable after the fact under Article IV, and what makes it possible to identify *which specific step* in a chain was weak when a recommendation turns out to be wrong — a capability that's worthless if only the final conclusion was ever recorded.

**Trade-off.** Retaining full intermediate reasoning chains is a real storage and complexity cost, and not every Tier 0 interaction needs it. **Mitigation:** the depth of retained reasoning scales with the task's tier (JARVIS-001 §11) — full chain retention is mandatory for anything Tier 2/3; Tier 0/1 may retain only the final claim and its top-line evidence grade, proportional to stakes rather than uniformly maximal.

---

## 27. Multi-Agent Collaboration

**Objectives.** Specify how two or more agents' outputs combine into one coherent result for a single owner request, extending JARVIS-001 §11's task-graph branching.

**Architectural decision.** When a task graph branches across agents (JARVIS-001 §13's cross-domain branching), each branch's output arrives at a **synthesis step** — not agent-to-agent, always routed through the Orchestrator (§21) — where evidence grades and confidence figures from different agents are combined using the same rules §23 already defines for single-agent claims, not a separate "multi-agent" confidence math. A synthesized result's confidence is never simply the average or the maximum of its contributing branches; it follows §22/§23's grading exactly as if the combined evidence had come from one agent, because from the owner's perspective, it should be evaluated identically regardless of how many specialists contributed to it.

---

## 28. Conflict Resolution

**Objectives.** Specify what happens when two agents' outputs genuinely disagree, rather than merely combine.

**Architectural decision.** A detected conflict (two branches producing contradictory claims about the same fact) is **never silently resolved by trust-tier precedence alone** (i.e., "the higher-trust agent wins" is not an acceptable default) — it is resolved by re-applying the Evidence Framework's grading (§22) to both claims independently. If the grades genuinely tie, the conflict is surfaced to the owner explicitly as an unresolved disagreement, with both positions and their evidence shown, rather than the system picking one and presenting it as settled. This is Article III's non-fabrication standard applied to a case with no clean answer: manufacturing false certainty by arbitrary tie-breaking is a fabrication, even if each individual claim involved was honestly sourced.

**Risk.** Surfacing unresolved conflicts to the owner adds friction and cognitive load. **Mitigation:** genuine evidence-grade ties are expected to be rare if §22's grading is applied consistently — most apparent conflicts will resolve cleanly once graded, and the "surface to owner" path exists specifically for the residual cases where it's actually true that the system doesn't know, which is exactly the situation where owner involvement is correct, not a failure to be engineered away.

---

## 29. Performance Optimization

**Objectives.** Address latency and efficiency without compromising any evidentiary or governance property above.

**Architectural decision.** Optimization targets are explicitly ordered: correctness and auditability (§22–§28) are never traded for speed; within that constraint, caching of *knowledge* (§9, respecting supersession) and *relevance ranking* (§13) are the primary legitimate optimization surfaces, because both can be sped up without touching evidence grading or confidence computation. Caching of *confidence scores themselves* is explicitly disallowed beyond a short, task-bound window — a cached confidence figure is a stale claim about current certainty, and staleness is exactly what §23's recency factor exists to penalize; caching it defeats the mechanism.

---

## 30. Health Monitoring

**Objectives.** Extend JARVIS-001 §22's three health checks with intelligence-layer-specific signals.

**Architectural decision.** Add two checks to JARVIS-001's existing three: **calibration health** (are an agent's or the system's stated confidence figures, tracked against Episodic Memory's actual outcome records per §7, staying calibrated over time — directly operationalizing Section 4's Principle 1) and **knowledge staleness health** (how much of the active Knowledge Store, per §10, hasn't been rechecked against live evidence within a domain-appropriate window — a structural early-warning system for the exact failure mode the Upstox investigation surfaced manually).

---

## 31. Security Considerations

**Objectives.** Identify intelligence-layer-specific attack surfaces beyond JARVIS-001 §25's Core-level threat model.

**Architectural decision, threats named explicitly:**
- **Evidence poisoning** — an agent (compromised or malicious) repeatedly asserting false claims to build up apparent corroboration (§22 item 4's "quantity of corroborating evidence" factor) over time. Mitigated by weighting corroboration by source-agent trust tier (§20) and by diversity of source — many claims from one low-diversity origin do not accumulate confidence the way genuinely independent corroboration does.
- **Trust-score gaming** — an agent optimizing observable behavior specifically to raise its computed trust tier (§20) rather than genuinely improving. Mitigated by §20's asymmetric rule (automatic lowering, governance-gated raising) and by calibration health monitoring (§30) catching a gap between stated confidence and actual outcomes regardless of surface-level metrics.
- **Learning-pipeline abuse** — deliberately manufacturing repeated episodic patterns to force a preference through §11's promotion pipeline. Mitigated by the pipeline's hard requirement that approval-scoped promotions always route through Article V regardless of recurrence count — no volume of manufactured pattern data can substitute for the gate itself.

---

## 32. Risk Analysis

**Objectives.** Extend the risk registers already established in the Blueprint (§21) and JARVIS-001 (§33) with intelligence-layer risks.

| Risk | Source | Mitigation (this document) |
|---|---|---|
| Confident-sounding but miscalibrated agent output | Article III, Intelligence Philosophy §4 | Confidence Framework (§23), calibration health monitoring (§30) |
| Stale knowledge presented as current | Blueprint §9, this project's own Upstox investigation | Supersession model (§9), knowledge lifecycle (§10), staleness health check (§30) |
| Learned pattern quietly becoming unauthorized policy | Blueprint §15 | Promotion pipeline (§11), read-only Preference Engine (§12) |
| Silent multi-agent tie-breaking manufacturing false certainty | Article III | Conflict Resolution's explicit surfacing rule (§28) |
| Evidence poisoning via repeated low-quality corroboration | Novel to this document | Trust-weighted, diversity-weighted corroboration (§31) |
| Trust-tier inertia (static trust missing gradual degradation) | Blueprint §19 | Continuously recomputed, asymmetric Trust Model (§20) |
| Reasoning chains collapsed to unauditable conclusions | Article IV | Reasoning Framework's traceable intermediate claims (§26) |

---

## 33. Future Evolution

**Objectives.** Name deferred intelligence-layer decisions explicitly, matching JARVIS-001 §34's discipline.

**Deferred decisions:**
- **Cross-agent shared learning** (should a pattern learned by one agent inform another agent's priors) — deliberately not specified here; premature at current agent count, and carries its own confused-deputy-adjacent risk (an agent's "learning" quietly becoming another agent's unearned trust) that deserves its own dedicated review once there are enough agents for the question to be real rather than hypothetical.
- **Automated trust-tier raising under strict, pre-approved conditions** — §20 currently requires governance review for every raise; a future, narrowly-scoped exception process may be worth designing once enough track-record data exists to make that safe, but is explicitly not designed here.
- **Formal conflict-resolution escalation beyond "surface to owner"** (§28) — acceptable at current scale; revisit only if owner-facing conflict volume becomes a genuine usability problem, not preemptively.

---

## 34. Implementation Roadmap

*(Nested inside JARVIS-001 §35's Core phases and Blueprint §24's ecosystem phases — this document's roadmap governs the intelligence layer specifically.)*

- **Intelligence Phase 0:** Working and Episodic Memory (§6, §7) with the decision/suggestion/outcome classification enforced from the very first stored entry — retrofitting this classification onto an existing unclassified history is far harder than starting with it.
- **Intelligence Phase 1:** Evidence and Confidence Frameworks (§22, §23) operating on a single agent, proving the grading and computation model before any multi-agent complexity is introduced.
- **Intelligence Phase 2:** Base Agent Specification (§15) and Agent Lifecycle with mandatory probation (§16) applied to a second registered agent — proves the framework generalizes beyond the first, hand-tuned case.
- **Intelligence Phase 3:** Communication Protocol (§21) and Multi-Agent Collaboration/Conflict Resolution (§27, §28) — not attempted before Phase 2 is stable, since multi-agent coordination logic tested against an unproven single-agent foundation would produce false confidence in exactly the wrong layer.
- **Intelligence Phase 4:** Knowledge Architecture and Lifecycle (§9, §10) at full supersession-tracking capability, feeding staleness health monitoring (§30).
- **Intelligence Phase 5:** Learning Framework and Preference Engine (§11, §12) — deliberately last, since a learning system tuning behavior on top of an intelligence layer that hasn't yet proven its evidence and trust models would be learning from an unreliable signal.

---

## 35. Acceptance Criteria

- Every stored memory entry (any tier) carries a decision/suggestion/outcome or equivalent classification — demonstrated by query, not asserted.
- No confidence figure is ever surfaced without a traceable evidence grade (§22) behind it — demonstrated by attempting to produce one without adequate evidence and confirming the system refuses or down-grades rather than complying.
- A knowledge-store conflict between a documented source and a direct observation resolves in favor of direct observation by default, demonstrated with a reconstructed version of this project's own Upstox case.
- An agent cannot self-issue or self-expand a live permission under any tested condition, including simulated urgency.
- A genuine multi-agent evidence tie is surfaced to the owner rather than silently resolved, demonstrated under adversarial testing consistent with JARVIS-001 §30's philosophy.
- Trust-tier increases never occur without governance review, demonstrated by attempting an automatic raise and confirming it is blocked.

---

## 36. Definition of Done

The Intelligence & Agent Framework is "done" for a given release only when:

1. All Section 35 acceptance criteria pass with evidence, not assertion.
2. Every risk in Section 32's table has a corresponding adversarial test, consistent with JARVIS-001 §30's testing philosophy extended into this layer.
3. Calibration health (§30) has been measured against real Episodic Memory outcome data, not only simulated data, for at least one full probation cycle (§16).
4. This document has been re-checked against the release for drift, exactly as JARVIS-001 §37 requires of itself — a specification that stops matching the system it governs has already failed at its one job, regardless of how sound its reasoning was when written.

---

## Closing Statement

JARVIS-001 built the machine that could be trusted to route and gate work correctly. This document builds the part that has to be trusted to *know things* — which is the harder problem, because a routing failure is usually loud and a quietly miscalibrated fact is not. Every framework above exists to make that second kind of failure structurally harder to produce than to catch, in a system meant to still be reasoning well a decade from now, long after the specific investigation that taught this project why it mattered has been forgotten by everyone except the architecture itself.
