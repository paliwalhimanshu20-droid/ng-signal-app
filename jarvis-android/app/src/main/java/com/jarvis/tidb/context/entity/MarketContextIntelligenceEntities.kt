package com.jarvis.tidb.context.entity

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

/**
 * TRADING-007A.2 — Market Context Intelligence Platform (schema v8).
 *
 * Approved architecture, implemented exactly as blueprinted: ~7 new tables split across two
 * concerns that both live under "market context" per the platform's own name —
 *
 *  1. **Economic events** ([EconomicEventEntity] + its category/link/outcome satellites) —
 *     structured macro calendar data (FOMC, CPI, GDP, PPI, EIA Storage, OPEC, employment
 *     reports, ...). Table names are `economic_event_*` throughout, per the approved naming
 *     rule (and deliberately NOT `MarketContextEntity`, which already exists as the TRADING-006
 *     Knowledge Graph's snapshot-context table — see reconciliation note on
 *     [EconomicEventEntity] below).
 *
 *  2. **Drift / calibration monitoring** ([DriftMetricEntity], [CalibrationMetricEntity]) —
 *     tracking whether the models, indicators, strategies, and sources this platform (and the
 *     Evidence Engine / Learning Platform it feeds) relies on are still behaving the way they
 *     were expected to. These are NOT economic-event-scoped, so they intentionally do not carry
 *     the `economic_event_` prefix; the naming rule in the approved blueprint applies to the
 *     economic-calendar tables specifically. Both single-table polymorphic designs, reusing the
 *     `subjectType` (typed enum) + `subjectRowId` (logical-only reference, no Room `@ForeignKey`
 *     since the referenced table varies per row) shape already established by
 *     `intelligence.confidence.entity.ConfidenceScoreEntity.scoredEntityType` /
 *     `scoredEntityRowId` and `intelligence.graph.entity.EntityRelationshipEntity`'s
 *     `fromEntityType`/`toEntityType`. No separate per-subject-type drift table is created —
 *     exactly as instructed ("Reuse existing polymorphic design patterns. Do NOT create separate
 *     drift tables.").
 *
 * This module is pure storage — nothing here is the Decision Engine, and nothing here scores,
 * ranks, or recommends, consistent with every prior module's stated principle (`historical.dna`,
 * `historical.evidence`, `news`). It provides context the Evidence Engine, Learning Platform,
 * and future Decision Engine consume; it does not consume them.
 *
 * No duplication of existing concepts: this file does not redefine evidence, signal, confidence,
 * market-regime, trading-session, holiday, or performance tables — see the Duplicate Entity
 * Audit in `docs/database/TRADING-007A.2-Market-Context-Intelligence-Platform.md` §7.
 *
 * Connector preparation only (per blueprint — "prepare for future connectors, do NOT implement
 * them"): [EconomicEventEntity.providerCode] and [EconomicEventEntity.externalEventId] are
 * logical-only fields sized for provider keys such as Trading Economics, FRED, EIA, MCX, NSE,
 * CME, Federal Reserve, RBI, NOAA, and IMD — no connector client code is introduced here.
 */

// ======================================================================================
// Economic Events
// ======================================================================================

/** Lifecycle status of a scheduled/completed macro event. */
enum class EconomicEventStatus(val value: String) {
    SCHEDULED("SCHEDULED"),
    COMPLETED("COMPLETED"),
    POSTPONED("POSTPONED"),
    CANCELLED("CANCELLED");

    companion object {
        fun from(value: String): EconomicEventStatus = entries.firstOrNull { it.value == value } ?: SCHEDULED
    }
}

/** Expected market-moving weight of the event — the standard economic-calendar "impact" tier. */
enum class EconomicEventImportance(val value: String) {
    LOW("LOW"),
    MEDIUM("MEDIUM"),
    HIGH("HIGH");

    companion object {
        fun from(value: String): EconomicEventImportance = entries.firstOrNull { it.value == value } ?: MEDIUM
    }
}

