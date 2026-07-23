package com.jarvis.tidb.analytics.repository.impl

import com.jarvis.tidb.analytics.dao.DecisionExplanationDao
import com.jarvis.tidb.analytics.dao.DecisionRecordDao
import com.jarvis.tidb.analytics.dao.LessonLearnedDao
import com.jarvis.tidb.analytics.dao.TradingTimelineEventDao
import com.jarvis.tidb.analytics.entity.DecisionExplanationEntity
import com.jarvis.tidb.analytics.entity.DecisionOutcome
import com.jarvis.tidb.analytics.entity.DecisionRecordEntity
import com.jarvis.tidb.analytics.entity.DecisionWithExplanations
import com.jarvis.tidb.analytics.entity.LessonLearnedEntity
import com.jarvis.tidb.analytics.entity.TimelineEventType
import com.jarvis.tidb.analytics.entity.TradingTimelineEventEntity
import com.jarvis.tidb.analytics.repository.TimelineRepository
import kotlinx.coroutines.flow.Flow

class TimelineRepositoryImpl(
    private val eventDao: TradingTimelineEventDao,
    private val decisionDao: DecisionRecordDao,
    private val explanationDao: DecisionExplanationDao,
    private val lessonDao: LessonLearnedDao
) : TimelineRepository {

    override suspend fun recordEvent(event: TradingTimelineEventEntity): Long = eventDao.insert(event)

    override fun observeEvents(): Flow<List<TradingTimelineEventEntity>> = eventDao.observeAll()

    override fun observeEventsByType(eventType: TimelineEventType): Flow<List<TradingTimelineEventEntity>> =
        eventDao.observeByType(eventType)

    override fun observeEventsByDateRange(startMillis: Long, endMillis: Long): Flow<List<TradingTimelineEventEntity>> =
        eventDao.observeByDateRange(startMillis, endMillis)

    override fun observeEventsByTrade(tradeRowId: Long): Flow<List<TradingTimelineEventEntity>> =
        eventDao.observeByTrade(tradeRowId)

    override fun observeEventsByInstrument(instrumentId: Long): Flow<List<TradingTimelineEventEntity>> =
        eventDao.observeByInstrument(instrumentId)

    override fun observeEventsBySeverity(severity: String): Flow<List<TradingTimelineEventEntity>> =
        eventDao.observeBySeverity(severity)

    override suspend fun recordDecision(decision: DecisionRecordEntity): Long = decisionDao.insert(decision)

    override suspend fun updateDecision(decision: DecisionRecordEntity) = decisionDao.update(decision)

    override fun observeDecisionsByTrade(tradeRowId: Long): Flow<List<DecisionRecordEntity>> =
        decisionDao.observeByTrade(tradeRowId)

    override fun observeDecisionsByOutcome(outcome: DecisionOutcome): Flow<List<DecisionRecordEntity>> =
        decisionDao.observeByOutcome(outcome)

    override suspend fun getDecisionWithExplanations(rowId: Long): DecisionWithExplanations? =
        decisionDao.getWithExplanations(rowId)

    override suspend fun addExplanation(explanation: DecisionExplanationEntity): Long =
        explanationDao.insert(explanation)

    override fun observeExplanations(decisionRowId: Long): Flow<List<DecisionExplanationEntity>> =
        explanationDao.observeByDecision(decisionRowId)

    override suspend fun recordLesson(lesson: LessonLearnedEntity): Long = lessonDao.insert(lesson)

    override fun observeLessons(): Flow<List<LessonLearnedEntity>> = lessonDao.observeAll()

    override fun observeLessonsByMinImportance(minImportance: Double): Flow<List<LessonLearnedEntity>> =
        lessonDao.observeByMinImportance(minImportance)
}
