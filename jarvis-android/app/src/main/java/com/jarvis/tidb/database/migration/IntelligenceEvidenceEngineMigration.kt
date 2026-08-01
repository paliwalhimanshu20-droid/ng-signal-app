package com.jarvis.tidb.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * MIGRATION_5_6 — TRADING-006: Trading Intelligence & Evidence Engine (Module 5).
 *
 * Purely additive: 17 new tables under `com.jarvis.tidb.intelligence` (evidence extensions,
 * pattern catalog, market regimes, confidence models, research engine, knowledge graph), plus
 * one additive column (`pattern_occurrences.patternId`) on an existing Module 4 table. No
 * existing table's shape changes otherwise; no data is destroyed or moved. Every `CREATE TABLE`
 * uses `IF NOT EXISTS` and the `ALTER TABLE` is guarded at the call site by
 * [TidbMigrations] running each migration at most once per install, consistent with this
 * project's "no destructive migrations" invariant. See
 * docs/database/TRADING-006-Trading-Intelligence-Evidence-Engine.md for the full design notes
 * and reconciliation against Module 4/Module 3 entities that were intentionally NOT duplicated.
 */
val MIGRATION_5_6: Migration = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {

        // ============================== EVIDENCE FOUNDATION EXTENSIONS ==============================

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `evidence_categories` (
                `categoryId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `uuid` TEXT NOT NULL,
                `code` TEXT NOT NULL,
                `displayName` TEXT NOT NULL,
                `description` TEXT,
                `level` TEXT NOT NULL,
                `parentCategoryId` INTEGER,
                `isActive` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `createdBy` TEXT NOT NULL,
                `updatedBy` TEXT NOT NULL,
                `version` INTEGER NOT NULL,
                FOREIGN KEY(`parentCategoryId`) REFERENCES `evidence_categories`(`categoryId`) ON UPDATE CASCADE ON DELETE SET NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_evidence_categories_uuid` ON `evidence_categories` (`uuid`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_evidence_categories_code` ON `evidence_categories` (`code`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_evidence_categories_parentCategoryId` ON `evidence_categories` (`parentCategoryId`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `evidence_sources` (
                `sourceId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `uuid` TEXT NOT NULL,
                `sourceCode` TEXT NOT NULL,
                `displayName` TEXT NOT NULL,
                `sourceKind` TEXT NOT NULL,
                `reliabilityWeight` REAL NOT NULL,
                `description` TEXT,
                `isActive` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `createdBy` TEXT NOT NULL,
                `updatedBy` TEXT NOT NULL,
                `version` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_evidence_sources_uuid` ON `evidence_sources` (`uuid`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_evidence_sources_sourceCode` ON `evidence_sources` (`sourceCode`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_evidence_sources_isActive` ON `evidence_sources` (`isActive`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `evidence_links` (
                `linkId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `uuid` TEXT NOT NULL,
                `evidenceId` INTEGER NOT NULL,
                `linkedEntityType` TEXT NOT NULL,
                `linkedEntityRowId` INTEGER NOT NULL,
                `role` TEXT NOT NULL,
                `weight` REAL NOT NULL,
                `notes` TEXT,
                `linkedAt` INTEGER NOT NULL,
                FOREIGN KEY(`evidenceId`) REFERENCES `evidence_records`(`evidenceId`) ON UPDATE CASCADE ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_evidence_links_uuid` ON `evidence_links` (`uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_evidence_links_evidenceId` ON `evidence_links` (`evidenceId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_evidence_links_linkedEntityType_linkedEntityRowId` ON `evidence_links` (`linkedEntityType`, `linkedEntityRowId`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_evidence_links_evidenceId_linkedEntityType_linkedEntityRowId` ON `evidence_links` (`evidenceId`, `linkedEntityType`, `linkedEntityRowId`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `evidence_outcomes` (
                `outcomeId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `uuid` TEXT NOT NULL,
                `evidenceId` INTEGER NOT NULL,
                `verdict` TEXT NOT NULL,
                `evaluatedAt` INTEGER NOT NULL,
                `horizonDescription` TEXT,
                `actualMovePercent` REAL,
                `notes` TEXT,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `createdBy` TEXT NOT NULL,
                `updatedBy` TEXT NOT NULL,
                `version` INTEGER NOT NULL,
                FOREIGN KEY(`evidenceId`) REFERENCES `evidence_records`(`evidenceId`) ON UPDATE CASCADE ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_evidence_outcomes_uuid` ON `evidence_outcomes` (`uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_evidence_outcomes_evidenceId` ON `evidence_outcomes` (`evidenceId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_evidence_outcomes_verdict` ON `evidence_outcomes` (`verdict`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_evidence_outcomes_evaluatedAt` ON `evidence_outcomes` (`evaluatedAt`)")

        // ============================== PATTERN CATALOG ==============================

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `patterns` (
                `patternId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `uuid` TEXT NOT NULL,
                `patternKey` TEXT NOT NULL,
                `displayName` TEXT NOT NULL,
                `family` TEXT NOT NULL,
                `description` TEXT NOT NULL,
                `definitionJson` TEXT,
                `isActive` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `createdBy` TEXT NOT NULL,
                `updatedBy` TEXT NOT NULL,
                `version` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_patterns_uuid` ON `patterns` (`uuid`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_patterns_patternKey` ON `patterns` (`patternKey`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_patterns_family` ON `patterns` (`family`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_patterns_isActive` ON `patterns` (`isActive`)")

        // Additive column on the existing Module 4 table `pattern_occurrences`. Nullable, logical-only
        // reference to `patterns.patternId` (not a Room @ForeignKey — see PatternEntities.kt doc).
        // `patternName` (the existing free-text column) is left completely untouched.
        db.execSQL("ALTER TABLE `pattern_occurrences` ADD COLUMN `patternId` INTEGER")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_pattern_occurrences_patternId` ON `pattern_occurrences` (`patternId`)")

        // ============================== MARKET REGIME TRACKING ==============================

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `market_regimes` (
                `regimeId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `uuid` TEXT NOT NULL,
                `instrumentId` INTEGER NOT NULL,
                `timeframe` TEXT NOT NULL,
                `regimeType` TEXT NOT NULL,
                `startTimestamp` INTEGER NOT NULL,
                `endTimestamp` INTEGER,
                `confidence` REAL NOT NULL,
                `description` TEXT,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `createdBy` TEXT NOT NULL,
                `updatedBy` TEXT NOT NULL,
                `version` INTEGER NOT NULL,
                FOREIGN KEY(`instrumentId`) REFERENCES `instruments`(`instrumentId`) ON UPDATE CASCADE ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_market_regimes_uuid` ON `market_regimes` (`uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_market_regimes_instrumentId_timeframe_startTimestamp` ON `market_regimes` (`instrumentId`, `timeframe`, `startTimestamp`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_market_regimes_regimeType` ON `market_regimes` (`regimeType`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_market_regimes_instrumentId_timeframe_endTimestamp` ON `market_regimes` (`instrumentId`, `timeframe`, `endTimestamp`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `regime_observations` (
                `regimeObservationId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `uuid` TEXT NOT NULL,
                `regimeId` INTEGER NOT NULL,
                `observedAt` INTEGER NOT NULL,
                `supportingMetric` TEXT NOT NULL,
                `metricValue` REAL NOT NULL,
                `notes` TEXT,
                FOREIGN KEY(`regimeId`) REFERENCES `market_regimes`(`regimeId`) ON UPDATE CASCADE ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_regime_observations_uuid` ON `regime_observations` (`uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_regime_observations_regimeId` ON `regime_observations` (`regimeId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_regime_observations_observedAt` ON `regime_observations` (`observedAt`)")

        // ============================== CONFIDENCE MODELING ==============================

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `confidence_models` (
                `modelId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `uuid` TEXT NOT NULL,
                `modelKey` TEXT NOT NULL,
                `displayName` TEXT NOT NULL,
                `modelType` TEXT NOT NULL,
                `description` TEXT NOT NULL,
                `parametersJson` TEXT,
                `isActive` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `createdBy` TEXT NOT NULL,
                `updatedBy` TEXT NOT NULL,
                `version` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_confidence_models_uuid` ON `confidence_models` (`uuid`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_confidence_models_modelKey` ON `confidence_models` (`modelKey`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_confidence_models_isActive` ON `confidence_models` (`isActive`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `confidence_scores` (
                `scoreId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `uuid` TEXT NOT NULL,
                `modelId` INTEGER NOT NULL,
                `scoredEntityType` TEXT NOT NULL,
                `scoredEntityRowId` INTEGER NOT NULL,
                `score` REAL NOT NULL,
                `breakdownJson` TEXT,
                `computedAt` INTEGER NOT NULL,
                `notes` TEXT,
                FOREIGN KEY(`modelId`) REFERENCES `confidence_models`(`modelId`) ON UPDATE CASCADE ON DELETE RESTRICT
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_confidence_scores_uuid` ON `confidence_scores` (`uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_confidence_scores_modelId` ON `confidence_scores` (`modelId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_confidence_scores_scoredEntityType_scoredEntityRowId` ON `confidence_scores` (`scoredEntityType`, `scoredEntityRowId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_confidence_scores_computedAt` ON `confidence_scores` (`computedAt`)")

        // ============================== RESEARCH ENGINE ==============================

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `hypotheses` (
                `hypothesisId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `uuid` TEXT NOT NULL,
                `statement` TEXT NOT NULL,
                `rationale` TEXT,
                `status` TEXT NOT NULL,
                `proposedBy` TEXT NOT NULL,
                `proposedAt` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `createdBy` TEXT NOT NULL,
                `updatedBy` TEXT NOT NULL,
                `version` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_hypotheses_uuid` ON `hypotheses` (`uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_hypotheses_status` ON `hypotheses` (`status`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_hypotheses_proposedAt` ON `hypotheses` (`proposedAt`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `experiments` (
                `experimentId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `uuid` TEXT NOT NULL,
                `hypothesisId` INTEGER NOT NULL,
                `name` TEXT NOT NULL,
                `experimentType` TEXT NOT NULL,
                `designDescription` TEXT NOT NULL,
                `status` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `createdBy` TEXT NOT NULL,
                `updatedBy` TEXT NOT NULL,
                `version` INTEGER NOT NULL,
                FOREIGN KEY(`hypothesisId`) REFERENCES `hypotheses`(`hypothesisId`) ON UPDATE CASCADE ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_experiments_uuid` ON `experiments` (`uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_experiments_hypothesisId` ON `experiments` (`hypothesisId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_experiments_status` ON `experiments` (`status`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `experiment_runs` (
                `runId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `uuid` TEXT NOT NULL,
                `experimentId` INTEGER NOT NULL,
                `runLabel` TEXT NOT NULL,
                `parametersJson` TEXT,
                `status` TEXT NOT NULL,
                `startedAt` INTEGER,
                `completedAt` INTEGER,
                `relatedBacktestRunRowId` INTEGER,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `createdBy` TEXT NOT NULL,
                `updatedBy` TEXT NOT NULL,
                `version` INTEGER NOT NULL,
                FOREIGN KEY(`experimentId`) REFERENCES `experiments`(`experimentId`) ON UPDATE CASCADE ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_experiment_runs_uuid` ON `experiment_runs` (`uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_experiment_runs_experimentId` ON `experiment_runs` (`experimentId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_experiment_runs_status` ON `experiment_runs` (`status`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_experiment_runs_relatedBacktestRunRowId` ON `experiment_runs` (`relatedBacktestRunRowId`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `experiment_results` (
                `resultId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `uuid` TEXT NOT NULL,
                `runId` INTEGER NOT NULL,
                `metricName` TEXT NOT NULL,
                `metricValue` REAL NOT NULL,
                `conclusion` TEXT,
                `summary` TEXT,
                `producedInsightRowId` INTEGER,
                `recordedAt` INTEGER NOT NULL,
                FOREIGN KEY(`runId`) REFERENCES `experiment_runs`(`runId`) ON UPDATE CASCADE ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_experiment_results_uuid` ON `experiment_results` (`uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_experiment_results_runId` ON `experiment_results` (`runId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_experiment_results_conclusion` ON `experiment_results` (`conclusion`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_experiment_results_producedInsightRowId` ON `experiment_results` (`producedInsightRowId`)")

        // ============================== KNOWLEDGE GRAPH ==============================

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `entity_relationships` (
                `relationshipId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `uuid` TEXT NOT NULL,
                `fromEntityType` TEXT NOT NULL,
                `fromEntityRowId` INTEGER NOT NULL,
                `toEntityType` TEXT NOT NULL,
                `toEntityRowId` INTEGER NOT NULL,
                `relationshipType` TEXT NOT NULL,
                `strength` REAL NOT NULL,
                `notes` TEXT,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `createdBy` TEXT NOT NULL,
                `updatedBy` TEXT NOT NULL,
                `version` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_entity_relationships_uuid` ON `entity_relationships` (`uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_entity_relationships_fromEntityType_fromEntityRowId` ON `entity_relationships` (`fromEntityType`, `fromEntityRowId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_entity_relationships_toEntityType_toEntityRowId` ON `entity_relationships` (`toEntityType`, `toEntityRowId`)")
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_entity_relationships_from_to_type` ON `entity_relationships` " +
                "(`fromEntityType`, `fromEntityRowId`, `toEntityType`, `toEntityRowId`, `relationshipType`)"
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `market_contexts` (
                `contextId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `uuid` TEXT NOT NULL,
                `instrumentId` INTEGER NOT NULL,
                `timestamp` INTEGER NOT NULL,
                `regimeId` INTEGER,
                `contextSummary` TEXT NOT NULL,
                `macroNotes` TEXT,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `createdBy` TEXT NOT NULL,
                `updatedBy` TEXT NOT NULL,
                `version` INTEGER NOT NULL,
                FOREIGN KEY(`instrumentId`) REFERENCES `instruments`(`instrumentId`) ON UPDATE CASCADE ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_market_contexts_uuid` ON `market_contexts` (`uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_market_contexts_instrumentId_timestamp` ON `market_contexts` (`instrumentId`, `timestamp`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_market_contexts_regimeId` ON `market_contexts` (`regimeId`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `causal_observations` (
                `causalObservationId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `uuid` TEXT NOT NULL,
                `causeEntityType` TEXT NOT NULL,
                `causeEntityRowId` INTEGER NOT NULL,
                `effectEntityType` TEXT NOT NULL,
                `effectEntityRowId` INTEGER NOT NULL,
                `direction` TEXT NOT NULL,
                `lagMillis` INTEGER,
                `confidence` REAL NOT NULL,
                `description` TEXT NOT NULL,
                `observedAt` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `createdBy` TEXT NOT NULL,
                `updatedBy` TEXT NOT NULL,
                `version` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_causal_observations_uuid` ON `causal_observations` (`uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_causal_observations_causeEntityType_causeEntityRowId` ON `causal_observations` (`causeEntityType`, `causeEntityRowId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_causal_observations_effectEntityType_effectEntityRowId` ON `causal_observations` (`effectEntityType`, `effectEntityRowId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_causal_observations_observedAt` ON `causal_observations` (`observedAt`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `correlations` (
                `correlationId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `uuid` TEXT NOT NULL,
                `entityAType` TEXT NOT NULL,
                `entityARowId` INTEGER NOT NULL,
                `entityBType` TEXT NOT NULL,
                `entityBRowId` INTEGER NOT NULL,
                `coefficient` REAL NOT NULL,
                `windowDescription` TEXT NOT NULL,
                `sampleSize` INTEGER NOT NULL,
                `computedAt` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `createdBy` TEXT NOT NULL,
                `updatedBy` TEXT NOT NULL,
                `version` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_correlations_uuid` ON `correlations` (`uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_correlations_entityAType_entityARowId` ON `correlations` (`entityAType`, `entityARowId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_correlations_entityBType_entityBRowId` ON `correlations` (`entityBType`, `entityBRowId`)")
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_correlations_a_b_window` ON `correlations` " +
                "(`entityAType`, `entityARowId`, `entityBType`, `entityBRowId`, `windowDescription`)"
        )
    }
}
