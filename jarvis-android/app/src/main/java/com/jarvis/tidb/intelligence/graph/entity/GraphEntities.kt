package com.jarvis.tidb.intelligence.graph.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.jarvis.tidb.core.common.AuditMetadata
import com.jarvis.tidb.core.common.GlobalId
import com.jarvis.tidb.core.entity.InstrumentEntity

/**
 * TRADING-006 — Module 5 Phase 4: Knowledge Graph.
 *
 * Entirely new. Nothing in Modules 1-4 modeled generic relationships *between* trading-intelligence
 * concepts (patterns, regimes, hypotheses, signals, trades, ...) as first-class rows — everything
 * so far is a tree rooted at one concept (an evidence record's links, a trade's executions). This
 * package adds the general graph layer on top: typed edges ([EntityRelationshipEntity]), a
 * point-in-time market snapshot for context ([MarketContextEntity]), directional lead/lag
 * observations ([CausalObservationEntity]), and measured statistical co-movement
 * ([CorrelationEntity]). All four are pure storage — no graph traversal, causal inference, or
 * correlation computation happens here; that is a future reader's job.
 */

/** The set of Module 5 concepts (plus a few Module 1/2/3 anchors) that can participate in the knowledge graph. Deliberately open-ended. */
enum class GraphEntityType(val value: String) {
    INSTRUMENT("INSTRUMENT"),
    PATTERN("PATTERN"),
    PATTERN_OCCURRENCE("PATTERN_OCCURRENCE"),
    MARKET_REGIME("MARKET_REGIME"),
    HYPOTHESIS("HYPOTHESIS"),
    EXPERIMENT("EXPERIMENT"),
    SIGNAL("SIGNAL"),
    TRADE("TRADE"),
    EVIDENCE_RECORD("EVIDENCE_RECORD"),
    MARKET_CONTEXT("MARKET_CONTEXT");

    companion object {
        fun from(value: String): GraphEntityType = entries.firstOrNull { it.value == value } ?: INSTRUMENT
    }
}

enum class RelationshipType(val value: String) {
    CORRELATES_WITH("CORRELATES_WITH"),
    PRECEDES("PRECEDES"),
    CAUSES("CAUSES"),
    CONTRADICTS("CONTRADICTS"),
    CONFIRMS("CONFIRMS"),
    PART_OF("PART_OF"),
    SIMILAR_TO("SIMILAR_TO");

    companion object {
        fun from(value: String): RelationshipType = entries.firstOrNull { it.value == value } ?: SIMILAR_TO
    }
}

/** A typed, directed edge between any two entities in the system. Both endpoints are polymorphic references, not Room `@ForeignKey`s. */
@Entity(
    tableName = "entity_relationships",
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["fromEntityType", "fromEntityRowId"]),
        Index(value = ["toEntityType", "toEntityRowId"]),
        Index(
            value = ["fromEntityType", "fromEntityRowId", "toEntityType", "toEntityRowId", "relationshipType"],
            unique = true
        )
    ]
)
data class EntityRelationshipEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "relationshipId") val relationshipId: Long = 0L,
    @ColumnInfo(name = "uuid") val uuid: String = GlobalId.new(),
    @ColumnInfo(name = "fromEntityType") val fromEntityType: GraphEntityType,
    @ColumnInfo(name = "fromEntityRowId") val fromEntityRowId: Long,
    @ColumnInfo(name = "toEntityType") val toEntityType: GraphEntityType,
    @ColumnInfo(name = "toEntityRowId") val toEntityRowId: Long,
    @ColumnInfo(name = "relationshipType") val relationshipType: RelationshipType,
    /** Descriptive edge strength in [0.0, 1.0]. */
    @ColumnInfo(name = "strength") val strength: Double = 1.0,
    @ColumnInfo(name = "notes") val notes: String? = null,
    @Embedded val audit: AuditMetadata = AuditMetadata()
)

/**
 * A point-in-time snapshot of the broader context around an instrument — the "what else was true"
 * anchor that [CausalObservationEntity] and [CorrelationEntity] rows can reference.
 * `regimeId` is a logical-only reference into `intelligence.regime.entity.MarketRegimeEntity`
 * (kept logical, not a Room FK, to avoid a cross-package compile-time dependency between
 * `graph` and `regime`).
 */
