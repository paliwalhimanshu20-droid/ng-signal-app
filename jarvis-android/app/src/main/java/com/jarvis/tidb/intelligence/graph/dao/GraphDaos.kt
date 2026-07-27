package com.jarvis.tidb.intelligence.graph.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.jarvis.tidb.intelligence.graph.entity.CausalObservationEntity
import com.jarvis.tidb.intelligence.graph.entity.CorrelationEntity
import com.jarvis.tidb.intelligence.graph.entity.EntityRelationshipEntity
import com.jarvis.tidb.intelligence.graph.entity.MarketContextEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EntityRelationshipDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(relationship: EntityRelationshipEntity): Long

    @Query("SELECT * FROM entity_relationships WHERE fromEntityType = :entityType AND fromEntityRowId = :entityRowId")
    fun observeOutgoing(entityType: String, entityRowId: Long): Flow<List<EntityRelationshipEntity>>

    @Query("SELECT * FROM entity_relationships WHERE toEntityType = :entityType AND toEntityRowId = :entityRowId")
    fun observeIncoming(entityType: String, entityRowId: Long): Flow<List<EntityRelationshipEntity>>
}

@Dao
interface MarketContextDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(context: MarketContextEntity): Long

    @Query("SELECT * FROM market_contexts WHERE instrumentId = :instrumentId ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(instrumentId: Long, limit: Int = 50): Flow<List<MarketContextEntity>>

    @Query("SELECT * FROM market_contexts WHERE contextId = :contextId")
    suspend fun findById(contextId: Long): MarketContextEntity?
}

@Dao
interface CausalObservationDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(observation: CausalObservationEntity): Long

    @Query("SELECT * FROM causal_observations WHERE causeEntityType = :entityType AND causeEntityRowId = :entityRowId ORDER BY observedAt DESC")
    fun observeByCause(entityType: String, entityRowId: Long): Flow<List<CausalObservationEntity>>

    @Query("SELECT * FROM causal_observations WHERE effectEntityType = :entityType AND effectEntityRowId = :entityRowId ORDER BY observedAt DESC")
    fun observeByEffect(entityType: String, entityRowId: Long): Flow<List<CausalObservationEntity>>
}

@Dao
interface CorrelationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(correlation: CorrelationEntity): Long

    @Query(
        "SELECT * FROM correlations WHERE (entityAType = :entityType AND entityARowId = :entityRowId) " +
            "OR (entityBType = :entityType AND entityBRowId = :entityRowId) ORDER BY computedAt DESC"
    )
    fun observeForEntity(entityType: String, entityRowId: Long): Flow<List<CorrelationEntity>>
}
