package com.jarvis.tidb.news.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.jarvis.tidb.core.common.AuditMetadata
import com.jarvis.tidb.core.common.GlobalId
import com.jarvis.tidb.core.common.SoftDeleteMetadata
import com.jarvis.tidb.core.entity.ContractEntity
import com.jarvis.tidb.core.entity.InstrumentEntity
import com.jarvis.tidb.intelligence.evidence.entity.EvidenceCategoryLevel

/**
 * TRADING-007A.1 — News & Sentiment Intelligence Platform (schema v7).
 *
 * This is an EXTENSION of the Evidence Engine (Module 4/5), not a parallel subsystem. Per the
 * approved architecture blueprint, a news article does not get its own evidence, confidence,
 * link, or outcome-tracking tables — once an article is judged citable, it is promoted into a
 * standard [com.jarvis.tidb.historical.evidence.entity.EvidenceRecordEntity] (one per relevant
 * instrument, since evidence records are single-instrument-scoped) via a standard
 * [com.jarvis.tidb.historical.evidence.entity.SourceReferenceEntity]
 * (`sourceType = "NEWS_ARTICLE"`, `sourceRowId = articleId`), then linked/scored/tracked using
 * the exact same [com.jarvis.tidb.intelligence.evidence.entity.EvidenceLinkEntity] /
 * [com.jarvis.tidb.intelligence.confidence.entity.ConfidenceScoreEntity] /
 * [com.jarvis.tidb.intelligence.evidence.entity.EvidenceOutcomeEntity] machinery every other
 * evidence source already uses. Nothing in this file redefines evidence, confidence, linking,
 * or outcome concepts — see `docs/database/TRADING-007A.1-News-Sentiment-Intelligence-Platform.md`
 * §1 for the full reconciliation, mirroring the reconciliation section every prior module
 * (TRADING-005 §0, TRADING-006 §1) has included before it.
 *
 * Only 7 new tables, all pure storage — consistent with "nothing here scores, ranks, or
 * recommends" (the same principle stated verbatim in `historical.dna` and `historical.evidence`).
 * Sentiment scoring itself is an external/compute concern; this package only stores the result.
 */

/** How much a source's classification is externally verified vs. self-published. Distinct from [com.jarvis.tidb.intelligence.evidence.entity.EvidenceSourceKind], which is coarser (SYSTEM_ENGINE/EXTERNAL_FEED/MANUAL_ENTRY/AI_INFERENCE) — this is news-domain-specific granularity. */
enum class NewsSourceTier(val value: String) {
    WIRE_SERVICE("WIRE_SERVICE"),
    REGULATORY_GOVERNMENT("REGULATORY_GOVERNMENT"),
    EXCHANGE_CIRCULAR("EXCHANGE_CIRCULAR"),
    AGGREGATOR("AGGREGATOR"),
    ANALYST_REPORT("ANALYST_REPORT"),
    SOCIAL("SOCIAL"),
    RSS_UNVERIFIED("RSS_UNVERIFIED");

    companion object {
        fun from(value: String): NewsSourceTier = entries.firstOrNull { it.value == value } ?: RSS_UNVERIFIED
    }
}

/**
 * A named, reusable publication/feed registry entry — the specific-publication grain that
 * [com.jarvis.tidb.intelligence.evidence.entity.EvidenceSourceEntity] deliberately does not
 * cover (that table's `EXTERNAL_FEED` kind is a broad category, not "which publication").
 *
 * `evidenceSourceCode` is a logical-only reference to
 * `intelligence.evidence.entity.EvidenceSourceEntity.sourceCode` — not a Room `@ForeignKey`,
 * matching the precedent already set by `graph.entity.MarketContextEntity.regimeId`
 * (cross-sub-package references stay logical to avoid a compile-time dependency between sibling
 * packages, even though both live in the same Gradle module). The actual trust multiplier used
 * in confidence composition lives on the referenced `EvidenceSourceEntity` row, not here — this
 * table is identity/registry only.
 */
