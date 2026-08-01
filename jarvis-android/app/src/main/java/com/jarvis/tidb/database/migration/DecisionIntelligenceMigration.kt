package com.jarvis.tidb.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * MIGRATION_8_9 — TRADING-007B: Decision Intelligence Engine.
 *
 * Purely additive: 5 new tables under `com.jarvis.tidb.decision`. No existing table's shape
 * changes — the additive enum values this module introduces on `LinkedEntityType`,
 * `ScoredEntityType`, `ContextMonitoringSubjectType`, and `TimelineEventType` are all
 * String-persisted (or Room's built-in enum-as-name conversion for `TimelineEventType`), so
 * they require no `ALTER TABLE`, only the Kotlin-side enum additions in their respective entity
 * files. Every `CREATE TABLE` uses `IF NOT EXISTS`, matching this project's "no destructive
 * migrations" invariant already followed by [MIGRATION_4_5] through [MIGRATION_7_8]. Every
 * column, index, and foreign key below was cross-checked against
 * `decision/entity/DecisionIntelligenceEntities.kt` before delivery, following the same
 * cross-check discipline documented in
 * docs/database/TRADING-005-Historical-Market-Data-Platform.md §5. See
 * docs/database/TRADING-007B-Decision-Intelligence-Engine-Architecture.md for the approved
 * design this migration implements.
 */
val MIGRATION_8_9: Migration = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {

        // ============================== DECISION RECOMMENDATIONS ==============================

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `decision_recommendations` (
                `recommendationId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `uuid` TEXT NOT NULL,
                `instrumentId` INTEGER NOT NULL,
                `contractId` INTEGER,
                `recommendationType` TEXT NOT NULL,
                `status` TEXT NOT NULL,
                `confidenceScoreId` INTEGER,
                `strength` REAL,
                `timeHorizon` TEXT NOT NULL,
                `expectedOutcomeDescription` TEXT,
                `expectedTargetLevel` REAL,
                `expectedInvalidationLevel` REAL,
                `assumptionsJson` TEXT,
                `reasoningSummary` TEXT,
                `decidedAt` INTEGER NOT NULL,
                `expiresAt` INTEGER,
                `resolvedAt` INTEGER,
                `revisesRecommendationId` INTEGER,
                `linkedDecisionRecordId` INTEGER,
                `generatedBy` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `createdBy` TEXT NOT NULL,
                `updatedBy` TEXT NOT NULL,
                `version` INTEGER NOT NULL,
                `isDeleted` INTEGER NOT NULL,
                `deletedAt` INTEGER,
                FOREIGN KEY(`instrumentId`) REFERENCES `instruments`(`instrumentId`) ON UPDATE CASCADE ON DELETE CASCADE,
                FOREIGN KEY(`contractId`) REFERENCES `contracts`(`contractId`) ON UPDATE CASCADE ON DELETE SET NULL,
                FOREIGN KEY(`revisesRecommendationId`) REFERENCES `decision_recommendations`(`recommendationId`) ON UPDATE CASCADE ON DELETE SET NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_decision_recommendations_uuid` ON `decision_recommendations` (`uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_decision_recommendations_instrumentId` ON `decision_recommendations` (`instrumentId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_decision_recommendations_instrumentId_status` ON `decision_recommendations` (`instrumentId`, `status`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_decision_recommendations_status` ON `decision_recommendations` (`status`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_decision_recommendations_recommendationType` ON `decision_recommendations` (`recommendationType`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_decision_recommendations_expiresAt` ON `decision_recommendations` (`expiresAt`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_decision_recommendations_revisesRecommendationId` ON `decision_recommendations` (`revisesRecommendationId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_decision_recommendations_linkedDecisionRecordId` ON `decision_recommendations` (`linkedDecisionRecordId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_decision_recommendations_decidedAt` ON `decision_recommendations` (`decidedAt`)")

        // ============================== RECOMMENDATION RISK ASSESSMENTS ==============================

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `recommendation_risk_assessments` (
                `assessmentId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `uuid` TEXT NOT NULL,
                `recommendationId` INTEGER NOT NULL,
                `riskCategory` TEXT NOT NULL,
                `riskLevel` TEXT NOT NULL,
                `probability` REAL,
                `severity` REAL,
                `riskFactors` TEXT,
                `mitigation` TEXT,
                `assessedAt` INTEGER NOT NULL,
                FOREIGN KEY(`recommendationId`) REFERENCES `decision_recommendations`(`recommendationId`) ON UPDATE CASCADE ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_recommendation_risk_assessments_uuid` ON `recommendation_risk_assessments` (`uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_recommendation_risk_assessments_recommendationId_riskCategory` ON `recommendation_risk_assessments` (`recommendationId`, `riskCategory`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_recommendation_risk_assessments_riskCategory` ON `recommendation_risk_assessments` (`riskCategory`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_recommendation_risk_assessments_riskLevel` ON `recommendation_risk_assessments` (`riskLevel`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_recommendation_risk_assessments_assessedAt` ON `recommendation_risk_assessments` (`assessedAt`)")

        // ============================== RECOMMENDATION ALTERNATIVES ==============================

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `recommendation_alternatives` (
                `alternativeId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `uuid` TEXT NOT NULL,
                `recommendationId` INTEGER NOT NULL,
                `alternativeType` TEXT NOT NULL,
                `rejectionReason` TEXT NOT NULL,
                `relativeConfidenceScoreId` INTEGER,
                `consideredAt` INTEGER NOT NULL,
                FOREIGN KEY(`recommendationId`) REFERENCES `decision_recommendations`(`recommendationId`) ON UPDATE CASCADE ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_recommendation_alternatives_uuid` ON `recommendation_alternatives` (`uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_recommendation_alternatives_recommendationId` ON `recommendation_alternatives` (`recommendationId`)")

        // ============================== RECOMMENDATION OUTCOMES ==============================

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `recommendation_outcomes` (
                `outcomeId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `uuid` TEXT NOT NULL,
                `recommendationId` INTEGER NOT NULL,
                `verdict` TEXT NOT NULL,
                `actualMovePercent` REAL,
                `actualOutcomeDescription` TEXT,
                `performanceValue` REAL,
                `notes` TEXT,
                `evaluatedAt` INTEGER NOT NULL,
                FOREIGN KEY(`recommendationId`) REFERENCES `decision_recommendations`(`recommendationId`) ON UPDATE CASCADE ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_recommendation_outcomes_uuid` ON `recommendation_outcomes` (`uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_recommendation_outcomes_recommendationId` ON `recommendation_outcomes` (`recommendationId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_recommendation_outcomes_recommendationId_evaluatedAt` ON `recommendation_outcomes` (`recommendationId`, `evaluatedAt`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_recommendation_outcomes_verdict` ON `recommendation_outcomes` (`verdict`)")

        // ============================== DECISION REVIEWS ==============================

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `decision_reviews` (
                `reviewId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `uuid` TEXT NOT NULL,
                `recommendationId` INTEGER NOT NULL,
                `triggerType` TEXT NOT NULL,
                `triggerReferenceType` TEXT,
                `triggerReferenceRowId` INTEGER,
                `conclusion` TEXT NOT NULL,
                `resultingRecommendationId` INTEGER,
                `notes` TEXT,
                `reviewedAt` INTEGER NOT NULL,
                `reviewedBy` TEXT NOT NULL,
                FOREIGN KEY(`recommendationId`) REFERENCES `decision_recommendations`(`recommendationId`) ON UPDATE CASCADE ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_decision_reviews_uuid` ON `decision_reviews` (`uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_decision_reviews_recommendationId` ON `decision_reviews` (`recommendationId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_decision_reviews_triggerType` ON `decision_reviews` (`triggerType`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_decision_reviews_reviewedAt` ON `decision_reviews` (`reviewedAt`)")
    }
}
