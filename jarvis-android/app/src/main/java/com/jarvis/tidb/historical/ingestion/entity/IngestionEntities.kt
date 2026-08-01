package com.jarvis.tidb.historical.ingestion.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.jarvis.tidb.core.common.AuditMetadata
import com.jarvis.tidb.core.common.GlobalId
import com.jarvis.tidb.core.common.SoftDeleteMetadata
import com.jarvis.tidb.core.entity.InstrumentEntity

/**
 * HISTORICAL MARKET DATA PLATFORM — Ingestion Engine (schema v5).
 *
 * Extends the unified TradingIntelligenceDatabase (does not recreate anything from Module 1).
 * Candles produced by ingestion still land in the existing [com.jarvis.tidb.core.entity.HistoricalCandleEntity]
 * table — this package only adds the orchestration/bookkeeping layer around that: which
 * provider produced what, what ran, what's in flight, what failed and needs retry, and where
 * an incremental pull should resume from.
 */

/** Enumerations for the Ingestion Engine. Persisted as String `value`, same convention as every other module. */
enum class ProviderType(val value: String) {
    EXCHANGE_FEED("EXCHANGE_FEED"),
    BROKER_FEED("BROKER_FEED"),
    THIRD_PARTY_VENDOR("THIRD_PARTY_VENDOR"),
    AGGREGATOR("AGGREGATOR"),
    MANUAL_UPLOAD("MANUAL_UPLOAD"),
    /** TRADING-007A.1 (schema v7), additive: a news/RSS feed provider. See `com.jarvis.tidb.news`. */
    NEWS_FEED("NEWS_FEED");

    companion object {
        fun from(value: String): ProviderType = entries.firstOrNull { it.value == value } ?: THIRD_PARTY_VENDOR
    }
}

enum class IngestionJobType(val value: String) {
    FULL_BACKFILL("FULL_BACKFILL"),
    INCREMENTAL_UPDATE("INCREMENTAL_UPDATE"),
    GAP_FILL("GAP_FILL"),
    MANUAL_REIMPORT("MANUAL_REIMPORT"),
    /** TRADING-007A.1 (schema v7), additive: a news-article ingestion pull. See `com.jarvis.tidb.news`. */
    NEWS_PULL("NEWS_PULL");

    companion object {
        fun from(value: String): IngestionJobType = entries.firstOrNull { it.value == value } ?: INCREMENTAL_UPDATE
    }
}

enum class IngestionJobStatus(val value: String) {
    PENDING("PENDING"),
    RUNNING("RUNNING"),
    SUCCEEDED("SUCCEEDED"),
    FAILED("FAILED"),
    RETRYING("RETRYING"),
    CANCELLED("CANCELLED");

    companion object {
        fun from(value: String): IngestionJobStatus = entries.firstOrNull { it.value == value } ?: PENDING
    }
}

enum class IngestionEventType(val value: String) {
    STARTED("STARTED"),
    PROGRESS("PROGRESS"),
    RETRY("RETRY"),
    WARNING("WARNING"),
    ERROR("ERROR"),
    COMPLETED("COMPLETED");

    companion object {
        fun from(value: String): IngestionEventType = entries.firstOrNull { it.value == value } ?: PROGRESS
    }
}

/** A configured historical-data source. Asset-class-agnostic; multiple providers can serve the same instrument. */
@Entity(
    tableName = "data_providers",
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["providerCode"], unique = true),
        Index(value = ["isActive"]),
        Index(value = ["isDeleted"])
    ]
)
data class DataProviderEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "providerId")
    val providerId: Long = 0L,

    @ColumnInfo(name = "uuid")
    val uuid: String = GlobalId.new(),

    @ColumnInfo(name = "providerCode")
    val providerCode: String,

    @ColumnInfo(name = "displayName")
    val displayName: String,

    @ColumnInfo(name = "providerType")
    val providerType: ProviderType,

    /** Relative priority for multi-source resolution; lower runs first when several providers can serve a request. */
    @ColumnInfo(name = "priority")
    val priority: Int = 100,

    @ColumnInfo(name = "rateLimitPerMinute")
    val rateLimitPerMinute: Int? = null,

    /** CSV of Timeframe.value strings this provider can serve (e.g. "1m,5m,1d"). */
    @ColumnInfo(name = "supportedTimeframes")
    val supportedTimeframes: String,

    @ColumnInfo(name = "isActive")
    val isActive: Boolean = true,

    /** Free-form JSON for connection details (base URL, auth key alias, vendor-specific options). Never raw secrets. */
    @ColumnInfo(name = "configJson")
    val configJson: String? = null,

    @Embedded
    val audit: AuditMetadata = AuditMetadata(),

    @Embedded
    val softDelete: SoftDeleteMetadata = SoftDeleteMetadata.notDeleted()
)

/**
 * A single ingestion run for one (provider, instrument, timeframe). Tracks progress, retry
 * state, and outcome counts so the UI/orchestrator never has to guess what a job did.
 */
