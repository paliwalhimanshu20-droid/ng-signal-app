package com.jarvis.tidb.analytics.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.jarvis.tidb.analytics.entity.DecisionExplanationEntity
import com.jarvis.tidb.analytics.entity.DecisionOutcome
import com.jarvis.tidb.analytics.entity.DecisionRecordEntity
import com.jarvis.tidb.analytics.entity.DecisionWithExplanations
import com.jarvis.tidb.analytics.entity.LessonLearnedEntity
import com.jarvis.tidb.analytics.entity.TimelineEventType
import com.jarvis.tidb.analytics.entity.TradingTimelineEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TradingTimelineEventDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(event: TradingTimelineEventEntity): Long

    @Query("SELECT * FROM trading_timeline_events ORDER BY occurredAt DESC")
    fun observeAll(): Flow<List<TradingTimelineEventEntity>>

    @Query("SELECT * FROM trading_timeline_events WHERE eventType = :eventType ORDER BY occurredAt DESC")
    fun observeByType(eventType: TimelineEventType): Flow<List<TradingTimelineEventEntity>>

    @Query("SELECT * FROM trading_timeline_events WHERE occurredAt BETWEEN :startMillis AND :endMillis ORDER BY occurredAt DESC")
    fun observeByDateRange(startMillis: Long, endMillis: Long): Flow<List<TradingTimelineEventEntity>>

    @Query("SELECT * FROM trading_timeline_events WHERE relatedTradeRowId = :tradeRowId ORDER BY occurredAt ASC")
    fun observeByTrade(tradeRowId: Long): Flow<List<TradingTimelineEventEntity>>

    @Query("SELECT * FROM trading_timeline_events WHERE relatedInstrumentId = :instrumentId ORDER BY occurredAt DESC")
    fun observeByInstrument(instrumentId: Long): Flow<List<TradingTimelineEventEntity>>

    @Query("SELECT * FROM trading_timeline_events WHERE severity = :severity ORDER BY occurredAt DESC")
    fun observeBySeverity(severity: String): Flow<List<TradingTimelineEventEntity>>
}

@Dao
interface DecisionRecordDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(decision: DecisionRecordEntity): Long

    @Update
    suspend fun update(decision: DecisionRecordEntity)

    @Query("SELECT * FROM decision_records WHERE relatedTradeRowId = :tradeRowId ORDER BY decidedAt DESC")
    fun observeByTrade(tradeRowId: Long): Flow<List<DecisionRecordEntity>>

    @Query("SELECT * FROM decision_records WHERE outcome = :outcome ORDER BY decidedAt DESC")
    fun observeByOutcome(outcome: DecisionOutcome): Flow<List<DecisionRecordEntity>>

    @Transaction
    @Query("SELECT * FROM decision_records WHERE rowId = :rowId")
    suspend fun getWithExplanations(rowId: Long): DecisionWithExplanations?
}

@Dao
interface DecisionExplanationDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(explanation: DecisionExplanationEntity): Long

    @Query("SELECT * FROM decision_explanations WHERE decisionRecordRowId = :decisionRowId ORDER BY generatedAt ASC")
    fun observeByDecision(decisionRowId: Long): Flow<List<DecisionExplanationEntity>>
}

@Dao
interface LessonLearnedDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(lesson: LessonLearnedEntity): Long

    @Query("SELECT * FROM lessons_learned ORDER BY recordedAt DESC")
    fun observeAll(): Flow<List<LessonLearnedEntity>>

    @Query("SELECT * FROM lessons_learned WHERE importance >= :minImportance ORDER BY importance DESC")
    fun observeByMinImportance(minImportance: Double): Flow<List<LessonLearnedEntity>>
}