@Entity(
    tableName = "news_sources",
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["sourceKey"], unique = true),
        Index(value = ["isActive"])
    ]
)
data class NewsSourceEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "sourceId") val sourceId: Long = 0L,
    @ColumnInfo(name = "uuid") val uuid: String = GlobalId.new(),
    /** Stable machine slug, e.g. "reuters", "trading_economics", "mcx_circulars". */
    @ColumnInfo(name = "sourceKey") val sourceKey: String,
    @ColumnInfo(name = "displayName") val displayName: String,
    @ColumnInfo(name = "tier") val tier: NewsSourceTier,
    /** Logical-only reference to `evidence_sources.sourceCode` — see class doc. Nullable: a source can be registered here before it's been onboarded into the evidence-trust layer. */
    @ColumnInfo(name = "evidenceSourceCode") val evidenceSourceCode: String? = null,
    @ColumnInfo(name = "websiteUrl") val websiteUrl: String? = null,
    @ColumnInfo(name = "isActive") val isActive: Boolean = true,
    @Embedded val audit: AuditMetadata = AuditMetadata()
)

/** Lifecycle status of an ingested article — mirrors the ingestion validation flow (§6.3 of the blueprint): PENDING until validated, QUARANTINED if it fails, SUPERSEDED if a later correction replaces it. */
enum class NewsArticleStatus(val value: String) {
    PENDING("PENDING"),
    VALIDATED("VALIDATED"),
    QUARANTINED("QUARANTINED"),
    SUPERSEDED("SUPERSEDED");

    companion object {
        fun from(value: String): NewsArticleStatus = entries.firstOrNull { it.value == value } ?: PENDING
    }
}

/** How complete the ingested content is — a headline-only stub shouldn't be scored with the same confidence as a full article. */
enum class NewsContentCompleteness(val value: String) {
    FULL("FULL"),
    SUMMARY_ONLY("SUMMARY_ONLY"),
    HEADLINE_ONLY("HEADLINE_ONLY");

    companion object {
        fun from(value: String): NewsContentCompleteness = entries.firstOrNull { it.value == value } ?: HEADLINE_ONLY
    }
}

/**
 * The immutable ingested content of one article. Never edited after ingestion — a correction is
 * a new row with [correctsArticleId] pointing back at the original, the same "supersede, don't
 * mutate" shape already used by `historical.candle.entity.CandleVersionEntity`. Carries
 * [SoftDeleteMetadata] (unlike the append-only decision/learning tables elsewhere in this
 * schema) because aged-out, non-evidentiary articles are exactly the kind of row this platform
 * expects to prune from the on-device store per the retention tiering in the approved blueprint
 * §9.4 — full history remains server-side in `jarvis-gateway-main`.
 */
@Entity(
    tableName = "news_articles",
    foreignKeys = [
        ForeignKey(entity = NewsSourceEntity::class, parentColumns = ["sourceId"], childColumns = ["sourceId"], onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.CASCADE),
        ForeignKey(entity = NewsArticleEntity::class, parentColumns = ["articleId"], childColumns = ["correctsArticleId"], onDelete = ForeignKey.SET_NULL, onUpdate = ForeignKey.CASCADE)
    ],
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["sourceId", "externalArticleId"], unique = true),
        Index(value = ["publishedAt"]),
        Index(value = ["sourceId", "publishedAt"]),
        Index(value = ["contentHash"]),
        Index(value = ["correctsArticleId"]),
        Index(value = ["status"])
    ]
)
data class NewsArticleEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "articleId") val articleId: Long = 0L,
    @ColumnInfo(name = "uuid") val uuid: String = GlobalId.new(),
    @ColumnInfo(name = "sourceId") val sourceId: Long,
    /** The publisher/vendor's own article identifier, used with [sourceId] for exact-dedup at insert (unique composite index above). */
    @ColumnInfo(name = "externalArticleId") val externalArticleId: String,
    @ColumnInfo(name = "headline") val headline: String,
    /** A bounded summary, not necessarily full body text — see blueprint §9.6 on-device size guidance. Full content, if needed, lives server-side. */
    @ColumnInfo(name = "summary") val summary: String? = null,
    @ColumnInfo(name = "url") val url: String? = null,
    /** Content fingerprint (e.g. SHA-256 of normalized headline+summary) used for near-duplicate detection ahead of a full [NewsDuplicateEntity] similarity pass. */
    @ColumnInfo(name = "contentHash") val contentHash: String,
    /** ISO 639-1 code, e.g. "en". */
    @ColumnInfo(name = "language") val language: String = "en",
    @ColumnInfo(name = "publishedAt") val publishedAt: Long,
    @ColumnInfo(name = "ingestedAt") val ingestedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "status") val status: NewsArticleStatus = NewsArticleStatus.PENDING,
    @ColumnInfo(name = "contentCompleteness") val contentCompleteness: NewsContentCompleteness = NewsContentCompleteness.HEADLINE_ONLY,
    /**
     * A single scalar "how much does this matter" indicator, deliberately NOT a separate scored
     * facet/table — see blueprint §4.8. Distinct from sentiment (direction) and from
     * [NewsInstrumentLinkEntity.relevanceScore] (which instruments, how centrally). Nullable:
     * not every ingestion pipeline stage computes this immediately.
     */
    @ColumnInfo(name = "importanceScore") val importanceScore: Double? = null,
    /** Self-referential — points at the original article this row corrects. Null for original/uncorrected articles. */
    @ColumnInfo(name = "correctsArticleId") val correctsArticleId: Long? = null,
    @ColumnInfo(name = "rawPayloadJson") val rawPayloadJson: String? = null,
    @Embedded val audit: AuditMetadata = AuditMetadata(),
    @Embedded val softDelete: SoftDeleteMetadata = SoftDeleteMetadata()
)

