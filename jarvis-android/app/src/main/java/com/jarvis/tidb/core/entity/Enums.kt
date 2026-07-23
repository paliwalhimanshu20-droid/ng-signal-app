package com.jarvis.tidb.core.entity

/**
 * Enumerations shared across the Core Market Foundation module.
 *
 * These are stored in Room as their String `value` (see [com.jarvis.tidb.core.Converters]),
 * which keeps the database human-readable while still giving Kotlin compile-time safety
 * everywhere the enum is used in code.
 */

enum class RecordStatus(val value: String) {
    ACTIVE("ACTIVE"),
    INACTIVE("INACTIVE"),
    SUSPENDED("SUSPENDED"),
    DELISTED("DELISTED");

    companion object {
        fun from(value: String): RecordStatus = entries.firstOrNull { it.value == value } ?: ACTIVE
    }
}

enum class AssetClass(val value: String) {
    COMMODITY("COMMODITY"),
    EQUITY("EQUITY"),
    FUTURES("FUTURES"),
    OPTIONS("OPTIONS"),
    FOREX("FOREX"),
    CRYPTO("CRYPTO"),
    INDEX("INDEX");

    companion object {
        fun from(value: String): AssetClass = entries.firstOrNull { it.value == value } ?: COMMODITY
    }
}

enum class InstrumentType(val value: String) {
    SPOT("SPOT"),
    FUTURE("FUTURE"),
    OPTION_CALL("OPTION_CALL"),
    OPTION_PUT("OPTION_PUT"),
    PERPETUAL("PERPETUAL"),
    CFD("CFD");

    companion object {
        fun from(value: String): InstrumentType = entries.firstOrNull { it.value == value } ?: SPOT
    }
}

enum class ContractTradingStatus(val value: String) {
    ACTIVE("ACTIVE"),
    NEAR_EXPIRY("NEAR_EXPIRY"),
    EXPIRED("EXPIRED"),
    ROLLED("ROLLED"),
    SUSPENDED("SUSPENDED");

    companion object {
        fun from(value: String): ContractTradingStatus =
            entries.firstOrNull { it.value == value } ?: ACTIVE
    }
}

enum class Timeframe(val value: String) {
    M1("1m"),
    M3("3m"),
    M5("5m"),
    M15("15m"),
    M30("30m"),
    H1("1H"),
    H4("4H"),
    DAILY("Daily"),
    WEEKLY("Weekly"),
    MONTHLY("Monthly");

    companion object {
        fun from(value: String): Timeframe = entries.firstOrNull { it.value == value } ?: M1
    }
}

enum class CandleSource(val value: String) {
    EXCHANGE_FEED("EXCHANGE_FEED"),
    BROKER_FEED("BROKER_FEED"),
    THIRD_PARTY("THIRD_PARTY"),
    AGGREGATED("AGGREGATED"),
    BACKFILL("BACKFILL");

    companion object {
        fun from(value: String): CandleSource = entries.firstOrNull { it.value == value } ?: EXCHANGE_FEED
    }
}

enum class MarketStatus(val value: String) {
    PRE_OPEN("PRE_OPEN"),
    OPEN("OPEN"),
    CLOSED("CLOSED"),
    HALTED("HALTED"),
    CIRCUIT("CIRCUIT"),
    POST_CLOSE("POST_CLOSE");

    companion object {
        fun from(value: String): MarketStatus = entries.firstOrNull { it.value == value } ?: CLOSED
    }
}

enum class MarketEventType(val value: String) {
    EXPIRY("EXPIRY"),
    ROLLOVER("ROLLOVER"),
    TRADING_HALT("TRADING_HALT"),
    CIRCUIT("CIRCUIT"),
    HIGH_VOLATILITY("HIGH_VOLATILITY"),
    VOLUME_SPIKE("VOLUME_SPIKE"),
    NEWS_SHOCK("NEWS_SHOCK"),
    GAP_OPEN("GAP_OPEN");

    companion object {
        fun from(value: String): MarketEventType =
            entries.firstOrNull { it.value == value } ?: TRADING_HALT
    }
}

enum class EventSeverity(val value: String) {
    INFO("INFO"),
    LOW("LOW"),
    MEDIUM("MEDIUM"),
    HIGH("HIGH"),
    CRITICAL("CRITICAL");

    companion object {
        fun from(value: String): EventSeverity = entries.firstOrNull { it.value == value } ?: INFO
    }
}
