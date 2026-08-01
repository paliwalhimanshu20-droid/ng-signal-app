package com.jarvis.tidb.analytics.repository

import com.jarvis.tidb.analytics.entity.TradeEntity
import com.jarvis.tidb.analytics.entity.TradeExecutionEntity
import com.jarvis.tidb.analytics.entity.TradeExitEntity
import com.jarvis.tidb.analytics.entity.TradeFeesEntity
import com.jarvis.tidb.analytics.entity.TradeFullHistory
import com.jarvis.tidb.analytics.entity.TradeJournalEntity
import com.jarvis.tidb.analytics.entity.TradeStatus
import com.jarvis.tidb.analytics.entity.TradeWithDetails
import kotlinx.coroutines.flow.Flow

/**
 * Owns [TradeEntity] and everything hanging off it (executions, exits, fees, journal).
 * `signalId` and `instrumentId` are validated against Module 2's `SignalRepository` and
 * Module 1's `InstrumentRepository` inside the implementation — this module never opens
 * either module's Room database directly.
 */
interface TradeRepository {

    suspend fun recordTrade(trade: TradeEntity): Long

    suspend fun updateTrade(trade: TradeEntity)

    suspend fun getTrade(rowId: Long): TradeEntity?

    suspend fun getTradeByUuid(uuid: String): TradeEntity?

    fun observeAllTrades(): Flow<List<TradeEntity>>

    fun observeTradesBySignal(signalId: Long): Flow<List<TradeEntity>>

    fun observeTradesByInstrument(instrumentId: Long): Flow<List<TradeEntity>>

    fun observeTradesByDateRange(startMillis: Long, endMillis: Long): Flow<List<TradeEntity>>

    fun observeTradesByStatus(status: TradeStatus): Flow<List<TradeEntity>>

    fun observeTradesByStrategy(strategyId: String): Flow<List<TradeEntity>>

    fun observeOpenTrades(): Flow<List<TradeEntity>>

    suspend fun getTradeWithDetails(rowId: Long): TradeWithDetails?

    suspend fun getTradeFullHistory(rowId: Long): TradeFullHistory?

    suspend fun softDeleteTrade(rowId: Long)

    suspend fun recordExecution(execution: TradeExecutionEntity): Long

    fun observeExecutions(tradeRowId: Long): Flow<List<TradeExecutionEntity>>

    suspend fun recordExit(exit: TradeExitEntity): Long

    fun observeExits(tradeRowId: Long): Flow<List<TradeExitEntity>>

    suspend fun recordFee(fee: TradeFeesEntity): Long

    fun observeFees(tradeRowId: Long): Flow<List<TradeFeesEntity>>

    suspend fun totalFees(tradeRowId: Long): Double

    suspend fun addJournalEntry(entry: TradeJournalEntity): Long

    fun observeJournal(tradeRowId: Long): Flow<List<TradeJournalEntity>>
}