/**
 * A hierarchical news classification taxonomy — a news-scoped instance of the exact same
 * ROOT/SUBCATEGORY shape [com.jarvis.tidb.intelligence.evidence.entity.EvidenceCategoryEntity]
 * already established. Deliberately does NOT reuse `evidence_categories` rows directly: news
 * categories (SUPPLY_DISRUPTION, WEATHER_EVENT, POLICY, INVENTORY, ...) are a different,
 * domain-specific vocabulary from evidence categories (PRICE_STRUCTURE, VOLUME_ANOMALY, ...),
 * even though the shape they're stored in is identical — hence reusing [EvidenceCategoryLevel]
 * (the enum) but not `evidence_categories` (the table).
 */
@Entity(
    tableName = "news_categories",
    foreignKeys = [
        ForeignKey(entity = NewsCategoryEntity::class, parentColumns = ["categoryId"], childColumns = ["parentCategoryId"], onDelete = ForeignKey.SET_NULL, onUpdate = ForeignKey.CASCADE)
    ],
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["code"], unique = true),
        Index(value = ["parentCategoryId"])
    ]
)
data class NewsCategoryEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "categoryId") val categoryId: Long = 0L,
    @ColumnInfo(name = "uuid") val uuid: String = GlobalId.new(),
    /** Stable machine slug, e.g. "SUPPLY_DISRUPTION", "WEATHER_EVENT", "POLICY", "INVENTORY", "MACRO", "CORPORATE". */
    @ColumnInfo(name = "code") val code: String,
    @ColumnInfo(name = "displayName") val displayName: String,
    @ColumnInfo(name = "description") val description: String? = null,
    @ColumnInfo(name = "level") val level: EvidenceCategoryLevel = EvidenceCategoryLevel.ROOT,
    @ColumnInfo(name = "parentCategoryId") val parentCategoryId: Long? = null,
    @ColumnInfo(name = "isActive") val isActive: Boolean = true,
    @Embedded val audit: AuditMetadata = AuditMetadata()
)

/** Many-to-many: one article routinely belongs to more than one category (e.g. both SUPPLY_DISRUPTION and GEOPOLITICAL). */
@Entity(
    tableName = "news_category_links",
    foreignKeys = [
        ForeignKey(entity = NewsArticleEntity::class, parentColumns = ["articleId"], childColumns = ["articleId"], onDelete = ForeignKey.CASCADE, onUpdate = ForeignKey.CASCADE),
        ForeignKey(entity = NewsCategoryEntity::class, parentColumns = ["categoryId"], childColumns = ["categoryId"], onDelete = ForeignKey.CASCADE, onUpdate = ForeignKey.CASCADE)
    ],
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["articleId", "categoryId"], unique = true),
        Index(value = ["categoryId"])
    ]
)
data class NewsCategoryLinkEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "linkId") val linkId: Long = 0L,
    @ColumnInfo(name = "uuid") val uuid: String = GlobalId.new(),
    @ColumnInfo(name = "articleId") val articleId: Long,
    @ColumnInfo(name = "categoryId") val categoryId: Long,
    /** How central this category is to the article, in [0.0, 1.0] — a passing mention scores lower than the article's primary topic. */
    @ColumnInfo(name = "relevanceWeight") val relevanceWeight: Double = 1.0,
    @ColumnInfo(name = "linkedAt") val linkedAt: Long = System.currentTimeMillis()
)