/**
 * A scheduled or completed macroeconomic event — FOMC, CPI, GDP, PPI, EIA Storage, OPEC
 * Meeting, Employment Report, and similar calendar items.
 *
 * Reconciliation: this is unrelated to
 * [com.jarvis.tidb.intelligence.graph.entity.MarketContextEntity] (TRADING-006 Knowledge Graph),
 * which is a point-in-time snapshot of regime/session/volatility conditions attached to a single
 * observation. [EconomicEventEntity] is a calendar entry for a real-world macro release —
 * different grain, different lifecycle, different owner. Per the approved naming rule this
 * module never reuses the `MarketContextEntity` name; every table here is `economic_event_*`.
 *
 * `providerCode` + `externalEventId` are logical-only connector-preparation fields (see file
 * doc) — no FK, since the provider registry this would eventually reference does not exist yet
 * and standing one up is explicitly out of scope for this phase.
 */
@Entity(
    tableName = "economic_events",
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["eventKey"]),
        Index(value = ["scheduledAt"]),
        Index(value = ["status"]),
        Index(value = ["providerCode", "externalEventId"], unique = true)
    ]
)
data class EconomicEventEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "eventId") val eventId: Long = 0L,
    @ColumnInfo(name = "uuid") val uuid: String = GlobalId.new(),
    /** Stable machine slug, e.g. "US_FOMC_RATE_DECISION", "US_CPI_MOM", "IN_RBI_POLICY". Not unique alone — the same recurring calendar item produces one row per occurrence. */
    @ColumnInfo(name = "eventKey") val eventKey: String,
    @ColumnInfo(name = "displayName") val displayName: String,
    /** ISO 3166-1 alpha-2, e.g. "US", "IN". Null for supranational events (e.g. OPEC meetings). */
    @ColumnInfo(name = "countryCode") val countryCode: String? = null,
    @ColumnInfo(name = "importance") val importance: EconomicEventImportance = EconomicEventImportance.MEDIUM,
    @ColumnInfo(name = "status") val status: EconomicEventStatus = EconomicEventStatus.SCHEDULED,
    /** When the event is/was scheduled to occur. */
    @ColumnInfo(name = "scheduledAt") val scheduledAt: Long,
    /** When it actually released, if different from [scheduledAt] (postponements) or once [status] reaches COMPLETED. Null while still SCHEDULED. */
    @ColumnInfo(name = "actualReleasedAt") val actualReleasedAt: Long? = null,
    /** Connector-preparation only — see class doc. e.g. "TRADING_ECONOMICS", "FRED", "EIA". */
    @ColumnInfo(name = "providerCode") val providerCode: String? = null,
    /** The provider's own event identifier, used with [providerCode] for exact-dedup on re-ingest. */
    @ColumnInfo(name = "externalEventId") val externalEventId: String? = null,
    @ColumnInfo(name = "description") val description: String? = null,
    @ColumnInfo(name = "rawPayloadJson") val rawPayloadJson: String? = null,
    @Embedded val audit: AuditMetadata = AuditMetadata(),
    @Embedded val softDelete: SoftDeleteMetadata = SoftDeleteMetadata()
)

/**
 * Controlled vocabulary for event classification (e.g. "MONETARY_POLICY", "INFLATION",
 * "EMPLOYMENT", "GROWTH", "ENERGY_SUPPLY", "OPEC"). Flat, not hierarchical — the approved
 * blueprint does not ask for a ROOT/SUBCATEGORY taxonomy here (contrast
 * [com.jarvis.tidb.news.entity.NewsCategoryEntity], which does have one); introducing hierarchy
 * would be an alternative design not covered by the approved architecture.
 */
