package com.jarvis.tidb.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * MIGRATION_4_5 — Historical Market Data Platform.
 *
 * Purely additive: 26 new tables across six new packages under `com.jarvis.tidb.historical`
 * (ingestion, candle extensions, quality, indicator, dna, evidence). Nothing in Module 1/2/3
 * is altered — `historical_candles`, `instruments`, etc. keep their exact existing shape;
 * the new tables only add foreign keys pointing *at* them. No data is destroyed or moved;
 * every `CREATE TABLE` uses `IF NOT EXISTS` so a partially-applied prior attempt is safe to
 * retry, consistent with this project's "no destructive migrations" invariant.
 */
val MIGRATION_4_5: Migration = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {

        // ============================== INGESTION ENGINE ==============================

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `data_providers` (
                `providerId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `uuid` TEXT NOT NULL,
                `providerCode` TEXT NOT NULL,
                `displayName` TEXT NOT NULL,
                `providerType` TEXT NOT NULL,
                `priority` INTEGER NOT NULL,
                `rateLimitPerMinute` INTEGER,
                `supportedTimeframes` TEXT NOT NULL,
                `isActive` INTEGER NOT NULL,
                `configJson` TEXT,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `createdBy` TEXT NOT NULL,
                `updatedBy` TEXT NOT NULL,
                `version` INTEGER NOT NULL,
                `isDeleted` INTEGER NOT NULL,
                `deletedAt` INTEGER
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_data_providers_uuid` ON `data_providers` (`uuid`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_data_providers_providerCode` ON `data_providers` (`providerCode`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_data_providers_isActive` ON `data_providers` (`isActive`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_data_providers_isDeleted` ON `data_providers` (`isDeleted`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `ingestion_jobs` (
                `jobId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `uuid` TEXT NOT NULL,
                `providerId` INTEGER NOT NULL,
                `instrumentId` INTEGER NOT NULL,
                `timeframe` TEXT NOT NULL,
                `jobType` TEXT NOT NULL,
                `status` TEXT NOT NULL,
                `requestedRangeStart` INTEGER,
                `requestedRangeEnd` INTEGER,
                `progressPercent` REAL NOT NULL,
                `rowsFetched` INTEGER NOT NULL,
                `rowsInserted` INTEGER NOT NULL,
                `rowsSkipped` INTEGER NOT NULL,
                `rowsFailed` INTEGER NOT NULL,
                `retryCount` INTEGER NOT NULL,
                `maxRetries` INTEGER NOT NULL,
                `lastError` TEXT,
                `nextRetryAt` INTEGER,
                `startedAt` INTEGER,
                `completedAt` INTEGER,
                `triggeredBy` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `createdBy` TEXT NOT NULL,
                `updatedBy` TEXT NOT NULL,
                `version` INTEGER NOT NULL,
                `isDeleted` INTEGER NOT NULL,
                `deletedAt` INTEGER,
                FOREIGN KEY(`providerId`) REFERENCES `data_providers`(`providerId`) ON UPDATE CASCADE ON DELETE RESTRICT,
                FOREIGN KEY(`instrumentId`) REFERENCES `instruments`(`instrumentId`) ON UPDATE CASCADE ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_ingestion_jobs_uuid` ON `ingestion_jobs` (`uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_ingestion_jobs_providerId` ON `ingestion_jobs` (`providerId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_ingestion_jobs_instrumentId` ON `ingestion_jobs` (`instrumentId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_ingestion_jobs_instrumentId_timeframe` ON `ingestion_jobs` (`instrumentId`, `timeframe`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_ingestion_jobs_status` ON `ingestion_jobs` (`status`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_ingestion_jobs_jobType` ON `ingestion_jobs` (`jobType`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_ingestion_jobs_nextRetryAt` ON `ingestion_jobs` (`nextRetryAt`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_ingestion_jobs_isDeleted` ON `ingestion_jobs` (`isDeleted`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `ingestion_job_logs` (
                `logId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `jobId` INTEGER NOT NULL,
                `attemptNumber` INTEGER NOT NULL,
                `eventType` TEXT NOT NULL,
                `message` TEXT NOT NULL,
                `detailsJson` TEXT,
                `timestamp` INTEGER NOT NULL,
                FOREIGN KEY(`jobId`) REFERENCES `ingestion_jobs`(`jobId`) ON UPDATE CASCADE ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_ingestion_job_logs_jobId` ON `ingestion_job_logs` (`jobId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_ingestion_job_logs_eventType` ON `ingestion_job_logs` (`eventType`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_ingestion_job_logs_timestamp` ON `ingestion_job_logs` (`timestamp`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `ingestion_checkpoints` (
                `checkpointId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `providerId` INTEGER NOT NULL,
                `instrumentId` INTEGER NOT NULL,
                `timeframe` TEXT NOT NULL,
                `lastSuccessfulTimestamp` INTEGER,
                `lastRunAt` INTEGER,
                `cursorToken` TEXT,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `createdBy` TEXT NOT NULL,
                `updatedBy` TEXT NOT NULL,
                `version` INTEGER NOT NULL,
                FOREIGN KEY(`providerId`) REFERENCES `data_providers`(`providerId`) ON UPDATE CASCADE ON DELETE CASCADE,
                FOREIGN KEY(`instrumentId`) REFERENCES `instruments`(`instrumentId`) ON UPDATE CASCADE ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `idx_checkpoint_provider_instrument_timeframe` ON `ingestion_checkpoints` (`providerId`, `instrumentId`, `timeframe`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_ingestion_checkpoints_instrumentId` ON `ingestion_checkpoints` (`instrumentId`)")

        // ============================== CANDLE STORAGE EXTENSIONS ==============================

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `candle_versions` (
                `candleVersionId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `uuid` TEXT NOT NULL,
                `candleId` INTEGER NOT NULL,
                `versionNumber` INTEGER NOT NULL,
                `open` REAL NOT NULL,
                `high` REAL NOT NULL,
                `low` REAL NOT NULL,
                `close` REAL NOT NULL,
                `volume` INTEGER NOT NULL,
                `openInterest` INTEGER,
                `qualityScore` REAL NOT NULL,
                `changeReason` TEXT NOT NULL,
                `changedBy` TEXT NOT NULL,
                `supersededAt` INTEGER NOT NULL,
                FOREIGN KEY(`candleId`) REFERENCES `historical_candles`(`candleId`) ON UPDATE CASCADE ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_candle_versions_candleId` ON `candle_versions` (`candleId`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `idx_candle_version_unique` ON `candle_versions` (`candleId`, `versionNumber`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `candle_gaps` (
                `gapId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `uuid` TEXT NOT NULL,
                `instrumentId` INTEGER NOT NULL,
                `timeframe` TEXT NOT NULL,
                `gapStart` INTEGER NOT NULL,
                `gapEnd` INTEGER NOT NULL,
                `expectedCandleCount` INTEGER NOT NULL,
                `status` TEXT NOT NULL,
                `reason` TEXT NOT NULL,
                `backfillJobId` INTEGER,
                `detectedAt` INTEGER NOT NULL,
                `resolvedAt` INTEGER,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `createdBy` TEXT NOT NULL,
                `updatedBy` TEXT NOT NULL,
                `version` INTEGER NOT NULL,
                FOREIGN KEY(`instrumentId`) REFERENCES `instruments`(`instrumentId`) ON UPDATE CASCADE ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_candle_gaps_uuid` ON `candle_gaps` (`uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_candle_gaps_instrumentId_timeframe` ON `candle_gaps` (`instrumentId`, `timeframe`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_candle_gaps_status` ON `candle_gaps` (`status`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_candle_gaps_gapStart` ON `candle_gaps` (`gapStart`)")

        // ============================== DATA QUALITY ENGINE ==============================

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `candle_quality_reports` (
                `reportId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `uuid` TEXT NOT NULL,
                `instrumentId` INTEGER NOT NULL,
                `timeframe` TEXT NOT NULL,
                `periodStart` INTEGER NOT NULL,
                `periodEnd` INTEGER NOT NULL,
                `expectedCandleCount` INTEGER NOT NULL,
                `actualCandleCount` INTEGER NOT NULL,
                `missingCount` INTEGER NOT NULL,
                `duplicateCount` INTEGER NOT NULL,
                `ohlcViolationCount` INTEGER NOT NULL,
                `volumeAnomalyCount` INTEGER NOT NULL,
                `timestampDiscontinuityCount` INTEGER NOT NULL,
                `qualityScore` REAL NOT NULL,
                `generatedAt` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `createdBy` TEXT NOT NULL,
                `updatedBy` TEXT NOT NULL,
                `version` INTEGER NOT NULL,
                FOREIGN KEY(`instrumentId`) REFERENCES `instruments`(`instrumentId`) ON UPDATE CASCADE ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_candle_quality_reports_uuid` ON `candle_quality_reports` (`uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_candle_quality_reports_instrumentId_timeframe` ON `candle_quality_reports` (`instrumentId`, `timeframe`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_candle_quality_reports_generatedAt` ON `candle_quality_reports` (`generatedAt`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `quality_issues` (
                `issueId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `reportId` INTEGER NOT NULL,
                `issueType` TEXT NOT NULL,
                `severity` TEXT NOT NULL,
                `candleId` INTEGER,
                `timestamp` INTEGER,
                `details` TEXT NOT NULL,
                `resolved` INTEGER NOT NULL,
                `resolvedAt` INTEGER,
                `createdAt` INTEGER NOT NULL,
                FOREIGN KEY(`reportId`) REFERENCES `candle_quality_reports`(`reportId`) ON UPDATE CASCADE ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_quality_issues_reportId` ON `quality_issues` (`reportId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_quality_issues_issueType` ON `quality_issues` (`issueType`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_quality_issues_severity` ON `quality_issues` (`severity`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_quality_issues_resolved` ON `quality_issues` (`resolved`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_quality_issues_candleId` ON `quality_issues` (`candleId`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `corporate_actions` (
                `actionId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `uuid` TEXT NOT NULL,
                `instrumentId` INTEGER NOT NULL,
                `actionType` TEXT NOT NULL,
                `effectiveDate` INTEGER NOT NULL,
                `adjustmentRatio` REAL,
                `description` TEXT NOT NULL,
                `source` TEXT NOT NULL,
                `applied` INTEGER NOT NULL,
                `appliedAt` INTEGER,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `createdBy` TEXT NOT NULL,
                `updatedBy` TEXT NOT NULL,
                `version` INTEGER NOT NULL,
                FOREIGN KEY(`instrumentId`) REFERENCES `instruments`(`instrumentId`) ON UPDATE CASCADE ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_corporate_actions_uuid` ON `corporate_actions` (`uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_corporate_actions_instrumentId` ON `corporate_actions` (`instrumentId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_corporate_actions_effectiveDate` ON `corporate_actions` (`effectiveDate`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_corporate_actions_applied` ON `corporate_actions` (`applied`)")

        // ============================== INDICATOR WAREHOUSE ==============================

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `indicator_definitions` (
                `indicatorDefId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `uuid` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `indicatorType` TEXT NOT NULL,
                `paramsJson` TEXT NOT NULL,
                `outputLabels` TEXT NOT NULL,
                `definitionVersion` INTEGER NOT NULL,
                `isActive` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `createdBy` TEXT NOT NULL,
                `updatedBy` TEXT NOT NULL,
                `version` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_indicator_definitions_uuid` ON `indicator_definitions` (`uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_indicator_definitions_indicatorType` ON `indicator_definitions` (`indicatorType`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `idx_indicator_def_name_version` ON `indicator_definitions` (`name`, `definitionVersion`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `indicator_values` (
                `indicatorValueId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `indicatorDefId` INTEGER NOT NULL,
                `instrumentId` INTEGER NOT NULL,
                `timeframe` TEXT NOT NULL,
                `timestamp` INTEGER NOT NULL,
                `version` INTEGER NOT NULL,
                `value1` REAL NOT NULL,
                `value2` REAL,
                `value3` REAL,
                `value4` REAL,
                `computedAt` INTEGER NOT NULL,
                FOREIGN KEY(`indicatorDefId`) REFERENCES `indicator_definitions`(`indicatorDefId`) ON UPDATE CASCADE ON DELETE CASCADE,
                FOREIGN KEY(`instrumentId`) REFERENCES `instruments`(`instrumentId`) ON UPDATE CASCADE ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `idx_indicator_value_unique` ON `indicator_values` (`indicatorDefId`, `instrumentId`, `timeframe`, `timestamp`, `version`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_indicator_values_instrumentId_timeframe_timestamp` ON `indicator_values` (`instrumentId`, `timeframe`, `timestamp`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_indicator_values_indicatorDefId` ON `indicator_values` (`indicatorDefId`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `indicator_computation_runs` (
                `runId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `uuid` TEXT NOT NULL,
                `indicatorDefId` INTEGER NOT NULL,
                `instrumentId` INTEGER NOT NULL,
                `timeframe` TEXT NOT NULL,
                `fromTimestamp` INTEGER NOT NULL,
                `toTimestamp` INTEGER NOT NULL,
                `status` TEXT NOT NULL,
                `rowsComputed` INTEGER NOT NULL,
                `isRecomputation` INTEGER NOT NULL,
                `startedAt` INTEGER,
                `completedAt` INTEGER,
                `error` TEXT,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `createdBy` TEXT NOT NULL,
                `updatedBy` TEXT NOT NULL,
                `version` INTEGER NOT NULL,
                FOREIGN KEY(`indicatorDefId`) REFERENCES `indicator_definitions`(`indicatorDefId`) ON UPDATE CASCADE ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_indicator_computation_runs_indicatorDefId` ON `indicator_computation_runs` (`indicatorDefId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_indicator_computation_runs_instrumentId_timeframe` ON `indicator_computation_runs` (`instrumentId`, `timeframe`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_indicator_computation_runs_status` ON `indicator_computation_runs` (`status`)")

        // ============================== INSTRUMENT DNA FOUNDATION ==============================

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `dna_volatility_profiles` (
                `profileId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `uuid` TEXT NOT NULL,
                `instrumentId` INTEGER NOT NULL,
                `timeframe` TEXT NOT NULL,
                `lookbackDays` INTEGER NOT NULL,
                `avgTrueRangePct` REAL NOT NULL,
                `realizedVolatilityAnnualizedPct` REAL NOT NULL,
                `avgDailyRangePct` REAL NOT NULL,
                `volatilityRegime` TEXT NOT NULL,
                `detailsJson` TEXT,
                `computedAt` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `createdBy` TEXT NOT NULL,
                `updatedBy` TEXT NOT NULL,
                `version` INTEGER NOT NULL,
                FOREIGN KEY(`instrumentId`) REFERENCES `instruments`(`instrumentId`) ON UPDATE CASCADE ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_dna_volatility_profiles_uuid` ON `dna_volatility_profiles` (`uuid`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `idx_dna_volatility_unique` ON `dna_volatility_profiles` (`instrumentId`, `timeframe`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `dna_session_behavior_profiles` (
                `profileId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `uuid` TEXT NOT NULL,
                `instrumentId` INTEGER NOT NULL,
                `openingRangeVolatilityPct` REAL NOT NULL,
                `closingRangeVolatilityPct` REAL NOT NULL,
                `typicalHighTimeOfDayMinutes` INTEGER,
                `typicalLowTimeOfDayMinutes` INTEGER,
                `avgVolumeConcentrationOpenPct` REAL NOT NULL,
                `avgVolumeConcentrationClosePct` REAL NOT NULL,
                `detailsJson` TEXT,
                `computedAt` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `createdBy` TEXT NOT NULL,
                `updatedBy` TEXT NOT NULL,
                `version` INTEGER NOT NULL,
                FOREIGN KEY(`instrumentId`) REFERENCES `instruments`(`instrumentId`) ON UPDATE CASCADE ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_dna_session_behavior_profiles_uuid` ON `dna_session_behavior_profiles` (`uuid`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_dna_session_behavior_profiles_instrumentId` ON `dna_session_behavior_profiles` (`instrumentId`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `dna_trend_persistence_profiles` (
                `profileId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `uuid` TEXT NOT NULL,
                `instrumentId` INTEGER NOT NULL,
                `timeframe` TEXT NOT NULL,
                `avgTrendDurationBars` REAL NOT NULL,
                `avgTrendMagnitudePct` REAL NOT NULL,
                `meanReversionTendencyScore` REAL NOT NULL,
                `trendingPctOfTime` REAL NOT NULL,
                `detailsJson` TEXT,
                `computedAt` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `createdBy` TEXT NOT NULL,
                `updatedBy` TEXT NOT NULL,
                `version` INTEGER NOT NULL,
                FOREIGN KEY(`instrumentId`) REFERENCES `instruments`(`instrumentId`) ON UPDATE CASCADE ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_dna_trend_persistence_profiles_uuid` ON `dna_trend_persistence_profiles` (`uuid`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `idx_dna_trend_unique` ON `dna_trend_persistence_profiles` (`instrumentId`, `timeframe`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `dna_liquidity_profiles` (
                `profileId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `uuid` TEXT NOT NULL,
                `instrumentId` INTEGER NOT NULL,
                `avgDailyVolume` INTEGER NOT NULL,
                `avgDailyOpenInterest` INTEGER,
                `volumeStabilityScore` REAL NOT NULL,
                `liquidityScore` REAL NOT NULL,
                `illiquidSessionPct` REAL NOT NULL,
                `detailsJson` TEXT,
                `computedAt` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `createdBy` TEXT NOT NULL,
                `updatedBy` TEXT NOT NULL,
                `version` INTEGER NOT NULL,
                FOREIGN KEY(`instrumentId`) REFERENCES `instruments`(`instrumentId`) ON UPDATE CASCADE ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_dna_liquidity_profiles_uuid` ON `dna_liquidity_profiles` (`uuid`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_dna_liquidity_profiles_instrumentId` ON `dna_liquidity_profiles` (`instrumentId`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `dna_gap_behavior_profiles` (
                `profileId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `uuid` TEXT NOT NULL,
                `instrumentId` INTEGER NOT NULL,
                `avgGapPct` REAL NOT NULL,
                `gapFrequencyPct` REAL NOT NULL,
                `gapFillRatePct` REAL NOT NULL,
                `avgGapFillDurationBars` REAL,
                `detailsJson` TEXT,
                `computedAt` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `createdBy` TEXT NOT NULL,
                `updatedBy` TEXT NOT NULL,
                `version` INTEGER NOT NULL,
                FOREIGN KEY(`instrumentId`) REFERENCES `instruments`(`instrumentId`) ON UPDATE CASCADE ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_dna_gap_behavior_profiles_uuid` ON `dna_gap_behavior_profiles` (`uuid`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_dna_gap_behavior_profiles_instrumentId` ON `dna_gap_behavior_profiles` (`instrumentId`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `dna_seasonal_tendencies` (
                `tendencyId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `uuid` TEXT NOT NULL,
                `instrumentId` INTEGER NOT NULL,
                `bucketType` TEXT NOT NULL,
                `bucketValue` TEXT NOT NULL,
                `avgReturnPct` REAL NOT NULL,
                `positiveOccurrencePct` REAL NOT NULL,
                `sampleSize` INTEGER NOT NULL,
                `detailsJson` TEXT,
                `computedAt` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `createdBy` TEXT NOT NULL,
                `updatedBy` TEXT NOT NULL,
                `version` INTEGER NOT NULL,
                FOREIGN KEY(`instrumentId`) REFERENCES `instruments`(`instrumentId`) ON UPDATE CASCADE ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_dna_seasonal_tendencies_uuid` ON `dna_seasonal_tendencies` (`uuid`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `idx_dna_seasonal_unique` ON `dna_seasonal_tendencies` (`instrumentId`, `bucketType`, `bucketValue`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `dna_indicator_behavior_profiles` (
                `profileId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `uuid` TEXT NOT NULL,
                `instrumentId` INTEGER NOT NULL,
                `indicatorDefId` INTEGER NOT NULL,
                `timeframe` TEXT NOT NULL,
                `meanReversionAfterExtremePct` REAL NOT NULL,
                `avgReactionMagnitudePct` REAL NOT NULL,
                `falseSignalRatePct` REAL NOT NULL,
                `detailsJson` TEXT,
                `computedAt` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `createdBy` TEXT NOT NULL,
                `updatedBy` TEXT NOT NULL,
                `version` INTEGER NOT NULL,
                FOREIGN KEY(`instrumentId`) REFERENCES `instruments`(`instrumentId`) ON UPDATE CASCADE ON DELETE CASCADE,
                FOREIGN KEY(`indicatorDefId`) REFERENCES `indicator_definitions`(`indicatorDefId`) ON UPDATE CASCADE ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_dna_indicator_behavior_profiles_uuid` ON `dna_indicator_behavior_profiles` (`uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_dna_indicator_behavior_profiles_indicatorDefId` ON `dna_indicator_behavior_profiles` (`indicatorDefId`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `idx_dna_indicator_behavior_unique` ON `dna_indicator_behavior_profiles` (`instrumentId`, `indicatorDefId`, `timeframe`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `dna_statistical_characteristics` (
                `characteristicId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `uuid` TEXT NOT NULL,
                `instrumentId` INTEGER NOT NULL,
                `timeframe` TEXT NOT NULL,
                `returnSkewness` REAL NOT NULL,
                `returnKurtosis` REAL NOT NULL,
                `autocorrelationLag1` REAL NOT NULL,
                `sampleSize` INTEGER NOT NULL,
                `detailsJson` TEXT,
                `computedAt` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `createdBy` TEXT NOT NULL,
                `updatedBy` TEXT NOT NULL,
                `version` INTEGER NOT NULL,
                FOREIGN KEY(`instrumentId`) REFERENCES `instruments`(`instrumentId`) ON UPDATE CASCADE ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_dna_statistical_characteristics_uuid` ON `dna_statistical_characteristics` (`uuid`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `idx_dna_statistical_unique` ON `dna_statistical_characteristics` (`instrumentId`, `timeframe`)")

        // ============================== EVIDENCE FOUNDATION ==============================

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `market_observations` (
                `observationId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `uuid` TEXT NOT NULL,
                `instrumentId` INTEGER NOT NULL,
                `timeframe` TEXT NOT NULL,
                `timestamp` INTEGER NOT NULL,
                `observationType` TEXT NOT NULL,
                `description` TEXT NOT NULL,
                `source` TEXT NOT NULL,
                `detailsJson` TEXT,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `createdBy` TEXT NOT NULL,
                `updatedBy` TEXT NOT NULL,
                `version` INTEGER NOT NULL,
                FOREIGN KEY(`instrumentId`) REFERENCES `instruments`(`instrumentId`) ON UPDATE CASCADE ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_market_observations_uuid` ON `market_observations` (`uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_market_observations_instrumentId_timestamp` ON `market_observations` (`instrumentId`, `timestamp`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_market_observations_observationType` ON `market_observations` (`observationType`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `evidence_records` (
                `evidenceId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `uuid` TEXT NOT NULL,
                `instrumentId` INTEGER NOT NULL,
                `observationId` INTEGER,
                `timestamp` INTEGER NOT NULL,
                `evidenceType` TEXT NOT NULL,
                `strength` REAL NOT NULL,
                `description` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `createdBy` TEXT NOT NULL,
                `updatedBy` TEXT NOT NULL,
                `version` INTEGER NOT NULL,
                FOREIGN KEY(`instrumentId`) REFERENCES `instruments`(`instrumentId`) ON UPDATE CASCADE ON DELETE CASCADE,
                FOREIGN KEY(`observationId`) REFERENCES `market_observations`(`observationId`) ON UPDATE CASCADE ON DELETE SET NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_evidence_records_uuid` ON `evidence_records` (`uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_evidence_records_instrumentId_timestamp` ON `evidence_records` (`instrumentId`, `timestamp`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_evidence_records_observationId` ON `evidence_records` (`observationId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_evidence_records_evidenceType` ON `evidence_records` (`evidenceType`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `pattern_occurrences` (
                `occurrenceId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `uuid` TEXT NOT NULL,
                `instrumentId` INTEGER NOT NULL,
                `timeframe` TEXT NOT NULL,
                `patternName` TEXT NOT NULL,
                `startTimestamp` INTEGER NOT NULL,
                `endTimestamp` INTEGER,
                `matchConfidence` REAL NOT NULL,
                `outcome` TEXT NOT NULL,
                `outcomeNotes` TEXT,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `createdBy` TEXT NOT NULL,
                `updatedBy` TEXT NOT NULL,
                `version` INTEGER NOT NULL,
                FOREIGN KEY(`instrumentId`) REFERENCES `instruments`(`instrumentId`) ON UPDATE CASCADE ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_pattern_occurrences_uuid` ON `pattern_occurrences` (`uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_pattern_occurrences_instrumentId_timeframe_startTimestamp` ON `pattern_occurrences` (`instrumentId`, `timeframe`, `startTimestamp`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_pattern_occurrences_patternName` ON `pattern_occurrences` (`patternName`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_pattern_occurrences_outcome` ON `pattern_occurrences` (`outcome`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `supporting_indicators` (
                `supportingIndicatorId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `evidenceId` INTEGER NOT NULL,
                `indicatorValueId` INTEGER NOT NULL,
                `contributionWeight` REAL NOT NULL,
                `notes` TEXT,
                FOREIGN KEY(`evidenceId`) REFERENCES `evidence_records`(`evidenceId`) ON UPDATE CASCADE ON DELETE CASCADE,
                FOREIGN KEY(`indicatorValueId`) REFERENCES `indicator_values`(`indicatorValueId`) ON UPDATE CASCADE ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_supporting_indicators_evidenceId` ON `supporting_indicators` (`evidenceId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_supporting_indicators_indicatorValueId` ON `supporting_indicators` (`indicatorValueId`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `confidence_components` (
                `componentId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `evidenceId` INTEGER NOT NULL,
                `componentName` TEXT NOT NULL,
                `weight` REAL NOT NULL,
                `score` REAL NOT NULL,
                `rationale` TEXT NOT NULL,
                FOREIGN KEY(`evidenceId`) REFERENCES `evidence_records`(`evidenceId`) ON UPDATE CASCADE ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_confidence_components_evidenceId` ON `confidence_components` (`evidenceId`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `source_references` (
                `sourceRefId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `evidenceId` INTEGER NOT NULL,
                `sourceType` TEXT NOT NULL,
                `sourceRowId` INTEGER,
                `description` TEXT,
                `url` TEXT,
                FOREIGN KEY(`evidenceId`) REFERENCES `evidence_records`(`evidenceId`) ON UPDATE CASCADE ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_source_references_evidenceId` ON `source_references` (`evidenceId`)")
    }
}
