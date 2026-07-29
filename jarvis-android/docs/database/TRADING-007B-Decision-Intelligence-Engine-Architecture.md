# TRADING-007B — Decision Intelligence Engine
## Architecture & Blueprint (Phase 1 — design only, no code)

Status: **PROPOSED**, pending review. Nothing in this document has been implemented. No Kotlin,
no Room entities, no DAOs, no repositories, no schema changes accompany this phase — per the
architecture brief, this is exclusively the blueprint that TRADING-007B Phase 2 will implement
against.

Baseline this design was audited against: schema **v8** (TRADING-001 through TRADING-007A.2,
108 tables, "Knowledge Layer complete").

---

## 1. Executive Summary

The Knowledge Layer (TRADING-001 → 007A.2) answers "what is true, and how sure are we?" — market
data, signals, evidence, confidence, patterns, regimes, news/sentiment, and economic context are
all stored, scored, and linked. Nothing in that layer decides anything. TRADING-007B is the first
module in the **Decision Layer**: it consumes everything the Knowledge Layer already knows, and
produces a single artifact — a **Recommendation** — that is confidence-scored, risk-assessed,
fully explainable, and tracked to an outcome that feeds back into the same learning tables the
Knowledge Layer already maintains.

The central design finding of this phase (see §16, Self-Review) is that **the Decision Layer
needs far fewer new tables than a naive reading of the brief would suggest**. Three of this
schema's existing polymorphic mechanisms — `EvidenceLinkEntity` (evidence-to-anything linking,
already carrying `role` and `weight`), `ConfidenceScoreEntity` (score-anything, via
`scoredEntityType`), and `DriftMetricEntity`/`CalibrationMetricEntity` (monitor-anything, via
`subjectType`) — already do most of what "Evidence Weighting," "Confidence," and "Decision Drift/
Bias Detection" ask for. Extending three enums with one additive value each buys back what would
otherwise be three or four duplicate tables. The result: **5 new tables**, 3 additive enum
values, 2 additive `TimelineEventType` values, and zero changes to any existing table's shape.

This is not an order execution engine and not a broker integration — it stops at "here is what
JARVIS recommends, and here is exactly why." Acting on a recommendation remains a human (or a
future, separately-scoped execution module's) decision.

## 2. Decision Philosophy

Four principles, each already precedented elsewhere in this schema and carried forward rather
than reinvented:

1. **Store, don't decide twice.** The Decision Engine composes existing scored/weighted
   evidence; it does not re-score evidence, re-compute confidence components, or re-derive
   sentiment. If a number already exists upstream (an `EvidenceRecordEntity.strength`, a
   `SentimentScoreEntity.score`, a `DriftMetricEntity.driftScore`), this module reads it — it
   never recalculates it. This is the same boundary `historical.dna`, `historical.evidence`,
   and `news` all already draw ("nothing here scores, ranks, or recommends" — this module is
   the one place in the schema where that sentence finally becomes false, and only here).
2. **Explainability is structural, not narrative.** A recommendation's "why" is not a single
   free-text field written after the fact — it is the literal graph of `EvidenceLinkEntity` rows
   (role + weight) attached to it, resolvable by any caller without trusting a summary. Free
   text (`RecommendationEntity.reasoningSummary`) exists too, but it is a rendering of that
   graph, not a substitute for it.
3. **Supersede, don't mutate.** Every revision-capable concept in this schema (news corrections,
   economic-event revisions) uses a self-referential "new row points back at the old one" chain
   instead of overwriting history. Recommendations follow the same pattern
   (`revisesRecommendationId`) so a recommendation's evolution is itself an auditable, permanent
   record — which matters more here than anywhere else, since regulators and post-mortems will
   eventually ask "what did the system believe, and when."
4. **Confidence is composed, not asserted.** No recommendation carries a bare confidence number
   with no derivation. Every `ConfidenceScoreEntity` row already requires a `modelId` pointing
   at a versioned, named `ConfidenceModelEntity` methodology — a recommendation's confidence is
   never magic, it is always "methodology X, version Y, applied to this specific evidence set."

## 3. Decision Lifecycle

