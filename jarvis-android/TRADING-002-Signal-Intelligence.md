# TRADING-002 — Signal Intelligence Engine

**Module:** 2 of N — Trading Intelligence Database (TIDB), JARVIS AI Operating System
**Status:** Implemented
**Schema version:** 1
**Depends on:** Module 1 (Core Market Foundation) — via repository interfaces only
**Depended on by:** Strategy Engine, Backtesting, AI Learning, Performance Analytics (future modules)

---

## 1. Architecture Overview

This module is the intelligence layer that records **what was predicted, why it was predicted,
and how it performed**. It is explicitly not a strategy engine and does not decide when to
generate a signal — it only stores, tracks, explains, and evaluates signals that some other
(future) module produces.

```
Strategy Engine (future)  ─────generates──────▶  SignalRepository.createSignal()
                                                          │
Future AI / Backtesting  ◀────Flow<T> reads────  Repositories (this module)
                                                          │
                                                          ▼
                                                        DAOs
                                                          │
                                                          ▼
                                          Room / SQLite (jarvis_tidb_signals.db)
```

Module 2 ships as its **own Room database** (`SignalDatabase`, `jarvis_tidb_signals.db`),
separate from Module 1's `TidbDatabase` (`jarvis_tidb.db`). This is deliberate:

- SQLite cannot enforce foreign keys across two separate `.db` files, so a shared database
  would not actually buy referential integrity between the modules anyway.
- The implementation prompt requires Module 2 to "consume Module 1 only through repository
  interfaces" and "never access Module 1 tables directly." A physically separate database makes
  that boundary impossible to accidentally violate — there is no table to `JOIN` against even by
  mistake.
- `instrumentId` on `SignalEntity` is a **logical** foreign key into Module 1's
  `InstrumentEntity`. Referential validity (does this instrument exist?) is the responsibility
  of whatever future module generates signals, checked through Module 1's
  `InstrumentRepository.observeById()`/equivalent before calling
  `SignalRepository.createSignal()` — not enforced by SQLite.

---

## 2. Entity-Relationship Diagram

```
                    ┌───────────────────┐
                    │      Signal        │
                    │  (signals)          │
                    │  PK signalId        │
                    │  logical FK          │
                    │   -> Module1         │
                    │      .Instrument     │
                    └─────────┬─────────┘
                              │ 1
        ┌─────────────┬───────┼───────┬─────────────┬─────────────┐
        │ N            │ 1      │ N      │ N            │ N            │
┌───────▼──────┐ ┌────▼─────┐ ┌▼───────────────┐ ┌▼──────────┐ ┌▼──────────┐
│ SignalReason  │ │ Signal    │ │ SignalLifecycle │ │ SignalTag  │ │ SignalNote │
│ (signal_      │ │ Snapshot  │ │ (signal_        │ │ (signal_   │ │ (signal_   │
│  reasons)     │ │ (signal_  │ │  lifecycle_     │ │  tags)     │ │  notes)    │
│ FK signalId   │ │  snapshots│ │  events)         │ │ FK signalId│ │ FK signalId│
│               │ │ )         │ │ FK signalId      │ │            │ │            │
│               │ │ FK signalId,│ │                 │ │            │ │            │
│               │ │ unique    │ │                  │ │            │ │            │
└───────────────┘ └───────────┘ └──────────────────┘ └────────────┘ └────────────┘
```

All five child tables cascade-delete when their parent `Signal` row is deleted (Room
`ForeignKey.CASCADE`). `SignalSnapshot` additionally carries a **unique** index on `signalId` —
it is a strict 1:1 relationship, not 1:N, since a signal's generation-time market conditions are
captured exactly once.

---

## 3. Signal Lifecycle

`SignalEntity.status` holds one of four **coarse** states used for filtering/sorting:

```
ACTIVE  ──▶  EXECUTED  ──▶  EXPIRED
   │                          ▲
   └──────────▶ CANCELLED ────┘
```

`signal_lifecycle_events` is a separate, **append-only, unbounded-granularity** audit trail.
Its `previousStatus`/`newStatus` columns are free text rather than the four-value enum, because
the real operational lifecycle is richer than the coarse status field:

```
ACTIVE → EXECUTED → TARGET1 HIT → TARGET2 HIT → TARGET3 HIT → CLOSED
                  ↘ STOP LOSS HIT → CLOSED
ACTIVE → EXPIRED
ACTIVE → CANCELLED
```

`SignalRepository.transitionStatus()` is the single sanctioned write path for status changes:
it always writes a lifecycle row, and only overwrites the coarse `SignalEntity.status` column
when the new value maps onto one of the four canonical states — so a milestone like
`"TARGET1 HIT"` is captured in the audit trail without prematurely marking the signal `EXECUTED`
or `EXPIRED`. Fine-grained milestones that don't map onto the coarse enum can also be recorded
directly via `SignalLifecycleRepository.recordEvent()` without touching `SignalEntity` at all.

---

## 4. Repository Design

Six repositories, one per entity, each with an interface + implementation split (Clean
Architecture data layer):