@Entity(
    tableName = "economic_event_categories",
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["code"], unique = true),
        Index(value = ["isActive"])
    ]
)
data class EconomicEventCategoryEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "categoryId") val categoryId: Long = 0L,
    @ColumnInfo(name = "uuid") val uuid: String = GlobalId.new(),
    @ColumnInfo(name = "code") val code: String,
    @ColumnInfo(name = "displayName") val displayName: String,
    @ColumnInfo(name = "description") val description: String? = null,
    @ColumnInfo(name = "isActive") val isActive: Boolean = true,
    @Embedded val audit: AuditMetadata = AuditMetadata()
)

/** Many-to-many: one event routinely belongs to more than one category (e.g. an OPEC meeting is both ENERGY_SUPPLY and OPEC). */
@Entity(
    tableName = "economic_event_category_links",
    foreignKeys = [
        ForeignKey(entity = EconomicEventEntity::class, parentColumns = ["eventId"], childColumns = ["eventId"], onDelete = ForeignKey.CASCADE, onUpdate = ForeignKey.CASCADE),
        ForeignKey(entity = EconomicEventCategoryEntity::class, parentColumns = ["categoryId"], childColumns = ["categoryId"], onDelete = ForeignKey.CASCADE, onUpdate = ForeignKey.CASCADE)
    ],
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["eventId", "categoryId"], unique = true),
        Index(value = ["categoryId"])
    ]
)
data class EconomicEventCategoryLinkEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "linkId") val linkId: Long = 0L,
    @ColumnInfo(name = "uuid") val uuid: String = GlobalId.new(),
    @ColumnInfo(name = "eventId") val eventId: Long,
    @ColumnInfo(name = "categoryId") val categoryId: Long,
    @ColumnInfo(name = "linkedAt") val linkedAt: Long = System.currentTimeMillis()
)

/** Which kind of row [EconomicEventInstrumentLinkEntity.subjectRowId] resolves against, when applicable — see field docs on that entity. */
enum class EconomicEventLinkScope(val value: String) {
    INSTRUMENT("INSTRUMENT"),
    SECTOR("SECTOR"),
    ASSET_CLASS("ASSET_CLASS"),
    INDEX("INDEX");

    companion object {
        fun from(value: String): EconomicEventLinkScope = entries.firstOrNull { it.value == value } ?: INSTRUMENT
    }
}

/**
 * Maps an event to whatever it's relevant to — an [InstrumentEntity], a sector, an asset class,
 * or an index, per the approved blueprint's four mapping targets. [scope] selects which; for
 * `INSTRUMENT` the Room `@ForeignKey` on [instrumentId] (and optionally the more specific
 * [contractId]) applies, for the other three scopes [scopeLabel] carries the free-text name
 * (e.g. "Energy", "Precious Metals", "Nifty 50") — this schema has no existing sector/asset-
 * class/index taxonomy table to extend, matching the same reasoning
 * `news.entity.NewsInstrumentLinkEntity.sector` already documents.
 */
@Entity(
    tableName = "economic_event_instrument_links",
    foreignKeys = [
        ForeignKey(entity = EconomicEventEntity::class, parentColumns = ["eventId"], childColumns = ["eventId"], onDelete = ForeignKey.CASCADE, onUpdate = ForeignKey.CASCADE),
        ForeignKey(entity = InstrumentEntity::class, parentColumns = ["instrumentId"], childColumns = ["instrumentId"], onDelete = ForeignKey.CASCADE, onUpdate = ForeignKey.CASCADE),
        ForeignKey(entity = ContractEntity::class, parentColumns = ["contractId"], childColumns = ["contractId"], onDelete = ForeignKey.SET_NULL, onUpdate = ForeignKey.CASCADE)
    ],
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["eventId", "scope", "instrumentId", "scopeLabel"], unique = true),
        Index(value = ["instrumentId", "eventId"]),
        Index(value = ["contractId"]),
        Index(value = ["scope", "scopeLabel"])
    ]
)
data class EconomicEventInstrumentLinkEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "linkId") val linkId: Long = 0L,
    @ColumnInfo(name = "uuid") val uuid: String = GlobalId.new(),
    @ColumnInfo(name = "eventId") val eventId: Long,
    @ColumnInfo(name = "scope") val scope: EconomicEventLinkScope,
    /** Populated when [scope] == INSTRUMENT; null otherwise. */
    @ColumnInfo(name = "instrumentId") val instrumentId: Long? = null,
    /** Optional front-month/specific-expiry narrowing when [scope] == INSTRUMENT and the event is that granular. */
    @ColumnInfo(name = "contractId") val contractId: Long? = null,
    /** Free-text label populated when [scope] != INSTRUMENT (the sector/asset-class/index name). Null when [scope] == INSTRUMENT. */
    @ColumnInfo(name = "scopeLabel") val scopeLabel: String? = null,
    /** How central this mapping is to the event, in [0.0, 1.0]. */
    @ColumnInfo(name = "relevanceScore") val relevanceScore: Double,
    @ColumnInfo(name = "createdAt") val createdAt: Long = System.currentTimeMillis()
)