/**
 * Many-to-many: one article routinely moves several instruments (a USD-strength story touches
 * gold, silver, and several agri-commodities at once). This is the relevance-scope mapping every
 * other domain in this platform depends on — the "which instruments does this news item concern"
 * anchor. Optionally scoped to a specific [ContractEntity] (front-month vs. a specific expiry)
 * when the article is that granular; nullable because most news is instrument-level, not
 * contract-specific. [sector] is a free-text field rather than a new taxonomy table — this
 * schema has no existing sector concept to extend, and inventing one is out of scope for a
 * platform whose job is storing news, not building a sector classification system.
 */
@Entity(
    tableName = "news_instrument_links",
    foreignKeys = [
        ForeignKey(entity = NewsArticleEntity::class, parentColumns = ["articleId"], childColumns = ["articleId"], onDelete = ForeignKey.CASCADE, onUpdate = ForeignKey.CASCADE),
        ForeignKey(entity = InstrumentEntity::class, parentColumns = ["instrumentId"], childColumns = ["instrumentId"], onDelete = ForeignKey.CASCADE, onUpdate = ForeignKey.CASCADE),
        ForeignKey(entity = ContractEntity::class, parentColumns = ["contractId"], childColumns = ["contractId"], onDelete = ForeignKey.SET_NULL, onUpdate = ForeignKey.CASCADE)
    ],
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["articleId", "instrumentId"], unique = true),
        Index(value = ["instrumentId", "articleId"]),
        Index(value = ["contractId"])
    ]
)
data class NewsInstrumentLinkEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "linkId") val linkId: Long = 0L,
    @ColumnInfo(name = "uuid") val uuid: String = GlobalId.new(),
    @ColumnInfo(name = "articleId") val articleId: Long,
    @ColumnInfo(name = "instrumentId") val instrumentId: Long,
    @ColumnInfo(name = "contractId") val contractId: Long? = null,
    /** Free-text sector label (e.g. "Energy", "Precious Metals") — see class doc for why this isn't a new taxonomy table. */
    @ColumnInfo(name = "sector") val sector: String? = null,
    /** How central this instrument is to the article, in [0.0, 1.0] — not every mention is equal. */
    @ColumnInfo(name = "relevanceScore") val relevanceScore: Double,
    @ColumnInfo(name = "createdAt") val createdAt: Long = System.currentTimeMillis()
)

/** Coarse directional label alongside the continuous [SentimentScoreEntity.score] — convenient for filtering/display without re-deriving from the raw score. */
enum class SentimentLabel(val value: String) {
    VERY_NEGATIVE("VERY_NEGATIVE"),
    NEGATIVE("NEGATIVE"),
    NEUTRAL("NEUTRAL"),
    POSITIVE("POSITIVE"),
    VERY_POSITIVE("VERY_POSITIVE");

    companion object {
        fun from(value: String): SentimentLabel = entries.firstOrNull { it.value == value } ?: NEUTRAL
    }
}

/**
 * A scored opinion about an article (or an article+instrument pair), kept separate from
 * [NewsArticleEntity] so re-scoring on model upgrade never touches immutable ingested content —
 * "store, don't recompute" per the same principle already used by
 * `historical.indicator.entity.IndicatorValueEntity`. Rows are insert-only; a new model version
 * scoring the same article produces a new row rather than overwriting the old one, which is how
 * this table provides its own version history without a separate history table (mirrors why
 * `IndicatorComputationRunEntity` doesn't need one either).
 *
 * Deliberately NOT built on `intelligence.confidence.entity.ConfidenceScoreEntity` — see
 * `docs/database/TRADING-007A.1-News-Sentiment-Intelligence-Platform.md` §4.10 for the full
 * reasoning: sentiment (direction/magnitude of an opinion) and confidence (trust in a piece of
 * evidence) are different measurements on different objects, and `ConfidenceScoreEntity
 * .scoredEntityType` is a closed enum that doesn't include "news article." Once an article
 * becomes an `EvidenceRecordEntity`, that record can separately receive a real
 * `ConfidenceScoreEntity` — the two coexist.
 */