```
 1. COLLECT      — gather evidence, signals, regime, sentiment, economic events, DNA, drift
 2. VALIDATE     — completeness/staleness checks on collected inputs (see §9)
 3. SCORE        — read existing per-item confidence/strength; no re-scoring
 4. CONTEXTUALIZE— apply current MarketRegimeEntity + relevant EconomicEventEntity windows
 5. HISTORICAL   — pull comparable PatternOccurrenceEntity / EvidenceOutcomeEntity history
 6. WEIGH        — compose a ConfidenceScoreEntity (scoredEntityType=DECISION) from the
                    weighted EvidenceLinkEntity set
 7. RISK         — produce a RecommendationRiskAssessmentEntity
 8. ALTERNATIVES — record rejected alternatives + why-not (RecommendationAlternativeEntity)
 9. RECOMMEND    — persist the RecommendationEntity (state=DRAFT → ACTIVE)
10. EXPLAIN      — the evidence graph + risk assessment + alternatives ARE the explanation;
                    optionally render a natural-language summary onto the record
11. MONITOR      — TradingTimelineEventEntity gets a RECOMMENDATION_ISSUED event; scheduled/
                    triggered checks create DecisionReviewEntity rows
12. RESOLVE      — RecommendationOutcomeEntity records what happened; state → EXECUTED/EXPIRED/
                    WITHDRAWN/SUPERSEDED
13. LEARN        — outcome feeds LearningObservationEntity/LearningInsightEntity (existing,
                    Module 3) and EvidenceSourceEntity.reliabilityWeight (existing, Module 5);
                    calibration measured via CalibrationMetricEntity (existing, TRADING-007A.2)
```

Stages 1–10 are synchronous (one pipeline run produces one `RecommendationEntity`). Stages 11–13
are asynchronous and ongoing for the lifetime of the recommendation — this is why review/outcome/
learning are separate tables rather than columns that would force the initial insert to stay open
for edits (consistent with this schema's "insert-only, append the next fact" convention
throughout).

## 4. Decision Pipeline (detail)

| Stage | Reads from (existing) | Writes to |
|---|---|---|
| Collect | `signals.*`, `historical.evidence.EvidenceRecordEntity`, `historical.dna.*`, `news.*`, `context.economic_event*`, `intelligence.graph.*` | — |
| Validate | (input completeness check against the above — pure logic, no new table) | — |
| Score | `intelligence.confidence.ConfidenceScoreEntity` (existing scores per evidence/pattern/etc.) | — |
| Contextualize | `intelligence.regime.MarketRegimeEntity`, `context.economic_events` (events inside the decision's time horizon) | — |
| Historical | `historical.evidence.PatternOccurrenceEntity`, `intelligence.evidence.EvidenceOutcomeEntity`, `intelligence.graph.CorrelationEntity` | — |
| Weigh | (composition logic) | `intelligence.evidence.EvidenceLinkEntity` (new rows, `linkedEntityType=DECISION`), `intelligence.confidence.ConfidenceScoreEntity` (new row, `scoredEntityType=DECISION`) |
| Risk | `analytics.PortfolioRiskEntity`, `context.drift_metrics`, `historical.dna.VolatilityProfileEntity` | **`decision.RecommendationRiskAssessmentEntity`** (new) |
| Alternatives | (evaluated candidates from Score/Weigh) | **`decision.RecommendationAlternativeEntity`** (new) |
| Recommend | — | **`decision.RecommendationEntity`** (new) |
| Monitor | — | `analytics.TradingTimelineEventEntity` (existing; 2 new event-type values), **`decision.DecisionReviewEntity`** (new) |
| Resolve | — | **`decision.RecommendationOutcomeEntity`** (new) |
| Learn | outcome data | `analytics.LearningObservationEntity`, `analytics.LessonLearnedEntity`, `context.calibration_metrics` (existing; 1 new subject-type value), `intelligence.evidence.EvidenceSourceEntity.reliabilityWeight` (existing, updated) |

## 5. Evidence Weighting Framework

**Philosophy: weight where the evidence already lives, don't build a parallel weighting system.**

- Per-item contribution weight is `EvidenceLinkEntity.weight` (already exists, [0.0, 1.0],
  already used for exactly this purpose elsewhere in the schema) — reused verbatim for
  decision-evidence links, not reinvented.
- Direction of each item's pull is `EvidenceLinkEntity.role` (`SUPPORTS` / `CONTRADICTS` /
  `CONTEXTUALIZES` / `TRIGGERED_BY` — already exists) — this is the literal, structural answer
  to "what evidence supports this / disagrees" from §7. No new supporting/contradicting columns
  are needed on `RecommendationEntity` itself; they are queries (`WHERE linkedEntityType =
  'DECISION' AND linkedEntityRowId = :id AND role = 'SUPPORTS'`), not stored duplicates.
- The composition methodology — how per-item weights and roles become one confidence number —
  is a **named, versioned `ConfidenceModelEntity`** (already exists: `WEIGHTED_SUM` / `BAYESIAN`
  / `RULE_BASED` / `ML_INFERENCE` / `HYBRID`). The Decision Engine does not invent a new
  weighting-config table; it either reuses an existing model definition or registers a new
  `ConfidenceModelEntity` row the same way any other confidence-consuming subsystem would.
