package com.jarvis.tidb.historical.indicator.repository

import com.jarvis.tidb.historical.indicator.entity.IndicatorComputationRunEntity
import com.jarvis.tidb.historical.indicator.entity.IndicatorDefinitionEntity
import com.jarvis.tidb.historical.indicator.entity.IndicatorValueEntity
import kotlinx.coroutines.flow.Flow

interface IndicatorDefinitionRepository {
    suspend fun define(definition: IndicatorDefinitionEntity): Long
    suspend fun getById(id: Long): IndicatorDefinitionEntity?
    suspend fun getLatestByName(name: String): IndicatorDefinitionEntity?
    fun observeByType(type: String): Flow<List<IndicatorDefinitionEntity>>
    fun observeActive(): Flow<List<IndicatorDefinitionEntity>>

    /** Bumps [definition]'s version and stores it as a new definition row, preserving the old version's values untouched. */
    suspend fun createNewVersion(definition: IndicatorDefinitionEntity, newParamsJson: String): Long
}

/** Stores/reads computed indicator values. This module never recomputes on read — it only ever returns what a computation run already wrote. */
interface IndicatorValueRepository {
    suspend fun storeValues(values: List<IndicatorValueEntity>): List<Long>
    fun observeRange(indicatorDefId: Long, instrumentId: Long, timeframe: String, fromTs: Long, toTs: Long): Flow<List<IndicatorValueEntity>>
    suspend fun getLatest(indicatorDefId: Long, instrumentId: Long, timeframe: String, limit: Int = 1): List<IndicatorValueEntity>
    suspend fun countInRange(indicatorDefId: Long, instrumentId: Long, timeframe: String, fromTs: Long, toTs: Long): Int
    suspend fun discardVersion(indicatorDefId: Long, instrumentId: Long, timeframe: String, version: Int)
}

interface IndicatorComputationRunRepository {
    suspend fun startRun(run: IndicatorComputationRunEntity): Long
    suspend fun completeRun(runId: Long, rowsComputed: Long)
    suspend fun failRun(runId: Long, error: String)
    fun observeActive(): Flow<List<IndicatorComputationRunEntity>>
    suspend fun getLastRun(indicatorDefId: Long, instrumentId: Long, timeframe: String): IndicatorComputationRunEntity?
}
