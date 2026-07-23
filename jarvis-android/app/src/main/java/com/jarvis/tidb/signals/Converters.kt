package com.jarvis.tidb.signals

import androidx.room.TypeConverter
import com.jarvis.tidb.signals.entity.MarketTrend
import com.jarvis.tidb.signals.entity.SignalStatus
import com.jarvis.tidb.signals.entity.SignalType

/**
 * Enum <-> String converters for the Signal Intelligence database.
 *
 * `timeframe` on [com.jarvis.tidb.signals.entity.SignalEntity] deliberately stays a raw String
 * rather than importing Module 1's `Timeframe` enum directly into the entity — this keeps
 * Module 2 decoupled from Module 1's schema types (per the "consume Module 1 only through
 * repository interfaces" rule) while still round-tripping cleanly through
 * `com.jarvis.tidb.core.entity.Timeframe.from(value)` wherever Module 1 data is needed.
 */
class Converters {

    @TypeConverter
    fun fromSignalType(value: SignalType): String = value.value

    @TypeConverter
    fun toSignalType(value: String): SignalType = SignalType.from(value)

    @TypeConverter
    fun fromSignalStatus(value: SignalStatus): String = value.value

    @TypeConverter
    fun toSignalStatus(value: String): SignalStatus = SignalStatus.from(value)

    @TypeConverter
    fun fromMarketTrend(value: MarketTrend): String = value.value

    @TypeConverter
    fun toMarketTrend(value: String): MarketTrend = MarketTrend.from(value)
}