- Inputs the framework must be able to weigh, and where each one's weight is expressed:

  | Input | Where it enters as an `EvidenceLinkEntity` |
  |---|---|
  | Technical indicators | via `historical.indicator.IndicatorValueEntity` → promoted to `EvidenceRecordEntity` (existing promotion pattern) |
  | Signals | `linkedEntityType = SIGNAL` already exists as an evidence-link target independent of this module |
  | Historical outcomes | `EvidenceOutcomeEntity` / `PatternOccurrenceEntity.outcome` inform the *initial* weight assigned at link time, not a separate stored field |
  | Evidence records | direct `evidenceId` link, the base case |
  | News & Sentiment | `NewsArticleEntity` promoted to `EvidenceRecordEntity` per the TRADING-007A.1 promotion pattern; `SentimentScoreEntity` informs weight/role at link time |
  | Economic Events | `EconomicEventEntity`/`EconomicEventOutcomeEntity` promoted to `EvidenceRecordEntity` the same way news is |
  | Instrument DNA | `historical.dna.*` profiles inform context weighting (regime-fit, volatility-fit) rather than being linked as discrete evidence items — DNA is a *lens*, not a *fact* |
  | Cross-Asset Intelligence | `intelligence.graph.CorrelationEntity` / `CausalObservationEntity`, linked via `linkedEntityType = CORRELATION` / `CAUSAL_OBSERVATION` (both already exist) |
  | Calibration metrics | inform *model selection/trust*, not linked as decision evidence — see §7 |
  | Drift metrics | same — a high-drift model's outputs get down-weighted before linking, not linked themselves |
  | Human observations | `analytics.LearningObservationEntity` (existing, `observationSource` already distinguishes human vs. system) linked via a new `LinkedEntityType` catch-all path already generalized enough to cover it |
  | Future AI insights | `analytics.LearningInsightEntity` (existing) — same path |

  Net: **zero new evidence-input tables.** Every input the brief lists already has a home; the
  only genuinely new plumbing is one enum value.

## 6. Decision Model

All of the following are modeled as **columns on `RecommendationEntity`** unless noted — none of
these concepts (type, state, time horizon) warrant their own controlled-vocabulary table, because
each is a small, closed, code-owned set that changes at the rate of a schema migration anyway
(unlike, say, `EconomicEventCategoryEntity`, which is genuinely open and ops-managed). Introducing
tables for closed enums would be the "unnecessary complexity" §16 explicitly warns against.

- **Decision Type** — enum: `ENTRY_LONG`, `ENTRY_SHORT`, `EXIT`, `HOLD`, `HEDGE`, `SCALE_IN`,
  `SCALE_OUT`, `WATCH`, `AVOID`.
- **Decision State** — enum: `DRAFT` (pipeline still assembling it) → `ACTIVE` (issued) →
  one of `EXECUTED` / `EXPIRED` / `WITHDRAWN` / `SUPERSEDED` / `REJECTED`. Terminal states are
  final; a changed mind produces a new row via `revisesRecommendationId`, not a state rollback.
- **Decision Confidence** — not a column; it's the `ConfidenceScoreEntity` row this
  recommendation points at (§5). `RecommendationEntity` stores the FK, not a duplicate number.
- **Decision Strength** — a column distinct from confidence: how large/aggressive the
  recommendation is (e.g. suggested position-size multiplier or conviction tier), independent of
  how *sure* the system is. A high-confidence WATCH and a high-confidence ENTRY_LONG can share a
  confidence score while differing entirely in strength.
- **Supporting / Contradicting Evidence** — derived query over `EvidenceLinkEntity`, §5.
- **Alternative Scenarios** — `RecommendationAlternativeEntity` (new, §9).
- **Expected Outcome** — a text + optional target/invalidation-level pair of columns on
  `RecommendationEntity`, mirroring `EvidenceOutcomeEntity.horizonDescription`'s shape.