@Entity(
    tableName = "sentiment_scores",
    foreignKeys = [
        ForeignKey(entity = NewsArticleEntity::class, parentColumns = ["articleId"], childColumns = ["articleId"], onDelete = ForeignKey.CASCADE, onUpdate = ForeignKey.CASCADE),
        ForeignKey(entity = NewsInstrumentLinkEntity::class, parentColumns = ["linkId"], childColumns = ["instrumentLinkId"], onDelete = ForeignKey.CASCADE, onUpdate = ForeignKey.CASCADE)
    ],
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["articleId", "modelKey"]),
        Index(value = ["instrumentLinkId"]),
        Index(value = ["computedAt"])
    ]
)
data class SentimentScoreEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "scoreId") val scoreId: Long = 0L,
    @ColumnInfo(name = "uuid") val uuid: String = GlobalId.new(),
    @ColumnInfo(name = "articleId") val articleId: Long,
    /** Nullable: some scoring is article-level only; populated when sentiment was computed per-instrument (an article can read positive for one commodity and negative for another). */
    @ColumnInfo(name = "instrumentLinkId") val instrumentLinkId: Long? = null,
    /** Stable slug identifying the scoring methodology, e.g. "finnhub", "in_house_finbert_v2". */
    @ColumnInfo(name = "modelKey") val modelKey: String,
    @ColumnInfo(name = "modelVersion") val modelVersion: String,
    /** Normalized sentiment magnitude/direction in [-1.0, 1.0]; -1.0 maximally negative, +1.0 maximally positive. */
    @ColumnInfo(name = "score") val score: Double,
    @ColumnInfo(name = "label") val label: SentimentLabel,
    /** The scoring model's own confidence in this call, in [0.0, 1.0] — distinct from `ConfidenceScoreEntity`, which scores trust in evidence, not a model's self-reported certainty. */
    @ColumnInfo(name = "confidence") val confidence: Double,
    /** Short rationale/explanation string, if the scoring model provides one. */
    @ColumnInfo(name = "reason") val reason: String? = null,
    @ColumnInfo(name = "rawModelOutputJson") val rawModelOutputJson: String? = null,
    @ColumnInfo(name = "computedAt") val computedAt: Long = System.currentTimeMillis()
)

/**
 * A detected duplicate/syndication relationship between two articles — wire-service stories
 * republished by dozens of outlets. This is a detection record, not a rejection: syndicated
 * copies remain in [news_articles] (different framing/emphasis may still be worth having), but
 * flagged here so evidence-weighting doesn't double-count five outlets running the identical
 * story as five independent confirmations. Listed as OPTIONAL in the approved blueprint (§4.7,
 * §12 roadmap item 6) — included here as part of the full approved entity catalogue rather than
 * deferred, since it's a thin table with no dependency risk to build alongside the rest.
 */
@Entity(
    tableName = "news_duplicates",
    foreignKeys = [
        ForeignKey(entity = NewsArticleEntity::class, parentColumns = ["articleId"], childColumns = ["primaryArticleId"], onDelete = ForeignKey.CASCADE, onUpdate = ForeignKey.CASCADE),
        ForeignKey(entity = NewsArticleEntity::class, parentColumns = ["articleId"], childColumns = ["duplicateArticleId"], onDelete = ForeignKey.CASCADE, onUpdate = ForeignKey.CASCADE)
    ],
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["primaryArticleId", "duplicateArticleId"], unique = true),
        Index(value = ["duplicateArticleId"])
    ]
)
data class NewsDuplicateEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "duplicateId") val duplicateId: Long = 0L,
    @ColumnInfo(name = "uuid") val uuid: String = GlobalId.new(),
    /** The article treated as canonical/citable. */
    @ColumnInfo(name = "primaryArticleId") val primaryArticleId: Long,
    /** The syndicated/duplicate copy. */
    @ColumnInfo(name = "duplicateArticleId") val duplicateArticleId: Long,
    /** Similarity in [0.0, 1.0] from whatever detection pass found the match. */
    @ColumnInfo(name = "similarityScore") val similarityScore: Double,
    /** e.g. "CONTENT_HASH", "TITLE_FINGERPRINT" — which detection method flagged this pair. */
    @ColumnInfo(name = "detectionMethod") val detectionMethod: String,
    @ColumnInfo(name = "detectedAt") val detectedAt: Long = System.currentTimeMillis()
)
