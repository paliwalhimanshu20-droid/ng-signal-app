package com.jarvis.tidb.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * "Phase 4B, Section 1+7+8 -- Trust Layer." Purely additive: one nullable column on the existing
 * `decision_recommendations` table, no new tables at all. Per this phase's own Repository
 * Reality Report finding, [com.jarvis.tidb.intelligence.confidence.entity.ConfidenceScoreEntity]
 * (polymorphic via `ScoredEntityType` + `breakdownJson`) already covers everything a naive
 * reading of "add a Trust Score" would suggest needs a new table -- the same reasoning
 * TRADING-007B itself already applied to `confidenceScoreId` on this same table. This migration
 * adds `trustScoreId` as the logical-only sibling of the existing `confidenceScoreId` column;
 * the Trust Score's six-dimension breakdown (historical data, indicators, optimization,
 * backtests, learning, paper trading) is a new `ScoredEntityType.TRUST_ASSESSMENT` enum value
 * (a Kotlin-level addition, no schema change of its own) plus a `confidence_scores` row, not a
 * new table.
 */
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE decision_recommendations ADD COLUMN trustScoreId INTEGER")
    }
}
