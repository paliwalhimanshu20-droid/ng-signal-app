package com.jarvis.tidb.core.entity

/**
 * All Module 1 enums are persisted as their String [value] (via `core.common.Converters`)
 * rather than ordinal, so reordering a declaration or adding new asset classes never corrupts
 * existing rows. Each enum carries a `from(value)` fallback to a safe default/`UNKNOWN` member
 * so forward-compatible reads never crash on an unrecognized string written by a newer schema.
 */

enum class RecordStatus(val value: String) {
    ACTIVE("ACTIVE"),
    INACTIVE("INACTIVE"),
    SUSPENDED("SUSPENDED");

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
    OPTION("OPTION"),
    INDEX("INDEX"),
    ETF("ETF");

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
        fun from(value: String): ContractTradingStatus = entries.firstOrNull { it.value == value } ?: ACTIVE
    }
}

enum class Timeframe(val value: String) {
    M1("1m"), M5("5m"), M15("15m"), M30("30m"),
    H1("1h"), H4("4h"),
    D1("1d"), W1("1w"), MN1("1M");

    companion object {
        fun from(value: String): Timeframe = entries.firstOrNull { it.value == value } ?: D1
    }
}

enum class CandleSource(val value: String) {
    LIVE_FEED("LIVE_FEED"),
    HISTORICAL_IMPORT("HISTORICAL_IMPORT"),
    BACKFILL("BACKFILL"),
    VENDOR_API("VENDOR_API"),
    MANUAL("MANUAL");

    companion object {
        fun from(value: String): CandleSource = entries.firstOrNull { it.value == value } ?: LIVE_FEED
    }
}

enum class MarketStatus(val value: String) {
    PRE_OPEN("PRE_OPEN"),
    OPEN("OPEN"),
    CLOSED("CLOSED"),
    HALTED("HALTED"),
    POST_CLOSE("POST_CLOSE");

    companion object {
        fun from(value: String): MarketStatus = entries.firstOrNull { it.value == value } ?: CLOSED
    }
}

enum class MarketEventType(val value: String) {
    NEWS("NEWS"),
    CIRCUIT_BREAKER("CIRCUIT_BREAKER"),
    EXPIRY("EXPIRY"),
    HOLIDAY("HOLIDAY"),
    HALT("HALT"),
    CORPORATE_ACTION("CORPORATE_ACTION"),
    OTHER("OTHER");

    companion object {
        fun from(value: String): MarketEventType = entries.firstOrNull { it.value == value } ?: OTHER
    }
}

enum class EventSeverity(val value: String) {
    INFO("INFO"),
    NOTABLE("NOTABLE"),
    WARNING("WARNING"),
    CRITICAL("CRITICAL");

    companion object {
        fun from(value: String): EventSeverity = entries.firstOrNull { it.value == value } ?: INFO
    }
}
