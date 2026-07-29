package com.jarvis.tidb.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * MIGRATION_6_7 — TRADING-007A.1: News & Sentiment Intelligence Platform.
 *
 * Purely additive: 7 new tables under `com.jarvis.tidb.news`, plus two additive enum values
 * (`ProviderType.NEWS_FEED`, `IngestionJobType.NEWS_PULL` — enum values are String-persisted,
 * so they require no `ALTER TABLE`, only the Kotlin-side additions in
 * `historical/ingestion/entity/IngestionEntities.kt`). No existing table's shape changes. Every
 * `CREATE TABLE` uses `IF NOT EXISTS`, matching this project's "no destructive migrations"
 * invariant already followed by [MIGRATION_4_5] and [MIGRATION_5_6]. Every column, index, and
 * foreign key below was cross-checked against `news/entity/NewsEntities.kt` before delivery,
 * following the same cross-check discipline documented in
 * docs/database/TRADING-005-Historical-Market-Data-Platform.md §5. See
 * docs/database/TRADING-007A.1-News-Sentiment-Intelligence-Platform.md for full design notes
 * and the Evidence Engine reconciliation (this platform does NOT redefine evidence, confidence,
 * link, or outcome-tracking tables — it reuses Module 4/5's).
 */
val MIGRATION_6_7: Migration = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {

        // ============================== NEWS SOURCES ==============================

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `news_sources` (
                `sourceId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `uuid` TEXT NOT NULL,
                `sourceKey` TEXT NOT NULL,
                `displayName` TEXT NOT NULL,
                `tier` TEXT NOT NULL,
                `evidenceSourceCode` TEXT,
                `websiteUrl` TEXT,
                `isActive` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `createdBy` TEXT NOT NULL,
                `updatedBy` TEXT NOT NULL,
                `version` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_news_sources_uuid` ON `news_sources` (`uuid`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_news_sources_sourceKey` ON `news_sources` (`sourceKey`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_news_sources_isActive` ON `news_sources` (`isActive`)")

        // ============================== NEWS ARTICLES ==============================

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `news_articles` (
                `articleId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `uuid` TEXT NOT NULL,
                `sourceId` INTEGER NOT NULL,
                `externalArticleId` TEXT NOT NULL,
                `headline` TEXT NOT NULL,
                `summary` TEXT,
                `url` TEXT,
                `contentHash` TEXT NOT NULL,
                `language` TEXT NOT NULL,
                `publishedAt` INTEGER NOT NULL,
                `ingestedAt` INTEGER NOT NULL,
                `status` TEXT NOT NULL,
                `contentCompleteness` TEXT NOT NULL,
                `importanceScore` REAL,
                `correctsArticleId` INTEGER,
                `rawPayloadJson` TEXT,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `createdBy` TEXT NOT NULL,
                `updatedBy` TEXT NOT NULL,
                `version` INTEGER NOT NULL,
                `isDeleted` INTEGER NOT NULL,
                `deletedAt` INTEGER,
                FOREIGN KEY(`sourceId`) REFERENCES `news_sources`(`sourceId`) ON UPDATE CASCADE ON DELETE RESTRICT,
                FOREIGN KEY(`correctsArticleId`) REFERENCES `news_articles`(`articleId`) ON UPDATE CASCADE ON DELETE SET NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_news_articles_uuid` ON `news_articles` (`uuid`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_news_articles_sourceId_externalArticleId` ON `news_articles` (`sourceId`, `externalArticleId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_news_articles_publishedAt` ON `news_articles` (`publishedAt`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_news_articles_sourceId_publishedAt` ON `news_articles` (`sourceId`, `publishedAt`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_news_articles_contentHash` ON `news_articles` (`contentHash`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_news_articles_correctsArticleId` ON `news_articles` (`correctsArticleId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_news_articles_status` ON `news_articles` (`status`)")

        // ============================== NEWS CATEGORIES ==============================

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `news_categories` (
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
                FOREIGN KEY(`parentCategoryId`) REFERENCES `news_categories`(`categoryId`) ON UPDATE CASCADE ON DELETE SET NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_news_categories_uuid` ON `news_categories` (`uuid`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_news_categories_code` ON `news_categories` (`code`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_news_categories_parentCategoryId` ON `news_categories` (`parentCategoryId`)")

        // ============================== NEWS CATEGORY LINKS ==============================

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `news_category_links` (
                `linkId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `uuid` TEXT NOT NULL,
                `articleId` INTEGER NOT NULL,
                `categoryId` INTEGER NOT NULL,
                `relevanceWeight` REAL NOT NULL,
                `linkedAt` INTEGER NOT NULL,
                FOREIGN KEY(`articleId`) REFERENCES `news_articles`(`articleId`) ON UPDATE CASCADE ON DELETE CASCADE,
                FOREIGN KEY(`categoryId`) REFERENCES `news_categories`(`categoryId`) ON UPDATE CASCADE ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_news_category_links_uuid` ON `news_category_links` (`uuid`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_news_category_links_articleId_categoryId` ON `news_category_links` (`articleId`, `categoryId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_news_category_links_categoryId` ON `news_category_links` (`categoryId`)")

        // ============================== NEWS INSTRUMENT LINKS ==============================

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `news_instrument_links` (
                `linkId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `uuid` TEXT NOT NULL,
                `articleId` INTEGER NOT NULL,
                `instrumentId` INTEGER NOT NULL,
                `contractId` INTEGER,
                `sector` TEXT,
                `relevanceScore` REAL NOT NULL,
                `createdAt` INTEGER NOT NULL,
                FOREIGN KEY(`articleId`) REFERENCES `news_articles`(`articleId`) ON UPDATE CASCADE ON DELETE CASCADE,
                FOREIGN KEY(`instrumentId`) REFERENCES `instruments`(`instrumentId`) ON UPDATE CASCADE ON DELETE CASCADE,
                FOREIGN KEY(`contractId`) REFERENCES `contracts`(`contractId`) ON UPDATE CASCADE ON DELETE SET NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_news_instrument_links_uuid` ON `news_instrument_links` (`uuid`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_news_instrument_links_articleId_instrumentId` ON `news_instrument_links` (`articleId`, `instrumentId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_news_instrument_links_instrumentId_articleId` ON `news_instrument_links` (`instrumentId`, `articleId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_news_instrument_links_contractId` ON `news_instrument_links` (`contractId`)")

        // ============================== SENTIMENT SCORES ==============================

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `sentiment_scores` (
                `scoreId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `uuid` TEXT NOT NULL,
                `articleId` INTEGER NOT NULL,
                `instrumentLinkId` INTEGER,
                `modelKey` TEXT NOT NULL,
                `modelVersion` TEXT NOT NULL,
                `score` REAL NOT NULL,
                `label` TEXT NOT NULL,
                `confidence` REAL NOT NULL,
                `reason` TEXT,
                `rawModelOutputJson` TEXT,
                `computedAt` INTEGER NOT NULL,
                FOREIGN KEY(`articleId`) REFERENCES `news_articles`(`articleId`) ON UPDATE CASCADE ON DELETE CASCADE,
                FOREIGN KEY(`instrumentLinkId`) REFERENCES `news_instrument_links`(`linkId`) ON UPDATE CASCADE ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_sentiment_scores_uuid` ON `sentiment_scores` (`uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sentiment_scores_articleId_modelKey` ON `sentiment_scores` (`articleId`, `modelKey`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sentiment_scores_instrumentLinkId` ON `sentiment_scores` (`instrumentLinkId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sentiment_scores_computedAt` ON `sentiment_scores` (`computedAt`)")

        // ============================== NEWS DUPLICATES (optional per blueprint, included) ==============================

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `news_duplicates` (
                `duplicateId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `uuid` TEXT NOT NULL,
                `primaryArticleId` INTEGER NOT NULL,
                `duplicateArticleId` INTEGER NOT NULL,
                `similarityScore` REAL NOT NULL,
                `detectionMethod` TEXT NOT NULL,
                `detectedAt` INTEGER NOT NULL,
                FOREIGN KEY(`primaryArticleId`) REFERENCES `news_articles`(`articleId`) ON UPDATE CASCADE ON DELETE CASCADE,
                FOREIGN KEY(`duplicateArticleId`) REFERENCES `news_articles`(`articleId`) ON UPDATE CASCADE ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_news_duplicates_uuid` ON `news_duplicates` (`uuid`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_news_duplicates_primaryArticleId_duplicateArticleId` ON `news_duplicates` (`primaryArticleId`, `duplicateArticleId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_news_duplicates_duplicateArticleId` ON `news_duplicates` (`duplicateArticleId`)")
    }
}
