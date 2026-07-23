package com.jarvis.tidb.signals.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * A single reason contributing to why a Signal was generated. A signal typically has several
 * of these (e.g. one for EMA crossover, one for RSI divergence, one for a support bounce).
 *
 * `category` is a free-text label rather than a closed enum on purpose — the spec's category
 * list (EMA, MACD, RSI, ADX, Supertrend, Price Action, Volume, Support, Resistance, Pattern,
 * AI, ...) is explicitly extensible, and future modules (AI Learning, Strategy Engine) will
 * introduce categories that don't exist yet. `evidenceJson` carries whatever structured payload
 * the generating logic wants to preserve (indicator values, pattern coordinates, etc.) without
 * this table needing to know its shape.
 *
 * Cascade-deletes with its parent Signal: a reason has no meaning without the signal it explains.
 */
@Entity(
    tableName = "signal_reasons",
    foreignKeys = [
        ForeignKey(
            entity = SignalEntity::class,
            parentColumns = ["signalId"],
            childColumns = ["signalId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["signalId"]),
        Index(value = ["category"]),
        Index(value = ["uuid"], unique = true)
    ]
)
data class SignalReasonEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "reasonId")
    val reasonId: Long = 0,

    @ColumnInfo(name = "uuid")
    val uuid: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "signalId")
    val signalId: Long,

    @ColumnInfo(name = "category")
    val category: String,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "description")
    val description: String,

    /** Relative contribution of this reason to the overall confidence score, e.g. 0.0-1.0. */
    @ColumnInfo(name = "weight")
    val weight: Double,

    /** Free-form JSON payload with the raw evidence (indicator values, coordinates, etc.). */
    @ColumnInfo(name = "evidenceJson")
    val evidenceJson: String? = null,

    @ColumnInfo(name = "createdAt")
    val createdAt: Long = System.currentTimeMillis()
)
