# TRADING-003 — Trading Analytics & Learning

**Module:** 3 of N — Trading Intelligence Database (TIDB), JARVIS AI Operating System
**Status:** Implemented
**Schema version:** 1
**Depends on:** Module 1 (Core Market Foundation) and Module 2 (Signal Intelligence Engine), consumed only through their repository interfaces
**Depended on by:** future Strategy Optimization, AI Inference, and Reporting modules

---

## 1. Architecture Overview

Module 3 turns JARVIS from a signal generator into an evidence-driven trading intelligence
platform. It is a third, independent Room database — `AnalyticsDatabase` — sitting alongside
Module 1's `TidbDatabase` and Module 2's `SignalDatabase`:

```
UI / ViewModels / future AI inference layer
            │
            ▼
   Repositories (interface -> impl, Flow<T>)
   TradeRepository | BacktestRepository | PerformanceRepository
   LearningRepository | TimelineRepository | PortfolioRepository
            │
            ▼
          DAOs
            │
            ▼
  AnalyticsDatabase (jarvis_tidb_analytics.db)


  TradeRepositoryImpl also holds references to:
    SignalRepository      (Module 2, interface only)
    InstrumentRepository  (Module 1, interface only)
  used solely to validate signalId / instrumentId before a trade is recorded.
```

Three independent Room/SQLite databases is intentional, not incidental — it's the same choice
Module 2 made against Module 1. SQLite cannot enforce a `@ForeignKey` across separate `.db`
files, so every cross-module reference in this module (`signalId`, `instrumentId`) is a
**logical foreign key**: a plain `Long`/`String` column, validated by calling the owning
module's repository at write time, never joined at the SQL level. This keeps Module 1, 2, and 3
independently deployable, testable, and — if JARVIS ever needs it — independently
scalable/shardable.

## 2. ER Diagram (textual)

```
Module 2: Signal ──(logical FK: signalId)──┐
Module 1: Instrument ──(logical FK: instrumentId)──┤
                                                     ▼
                                                  Trade ──┬── TradeExecution (1:N, append-only)
                                                           ├── TradeExit (1:N)
                                                           ├── TradeFees (1:N)
                                                           └── TradeJournal (1:N)

Backtest ── BacktestConfiguration (1:N, versioned)
   │
   └── BacktestRun ──┬── BacktestTrade (1:N)
                       └── BacktestResult (1:1)

PerformanceSnapshot ── PerformanceMetric (1:N, generic name/value)
StrategyPerformance      (1 row per strategyId, upserted)
InstrumentPerformance    (1 row per instrumentId, upserted)
MonthlyPerformance       (1 row per yearMonth, upserted)

LearningObservation ──(evidence for)──> LearningInsight ──(basis for)──> OptimizationSuggestion
PatternDiscovery       (independent, recurring-pattern table)
FailureAnalysis         (references Trade and/or BacktestRun)

TradingTimelineEvent    (append-only, referenced by Trade/Signal/Instrument/BacktestRun/Portfolio)
DecisionRecord ── DecisionExplanation (1:N)
LessonLearned           (references LearningInsight and/or FailureAnalysis)

Portfolio ──┬── PortfolioPosition (1:N)
             ├── PortfolioAllocation (1:N, time series)
             ├── PortfolioRisk (1:N, time series, append-only)
             └── CapitalMovement (1:N, append-only ledger)
```

## 3. Trade Lifecycle Flow

1. **Signal Generated** (Module 2) → a `TradingTimelineEventEntity(eventType = SIGNAL_GENERATED)`
   is appended, and optionally a `DecisionRecordEntity` captures whether JARVIS/the user chose
   to act on it.
2. **Trade opened** → `TradeRepository.recordTrade()` validates `signalId` against
   `SignalRepository` and `instrumentId` against `InstrumentRepository`, then inserts a
   `TradeEntity` with `status = PENDING` or `OPEN`.
3. **Fills happen** → each fill (entry, add-on, partial exit, stop, target, manual, cancellation)
   is appended as a `TradeExecutionEntity`. This table is insert-only.
4. **Exits happen** → each closing slice is appended as a `TradeExitEntity` with its own
   realized P&L, distinct from the execution log so P&L can be queried without re-deriving it
   from raw fills.
5. **Fees accrue** → every charge (brokerage, STT, GST, stamp duty, SEBI charge, slippage, other)
   is appended as a `TradeFeesEntity`, so cost drag can be analyzed per fee type later.
6. **Trade closes** → `TradeEntity` is updated (`status = CLOSED`, `closeReason`, `netPnl`,
   `holdingTimeMillis`, `executionQuality`, ...). A `TradeExitEntity`/`TimelineEvent` pair
   marks the moment.
7. **Journal** → at any point, humans or the system can append a `TradeJournalEntity` note
   (soft-deletable, everything else in the trade lifecycle is not).
8. **Learning** (external process) → reads the closed trade via `TradeRepository.getTradeFullHistory()`
   and writes `LearningObservationEntity` / `FailureAnalysisEntity` rows back through
   `LearningRepository`.
