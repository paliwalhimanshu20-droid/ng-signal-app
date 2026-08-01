package com.jarvis.tidb.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * "Phase 3B, Section 1 -- Optimization Persistence." Purely additive: two new tables,
 * `optimization_jobs` and `optimization_combinations`, no changes to any existing table.
 * `optimization_combinations.backtestRunRowId` / `backtestResultRowId` are the explicit reuse
 * point for `backtest_runs` / `backtest_results` (see [com.jarvis.tidb.optimization.entity.
 * OptimizationCombinationEntity]'s own docstring) -- this migration deliberately does NOT
 * duplicate `BacktestConfigurationEntity` / `BacktestRunEntity` / `BacktestResultEntity`, per
 * this phase's explicit "DO NOT duplicate existing schemas" instruction.
 */
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `optimization_jobs` (
                `rowId` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                `uuid` TEXT NOT NULL,
                `componentId` TEXT NOT NULL,
                `algorithmId` TEXT NOT NULL,
                `instrumentId` INTEGER NOT NULL,
                `timeframe` TEXT NOT NULL,
                `periodStart` INTEGER NOT NULL,
                `periodEnd` INTEGER NOT NULL,
                `budget` INTEGER NOT NULL,
                `randomSeed` INTEGER,
                `status` TEXT NOT NULL,
                `totalCombinations` INTEGER NOT NULL,
                `completedCombinations` INTEGER NOT NULL DEFAULT 0,
                `checkpointCombinationIndex` INTEGER NOT NULL DEFAULT -1,
                `backtestRowId` INTEGER,
                `errorMessage` TEXT,
                `startedAt` INTEGER,
                `completedAt` INTEGER,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `createdBy` TEXT NOT NULL,
                `updatedBy` TEXT NOT NULL,
                `version` INTEGER NOT NULL,
                `isDeleted` INTEGER NOT NULL DEFAULT 0,
                `deletedAt` INTEGER,
                FOREIGN KEY(`backtestRowId`) REFERENCES `backtests`(`rowId`) ON DELETE SET NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_optimization_jobs_uuid` ON `optimization_jobs` (`uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_optimization_jobs_componentId` ON `optimization_jobs` (`componentId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_optimization_jobs_status` ON `optimization_jobs` (`status`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_optimization_jobs_instrumentId` ON `optimization_jobs` (`instrumentId`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `optimization_combinations` (
                `rowId` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                `uuid` TEXT NOT NULL,
                `jobRowId` INTEGER NOT NULL,
                `combinationIndex` INTEGER NOT NULL,
                `parametersJson` TEXT NOT NULL,
                `status` TEXT NOT NULL,
                `backtestRunRowId` INTEGER,
                `backtestResultRowId` INTEGER,
                `rank` INTEGER,
                `errorMessage` TEXT,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `createdBy` TEXT NOT NULL,
                `updatedBy` TEXT NOT NULL,
                `version` INTEGER NOT NULL,
                FOREIGN KEY(`jobRowId`) REFERENCES `optimization_jobs`(`rowId`) ON DELETE CASCADE,
                FOREIGN KEY(`backtestRunRowId`) REFERENCES `backtest_runs`(`rowId`) ON DELETE SET NULL,
                FOREIGN KEY(`backtestResultRowId`) REFERENCES `backtest_results`(`rowId`) ON DELETE SET NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_optimization_combinations_uuid` ON `optimization_combinations` (`uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_optimization_combinations_jobRowId` ON `optimization_combinations` (`jobRowId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_optimization_combinations_status` ON `optimization_combinations` (`status`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_optimization_combinations_job_index` ON `optimization_combinations` (`jobRowId`, `combinationIndex`)")
    }
}
