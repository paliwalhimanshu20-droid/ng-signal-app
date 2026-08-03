package com.jarvis.tidb.database.migration

import androidx.room.migration.Migration

/**
 * Migration registry for [com.jarvis.tidb.database.TradingIntelligenceDatabase].
 *
 * The unified schema is introduced at version 4 (see the database class doc for why the
 * counter doesn't restart at 1). There is nothing to `ALTER`/`CREATE` *within* the unified
 * database yet — [ALL] is empty. What upgrading installs actually need is a one-time DATA
 * CONSOLIDATION from the three legacy physical database files into this one, which is a
 * fundamentally different operation than a Room `Migration` (a `Migration` only ever
 * transforms the database Room is currently opening; it cannot reach into a separate `.db`
 * file). That step is [com.jarvis.tidb.database.migration.LegacyDatabaseConsolidator], run
 * once, before `TradingIntelligenceDatabase` is opened for the first time — see the
 * architecture doc §6 (Migration Strategy) for the full sequencing.
 *
 * Template for the next *in-place* schema change once the unified database itself needs to
 * evolve (e.g. adding a column to `trades` post-v1.0):
 *
 * ```kotlin
 * val MIGRATION_4_5 = object : Migration(4, 5) {
 *     override fun migrate(db: SupportSQLiteDatabase) {
 *         db.execSQL("ALTER TABLE trades ADD COLUMN newColumn TEXT")
 *         db.execSQL("CREATE INDEX IF NOT EXISTS index_trades_newColumn ON trades(newColumn)")
 *     }
 * }
 * ```
 */
object TidbMigrations {

    /**
     * All in-place migrations for [com.jarvis.tidb.database.TradingIntelligenceDatabase], in order.
     *
     * v4 -> v5: [MIGRATION_4_5] — Historical Market Data Platform. Purely additive (26 new
     * tables across ingestion/candle-extensions/quality/indicator/dna/evidence); no existing
     * table is altered. See docs/database/TRADING-005-Historical-Market-Data-Platform.md.
     *
     * v5 -> v6: [MIGRATION_5_6] — TRADING-006 Trading Intelligence & Evidence Engine (Module 5).
     * Purely additive (17 new tables under `com.jarvis.tidb.intelligence`, plus one additive
     * nullable column — `pattern_occurrences.patternId`). See
     * docs/database/TRADING-006-Trading-Intelligence-Evidence-Engine.md.
     *
     * v6 -> v7: [MIGRATION_6_7] — TRADING-007A.1 News & Sentiment Intelligence Platform.
     * Purely additive (7 new tables under `com.jarvis.tidb.news`). Reuses Module 4/5's
     * evidence/confidence/outcome tables rather than duplicating them — see
     * docs/database/TRADING-007A.1-News-Sentiment-Intelligence-Platform.md.
     *
     * v7 -> v8: [MIGRATION_7_8] — TRADING-007A.2 Market Context Intelligence Platform.
     * Purely additive (7 new tables under `com.jarvis.tidb.context`: 5 `economic_event_*`
     * calendar tables plus the standalone `drift_metrics` / `calibration_metrics` polymorphic
     * monitoring tables). Reuses the `subjectType`/`subjectRowId` polymorphic pattern already
     * established by Module 5's `confidence_scores` and `entity_relationships` rather than
     * introducing separate per-subject-type drift tables — see
     * docs/database/TRADING-007A.2-Market-Context-Intelligence-Platform.md.
     *
     * v8 -> v9: [MIGRATION_8_9] — TRADING-007B Decision Intelligence Engine.
     * Purely additive (5 new tables under `com.jarvis.tidb.decision`). Persistence only — no
     * inference/scoring logic. Reuses `EvidenceLinkEntity` (evidence weighting), `ConfidenceScoreEntity`
     * (confidence), and `drift_metrics`/`calibration_metrics` (decision drift/calibration) via
     * additive enum values rather than duplicating any of them — see
     * docs/database/TRADING-007B-Decision-Intelligence-Engine-Architecture.md.
     *
     * v9 -> v10: [MIGRATION_9_10] — Phase 3B Section 1, Optimization Persistence.
     * Purely additive (2 new tables: `optimization_jobs`, `optimization_combinations`).
     * `optimization_combinations` links to the existing `backtest_runs`/`backtest_results`
     * tables (nullable FKs, populated once a combination is actually evaluated) rather than
     * duplicating any backtest schema — see [com.jarvis.tidb.optimization.entity.
     * OptimizationCombinationEntity]'s own docstring.
     *
     * v10 -> v11: [MIGRATION_10_11] — Phase 4B, Section 1+7+8: Trust Layer.
     * Purely additive (1 new nullable column: `decision_recommendations.trustScoreId`). No new
     * tables — the Trust Score itself reuses `confidence_scores` via the additive
     * `ScoredEntityType.TRUST_ASSESSMENT` enum value, the same polymorphic mechanism
     * `confidenceScoreId`/`ScoredEntityType.DECISION` already established — see
     * [com.jarvis.tidb.database.migration.MIGRATION_10_11]'s own docstring.
     */
    val ALL: Array<Migration> = arrayOf(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)
}
