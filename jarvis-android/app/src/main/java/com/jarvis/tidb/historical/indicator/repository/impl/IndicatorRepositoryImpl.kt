package com.jarvis.tidb.historical.indicator.repository.impl

import com.jarvis.tidb.historical.indicator.dao.IndicatorComputationRunDao
import com.jarvis.tidb.historical.indicator.dao.IndicatorDefinitionDao
import com.jarvis.tidb.historical.indicator.dao.IndicatorValueDao
import com.jarvis.tidb.historical.indicator.entity.ComputationStatus
import com.jarvis.tidb.historical.indicator.entity.IndicatorComputationRunEntity
import com.jarvis.tidb.historical.indicator.entity.IndicatorDefinitionEntity
import com.jarvis.tidb.historical.indicator.entity.IndicatorValueEntity
import com.jarvis.tidb.historical.indicator.repository.IndicatorComputationRunRepository
import com.jarvis.tidb.historical.indicator.repository.IndicatorDefinitionRepository
import com.jarvis.tidb.historical.indicator.repository.IndicatorValueRepository
import kotlinx.coroutines.flow.Flow

class IndicatorDefinitionRepositoryImpl(private val dao: IndicatorDefinitionDao) : IndicatorDefinitionRepository {
    override suspend fun define(definition: IndicatorDefinitionEntity): Long = dao.insert(definition)
    override suspend fun getById(id: Long): IndicatorDefinitionEntity? = dao.findById(id)
    override suspend fun getLatestByName(name: String): IndicatorDefinitionEntity? = dao.findLatestByName(name)
    override fun observeByType(type: String): Flow<List<IndicatorDefinitionEntity>> = dao.observeByType(type)
    override fun observeActive(): Flow<List<IndicatorDefinitionEntity>> = dao.observeActive()

    override suspend fun createNewVersion(definition: IndicatorDefinitionEntity, newParamsJson: String): Long =
        dao.insert(
            definition.copy(
                indicatorDefId = 0L,
                uuid = com.jarvis.tidb.core.common.GlobalId.new(),
                paramsJson = newParamsJson,
                definitionVersion = definition.definitionVersion + 1,
                audit = definition.audit.touched()
            )
        )
}

class IndicatorValueRepositoryImpl(private val dao: IndicatorValueDao) : IndicatorValueRepository {
    override suspend fun storeValues(values: List<IndicatorValueEntity>): List<Long> = dao.insertAll(values)

    override fun observeRange(
        indicatorDefId: Long,
        instrumentId: Long,
        timeframe: String,
        fromTs: Long,
        toTs: Long
    ): Flow<List<IndicatorValueEntity>> = dao.observeRange(indicatorDefId, instrumentId, timeframe, fromTs, toTs)

    override suspend fun getLatest(indicatorDefId: Long, instrumentId: Long, timeframe: String, limit: Int): List<IndicatorValueEntity> =
        dao.getLatest(indicatorDefId, instrumentId, timeframe, limit)

    override suspend fun countInRange(indicatorDefId: Long, instrumentId: Long, timeframe: String, fromTs: Long, toTs: Long): Int =
        dao.countInRange(indicatorDefId, instrumentId, timeframe, fromTs, toTs)

    override suspend fun discardVersion(indicatorDefId: Long, instrumentId: Long, timeframe: String, version: Int) =
        dao.deleteVersion(indicatorDefId, instrumentId, timeframe, version)
}

class IndicatorComputationRunRepositoryImpl(
    private val dao: IndicatorComputationRunDao
) : IndicatorComputationRunRepository {

    override suspend fun startRun(run: IndicatorComputationRunEntity): Long =
        dao.insert(run.copy(status = ComputationStatus.RUNNING, startedAt = System.currentTimeMillis()))

    override suspend fun completeRun(runId: Long, rowsComputed: Long) {
        val run = dao.findById(runId) ?: return
        dao.update(
            run.copy(
                status = ComputationStatus.COMPLETED,
                rowsComputed = rowsComputed,
                completedAt = System.currentTimeMillis(),
                audit = run.audit.touched()
            )
        )
    }

    override suspend fun failRun(runId: Long, error: String) {
        val run = dao.findById(runId) ?: return
        dao.update(
            run.copy(
                status = ComputationStatus.FAILED,
                error = error,
                completedAt = System.currentTimeMillis(),
                audit = run.audit.touched()
            )
        )
    }

    override fun observeActive(): Flow<List<IndicatorComputationRunEntity>> = dao.observeActive()

    override suspend fun getLastRun(indicatorDefId: Long, instrumentId: Long, timeframe: String): IndicatorComputationRunEntity? =
        dao.getLastRun(indicatorDefId, instrumentId, timeframe)
}