| Repository | Backing DAO | Notable behavior |
|---|---|---|
| `SignalRepository` | `SignalDao` + `SignalLifecycleDao` | `createSignal()` seeds an initial lifecycle row; `transitionStatus()` keeps status + audit trail in sync; `softDeleteSignal()` never physically deletes |
| `SignalReasonRepository` | `SignalReasonDao` | Batch insert (`addReasons`) for the common case of writing all of a signal's reasons at once |
| `SignalSnapshotRepository` | `SignalSnapshotDao` | **No update method anywhere in the stack** — snapshots are immutable by construction |
| `SignalLifecycleRepository` | `SignalLifecycleDao` | Append-only; no update/delete exposed |
| `SignalTagRepository` | `SignalTagDao` | Tag insert uses `OnConflictStrategy.IGNORE` so re-adding an existing tag is a no-op, not an error |
| `SignalNoteRepository` | `SignalNoteDao` | Plain CRUD (minus update — notes are corrected by adding a new note, preserving history) |

Every repository exposes reads as Kotlin `Flow`, writes as `suspend fun`. No DAO is ever
referenced outside the `com.jarvis.tidb.signals.repository` package — callers (including future
modules) depend only on the interfaces.

`SignalModule` (manual DI, mirroring Module 1's `TidbModule`) wires DAOs into repositories via
lazily-initialized singletons, so it drops in cleanly if/when the host app adopts Hilt/Koin.

---

## 5. Query Strategy

`SignalDao` implements every query family called out in the implementation prompt:

- **Active signals** — `observeActiveSignals()`
- **By instrument** — `observeByInstrument()`, `observeActiveByInstrument()`
- **By timeframe** — `observeByTimeframe()`
- **By confidence** — `observeByMinConfidence()` (threshold, sorted descending)
- **By status** — `observeByStatus()`
- **Between dates** — `observeBetweenDates()` (`generatedAt BETWEEN start AND end`)
- **By tag** — `observeByTag()` (`INNER JOIN signal_tags`)
- **Latest signal** — `observeLatest()`, `observeLatestForInstrument()`
- **Search by UUID** — `findByUuid()` (one-shot `suspend`, for exact lookups/imports), plus
  `observeByUuid()` for reactive screens

Two hydration strategies are provided deliberately:

- `SignalWithReasonsAndTags` — used by list/dashboard screens; pulls only the two child tables a
  signal card typically needs.
- `SignalWithDetails` — used by detail screens and future AI/backtesting consumers; pulls all
  five child tables (reasons, snapshot, lifecycle, tags, notes) in one `@Transaction` query.

Keeping these separate avoids the common Room pitfall of a "get everything" query being reused
for a scrolling list and silently multiplying I/O per row.

---

## 6. Index Strategy

| Table | Indexes | Why |
|---|---|---|
| `signals` | `instrumentId`, `generatedAt`, `status`, `confidenceScore`, `timeframe`, `signalType`, unique `uuid`, composite `(instrumentId, timeframe, status, generatedAt)` | Every prompt-required query family gets its own covering or supporting index; the composite index targets the single most common real query — "active signals for instrument X on timeframe Y, newest first" |
| `signal_reasons` | `signalId`, `category`, unique `uuid` | Fast child lookups by parent; category filtering for future analytics ("which indicators drive our best signals?") |
| `signal_snapshots` | unique `signalId`, unique `uuid` | The unique index on `signalId` is also what enforces the 1:1 cardinality at the database level |
| `signal_lifecycle_events` | `signalId`, `changedAt`, unique `uuid` | Chronological audit-trail scans per signal |
| `signal_tags` | `signalId`, `tag`, unique `(signalId, tag)` | Supports both "tags for this signal" and "signals with this tag" (via `SignalDao.observeByTag`'s join); the composite unique index prevents duplicate tag rows |
| `signal_notes` | `signalId`, `createdAt` | Chronological notes per signal |

---

## 7. Future AI Integration

This module is designed to be the training-data source for AI Learning without any schema
changes:

- `SignalSnapshot` gives a frozen, replayable feature vector (OHLCV + indicators) per signal.
- `SignalReason.evidenceJson` preserves arbitrary structured evidence per contributing factor,
  so a future model can be trained on which reason categories/weights actually correlated with
  good outcomes.
- `SignalNote` already accepts non-human authors (`author` is free text) so an AI process can
  attach its own post-hoc commentary without a schema change.
- `SignalLifecycle`'s free-text status trail is rich enough to derive outcome labels
  (e.g. "hit target1 before stop" vs "stopped out") for supervised learning.

## 8. Future Backtesting Integration

`SignalSnapshot` + `SignalLifecycle` together let a future Backtesting module replay history
deterministically: snapshot gives the exact conditions at generation time, lifecycle gives the
exact sequence and timing of what happened afterward. Because both are append-only/immutable,
backtests run against them will always reproduce the same result — there's no risk of a later
mutation quietly changing a historical backtest's outcome.

## 9. Future Performance Analytics Integration

`observeBetweenDates()`, `observeByMinConfidence()`, and the tag/category-based queries give a
future Performance Analytics module the raw slicing it needs (by time window, by confidence
band, by tag, by reason category) without adding new tables — analytics can be built as pure
read-side aggregation over this module's existing repositories.

---

## 10. Explicitly Out of Scope (per implementation prompt)

Not implemented in this module: Strategy Engine, Trade Execution, Backtesting, Performance
Analytics, Instrument DNA, AI Learning. This module only records and manages trading signals.