9. **Timeline** → every step above also appends a `TradingTimelineEventEntity`, so the full
   story of the trade can always be reconstructed independent of the typed tables.

## 4. Backtesting Model

Per the implementation prompt, **no simulation engine is implemented** — only the data model a
future engine will read from and write to:

- `BacktestEntity` — the reusable definition: name, strategy, instruments, time period.
- `BacktestConfigurationEntity` — a **versioned** parameter set for that definition (JSON
  `parametersJson` blob, kept schema-agnostic so the strategy engine can evolve its own
  parameter shape without a Room migration here). A backtest can be re-run against a new
  configuration version without losing the history of prior parameter sets.
- `BacktestRunEntity` — one execution of a definition against a configuration version, with
  `status` (`QUEUED` → `RUNNING` → `COMPLETED`/`FAILED`/`CANCELLED`).
- `BacktestTradeEntity` — synthetic trades the run produced. Deliberately **not** the same
  table as `TradeEntity`, so live and simulated trades can never be accidentally mixed into
  one aggregate performance number.
- `BacktestResultEntity` — one summary row per run: net profit, win rate, drawdown, Sharpe,
  Sortino, profit factor, expectancy, consecutive win/loss streaks, CAGR.

## 5. Performance Model

`PerformanceSnapshotEntity` + `PerformanceMetricEntity` form a generic, extensible pair — a
snapshot is a point-in-time rollup for a `scope`/`scopeKey` (overall, one strategy, one
instrument, one month, or a custom slice), and metrics are free-form name/value rows so a new
metric can be added without a schema migration.

`StrategyPerformanceEntity`, `InstrumentPerformanceEntity`, and `MonthlyPerformanceEntity` are
**denormalized, upserted** rollups (one row per strategy / instrument / month) rather than
views computed on every read, because dashboards read these far more often than trades change.
They're expected to be recomputed by a (future) aggregation job that reads `TradeRepository`
and calls `PerformanceRepository.upsert*()`.

## 6. Learning Model

This module **stores** AI findings; it does not generate them — no inference code lives here.

