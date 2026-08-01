package com.jarvis.tidb.intelligence.graph.repository.impl

import com.jarvis.tidb.intelligence.graph.dao.CausalObservationDao
import com.jarvis.tidb.intelligence.graph.dao.CorrelationDao
import com.jarvis.tidb.intelligence.graph.dao.EntityRelationshipDao
import com.jarvis.tidb.intelligence.graph.dao.MarketContextDao
import com.jarvis.tidb.intelligence.graph.entity.CausalObservationEntity
import com.jarvis.tidb.intelligence.graph.entity.CorrelationEntity
import com.jarvis.tidb.intelligence.graph.entity.EntityRelationshipEntity
import com.jarvis.tidb.intelligence.graph.entity.GraphEntityType
import com.jarvis.tidb.intelligence.graph.entity.MarketContextEntity
import com.jarvis.tidb.intelligence.graph.repository.GraphRepository
import kotlinx.coroutines.flow.Flow

class GraphRepositoryImpl(
    private val relationshipDao: EntityRelationshipDao,
    private val contextDao: MarketContextDao,
    private val causalDao: CausalObservationDao,
    private val correlationDao: CorrelationDao
) : GraphRepository {

    override suspend fun relate(relationship: EntityRelationshipEntity): Long = relationshipDao.insert(relationship)

    override fun observeOutgoingRelationships(entityType: GraphEntityType, entityRowId: Long): Flow<List<EntityRelationshipEntity>> =
        relationshipDao.observeOutgoing(entityType.value, entityRowId)

    override fun observeIncomingRelationships(entityType: GraphEntityType, entityRowId: Long): Flow<List<EntityRelationshipEntity>> =
        relationshipDao.observeIncoming(entityType.value, entityRowId)

    override suspend fun recordContext(context: MarketContextEntity): Long = contextDao.insert(context)

    override fun observeRecentContext(instrumentId: Long, limit: Int): Flow<List<MarketContextEntity>> =
        contextDao.observeRecent(instrumentId, limit)

    override suspend fun getContext(contextId: Long): MarketContextEntity? = contextDao.findById(contextId)

    override suspend fun recordCausalObservation(observation: CausalObservationEntity): Long = causalDao.insert(observation)

    override fun observeCausesOf(entityType: GraphEntityType, entityRowId: Long): Flow<List<CausalObservationEntity>> =
        causalDao.observeByEffect(entityType.value, entityRowId)

    override fun observeEffectsOf(entityType: GraphEntityType, entityRowId: Long): Flow<List<CausalObservationEntity>> =
        causalDao.observeByCause(entityType.value, entityRowId)

    override suspend fun recordCorrelation(correlation: CorrelationEntity): Long = correlationDao.insert(correlation)

    override fun observeCorrelationsFor(entityType: GraphEntityType, entityRowId: Long): Flow<List<CorrelationEntity>> =
        correlationDao.observeForEntity(entityType.value, entityRowId)
}