@Entity(
    tableName = "market_contexts",
    foreignKeys = [
        ForeignKey(
            entity = InstrumentEntity::class,
            parentColumns = ["instrumentId"],
            childColumns = ["instrumentId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["instrumentId", "timestamp"]),
        Index(value = ["regimeId"])
    ]
)
data class MarketContextEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "contextId") val contextId: Long = 0L,
    @ColumnInfo(name = "uuid") val uuid: String = GlobalId.new(),
    @ColumnInfo(name = "instrumentId") val instrumentId: Long,
    @ColumnInfo(name = "timestamp") val timestamp: Long,
    @ColumnInfo(name = "regimeId") val regimeId: Long? = null,
    @ColumnInfo(name = "contextSummary") val contextSummary: String,
    @ColumnInfo(name = "macroNotes") val macroNotes: String? = null,
    @Embedded val audit: AuditMetadata = AuditMetadata()
)

enum class CausalDirection(val value: String) {
    LEADS("LEADS"),
    LAGS("LAGS"),
    CONCURRENT("CONCURRENT"),
    UNKNOWN("UNKNOWN");

    companion object {
        fun from(value: String): CausalDirection = entries.firstOrNull { it.value == value } ?: UNKNOWN
    }
}

/** A recorded lead/lag observation between a cause and an effect entity — descriptive, not a proof of causation. */
@Entity(
    tableName = "causal_observations",
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["causeEntityType", "causeEntityRowId"]),
        Index(value = ["effectEntityType", "effectEntityRowId"]),
        Index(value = ["observedAt"])
    ]
)
data class CausalObservationEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "causalObservationId") val causalObservationId: Long = 0L,
    @ColumnInfo(name = "uuid") val uuid: String = GlobalId.new(),
    @ColumnInfo(name = "causeEntityType") val causeEntityType: GraphEntityType,
    @ColumnInfo(name = "causeEntityRowId") val causeEntityRowId: Long,
    @ColumnInfo(name = "effectEntityType") val effectEntityType: GraphEntityType,
    @ColumnInfo(name = "effectEntityRowId") val effectEntityRowId: Long,
    @ColumnInfo(name = "direction") val direction: CausalDirection = CausalDirection.UNKNOWN,
    @ColumnInfo(name = "lagMillis") val lagMillis: Long? = null,
    /** Descriptive confidence in [0.0, 1.0] — not a statistical significance measure. */
    @ColumnInfo(name = "confidence") val confidence: Double,
    @ColumnInfo(name = "description") val description: String,
    @ColumnInfo(name = "observedAt") val observedAt: Long = System.currentTimeMillis(),
    @Embedded val audit: AuditMetadata = AuditMetadata()
)

/** A measured statistical co-movement between two entities over a stated window. Pure storage — the coefficient is computed elsewhere. */
@Entity(
    tableName = "correlations",
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["entityAType", "entityARowId"]),
        Index(value = ["entityBType", "entityBRowId"]),
        Index(
            value = ["entityAType", "entityARowId", "entityBType", "entityBRowId", "windowDescription"],
            unique = true
        )
    ]
)
data class CorrelationEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "correlationId") val correlationId: Long = 0L,
    @ColumnInfo(name = "uuid") val uuid: String = GlobalId.new(),
    @ColumnInfo(name = "entityAType") val entityAType: GraphEntityType,
    @ColumnInfo(name = "entityARowId") val entityARowId: Long,
    @ColumnInfo(name = "entityBType") val entityBType: GraphEntityType,
    @ColumnInfo(name = "entityBRowId") val entityBRowId: Long,
    /** e.g. Pearson correlation coefficient in [-1.0, 1.0]. */
    @ColumnInfo(name = "coefficient") val coefficient: Double,
    /** e.g. "90d_daily", "252d_weekly". */
    @ColumnInfo(name = "windowDescription") val windowDescription: String,
    @ColumnInfo(name = "sampleSize") val sampleSize: Int,
    @ColumnInfo(name = "computedAt") val computedAt: Long = System.currentTimeMillis(),
    @Embedded val audit: AuditMetadata = AuditMetadata()
)
