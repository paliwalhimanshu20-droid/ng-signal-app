package com.jarvis.tidb.analytics.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration registry for [com.jarvis.tidb.analytics.AnalyticsDatabase].
 *
 * Module 3 ships as schema version 1 — there is nothing to migrate from yet. This file exists
 * from day one anyway (rather than being added on the first schema bump) so the "every
 * structural change ships an explicit Migration, no destructive fallback" convention from
 * Module 1 and Module 2 is visible and enforced from the start, and so future migrations have
 * an obvious place to live.
 *
 * Template for the next migration, following the Module 1 Revision-1 pattern (new nullable
 * columns get `ALTER TABLE ... ADD COLUMN` with a default; anything requiring a backfill uses
 * `UPDATE` statements, e.g. SQLite's `randomblob()` trick for per-row random defaults):
 *
 * ```kotlin
 * val MIGRATION_1_2 = object : Migration(1, 2) {
 *     override fun migrate(db: SupportSQLiteDatabase) {
 *         db.execSQL("ALTER TABLE trades ADD COLUMN newColumn TEXT")
 *         db.execSQL("CREATE INDEX IF NOT EXISTS index_trades_newColumn ON trades(newColumn)")
 *     }
 * }
 * ```
 */
object AnalyticsMigrations {

    /** All migrations for [com.jarvis.tidb.analytics.AnalyticsDatabase], in order. Empty until schema version 2 ships. */
    val ALL: Array<Migration> = emptyArray()
}