/**
 * Expected / actual / previous / revision / surprise / market-reaction figures for an event.
 * Insert-only, "supersede, don't mutate" — a later data revision (routine for CPI, GDP, NFP,
 * etc.) is a new row with [revisesOutcomeId] pointing back at the outcome it revises, the same
 * shape already used by `news.entity.NewsArticleEntity.correctsArticleId`. Multiple rows can
 * therefore exist per event over time; callers wanting the current figure should read the row
 * with the latest [recordedAt] that isn't itself revised (i.e. has no outcome pointing back at
 * it via `revisesOutcomeId`), which [EconomicEventInstrumentLinkDao]'s repository-level "latest
 * outcome" query resolves the same way `NewsRepository.observeCorrections` resolves article
 * revision chains.
 */
@Entity(
    tableName = "economic_event_outcomes",
    foreignKeys = [
        ForeignKey(entity = EconomicEventEntity::class, parentColumns = ["eventId"], childColumns = ["eventId"], onDelete = ForeignKey.CASCADE, onUpdate = ForeignKey.CASCADE),
        ForeignKey(entity = EconomicEventOutcomeEntity::class, parentColumns = ["outcomeId"], childColumns = ["revisesOutcomeId"], onDelete = ForeignKey.SET_NULL, onUpdate = ForeignKey.CASCADE)
    ],
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["eventId"]),
        Index(value = ["eventId", "recordedAt"]),
        Index(value = ["revisesOutcomeId"])
    ]
)
data class EconomicEventOutcomeEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "outcomeId") val outcomeId: Long = 0L,
    @ColumnInfo(name = "uuid") val uuid: String = GlobalId.new(),
    @ColumnInfo(name = "eventId") val eventId: Long,
    /** Unit label for the four value fields below, e.g. "%", "K", "Index Pts". Free text — this platform stores figures, it does not normalize units across providers. */
    @ColumnInfo(name = "unit") val unit: String? = null,
    @ColumnInfo(name = "expectedValue") val expectedValue: Double? = null,
    @ColumnInfo(name = "actualValue") val actualValue: Double? = null,
    @ColumnInfo(name = "previousValue") val previousValue: Double? = null,
    /** The revised figure for the *prior* release period, when this outcome's release included one (common for CPI/GDP/NFP). Distinct from [revisesOutcomeId], which revises this row's own release. */
    @ColumnInfo(name = "revisedPreviousValue") val revisedPreviousValue: Double? = null,
    /** actualValue - expectedValue, stored explicitly (not recomputed by readers) per the approved blueprint listing "Surprise" as its own tracked figure. */
    @ColumnInfo(name = "surprise") val surprise: Double? = null,
    /** Short free-text description of how the market reacted (e.g. "USD rallied 0.6% on the beat"). Not a price-series computation — that lives in `core.entity.HistoricalCandleEntity` / `core.entity.MarketEventEntity`, which this column only summarizes in prose for quick recall. */
    @ColumnInfo(name = "marketReactionSummary") val marketReactionSummary: String? = null,
    /** Self-referential — points at the outcome row this one revises. Null for the first-reported outcome of a release. */
    @ColumnInfo(name = "revisesOutcomeId") val revisesOutcomeId: Long? = null,
    @ColumnInfo(name = "recordedAt") val recordedAt: Long = System.currentTimeMillis()
)

