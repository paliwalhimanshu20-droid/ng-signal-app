package com.jarvis.tidb.signals.entity

/**
 * Enumerations for the Signal Intelligence module (Module 2).
 *
 * Stored in Room as their String `value` (see [com.jarvis.tidb.signals.Converters]) — same
 * convention as Module 1 (Core Market Foundation): human-readable in the DB, type-safe in code,
 * and safe against reordering since nothing is persisted as an ordinal.
 */

enum class SignalType(val value: String) {
    BUY("BUY"),
    SELL("SELL"),
    EXIT("EXIT"),
    HOLD("HOLD");

    companion object {
        fun from(value: String): SignalType = entries.firstOrNull { it.value == value } ?: HOLD
    }
}

enum class SignalStatus(val value: String) {
    ACTIVE("ACTIVE"),
    EXECUTED("EXECUTED"),
    EXPIRED("EXPIRED"),
    CANCELLED("CANCELLED");

    companion object {
        fun from(value: String): SignalStatus = entries.firstOrNull { it.value == value } ?: ACTIVE
    }
}

/**
 * Market trend classification captured in the immutable signal snapshot.
 */
enum class MarketTrend(val value: String) {
    STRONG_UPTREND("STRONG_UPTREND"),
    UPTREND("UPTREND"),
    SIDEWAYS("SIDEWAYS"),
    DOWNTREND("DOWNTREND"),
    STRONG_DOWNTREND("STRONG_DOWNTREND"),
    UNKNOWN("UNKNOWN");

    companion object {
        fun from(value: String): MarketTrend = entries.firstOrNull { it.value == value } ?: UNKNOWN
    }
}
