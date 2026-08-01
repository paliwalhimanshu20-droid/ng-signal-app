package com.jarvis.tidb.signals.entity

enum class SignalType(val value: String) {
    ENTRY_LONG("ENTRY_LONG"),
    ENTRY_SHORT("ENTRY_SHORT"),
    EXIT("EXIT"),
    ALERT("ALERT"),
    REBALANCE("REBALANCE");

    companion object {
        fun from(value: String): SignalType = entries.firstOrNull { it.value == value } ?: ALERT
    }
}

enum class SignalStatus(val value: String) {
    ACTIVE("ACTIVE"),
    TRIGGERED("TRIGGERED"),
    EXPIRED("EXPIRED"),
    INVALIDATED("INVALIDATED"),
    CANCELLED("CANCELLED"),
    DELETED("DELETED");

    companion object {
        fun from(value: String): SignalStatus = entries.firstOrNull { it.value == value } ?: ACTIVE
    }
}

enum class MarketTrend(val value: String) {
    BULLISH("BULLISH"),
    BEARISH("BEARISH"),
    SIDEWAYS("SIDEWAYS"),
    VOLATILE("VOLATILE"),
    UNKNOWN("UNKNOWN");

    companion object {
        fun from(value: String): MarketTrend = entries.firstOrNull { it.value == value } ?: UNKNOWN
    }
}
