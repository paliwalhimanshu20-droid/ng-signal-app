package com.jarvis.tidb.historical.indicator.entity

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
 * HISTORICAL MARKET DATA PLATFORM — Indicator Warehouse (schema v5).
 *
 * A computation framework that STORES indicator output rather than recomputing it on every
 * read. [IndicatorValueEntity] is deliberately generic (up to 4 numeric output slots) so one
 * table serves every indicator type in the full set — SMA/EMA/RSI/ATR/VWAP are single-output
 * (value1), MACD is 3-output (line/signal/histogram), Bollinger Bands is 3-output
 * (upper/middle/lower), Stochastic is 2-output (%K/%D), ADX commonly ships with +DI/-DI
 * alongside it (up to 3). [IndicatorDefinitionEntity.outputLabels] documents what each slot
 * means for a given definition so consumers don't have to hardcode column meaning per type.
 *
 * "Universal Indicator Engine" Phase 2: expanded from the original 10 (SMA/EMA/RSI/ATR/MACD/
 * BOLLINGER_BANDS/ADX/SUPERTREND/VWAP/STOCHASTIC) to the full 26-indicator set. Every addition
 * fits the existing 4-slot [IndicatorValueEntity] shape with one deliberate exception: Ichimoku
 * is a 5-line indicator (Tenkan-sen, Kijun-sen, Senkou Span A, Senkou Span B, Chikou Span), but
 * only the first 4 are stored (value1-value4, see ICHIMOKU's own doc below) -- Chikou Span is
 * just closing price shifted back by the indicator's displacement period, not an independently
 * computed series, so storing it here would duplicate data [com.jarvis.tidb.core.repository.HistoricalCandleRepository]
 * already holds. This is a real design decision, not a silent omission -- documented rather than
 * adding a 5th column for a value that's a trivial shift of data already on record.
 */
enum class IndicatorType(val value: String) {
    SMA("SMA"),
    EMA("EMA"),
    WMA("WMA"),
    VWMA("VWMA"),
    RSI("RSI"),
    ATR("ATR"),
    MACD("MACD"),
    BOLLINGER_BANDS("BOLLINGER_BANDS"),
    ADX("ADX"),
    SUPERTREND("SUPERTREND"),
    VWAP("VWAP"),
    STOCHASTIC("STOCHASTIC"),
    CCI("CCI"),
    ROC("ROC"),
    MOMENTUM("MOMENTUM"),
    WILLIAMS_R("WILLIAMS_R"),
    PARABOLIC_SAR("PARABOLIC_SAR"),
    /** value1=Tenkan-sen, value2=Kijun-sen, value3=Senkou Span A, value4=Senkou Span B. Chikou Span is deliberately not stored -- see this enum's own class docstring. */
    ICHIMOKU("ICHIMOKU"),
    DONCHIAN_CHANNEL("DONCHIAN_CHANNEL"),
    KELTNER_CHANNEL("KELTNER_CHANNEL"),
    OBV("OBV"),
    CMF("CMF"),
    MFI("MFI"),
    AROON("AROON"),
    TRIX("TRIX"),
    DMI("DMI");

    companion object {
        fun from(value: String): IndicatorType = entries.firstOrNull { it.value == value } ?: SMA
    }
}

enum class ComputationStatus(val value: String) {
    PENDING("PENDING"),
    RUNNING("RUNNING"),
    COMPLETED("COMPLETED"),
    FAILED("FAILED");

    companion object {
        fun from(value: String): ComputationStatus = entries.firstOrNull { it.value == value } ?: PENDING
    }
}

/**
 * A named, versioned indicator configuration (e.g. "RSI-14", "EMA-20", "MACD-12-26-9"). Storing
 * params here rather than as loose columns on the value table means the same indicator type can
 * be tracked at multiple parameterizations side by side without schema changes.
 */
@Entity(
    tableName = "indicator_definitions",
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["indicatorType"]),
        Index(value = ["name", "definitionVersion"], unique = true, name = "idx_indicator_def_name_version")
    ]
)
data class IndicatorDefinitionEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "indicatorDefId")
    val indicatorDefId: Long = 0L,

    @ColumnInfo(name = "uuid")
    val uuid: String = GlobalId.new(),

    /** Human-readable unique name, e.g. "RSI-14". */
    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "indicatorType")
    val indicatorType: IndicatorType,

    /** JSON of the parameter set, e.g. {"period":14} or {"fast":12,"slow":26,"signal":9}. */
    @ColumnInfo(name = "paramsJson")
    val paramsJson: String,

    /** JSON array naming what value1..value4 mean for this definition, e.g. ["macd","signal","histogram"]. */
    @ColumnInfo(name = "outputLabels")
    val outputLabels: String,

    /** Bumped whenever [paramsJson] or the computation logic materially changes; old values remain queryable under their original version. Named distinctly from the embedded [AuditMetadata.version] optimistic-lock counter below — the two are unrelated. */
    @ColumnInfo(name = "definitionVersion")
    val definitionVersion: Int = 1,

    @ColumnInfo(name = "isActive")
    val isActive: Boolean = true,

    @Embedded
    val audit: AuditMetadata = AuditMetadata()
)

