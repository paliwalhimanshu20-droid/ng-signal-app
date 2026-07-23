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

    /** All in-place migrations for [com.jarvis.tidb.database.TradingIntelligenceDatabase], in order. Empty until schema version 5 ships. */
    val ALL: Array<Migration> = emptyArray()
}