// ======================================================================================
// Drift & Calibration Monitoring
// ======================================================================================

/** What kind of row a [DriftMetricEntity] or [CalibrationMetricEntity] was measured against. Single shared enum — a model, indicator, strategy, or source can all drift or need recalibration, and the approved blueprint calls out exactly these four subject kinds for drift. */
enum class ContextMonitoringSubjectType(val value: String) {
    MODEL("MODEL"),
    INDICATOR("INDICATOR"),
    STRATEGY("STRATEGY"),
    SOURCE("SOURCE");

    companion object {
        fun from(value: String): ContextMonitoringSubjectType = entries.firstOrNull { it.value == value } ?: MODEL
    }
}

enum class DriftSeverity(val value: String) {
    NONE("NONE"),
    LOW("LOW"),
    MODERATE("MODERATE"),
    HIGH("HIGH"),
    CRITICAL("CRITICAL");

    companion object {
        fun from(value: String): DriftSeverity = entries.firstOrNull { it.value == value } ?: NONE
    }
}

/**
 * Single polymorphic drift-measurement table, per the approved blueprint ("Do NOT create
 * separate drift tables"). One row per drift measurement for one subject over one window.
 * [subjectType] selects which table [subjectRowId] logically resolves against — e.g.
 * `intelligence.confidence.entity.ConfidenceModelEntity.modelId` for MODEL,
 * `historical.indicator.entity.IndicatorDefinitionEntity.indicatorId` for INDICATOR,
 * `analytics.entity.StrategyPerformanceEntity.strategyId` (or equivalent strategy identifier)
 * for STRATEGY, `historical.evidence.entity.EvidenceSourceEntity.sourceId` for SOURCE. Logical-
 * only reference, no Room `@ForeignKey`, matching the precedent set by
 * `intelligence.confidence.entity.ConfidenceScoreEntity.scoredEntityRowId` and
 * `intelligence.graph.entity.EntityRelationshipEntity`'s endpoint columns — a single FK target
 * is impossible when the referenced table varies per row.
 *
 * Insert-only / "store, don't recompute": a new measurement is a new row, never an update to a
 * prior one, mirroring `historical.indicator.entity.IndicatorValueEntity` and
 * `news.entity.SentimentScoreEntity`.
 */
@Entity(
    tableName = "drift_metrics",
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["subjectType", "subjectRowId"]),
        Index(value = ["subjectType", "subjectRowId", "metricKey", "measuredAt"]),
        Index(value = ["severity"]),
        Index(value = ["measuredAt"])
    ]
)
data class DriftMetricEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "driftId") val driftId: Long = 0L,
    @ColumnInfo(name = "uuid") val uuid: String = GlobalId.new(),
    @ColumnInfo(name = "subjectType") val subjectType: ContextMonitoringSubjectType,
    /** Logical-only reference — see class doc for which table this resolves against per [subjectType]. */
    @ColumnInfo(name = "subjectRowId") val subjectRowId: Long,
    /** Stable slug for the drift statistic used, e.g. "PSI", "KL_DIVERGENCE", "ACCURACY_DELTA", "FEATURE_MEAN_SHIFT". Pure storage — this platform does not compute drift statistics itself. */
    @ColumnInfo(name = "metricKey") val metricKey: String,
    @ColumnInfo(name = "baselineValue") val baselineValue: Double,
    @ColumnInfo(name = "currentValue") val currentValue: Double,
    /** Normalized drift magnitude for [metricKey] (scale is metric-specific, e.g. PSI's own [0, ~1+] range). */
    @ColumnInfo(name = "driftScore") val driftScore: Double,
    @ColumnInfo(name = "severity") val severity: DriftSeverity = DriftSeverity.NONE,
    @ColumnInfo(name = "windowStart") val windowStart: Long,
    @ColumnInfo(name = "windowEnd") val windowEnd: Long,
    @ColumnInfo(name = "notes") val notes: String? = null,
    @ColumnInfo(name = "measuredAt") val measuredAt: Long = System.currentTimeMillis()
)

