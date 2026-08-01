package com.jarvis.tidb.analytics.repository

import com.jarvis.tidb.analytics.entity.DecisionExplanationEntity
import com.jarvis.tidb.analytics.entity.DecisionOutcome
import com.jarvis.tidb.analytics.entity.DecisionRecordEntity
import com.jarvis.tidb.analytics.entity.DecisionWithExplanations
import com.jarvis.tidb.analytics.entity.LessonLearnedEntity
import com.jarvis.tidb.analytics.entity.TimelineEventType
import com.jarvis.tidb.analytics.entity.TradingTimelineEventEntity
import kotlinx.coroutines.flow.Flow

/** The permanent, append-only executive trading memory. "Nothing should ever be permanently lost." */
interface TimelineRepository {

    suspend fun recordEvent(event: TradingTimelineEventEntity): Long

    fun observeEvents(): Flow<List<TradingTimelineEventEntity>>

    fun observeEventsByType(eventType: TimelineEventType): Flow<List<TradingTimelineEventEntity>>

    fun observeEventsByDateRange(startMillis: Long, endMillis: Long): Flow<List<TradingTimelineEventEntity>>

    fun observeEventsByTrade(tradeRowId: Long): Flow<List<TradingTimelineEventEntity>>

    fun observeEventsByInstrument(instrumentId: Long): Flow<List<TradingTimelineEventEntity>>

    fun observeEventsBySeverity(severity: String): Flow<List<TradingTimelineEventEntity>>

    suspend fun recordDecision(decision: DecisionRecordEntity): Long

    suspend fun updateDecision(decision: DecisionRecordEntity)

    fun observeDecisionsByTrade(tradeRowId: Long): Flow<List<DecisionRecordEntity>>

    fun observeDecisionsByOutcome(outcome: DecisionOutcome): Flow<List<DecisionRecordEntity>>

    suspend fun getDecisionWithExplanations(rowId: Long): DecisionWithExplanations?

    suspend fun addExplanation(explanation: DecisionExplanationEntity): Long

    fun observeExplanations(decisionRowId: Long): Flow<List<DecisionExplanationEntity>>

    suspend fun recordLesson(lesson: LessonLearnedEntity): Long

    fun observeLessons(): Flow<List<LessonLearnedEntity>>

    fun observeLessonsByMinImportance(minImportance: Double): Flow<List<LessonLearnedEntity>>
}