- **Risk Profile** — `RecommendationRiskAssessmentEntity` FK (new, §9).
- **Time Horizon** — enum column: `INTRADAY`, `SHORT_TERM`, `SWING`, `POSITION`, `LONG_TERM`.
- **Decision Expiration** — `expiresAt: Long?` column; a scheduled sweep (application-layer, not
  this schema's concern) transitions `ACTIVE → EXPIRED` past that timestamp.
- **Decision Review** — `DecisionReviewEntity` (new, §9) — scheduled or drift/calibration-
  triggered re-evaluation checkpoints against a still-active recommendation.
- **Decision Revision** — `revisesRecommendationId: Long?` self-reference on
  `RecommendationEntity`, identical shape to `EconomicEventOutcomeEntity.revisesOutcomeId` and
  `NewsArticleEntity.correctsArticleId`.

## 7. Explainability Framework

Every question the brief requires an answer to, mapped to where that answer actually lives:

| Question | Answered by |
|---|---|
| Why? | `EvidenceLinkEntity` rows with `role = SUPPORTS`, ordered by `weight` desc |
| Why not? | `EvidenceLinkEntity` rows with `role = CONTRADICTS`; plus `RecommendationAlternativeEntity` rows explaining rejected alternatives |
| What evidence supports this? | Same `SUPPORTS` query, resolved through `linkedEntityRowId`/`evidenceId` back to the underlying `EvidenceRecordEntity` |
| What evidence disagrees? | Same `CONTRADICTS` query |
| Which evidence mattered most? | `ORDER BY weight DESC` over the same link set — no separate ranking table |
| How reliable is this conclusion? | The `ConfidenceScoreEntity` this recommendation points at, plus that score's `ConfidenceModelEntity.modelType` (so "reliable how" is itself inspectable — Bayesian vs. rule-based carries different reliability semantics) |
| What assumptions exist? | `RecommendationEntity.assumptionsJson` — the one place this framework stores free-form structured text, because "assumptions" (e.g. "assumes no FOMC surprise," "assumes regime persists") are genuinely not a queryable fact anywhere upstream — they're a property of *this* reasoning pass |
| What changed vs. previous decisions? | Walk the `revisesRecommendationId` chain; diff the two recommendations' evidence-link sets and confidence scores directly (both are already structured data, no diffing logic needs its own table) |

No "explanation" table separate from the evidence graph is proposed. `analytics.
DecisionExplanationEntity` (existing) remains available for a rendered natural-language summary
*if* a caller wants one attached to the lightweight `decision_records` log entry created when a
recommendation is acted on (§16) — but it is not where this module's structural explainability
lives.

## 8. Learning Framework

Every learning concern in the brief already has an existing table it belongs on — this framework
is about **wiring outcomes back into them**, not creating a parallel "decision learning" schema:

| Concern | Existing home | What TRADING-007B adds |
|---|---|---|
| Decision Success / Failure | `RecommendationOutcomeEntity` (new) is the source event | feeds `analytics.LearningObservationEntity` (existing) |
| Confidence Calibration | `context.calibration_metrics` (existing, TRADING-007A.2) | additive `ContextMonitoringSubjectType.RECOMMENDATION_ENGINE` value so the engine's own confidence-vs-outcome calibration is trackable with zero new tables |
| Outcome Learning | `analytics.LearningInsightEntity` / `PatternDiscoveryEntity` (existing) | recommendation outcomes become another `LearningEvidenceLinkEntity`-linked input, same mechanism already used for trade/backtest outcomes |
| Strategy Improvement | `analytics.OptimizationSuggestionEntity` (existing) | unchanged; recommendation-outcome patterns are one more signal it already knows how to ingest |
| Evidence Reliability | `intelligence.evidence.EvidenceSourceEntity.reliabilityWeight` (existing, Module 5) | recommendation outcomes provide a feedback signal that nudges this weight — an *update*, not a new table |
| Decision Drift | `context.drift_metrics` (existing, TRADING-007A.2) | same additive `RECOMMENDATION_ENGINE` subject-type value covers this too (`metricKey` distinguishes drift-type from calibration-type usage) |
| Bias Detection | `context.calibration_metrics` + `RecommendationOutcomeEntity` aggregated by `decisionType`/`timeHorizon` | a query pattern (e.g. "win rate by decision type over time"), not a stored bias table — bias is a property observed *across* many rows, not a fact about any one row |
| Historical Recalibration | `context.calibration_metrics.triggeredRecalibration` (existing field, TRADING-007A.2) | unchanged; a `RECOMMENDATION_ENGINE`-subject row with this flag set is how a recalibration event gets recorded |

## 9. Risk Framework

**New tables in this section: 1 — `RecommendationRiskAssessmentEntity`.**

`analytics.PortfolioRiskEntity` (existing) is portfolio-scoped (VaR, drawdown, concentration
across *all* open positions) and is reused as an *input* — a recommendation's risk assessment
reads current portfolio risk to judge marginal impact, it does not duplicate portfolio-level
computation. What's missing is a **per-recommendation** risk record, since no existing table is
scoped to "the risk of acting on this one specific decision":

- **Risk Categories** (per brief): `MARKET_RISK`, `EVENT_RISK`, `LIQUIDITY_RISK`,
  `VOLATILITY_RISK`, `CORRELATION_RISK`, `UNKNOWN_RISK` — modeled as one row per category per
  assessment (same polymorphic-lite shape as `PerformanceMetricEntity`'s per-metric rows,
  Module 3) rather than six columns on one row, so a new risk category can be added later
  without a schema change to the parent table.
- **Market Risk** — informed by `historical.dna.VolatilityProfileEntity` +
  `intelligence.regime.MarketRegimeEntity`.
- **Event Risk** — informed by `context.economic_events` inside the recommendation's time
  horizon (a HIGH-importance FOMC decision two days into a SWING-horizon trade is exactly what
  this category exists to flag).
- **Liquidity Risk** — informed by `historical.dna.LiquidityProfileEntity`.
- **Volatility Risk** — informed by `historical.dna.VolatilityProfileEntity` +
  `context.drift_metrics` (a volatility *model* that's drifted is itself a risk signal).
- **Correlation Risk** — informed by `intelligence.graph.CorrelationEntity` (existing) —
  concentrated correlated exposure across open positions.
- **Unknown Risk** — a deliberate catch-all score for "confidence the risk model itself is
  complete," not zero-filled; this is the humility category, present precisely because a system
  that never admits what it doesn't know about risk is more dangerous than one that does.

## 10. Entity Catalogue (proposed — 5 new tables)

Package: `com.jarvis.tidb.decision` (new top-level package, matching the flat-per-module shape
of `news` and `context`). No entity below duplicates anything cataloged in §16.

### 10.1 `RecommendationEntity` (table: `decision_recommendations`)

- **Purpose:** The core output of the pipeline — one evidence-driven recommendation.
- **Responsibilities:** Identity, decision type/state/strength/time-horizon, links to its
  composed confidence score, its risk assessment, and (optionally) the legacy `decision_records`
  row created if/when it's acted on.
- **Key fields (illustrative, not final):** `recommendationId` (PK), `uuid`, `instrumentId`,
  `contractId?`, `decisionType`, `state`, `strength` (Double), `timeHorizon`,
  `confidenceScoreId` (logical → `confidence_scores.scoreId`), `riskAssessmentId` (FK),
  `expectedOutcomeDescription`, `expectedTargetLevel?`, `expectedInvalidationLevel?`,
  `assumptionsJson?`, `reasoningSummary?` (rendered text, not authoritative), `expiresAt?`,
  `revisesRecommendationId?` (self-ref), `linkedDecisionRecordId?` (logical →
  `decision_records.rowId`), `generatedBy`, audit metadata.
- **Relationships:** 1—* `EvidenceLinkEntity` (via `linkedEntityType = DECISION`), 1—1
  `ConfidenceScoreEntity` (logical), 1—1 `RecommendationRiskAssessmentEntity`, 1—*
  `RecommendationAlternativeEntity`, 1—* `DecisionReviewEntity`, 1—*
  `RecommendationOutcomeEntity`, 0—1 self (`revisesRecommendationId`).
- **Lifecycle:** Insert-only at creation; state transitions are updates to `state`/`resolvedAt`-
  style fields, never to the reasoning fields — a changed reasoning basis is a new row.
- **Indexes:** `uuid` (unique), `instrumentId`, `(instrumentId, state)`, `state`,
  `decisionType`, `expiresAt`, `revisesRecommendationId`, `generatedAt`.
- **Retention:** Never hard-deleted (regulatory/audit value); soft-delete only, mirroring
  `EconomicEventEntity`/`NewsArticleEntity`.
- **Growth:** One row per pipeline run that reaches `RECOMMEND`. Expected to be the highest-
  volume new table in this module if the pipeline runs frequently per instrument; partition/
  archive strategy deferred to §12 (Scalability).

### 10.2 `RecommendationRiskAssessmentEntity` (table: `recommendation_risk_assessments`)

- **Purpose:** Per-recommendation, per-category risk score (§9).
- **Responsibilities:** One row per `(recommendationId, riskCategory)` pair; pure storage of a
  score + narrative, no risk computation logic in the schema layer.
- **Key fields:** `assessmentId` (PK), `uuid`, `recommendationId` (FK), `riskCategory` (enum:
  the six from §9), `score` (Double, normalized), `narrative?`, `assessedAt`.
- **Relationships:** *—1 `RecommendationEntity`.
- **Lifecycle:** Insert-only; a re-assessment (e.g. after a review, §10.5) is a new row set,
  not an update — preserves the risk picture at each point in time.
- **Indexes:** `uuid` (unique), `(recommendationId, riskCategory)`, `assessedAt`.
- **Retention:** Same as parent recommendation.
- **Growth:** ~6 rows per assessment pass per recommendation; bounded and predictable.

### 10.3 `RecommendationAlternativeEntity` (table: `recommendation_alternatives`)

- **Purpose:** "Why not?" — records scenarios the pipeline evaluated and rejected.
- **Responsibilities:** One row per rejected alternative considered during the same pipeline run
  that produced the winning `RecommendationEntity`.
- **Key fields:** `alternativeId` (PK), `uuid`, `recommendationId` (FK, the *winning*
  recommendation this alternative was evaluated alongside), `alternativeDecisionType`,
  `rejectionReason`, `relativeConfidenceScoreId?` (logical, if the alternative was scored too),
  `consideredAt`.
- **Relationships:** *—1 `RecommendationEntity`.
- **Lifecycle:** Insert-only, permanent.
- **Indexes:** `uuid` (unique), `recommendationId`.
- **Retention:** Same as parent recommendation.
- **Growth:** Small, bounded per recommendation (typically 1–4 alternatives).

### 10.4 `RecommendationOutcomeEntity` (table: `recommendation_outcomes`)

- **Purpose:** What actually happened, tracked against `expectedOutcomeDescription` (§10.1).
- **Responsibilities:** Feeds the Learning Framework (§8); the source-of-truth for
  success/failure classification.
- **Key fields:** `outcomeId` (PK), `uuid`, `recommendationId` (FK), `verdict` (reuse
  `OutcomeVerdict` — already exists, `intelligence.evidence`, no new enum needed: `PENDING`/
  `CONFIRMED`/`PARTIALLY_CONFIRMED`/`INVALIDATED`/`INCONCLUSIVE`/`EXPIRED`), `actualMovePercent?`,
  `actualOutcomeDescription?`, `evaluatedAt`, `notes?`.
- **Relationships:** *—1 `RecommendationEntity`.
- **Lifecycle:** Insert-only; a recommendation can accumulate multiple outcome checkpoints over
  its horizon (interim + final), same "append the next fact" convention as
  `EvidenceOutcomeEntity`.
- **Indexes:** `uuid` (unique), `recommendationId`, `(recommendationId, evaluatedAt)`, `verdict`.
- **Retention:** Same as parent recommendation.
- **Growth:** 1–3 rows per recommendation typically (interim checks + final).

### 10.5 `DecisionReviewEntity` (table: `decision_reviews`)

- **Purpose:** "Decision Review" — scheduled or triggered re-evaluation checkpoints.
- **Responsibilities:** Records *that* a still-active recommendation was reviewed, what
  triggered the review (schedule vs. a drift/calibration alert vs. a new contradicting evidence
  item), and what the review concluded (no change / revise / withdraw).
- **Key fields:** `reviewId` (PK), `uuid`, `recommendationId` (FK), `triggerType` (enum:
  `SCHEDULED`, `DRIFT_ALERT`, `CALIBRATION_ALERT`, `NEW_CONTRADICTING_EVIDENCE`, `MANUAL`),
  `triggerReferenceType?`/`triggerReferenceRowId?` (logical, e.g. points at the
  `drift_metrics` row that triggered it), `conclusion` (enum: `NO_CHANGE`, `REVISED`,
  `WITHDRAWN`), `resultingRecommendationId?` (logical, if `REVISED` — points at the new row
  linked via that row's own `revisesRecommendationId`), `reviewedAt`, `reviewedBy`.
- **Relationships:** *—1 `RecommendationEntity`; logical reference into whichever table
  `triggerReferenceType` names (same polymorphic-lite convention as `DriftMetricEntity
  .subjectRowId`).
- **Lifecycle:** Insert-only, permanent.
- **Indexes:** `uuid` (unique), `recommendationId`, `triggerType`, `reviewedAt`.
- **Retention:** Same as parent recommendation.
- **Growth:** Variable — scheduled reviews are periodic per active recommendation; alert-
  triggered reviews are event-driven and expected to be the minority case.

### 10.6 Additive changes to existing entities (no new tables)

- `intelligence.evidence.entity.LinkedEntityType` — add `DECISION` (String-persisted enum value,
  no `ALTER TABLE`).
- `intelligence.confidence.entity.ScoredEntityType` — add `DECISION`.
- `context.entity.ContextMonitoringSubjectType` — add `RECOMMENDATION_ENGINE`.
- `analytics.entity.TimelineEventType` — add `RECOMMENDATION_ISSUED`, `RECOMMENDATION_REVIEWED`.

## 11. Integration Architecture

| Module | Direction | Nature of integration |
|---|---|---|
| Market Foundation | read | Instrument/contract identity, candles for context checks |
| Historical Market Data | read | Indicator values, DNA profiles, quality/gap awareness before trusting a data window |
| Signal Intelligence | read | `SignalEntity` as a first-class evidence input via existing `LinkedEntityType.SIGNAL` |
| Evidence Platform / Evidence Engine | read + write | Primary evidence source; write new `EvidenceLinkEntity` rows targeting `DECISION` |
| Trading Analytics | read + write | `PortfolioRiskEntity` read; `TradingTimelineEventEntity`, `LearningObservationEntity`, `LessonLearnedEntity` written to on outcome |
| News & Sentiment | read | Promoted-to-evidence articles + sentiment scores, as one evidence class among several |
| Market Context (economic events) | read | Event-risk input (§9), event-window evidence promotion |
| Calibration & Drift | read + write | Read to down-weight drifted models pre-decision; write `RECOMMENDATION_ENGINE`-subject rows post-outcome |
| Future Android Assistant | read (via repository) | Consumes `MarketContextIntelligenceRepository`-style facade (`DecisionRepository`, Phase 2) to surface recommendations + explanations in-app; no direct DB access |
| Future AI Providers | write (as a source) | Enter the system as `LearningInsightEntity`/`LearningObservationEntity` rows (existing `observationSource`/`generatedBy` fields already distinguish provider identity) — the Decision Engine doesn't need provider-specific plumbing, it already consumes this generically |

## 12. Scalability Strategy

- `RecommendationEntity` is the highest-growth new table. If the pipeline runs on a schedule per
  instrument (rather than purely event-triggered), row count scales with
  `instruments × runs/day`. Recommend: pipeline runs event-triggered (new signal, new high-
  importance economic event, drift alert) rather than fixed-interval polling, to keep growth
  proportional to genuinely new information rather than wall-clock time.
- `EvidenceLinkEntity` growth from this module is proportional to `recommendations ×
  avg_evidence_items_per_decision` — already an existing table under existing growth pressure
  from other modules; no new scaling concern introduced, just a new contributor to it.
- On-device retention: recommendations, their risk assessments, alternatives, and outcomes are
  natural candidates for the same tiered on-device/server-side retention policy already flagged
  as deferred future work for news articles and economic events (§6 of both TRADING-007A docs) —
  this module should join that future retention pass rather than invent its own.
- No table in this catalogue requires a wide/denormalized shape to stay performant — every read
  path in §7/§8 is index-backed (see §10's index lists) and joins at most two hops from
  `RecommendationEntity`.

## 13. Security & Governance

- Every table carries `AuditMetadata` (createdBy/updatedBy/version) per existing convention —
  who/what generated a recommendation is always attributable, which matters more here than
  anywhere else in the schema given the eventual regulatory surface of "the system told someone
  to trade."
- No PII is introduced by this module. `generatedBy`/`reviewedBy` are system/service identifiers,
  not end-user identity — consistent with every existing `*By` column in this schema.
- `RecommendationEntity` is never hard-deleted (§10.1) — this is a governance requirement, not
  just a technical convenience: a recommendation that turned out wrong must remain inspectable
  forever, not quietly pruned.
- The explicit `assumptionsJson` field (§7) exists partly for governance reasons — an auditor
  asking "did the system know it was assuming no FOMC surprise" needs a direct answer, not an
  inference from context.

## 14. Risks & Mitigations

| Risk | Mitigation |
|---|---|
| Evidence-linking volume from this module overwhelms `EvidenceLinkEntity` query performance for *other* consumers | Composite index already exists at `(linkedEntityType, linkedEntityRowId)` pattern conventionally used elsewhere; confirm at implementation time it's present, add if not — a schema note for Phase 2, not a new table |
| `RecommendationEntity` becomes a de-facto second "decisions" concept alongside `decision_records`, confusing future maintainers | Addressed head-on in §16 — the reconciliation is documented explicitly rather than left implicit, and `linkedDecisionRecordId` gives a queryable bridge between the two |
| Confidence composition methodology (`ConfidenceModelEntity`) becomes a black box if `ML_INFERENCE` models aren't independently auditable | `assumptionsJson` + the underlying evidence-link graph remain inspectable regardless of model type — explainability doesn't depend on the model being transparent, only on its inputs and their weights being stored |
| Bias goes undetected because §8's "query pattern, not a table" approach requires someone to actually run the query | Recommend a scheduled Learning Framework job (application-layer, Phase 2/3 scope) that periodically materializes bias/calibration checks into `calibration_metrics` rows automatically, rather than relying on ad-hoc analysis |
| Bootstrapping cold-start: no recommendations exist yet, so no `ConfidenceModelEntity` has a track record | Ship with `RULE_BASED` as the initial `ConfidenceModelEntity` for `DECISION`-scored confidence; graduate to `WEIGHTED_SUM`/`ML_INFERENCE` once outcome history (§8) exists to calibrate against |

## 15. Implementation Roadmap (non-binding, for Phase 2 planning)

1. Additive enum values (§10.6) — smallest possible first slice, unblocks nothing on its own
   but is zero-risk and can land independently.
2. `RecommendationEntity` + `RecommendationRiskAssessmentEntity` — the minimum pair needed to
   produce and persist a recommendation with a risk profile.
3. `RecommendationAlternativeEntity`, `RecommendationOutcomeEntity`, `DecisionReviewEntity` —
   round out explainability and the feedback loop.
4. `MIGRATION_8_9`, DAOs, repository, DI wiring — following the exact pattern established by
   TRADING-007A.1/007A.2 (this document intentionally does not produce those artifacts; that's
   Phase 2).
5. Application-layer pipeline orchestration (the 13 stages in §3/§4) — outside this database
   module's scope entirely; consumes the repository this module will expose.

## 16. Self-Review

Explicit answers to the four review questions posed in the brief:

**Existing entities reused instead of duplicated:**
`EvidenceLinkEntity` (evidence weighting/role — see §5), `ConfidenceScoreEntity`/
`ConfidenceModelEntity` (confidence — see §5, §6), `MarketRegimeEntity` (context),
`PortfolioRiskEntity` (portfolio-level risk input), `CorrelationEntity`/`CausalObservationEntity`
(cross-asset), `EvidenceSourceEntity.reliabilityWeight` (evidence reliability learning),
`LearningObservationEntity`/`LearningInsightEntity`/`OptimizationSuggestionEntity`/
`LessonLearnedEntity` (learning framework), `TradingTimelineEventEntity` (event log),
`calibration_metrics`/`drift_metrics` (calibration/drift — extended, not duplicated), and
`OutcomeVerdict` (reused directly on `RecommendationOutcomeEntity` rather than a new verdict
enum). That's 12 existing entities/fields this design leans on rather than re-creating.

**Whether any proposed tables can be merged into existing structures:** Yes, one was found and
folded in during this review: an initially-planned "per-decision evidence weight" table turned
out to be a complete duplicate of `EvidenceLinkEntity.weight`/`.role`, which already exist for
exactly this purpose — it was removed from the catalogue rather than shipped. No other proposed
table (§10.1–§10.5) overlaps an existing one closely enough to merge; each stores a fact
(risk-per-category, a rejected alternative, an outcome checkpoint, a review event) that no
current table has a slot for.

**Whether polymorphic entities should be used:** Yes, twice, both reusing an *existing* pattern
rather than inventing a new one — `LinkedEntityType`/`ScoredEntityType` gain a `DECISION` value
(§10.6) instead of `RecommendationEntity` growing its own bespoke evidence/confidence link
tables, and `DecisionReviewEntity.triggerReferenceType/RowId` follows the same logical-reference
shape as `DriftMetricEntity.subjectType/subjectRowId` rather than a `ForeignKey` per possible
trigger source. No *new* polymorphic pattern was introduced — every polymorphic reference in
this design reuses the schema's one established shape (typed-enum + logical-row-id, no FK).

**Unnecessary complexity avoided:** Decision Type/State/Time-Horizon stay as enums, not tables
(§6) — they're closed, code-owned vocabularies, unlike the genuinely open, ops-managed
`EconomicEventCategoryEntity` precedent that *did* warrant a table. "Which evidence mattered
most" and "supporting vs. contradicting" are both derived queries, not stored/duplicated
columns (§5, §7). Bias detection is a query pattern over existing outcome data, not a new
"bias" table (§8). Net effect: the module that does the most conceptually — turning the entire
Knowledge Layer into one number and an explanation — adds the fewest new tables of any module
in this schema's history (5, versus 7 each for TRADING-007A.1 and 007A.2, and considerably more
for TRADING-005/006).

**One open reconciliation, flagged rather than silently resolved:** `analytics.
DecisionRecordEntity`/`DecisionExplanationEntity` (existing, Module 3 §5) are a much thinner
"decision log" concept (a summary string + outcome enum, no evidence graph, no confidence score,
no risk profile) than this module's `RecommendationEntity`. They are **not** repurposed,
renamed, or removed — per this project's standing "no redesigning existing architecture, no
removing existing functionality" invariant, and because they may already serve callers this
review has no visibility into (manual/user-entered decisions, or decisions from a source that
predates this engine). Instead, `RecommendationEntity.linkedDecisionRecordId` is a logical
bridge: when a recommendation is acted on, a `decision_records` row can still be created (or
already exist) and referenced, so both concepts coexist without collision. This mirrors exactly
how `NewsRepository`'s promotion-to-evidence boundary is documented, and should be validated
with a human reviewer before Phase 2 implementation, since it's the one place this design chose
coexistence over consolidation.
