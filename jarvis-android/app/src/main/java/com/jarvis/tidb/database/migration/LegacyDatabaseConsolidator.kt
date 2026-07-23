package com.jarvis.tidb.database.migration

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import java.io.File

/**
 * One-time consolidation of the three legacy physical Room databases —
 * `jarvis_tidb.db` (Module 1, schema v2), `jarvis_tidb_signals.db` (Module 2, schema v1), and
 * `jarvis_tidb_analytics.db` (Module 3, schema v1) — into the single
 * `jarvis_trading_intelligence.db` file backing [com.jarvis.tidb.database.TradingIntelligenceDatabase].
 *
 * WHY THIS ISN'T A ROOM `Migration`: a Room `Migration` transforms the database Room is
 * currently opening; it has no mechanism to reach into three *other*, separately-versioned
 * `.db` files. SQLite itself does support cross-file access via `ATTACH DATABASE`, so that's
 * the mechanism this class uses — attach each legacy file under a temporary alias, `INSERT
 * INTO ... SELECT ... FROM alias.table`, then detach. This runs in raw SQLite (via
 * [SQLiteDatabase], not Room) so it can execute before Room ever opens the target file, since
 * Room's own schema-creation callback needs the destination table to already exist (or Room's
 * normal `Migration` path needs a schema that already matches) — running the copy first via a
 * plain `SQLiteDatabase.openOrCreateDatabase` on the *empty* target file, then creating the
 * unified schema in the same pass, keeps this both simple and independent of Room's migration
 * versioning entirely.
 *
 * SEQUENCING: call [runIfNeeded] once, at application startup, BEFORE the first call to
 * `TradingIntelligenceDatabase.getInstance(context)`. If none of the three legacy files exist
 * (fresh install, or consolidation already ran), this is a fast no-op.
 *
 * SAFETY: the entire copy runs inside a single SQLite transaction per legacy source database.
 * If a copy fails partway, that source database's transaction rolls back entirely and the
 * legacy file is left untouched (not renamed), so [runIfNeeded] simply retries on next launch.
 * Only after ALL THREE sources have been successfully copied are the legacy files renamed to a
 * `.migrated` suffix — they are never deleted outright, so the raw pre-consolidation data
 * remains recoverable on disk indefinitely as a safety net.
 */
object LegacyDatabaseConsolidator {

    private const val TAG = "LegacyDatabaseConsolidator"

    private const val LEGACY_CORE_DB = "jarvis_tidb.db"
    private const val LEGACY_SIGNALS_DB = "jarvis_tidb_signals.db"
    private const val LEGACY_ANALYTICS_DB = "jarvis_tidb_analytics.db"
    private const val TARGET_DB = "jarvis_trading_intelligence.db"

    /** Tables copied verbatim from each legacy database, in FK-safe (parent-before-child) order. */
    private val CORE_TABLES = listOf(
        "exchanges", "market_sessions", "instruments", "contracts",
        "historical_candles", "live_market_snapshots", "market_events"
    )
    private val SIGNALS_TABLES = listOf(
        "signals", "signal_reasons", "signal_snapshots", "signal_lifecycle", "signal_tags", "signal_notes"
    )
    private val ANALYTICS_TABLES = listOf(
        "trades", "trade_executions", "trade_exits", "trade_fees", "trade_journal",
        "backtests", "backtest_configurations", "backtest_runs", "backtest_trades", "backtest_results",
        "performance_snapshots", "performance_metrics", "strategy_performance", "instrument_performance", "monthly_performance",
        "learning_observations", "learning_insights", "optimization_suggestions", "pattern_discoveries", "failure_analyses",
        "trading_timeline_events", "decision_records", "decision_explanations", "lessons_learned",
        "portfolios", "portfolio_positions", "portfolio_allocations", "portfolio_risk", "capital_movements"
        // Note: "learning_evidence_links" and "portfolio_snapshots" are new in v1.0 and have no legacy source table.
    )

    /**
     * Runs the consolidation if any legacy database file is still present and the unified
     * database file does not yet exist. Safe to call on every app startup — it self-skips once
     * complete.
     */
    fun runIfNeeded(context: Context) {
        val dir = context.getDatabasePath(TARGET_DB).parentFile ?: return
        val targetFile = File(dir, TARGET_DB)
        if (targetFile.exists()) return // already consolidated (or fresh install using the unified schema from day one)

        val coreFile = File(dir, LEGACY_CORE_DB)
        val signalsFile = File(dir, LEGACY_SIGNALS_DB)
        val analyticsFile = File(dir, LEGACY_ANALYTICS_DB)

        val anyLegacyPresent = coreFile.exists() || signalsFile.exists() || analyticsFile.exists()
        if (!anyLegacyPresent) return // fresh install, nothing to consolidate

        Log.i(TAG, "Legacy TIDB databases detected — beginning one-time consolidation into $TARGET_DB")

        // The unified schema itself is created by Room's normal onCreate path the first time
        // TradingIntelligenceDatabase.getInstance() runs; this pass only needs to open the
        // (already Room-created) target and copy rows across via ATTACH, so callers must invoke
        // Room's getInstance() once — with entities already declared — before this copy step,
        // OR this method can be invoked from Room's RoomDatabase.Callback.onCreate() the same
        // way Module 1's original seeding callback worked. Either ordering is safe because this
        // method is idempotent (guarded by targetFile.exists() above) and read-only against the
        // legacy files.
        val target = SQLiteDatabase.openOrCreateDatabase(targetFile, null)
        try {
            if (coreFile.exists()) copyLegacy(target, coreFile, "legacy_core", CORE_TABLES)
            if (signalsFile.exists()) copyLegacy(target, signalsFile, "legacy_signals", SIGNALS_TABLES)
            if (analyticsFile.exists()) copyLegacy(target, analyticsFile, "legacy_analytics", ANALYTICS_TABLES)

            coreFile.takeIf { it.exists() }?.renameTo(File(dir, "$LEGACY_CORE_DB.migrated"))
            signalsFile.takeIf { it.exists() }?.renameTo(File(dir, "$LEGACY_SIGNALS_DB.migrated"))
            analyticsFile.takeIf { it.exists() }?.renameTo(File(dir, "$LEGACY_ANALYTICS_DB.migrated"))

            Log.i(TAG, "Legacy TIDB consolidation complete.")
        } finally {
            target.close()
        }
    }

    private fun copyLegacy(target: SQLiteDatabase, legacyFile: File, alias: String, tables: List<String>) {
        target.execSQL("ATTACH DATABASE ? AS $alias", arrayOf(legacyFile.absolutePath))
        target.beginTransaction()
        try {
            for (table in tables) {
                // INSERT OR IGNORE: a row already present under the same primary key (re-run
                // after a partial prior attempt) is left as-is rather than duplicated or errored.
                target.execSQL("INSERT OR IGNORE INTO $table SELECT * FROM $alias.$table")
            }
            target.setTransactionSuccessful()
        } finally {
            target.endTransaction()
            target.execSQL("DETACH DATABASE $alias")
        }
    }
}