@Entity(
    tableName = "ingestion_jobs",
    foreignKeys = [
        ForeignKey(
            entity = DataProviderEntity::class,
            parentColumns = ["providerId"],
            childColumns = ["providerId"],
            onDelete = ForeignKey.RESTRICT,
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
        Index(value = ["uuid"], unique = true),
        Index(value = ["providerId"]),
        Index(value = ["instrumentId"]),
        Index(value = ["instrumentId", "timeframe"]),
        Index(value = ["status"]),
        Index(value = ["jobType"]),
        Index(value = ["nextRetryAt"]),
        Index(value = ["isDeleted"])
    ]
)
data class IngestionJobEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "jobId")
    val jobId: Long = 0L,

    @ColumnInfo(name = "uuid")
    val uuid: String = GlobalId.new(),

    @ColumnInfo(name = "providerId")
    val providerId: Long,

    @ColumnInfo(name = "instrumentId")
    val instrumentId: Long,

    /** Stored as the raw Timeframe string value; this module deliberately avoids importing
     * Module 1's Timeframe enum type directly into the column definition so this table reads
     * naturally as data — conversion happens at the repository boundary via `Timeframe.from()`. */
    @ColumnInfo(name = "timeframe")
    val timeframe: String,

    @ColumnInfo(name = "jobType")
    val jobType: IngestionJobType,

    @ColumnInfo(name = "status")
    val status: IngestionJobStatus = IngestionJobStatus.PENDING,

    @ColumnInfo(name = "requestedRangeStart")
    val requestedRangeStart: Long? = null,

    @ColumnInfo(name = "requestedRangeEnd")
    val requestedRangeEnd: Long? = null,

    @ColumnInfo(name = "progressPercent")
    val progressPercent: Double = 0.0,

    @ColumnInfo(name = "rowsFetched")
    val rowsFetched: Long = 0L,

    @ColumnInfo(name = "rowsInserted")
    val rowsInserted: Long = 0L,

    @ColumnInfo(name = "rowsSkipped")
    val rowsSkipped: Long = 0L,

    @ColumnInfo(name = "rowsFailed")
    val rowsFailed: Long = 0L,

    @ColumnInfo(name = "retryCount")
    val retryCount: Int = 0,

    @ColumnInfo(name = "maxRetries")
    val maxRetries: Int = 3,

    @ColumnInfo(name = "lastError")
    val lastError: String? = null,

    /** Backoff schedule for [IngestionJobStatus.RETRYING]; the orchestrator polls on this. */
    @ColumnInfo(name = "nextRetryAt")
    val nextRetryAt: Long? = null,

    @ColumnInfo(name = "startedAt")
    val startedAt: Long? = null,

    @ColumnInfo(name = "completedAt")
    val completedAt: Long? = null,

    /** "SYSTEM" for scheduled/incremental runs, else a user/agent identifier. */
    @ColumnInfo(name = "triggeredBy")
    val triggeredBy: String = "SYSTEM",

    @Embedded
    val audit: AuditMetadata = AuditMetadata(),

    @Embedded
    val softDelete: SoftDeleteMetadata = SoftDeleteMetadata.notDeleted()
)

/** Append-only event trail for a job — the "recovery" half of retry-and-recovery: every attempt, warning, and error is preserved. */
@Entity(
    tableName = "ingestion_job_logs",
    foreignKeys = [
        ForeignKey(
            entity = IngestionJobEntity::class,
            parentColumns = ["jobId"],
            childColumns = ["jobId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["jobId"]),
        Index(value = ["eventType"]),
        Index(value = ["timestamp"])
    ]
)
data class IngestionJobLogEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "logId")
    val logId: Long = 0L,

    @ColumnInfo(name = "jobId")
    val jobId: Long,

    @ColumnInfo(name = "attemptNumber")
    val attemptNumber: Int,

    @ColumnInfo(name = "eventType")
    val eventType: IngestionEventType,

    @ColumnInfo(name = "message")
    val message: String,

    /** Optional structured detail (HTTP status, vendor error code, partial-range info, etc.). */
    @ColumnInfo(name = "detailsJson")
    val detailsJson: String? = null,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Resume cursor for incremental updates, keyed per (provider, instrument, timeframe). Lets the
 * ingestion engine ask a provider for "everything since X" instead of re-pulling full history.
 */
@Entity(
    tableName = "ingestion_checkpoints",
    foreignKeys = [
        ForeignKey(
            entity = DataProviderEntity::class,
            parentColumns = ["providerId"],
            childColumns = ["providerId"],
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
        Index(value = ["providerId", "instrumentId", "timeframe"], unique = true, name = "idx_checkpoint_provider_instrument_timeframe"),
        Index(value = ["instrumentId"])
    ]
)
data class IngestionCheckpointEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "checkpointId")
    val checkpointId: Long = 0L,

    @ColumnInfo(name = "providerId")
    val providerId: Long,

    @ColumnInfo(name = "instrumentId")
    val instrumentId: Long,

    @ColumnInfo(name = "timeframe")
    val timeframe: String,

    /** Epoch millis of the last successfully ingested candle's timestamp for this triple. */
    @ColumnInfo(name = "lastSuccessfulTimestamp")
    val lastSuccessfulTimestamp: Long? = null,

    @ColumnInfo(name = "lastRunAt")
    val lastRunAt: Long? = null,

    /** Opaque vendor pagination token, for providers whose API uses cursors instead of timestamps. */
    @ColumnInfo(name = "cursorToken")
    val cursorToken: String? = null,

    @Embedded
    val audit: AuditMetadata = AuditMetadata()
)
