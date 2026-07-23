package com.jarvis.tidb.analytics.repository.impl

import com.jarvis.tidb.analytics.dao.TradeDao
import com.jarvis.tidb.analytics.dao.TradeExecutionDao
import com.jarvis.tidb.analytics.dao.TradeExitDao
import com.jarvis.tidb.analytics.dao.TradeFeesDao
import com.jarvis.tidb.analytics.dao.TradeJournalDao
import com.jarvis.tidb.analytics.entity.TradeEntity
import com.jarvis.tidb.analytics.entity.TradeExecutionEntity
import com.jarvis.tidb.analytics.entity.TradeExitEntity
import com.jarvis.tidb.analytics.entity.TradeFeesEntity
import com.jarvis.tidb.analytics.entity.TradeFullHistory
import com.jarvis.tidb.analytics.entity.TradeJournalEntity
import com.jarvis.tidb.analytics.entity.TradeStatus
import com.jarvis.tidb.analytics.entity.TradeWithDetails
import com.jarvis.tidb.analytics.repository.TradeRepository
import com.jarvis.tidb.core.repository.InstrumentRepository
import com.jarvis.tidb.signals.repository.SignalRepository
import kotlinx.coroutines.flow.Flow

/**
 * `signalRepository` and `instrumentRepository` are injected purely for existence validation
 * before a trade is recorded — this class never touches Module 1/2's Room databases, only
 * their repository interfaces, per the "consume Module 1 and Module 2 only through
 * repositories" requirement.
 */
class TradeRepositoryImpl(
    private val tradeDao: TradeDao,
    private val executionDao: TradeExecutionDao,
    private val exitDao: TradeExitDao,
    private val feesDao: TradeFeesDao,
    private val journalDao: TradeJournalDao,
    private val signalRepository: SignalRepository,
    private val instrumentRepository: InstrumentRepository
) : TradeRepository {

    override suspend fun recordTrade(trade: TradeEntity): Long {
        require(signalRepository.exists(trade.signalId)) { "Signal ${trade.signalId} does not exist in Module 2" }
        require(instrumentRepository.exists(trade.instrumentId)) { "Instrument ${trade.instrumentId} does not exist in Module 1" }
        return tradeDao.insert(trade)
    }

    override suspend fun updateTrade(trade: TradeEntity) = tradeDao.update(trade)

    override suspend fun getTrade(rowId: Long): TradeEntity? = tradeDao.findByRowId(rowId)

    override suspend fun getTradeByUuid(uuid: String): TradeEntity? = tradeDao.findByUuid(uuid)

    override fun observeAllTrades(): Flow<List<TradeEntity>> = tradeDao.observeAll()

    override fun observeTradesBySignal(signalId: Long): Flow<List<TradeEntity>> = tradeDao.observeBySignal(signalId)

    override fun observeTradesByInstrument(instrumentId: Long): Flow<List<TradeEntity>> =
        tradeDao.observeByInstrument(instrumentId)

    override fun observeTradesByDateRange(startMillis: Long, endMillis: Long): Flow<List<TradeEntity>> =
        tradeDao.observeByDateRange(startMillis, endMillis)

    override fun observeTradesByStatus(status: TradeStatus): Flow<List<TradeEntity>> = tradeDao.observeByStatus(status)

    override fun observeTradesByStrategy(strategyId: String): Flow<List<TradeEntity>> =
        tradeDao.observeByStrategy(strategyId)

    override fun observeOpenTrades(): Flow<List<TradeEntity>> = tradeDao.observeOpenTrades()

    override suspend fun getTradeWithDetails(rowId: Long): TradeWithDetails? = tradeDao.getWithDetails(rowId)

    override suspend fun getTradeFullHistory(rowId: Long): TradeFullHistory? = tradeDao.getFullHistory(rowId)

    override suspend fun softDeleteTrade(rowId: Long) = tradeDao.softDelete(rowId)

    override suspend fun recordExecution(execution: TradeExecutionEntity): Long = executionDao.insert(execution)

    override fun observeExecutions(tradeRowId: Long): Flow<List<TradeExecutionEntity>> =
        executionDao.observeByTrade(tradeRowId)

    override suspend fun recordExit(exit: TradeExitEntity): Long = exitDao.insert(exit)

    override fun observeExits(tradeRowId: Long): Flow<List<TradeExitEntity>> = exitDao.observeByTrade(tradeRowId)

    override suspend fun recordFee(fee: TradeFeesEntity): Long = feesDao.insert(fee)

    override fun observeFees(tradeRowId: Long): Flow<List<TradeFeesEntity>> = feesDao.observeByTrade(tradeRowId)

    override suspend fun totalFees(tradeRowId: Long): Double = feesDao.totalFeesForTrade(tradeRowId)

    override suspend fun addJournalEntry(entry: TradeJournalEntity): Long = journalDao.insert(entry)

    override fun observeJournal(tradeRowId: Long): Flow<List<TradeJournalEntity>> = journalDao.observeByTrade(tradeRowId)
}
