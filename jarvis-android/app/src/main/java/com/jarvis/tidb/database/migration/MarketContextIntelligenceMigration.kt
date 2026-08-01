package com.jarvis.tidb.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * MIGRATION_7_8 — TRADING-007A.2: Market Context Intelligence Platform.
 *
 * Purely additive: 7 new tables under `com.jarvis.tidb.context` (5 `economic_event_*` tables
 * plus the standalone `drift_metrics` / `calibration_metrics` polymorphic monitoring tables — see
 * the naming note in `context/entity/MarketContextIntelligenceEntities.kt`'s file doc for why
 * the latter two are not `economic_event_`-prefixed). No existing table's shape changes. Every
 * `CREATE TABLE` uses `IF NOT EXISTS`, matching this project's "no destructive migrations"
 * invariant already followed by [MIGRATION_4_5], [MIGRATION_5_6], and [MIGRATION_6_7]. Every
 * column, index, and foreign key below was cross-checked against
 * `context/entity/MarketContextIntelligenceEntities.kt` before delivery, following the same
 * cross-check discipline documented in
 * docs/database/TRADING-005-Historical-Market-Data-Platform.md §5. See
 * docs/database/TRADING-007A.2-Market-Context-Intelligence-Platform.md for full design notes.
 */
val MIGRATION_7_8: Migration = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {

        // ============================== ECONOMIC EVENTS ==============================

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `economic_events` (
                `eventId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `uuid` TEXT NOT NULL,
                `eventKey` TEXT NOT NULL,
                `displayName` TEXT NOT NULL,
                `countryCode` TEXT,
                `importance` TEXT NOT NULL,
                `status` TEXT NOT NULL,
                `scheduledAt` INTEGER NOT NULL,
                `actualReleasedAt` INTEGER,
                `providerCode` TEXT,
                `externalEventId` TEXT,
                `description` TEXT,
                `rawPayloadJson` TEXT,
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
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_economic_events_uuid` ON `economic_events` (`uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_economic_events_eventKey` ON `economic_events` (`eventKey`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_economic_events_scheduledAt` ON `economic_events` (`scheduledAt`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_economic_events_status` ON `economic_events` (`status`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_economic_events_providerCode_externalEventId` ON `economic_events` (`providerCode`, `externalEventId`)")

        // ============================== ECONOMIC EVENT CATEGORIES ==============================

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `economic_event_categories` (
                `categoryId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `uuid` TEXT NOT NULL,
                `code` TEXT NOT NULL,
                `displayName` TEXT NOT NULL,
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
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_economic_event_categories_uuid` ON `economic_event_categories` (`uuid`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_economic_event_categories_code` ON `economic_event_categories` (`code`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_economic_event_categories_isActive` ON `economic_event_categories` (`isActive`)")

        // ============================== ECONOMIC EVENT CATEGORY LINKS ==============================

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `economic_event_category_links` (
                `linkId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `uuid` TEXT NOT NULL,
                `eventId` INTEGER NOT NULL,
                `categoryId` INTEGER NOT NULL,
                `linkedAt` INTEGER NOT NULL,
                FOREIGN KEY(`eventId`) REFERENCES `economic_events`(`eventId`) ON UPDATE CASCADE ON DELETE CASCADE,
                FOREIGN KEY(`categoryId`) REFERENCES `economic_event_categories`(`categoryId`) ON UPDATE CASCADE ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_economic_event_category_links_uuid` ON `economic_event_category_links` (`uuid`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_economic_event_category_links_eventId_categoryId` ON `economic_event_category_links` (`eventId`, `categoryId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_economic_event_category_links_categoryId` ON `economic_event_category_links` (`categoryId`)")

        // ============================== ECONOMIC EVENT INSTRUMENT LINKS ==============================

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `economic_event_instrument_links` (
                `linkId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `uuid` TEXT NOT NULL,
                `eventId` INTEGER NOT NULL,
                `scope` TEXT NOT NULL,
                `instrumentId` INTEGER,
                `contractId` INTEGER,
                `scopeLabel` TEXT,
                `relevanceScore` REAL NOT NULL,
                `createdAt` INTEGER NOT NULL,
                FOREIGN KEY(`eventId`) REFERENCES `economic_events`(`eventId`) ON UPDATE CASCADE ON DELETE CASCADE,
                FOREIGN KEY(`instrumentId`) REFERENCES `instruments`(`instrumentId`) ON UPDATE CASCADE ON DELETE CASCADE,
                FOREIGN KEY(`contractId`) REFERENCES `contracts`(`contractId`) ON UPDATE CASCADE ON DELETE SET NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_economic_event_instrument_links_uuid` ON `economic_event_instrument_links` (`uuid`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_economic_event_instrument_links_eventId_scope_instrumentId_scopeLabel` ON `economic_event_instrument_links` (`eventId`, `scope`, `instrumentId`, `scopeLabel`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_economic_event_instrument_links_instrumentId_eventId` ON `economic_event_instrument_links` (`instrumentId`, `eventId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_economic_event_instrument_links_contractId` ON `economic_event_instrument_links` (`contractId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_economic_event_instrument_links_scope_scopeLabel` ON `economic_event_instrument_links` (`scope`, `scopeLabel`)")

        // ============================== ECONOMIC EVENT OUTCOMES ==============================

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `economic_event_outcomes` (
                `outcomeId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `uuid` TEXT NOT NULL,
                `eventId` INTEGER NOT NULL,
                `unit` TEXT,
                `expectedValue` REAL,
                `actualValue` REAL,
                `previousValue` REAL,
                `revisedPreviousValue` REAL,
                `surprise` REAL,
                `marketReactionSummary` TEXT,
                `revisesOutcomeId` INTEGER,
                `recordedAt` INTEGER NOT NULL,
                FOREIGN KEY(`eventId`) REFERENCES `economic_events`(`eventId`) ON UPDATE CASCADE ON DELETE CASCADE,
                FOREIGN KEY(`revisesOutcomeId`) REFERENCES `economic_event_outcomes`(`outcomeId`) ON UPDATE CASCADE ON DELETE SET NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_economic_event_outcomes_uuid` ON `economic_event_outcomes` (`uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_economic_event_outcomes_eventId` ON `economic_event_outcomes` (`eventId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_economic_event_outcomes_eventId_recordedAt` ON `economic_event_outcomes` (`eventId`, `recordedAt`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_economic_event_outcomes_revisesOutcomeId` ON `economic_event_outcomes` (`revisesOutcomeId`)")

        // ============================== DRIFT METRICS ==============================

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `drift_metrics` (
                `driftId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `uuid` TEXT NOT NULL,
                `subjectType` TEXT NOT NULL,
                `subjectRowId` INTEGER NOT NULL,
                `metricKey` TEXT NOT NULL,
                `baselineValue` REAL NOT NULL,
                `currentValue` REAL NOT NULL,
                `driftScore` REAL NOT NULL,
                `severity` TEXT NOT NULL,
                `windowStart` INTEGER NOT NULL,
                `windowEnd` INTEGER NOT NULL,
                `notes` TEXT,
                `measuredAt` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_drift_metrics_uuid` ON `drift_metrics` (`uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_drift_metrics_subjectType_subjectRowId` ON `drift_metrics` (`subjectType`, `subjectRowId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_drift_metrics_subjectType_subjectRowId_metricKey_measuredAt` ON `drift_metrics` (`subjectType`, `subjectRowId`, `metricKey`, `measuredAt`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_drift_metrics_severity` ON `drift_metrics` (`severity`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_drift_metrics_measuredAt` ON `drift_metrics` (`measuredAt`)")

        // ============================== CALIBRATION METRICS ==============================

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `calibration_metrics` (
                `calibrationId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `uuid` TEXT NOT NULL,
                `subjectType` TEXT NOT NULL,
                `subjectRowId` INTEGER NOT NULL,
                `metricType` TEXT NOT NULL,
                `expectedValue` REAL NOT NULL,
                `observedValue` REAL NOT NULL,
                `sampleSize` INTEGER NOT NULL,
                `calibrationError` REAL NOT NULL,
                `windowStart` INTEGER NOT NULL,
                `windowEnd` INTEGER NOT NULL,
                `triggeredRecalibration` INTEGER NOT NULL,
                `notes` TEXT,
                `measuredAt` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_calibration_metrics_uuid` ON `calibration_metrics` (`uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_calibration_metrics_subjectType_subjectRowId` ON `calibration_metrics` (`subjectType`, `subjectRowId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_calibration_metrics_subjectType_subjectRowId_metricType_measuredAt` ON `calibration_metrics` (`subjectType`, `subjectRowId`, `metricType`, `measuredAt`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_calibration_metrics_metricType` ON `calibration_metrics` (`metricType`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_calibration_metrics_measuredAt` ON `calibration_metrics` (`measuredAt`)")
    }
}