- `LearningObservationEntity` — one atomic, evidence-level fact ("Trade #482 lost 1.4R after a
  stop placed inside the prior day's range").
- `LearningInsightEntity` — a synthesized conclusion from one or more observations
  (`supportingObservationRowIdsCsv` links back to the evidence).
- `OptimizationSuggestionEntity` — a concrete, actionable suggestion with a mutable `status`
  (`PROPOSED` → `UNDER_REVIEW` → `ACCEPTED`/`REJECTED` → `APPLIED`/`SUPERSEDED`) — the one
  learning entity a workflow actually progresses through states.
- `PatternDiscoveryEntity` — a recurring pattern tracked by a stable `patternKey`, with
  `occurrenceCount`/`firstObservedAt`/`lastObservedAt` updated as more evidence accumulates.
- `FailureAnalysisEntity` — a structured post-mortem (`FailureCategory`) tied to a specific
  trade or backtest run.

## 7. Timeline Model

`TradingTimelineEventEntity` is the single permanent, append-only ledger every module writes
to — "nothing should ever be permanently lost." It's kept as a wide, generic envelope
(`eventType`, `severity`, `title`, `details`, a handful of optional `relatedXRowId` columns,
plus a `payloadJson` escape hatch) specifically so new event-producing modules never require a
schema change here.

`DecisionRecordEntity` captures a specific decision point and its `outcome`;
`DecisionExplanationEntity` is a 1:N natural-language explanation attached to it (1:N rather
than 1:1 so a rule-based explanation and a later AI-generated one can coexist).
`LessonLearnedEntity` is the executive-summary layer sitting above raw `LearningInsightEntity`
rows — durable, distilled takeaways meant to be read by a human or a future planning process,
not re-derived on every query.

## 8. Portfolio Model

`PortfolioEntity` is the top-level book (JARVIS can run more than one — e.g. paper vs. live).
`PortfolioPositionEntity` holds current per-instrument holdings (`status = OPEN`/`CLOSED`).
`PortfolioAllocationEntity` is a time series of target-vs-actual weight per `scopeKey`
(instrument, strategy, or asset class), with `driftPercent` precomputed for fast dashboard
reads. `PortfolioRiskEntity` is an append-only point-in-time risk snapshot (exposure,
diversification, concentration, VaR, drawdown-to-date). `CapitalMovementEntity` is the
append-only ledger backing `PortfolioEntity.cashBalance` — every deposit, withdrawal, realized
P&L sweep, fee settlement, dividend, interest, and manual adjustment is a row here, so the cash
balance is always independently reconstructable and auditable.

## 9. Repository Design

Six repositories, one per section family (Trade, Backtest, Performance, Learning, Timeline,
Portfolio) rather than one per entity — matching the natural aggregate boundaries (e.g. a
trade's executions/exits/fees/journal are meaningless without their parent trade, so they're
exposed through `TradeRepository`, not five separate top-level repositories). Every repository
is `interface` + `*Impl`, constructed with only DAOs (and, for `TradeRepositoryImpl`, Module
1/2 repository interfaces) — never a direct `AnalyticsDatabase` reference — so call sites can
never bypass validation logic. All reads are Kotlin `Flow`; all writes are `suspend fun`.

`TradeRepositoryImpl` is the only cross-module-aware implementation in this file set: it holds
`SignalRepository` (Module 2) and `InstrumentRepository` (Module 1) references purely to
`require()` that `signalId`/`instrumentId` exist before a trade is persisted. No other
repository in this module reaches outside `AnalyticsDatabase`.

## 10. Index Strategy

Every table indexes its `uuid` (unique) for global lookups, plus the columns its DAO query
families actually filter or sort on:

| Access pattern | Indexed columns |
|---|---|
| Trades by signal / instrument / date / status / strategy | `signalId`, `instrumentId`, `entryTimestamp`, `closeTimestamp`, `status`, `strategyId`, plus composite `(instrumentId, status)` and `(entryTimestamp, instrumentId)` for the most common dashboard queries |
| Executions/exits/fees/journal by trade, ordered by time | `tradeRowId`, `executedAt`/`exitedAt`/`chargedAt`/`createdAt`, composite `(tradeRowId, executedAt)` |
| Backtests by strategy; runs by backtest/status; trades/results by run | `strategyId`, `backtestRowId`, `status`, `runRowId` (unique on `backtest_results.runRowId`) |
| Performance by scope, by date, by strategy/instrument, by month | composite `(scope, scopeKey, snapshotAt)`, unique `strategyId`/`instrumentId`/`yearMonth` on the rollup tables |
| Learning rows by trade/instrument/strategy/confidence/category/status | `relatedTradeRowId`, `relatedInstrumentId`, `relatedStrategyId`, `confidence`, `category`, `status` |
| Timeline by type/severity/date/trade/instrument | `eventType`, `severity`, `occurredAt`, `relatedTradeRowId`, `relatedInstrumentId`, composite `(eventType, occurredAt)` |
| Portfolio by portfolio+status, by instrument, by date | `(portfolioRowId, instrumentId, status)`, `instrumentId`, `occurredAt`, composite `(portfolioRowId, occurredAt)` |

Composite indexes are ordered with the highest-selectivity/most-frequently-filtered column
first so single-column queries on that leading column can still use the same index.

## 11. Future AI Integration

Every entity in Section 4 (`LearningObservationEntity` → `LearningInsightEntity` →
`OptimizationSuggestionEntity`) and Section 5 (`TradingTimelineEventEntity`,
`DecisionRecordEntity`/`DecisionExplanationEntity`, `LessonLearnedEntity`) exists specifically
so a future AI inference module can:

1. Read the complete Trade → Performance chain via `TradeRepository.getTradeFullHistory()`
   and `PerformanceRepository`.
2. Write its conclusions back as observations/insights/suggestions/patterns/failure analyses
   without ever needing write access to `trades`, `backtest_*`, or `portfolio_*` tables
   directly — it only needs `LearningRepository` and `TimelineRepository`.
3. Have every conclusion be traceable: `LearningInsightEntity.supportingObservationRowIdsCsv`,
   `FailureAnalysisEntity.relatedTradeRowId`/`relatedBacktestRunRowId`, and
   `DecisionExplanationEntity.decisionRecordRowId` all point back to the evidence that produced
   them.

No inference logic, no strategy-optimization algorithm, and no scoring model is implemented in
this module — only the storage contract that logic will read and write against.

## 12. Scalability Notes

- **Row growth**: `trade_executions`, `trade_fees`, `trading_timeline_events`, and
  `backtest_trades` are the highest-growth tables (many rows per trade / per backtest run).
  All four are append-only with time-ordered composite indexes, which keeps insert cost low
  (no update-in-place, no index rebalancing from status flips) and read cost bounded by the
  query's actual time window.
- **Denormalized rollups**: `StrategyPerformanceEntity`, `InstrumentPerformanceEntity`, and
  `MonthlyPerformanceEntity` exist so dashboards never have to aggregate the full `trades`
  table on every read. As trade volume grows, the cost of keeping these upserted rollups
  current is paid once (by whatever job recomputes them), not on every dashboard render.
- **JSON escape hatches**: `BacktestConfigurationEntity.parametersJson`,
  `TradingTimelineEventEntity.payloadJson`, and the commission/slippage model JSON columns
  let adjacent modules (strategy engine, execution engine) evolve their own payload shapes
  without forcing a Room migration in this module.
- **Independent databases**: because `AnalyticsDatabase` is physically separate from
  `TidbDatabase`/`SignalDatabase`, this module's tables can eventually be moved to a separate
  physical store (e.g. a server-side Postgres/DuckDB warehouse, mirroring the migration path
  already documented for Module 1) without touching Module 1 or Module 2 at all — the only
  coupling points are the two repository interfaces `TradeRepositoryImpl` calls.
- **No destructive fallback**: `AnalyticsDatabase.getInstance()` never calls
  `fallbackToDestructiveMigration()`. `AnalyticsMigrations.ALL` is the single place all future
  schema changes must register, ready for schema version 2 onward.
