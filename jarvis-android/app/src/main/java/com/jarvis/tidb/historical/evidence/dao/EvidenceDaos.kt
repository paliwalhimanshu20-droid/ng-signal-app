package com.jarvis.tidb.historical.evidence.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.jarvis.tidb.historical.evidence.entity.ConfidenceComponentEntity
import com.jarvis.tidb.historical.evidence.entity.EvidenceRecordEntity
import com.jarvis.tidb.historical.evidence.entity.MarketObservationEntity
import com.jarvis.tidb.historical.evidence.entity.PatternOccurrenceEntity
import com.jarvis.tidb.historical.evidence.entity.SourceReferenceEntity
import com.jarvis.tidb.historical.evidence.entity.SupportingIndicatorEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MarketObservationDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(observation: MarketObservationEntity): Long

    @Query("SELECT * FROM market_observations WHERE instrumentId = :instrumentId ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(instrumentId: Long, limit: Int = 100): Flow<List<MarketObservationEntity>>

    @Query("SELECT * FROM market_observations WHERE instrumentId = :instrumentId AND observationType = :type ORDER BY timestamp DESC")
    fun observeByType(instrumentId: Long, type: String): Flow<List<MarketObservationEntity>>
}

@Dao
interface EvidenceRecordDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(evidence: EvidenceRecordEntity): Long

    @Query("SELECT * FROM evidence_records WHERE evidenceId = :evidenceId")
    suspend fun findById(evidenceId: Long): EvidenceRecordEntity?

    @Query("SELECT * FROM evidence_records WHERE instrumentId = :instrumentId ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(instrumentId: Long, limit: Int = 100): Flow<List<EvidenceRecordEntity>>

    @Query("SELECT * FROM evidence_records WHERE instrumentId = :instrumentId AND evidenceType = :type ORDER BY timestamp DESC")
    fun observeByType(instrumentId: Long, type: String): Flow<List<EvidenceRecordEntity>>
}

@Dao
interface PatternOccurrenceDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(occurrence: PatternOccurrenceEntity): Long

    @Update
    suspend fun update(occurrence: PatternOccurrenceEntity)

    @Query("SELECT * FROM pattern_occurrences WHERE occurrenceId = :occurrenceId")
    suspend fun findById(occurrenceId: Long): PatternOccurrenceEntity?

    @Query("SELECT * FROM pattern_occurrences WHERE instrumentId = :instrumentId AND timeframe = :timeframe ORDER BY startTimestamp DESC")
    fun observeByInstrumentTimeframe(instrumentId: Long, timeframe: String): Flow<List<PatternOccurrenceEntity>>

    @Query("SELECT * FROM pattern_occurrences WHERE patternName = :patternName ORDER BY startTimestamp DESC")
    fun observeByPattern(patternName: String): Flow<List<PatternOccurrenceEntity>>

    @Query("SELECT * FROM pattern_occurrences WHERE outcome = 'PENDING' ORDER BY startTimestamp ASC")
    fun observePending(): Flow<List<PatternOccurrenceEntity>>
}

@Dao
interface SupportingIndicatorDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(links: List<SupportingIndicatorEntity>): List<Long>

    @Query("SELECT * FROM supporting_indicators WHERE evidenceId = :evidenceId")
    fun observeForEvidence(evidenceId: Long): Flow<List<SupportingIndicatorEntity>>
}

@Dao
interface ConfidenceComponentDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(components: List<ConfidenceComponentEntity>): List<Long>

    @Query("SELECT * FROM confidence_components WHERE evidenceId = :evidenceId")
    fun observeForEvidence(evidenceId: Long): Flow<List<ConfidenceComponentEntity>>
}

@Dao
interface SourceReferenceDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(references: List<SourceReferenceEntity>): List<Long>

    @Query("SELECT * FROM source_references WHERE evidenceId = :evidenceId")
    fun observeForEvidence(evidenceId: Long): Flow<List<SourceReferenceEntity>>
}