/** Which calibration/accuracy concern a [CalibrationMetricEntity] row tracks — the five concerns named explicitly in the approved blueprint. */
enum class CalibrationMetricType(val value: String) {
    CONFIDENCE_CALIBRATION("CONFIDENCE_CALIBRATION"),
    PREDICTION_ACCURACY("PREDICTION_ACCURACY"),
    EVIDENCE_ACCURACY("EVIDENCE_ACCURACY"),
    PERFORMANCE_DRIFT("PERFORMANCE_DRIFT"),
    HISTORICAL_RECALIBRATION("HISTORICAL_RECALIBRATION");

    companion object {
        fun from(value: String): CalibrationMetricType = entries.firstOrNull { it.value == value } ?: CONFIDENCE_CALIBRATION
    }
}

/**
 * Single polymorphic calibration-measurement table — same "one table, typed subject" shape as
 * [DriftMetricEntity], reused rather than duplicated (the approved blueprint's "reuse existing
 * polymorphic design patterns" applies just as much to this table as to drift). [subjectType] /
 * [subjectRowId] resolve the same way as on [DriftMetricEntity]; [metricType] selects which of
 * the five tracked concerns this row represents.
 *
 * `expectedValue`/`observedValue` are intentionally generic (not "confidence bucket" typed
 * fields) so the same two columns serve all five [CalibrationMetricType] values — e.g. for
 * CONFIDENCE_CALIBRATION, expected is a confidence bucket midpoint and observed is the realized
 * hit-rate in that bucket; for PREDICTION_ACCURACY, expected is a predicted value/direction and
 * observed is what actually happened. Insert-only, same as [DriftMetricEntity].
 */
@Entity(
    tableName = "calibration_metrics",
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["subjectType", "subjectRowId"]),
        Index(value = ["subjectType", "subjectRowId", "metricType", "measuredAt"]),
        Index(value = ["metricType"]),
        Index(value = ["measuredAt"])
    ]
)
data class CalibrationMetricEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "calibrationId") val calibrationId: Long = 0L,
    @ColumnInfo(name = "uuid") val uuid: String = GlobalId.new(),
    @ColumnInfo(name = "subjectType") val subjectType: ContextMonitoringSubjectType,
    /** Logical-only reference — see [DriftMetricEntity.subjectRowId] doc for resolution per [subjectType]. */
    @ColumnInfo(name = "subjectRowId") val subjectRowId: Long,
    @ColumnInfo(name = "metricType") val metricType: CalibrationMetricType,
    /** See class doc — meaning depends on [metricType]. */
    @ColumnInfo(name = "expectedValue") val expectedValue: Double,
    /** See class doc — meaning depends on [metricType]. */
    @ColumnInfo(name = "observedValue") val observedValue: Double,
    @ColumnInfo(name = "sampleSize") val sampleSize: Int,
    /** Magnitude of miscalibration for this measurement, e.g. |expected - observed| or a Brier-style score. Metric-specific scale, same convention as [DriftMetricEntity.driftScore]. */
    @ColumnInfo(name = "calibrationError") val calibrationError: Double,
    @ColumnInfo(name = "windowStart") val windowStart: Long,
    @ColumnInfo(name = "windowEnd") val windowEnd: Long,
    /** Whether this measurement crossed the threshold that triggered an actual recalibration action (HISTORICAL_RECALIBRATION rows are typically the record of that action itself). */
    @ColumnInfo(name = "triggeredRecalibration") val triggeredRecalibration: Boolean = false,
    @ColumnInfo(name = "notes") val notes: String? = null,
    @ColumnInfo(name = "measuredAt") val measuredAt: Long = System.currentTimeMillis()
)