/** One computed indicator reading for one (instrument, timeframe, timestamp, definition, version). */
@Entity(
    tableName = "indicator_values",
    foreignKeys = [
        ForeignKey(
            entity = IndicatorDefinitionEntity::class,
            parentColumns = ["indicatorDefId"],
            childColumns = ["indicatorDefId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = InstrumentEntity::class,
            parentColumns = ["instrumentId"],
            childColumns = ["instrumentId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(
            value = ["indicatorDefId", "instrumentId", "timeframe", "timestamp", "version"],
            unique = true,
            name = "idx_indicator_value_unique"
        ),
        Index(value = ["instrumentId", "timeframe", "timestamp"]),
        Index(value = ["indicatorDefId"])
    ]
)
data class IndicatorValueEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "indicatorValueId")
    val indicatorValueId: Long = 0L,

    @ColumnInfo(name = "indicatorDefId")
    val indicatorDefId: Long,

    @ColumnInfo(name = "instrumentId")
    val instrumentId: Long,

    @ColumnInfo(name = "timeframe")
    val timeframe: String,

    /** Candle open timestamp this value was computed for — mirrors HistoricalCandleEntity.timestamp. */
    @ColumnInfo(name = "timestamp")
    val timestamp: Long,

    /** Recomputation generation. A new [version] can be written without deleting the old one (e.g. after a candle correction upstream). */
    @ColumnInfo(name = "version")
    val version: Int = 1,

    @ColumnInfo(name = "value1")
    val value1: Double,

    @ColumnInfo(name = "value2")
    val value2: Double? = null,

    @ColumnInfo(name = "value3")
    val value3: Double? = null,

    @ColumnInfo(name = "value4")
    val value4: Double? = null,

    @ColumnInfo(name = "computedAt")
    val computedAt: Long = System.currentTimeMillis()
)

/** Tracks one computation/recomputation pass over a range, so the warehouse knows what's fresh and what still needs computing. */
@Entity(
    tableName = "indicator_computation_runs",
    foreignKeys = [
        ForeignKey(
            entity = IndicatorDefinitionEntity::class,
            parentColumns = ["indicatorDefId"],
            childColumns = ["indicatorDefId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["indicatorDefId"]),
        Index(value = ["instrumentId", "timeframe"]),
        Index(value = ["status"])
    ]
)
data class IndicatorComputationRunEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "runId")
    val runId: Long = 0L,

    @ColumnInfo(name = "uuid")
    val uuid: String = GlobalId.new(),

    @ColumnInfo(name = "indicatorDefId")
    val indicatorDefId: Long,

    @ColumnInfo(name = "instrumentId")
    val instrumentId: Long,

    @ColumnInfo(name = "timeframe")
    val timeframe: String,

    @ColumnInfo(name = "fromTimestamp")
    val fromTimestamp: Long,

    @ColumnInfo(name = "toTimestamp")
    val toTimestamp: Long,

    @ColumnInfo(name = "status")
    val status: ComputationStatus = ComputationStatus.PENDING,

    @ColumnInfo(name = "rowsComputed")
    val rowsComputed: Long = 0L,

    /** True if this run is recomputing a version that already existed (e.g. after upstream candle corrections), false for a fresh compute. */
    @ColumnInfo(name = "isRecomputation")
    val isRecomputation: Boolean = false,

    @ColumnInfo(name = "startedAt")
    val startedAt: Long? = null,

    @ColumnInfo(name = "completedAt")
    val completedAt: Long? = null,

    @ColumnInfo(name = "error")
    val error: String? = null,

    @Embedded
    val audit: AuditMetadata = AuditMetadata()
)
