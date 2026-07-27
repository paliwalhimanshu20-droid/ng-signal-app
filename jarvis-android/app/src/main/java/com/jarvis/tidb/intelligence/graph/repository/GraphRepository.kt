package com.jarvis.tidb.intelligence.graph.repository

import com.jarvis.tidb.intelligence.graph.entity.CausalObservationEntity
import com.jarvis.tidb.intelligence.graph.entity.CorrelationEntity
import com.jarvis.tidb.intelligence.graph.entity.EntityRelationshipEntity
import com.jarvis.tidb.intelligence.graph.entity.GraphEntityType
import com.jarvis.tidb.intelligence.graph.entity.MarketContextEntity
import kotlinx.coroutines.flow.Flow

interface GraphRepository {
    suspend fun relate(relationship: EntityRelationshipEntity): Long
    fun observeOutgoingRelationships(entityType: GraphEntityType, entityRowId: Long): Flow<List<EntityRelationshipEntity>>
    fun observeIncomingRelationships(entityType: GraphEntityType, entityRowId: Long): Flow<List<EntityRelationshipEntity>>

    suspend fun recordContext(context: MarketContextEntity): Long
    fun observeRecentContext(instrumentId: Long, limit: Int = 50): Flow<List<MarketContextEntity>>
    suspend fun getContext(contextId: Long): MarketContextEntity?

    suspend fun recordCausalObservation(observation: CausalObservationEntity): Long
    fun observeCausesOf(entityType: GraphEntityType, entityRowId: Long): Flow<List<CausalObservationEntity>>
    fun observeEffectsOf(entityType: GraphEntityType, entityRowId: Long): Flow<List<CausalObservationEntity>>

    suspend fun recordCorrelation(correlation: CorrelationEntity): Long
    fun observeCorrelationsFor(entityType: GraphEntityType, entityRowId: Long): Flow<List<CorrelationEntity>>
}
