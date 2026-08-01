package com.jarvis.tidb.context.repository.impl

import com.jarvis.tidb.context.dao.CalibrationMetricDao
import com.jarvis.tidb.context.dao.DriftMetricDao
import com.jarvis.tidb.context.dao.EconomicEventCategoryDao
import com.jarvis.tidb.context.dao.EconomicEventCategoryLinkDao
import com.jarvis.tidb.context.dao.EconomicEventDao
import com.jarvis.tidb.context.dao.EconomicEventInstrumentLinkDao
import com.jarvis.tidb.context.dao.EconomicEventOutcomeDao
import com.jarvis.tidb.context.entity.CalibrationMetricEntity
import com.jarvis.tidb.context.entity.CalibrationMetricType
import com.jarvis.tidb.context.entity.ContextMonitoringSubjectType
import com.jarvis.tidb.context.entity.DriftMetricEntity
import com.jarvis.tidb.context.entity.DriftSeverity
import com.jarvis.tidb.context.entity.EconomicEventCategoryEntity
import com.jarvis.tidb.context.entity.EconomicEventCategoryLinkEntity
import com.jarvis.tidb.context.entity.EconomicEventEntity
import com.jarvis.tidb.context.entity.EconomicEventInstrumentLinkEntity
import com.jarvis.tidb.context.entity.EconomicEventLinkScope
import com.jarvis.tidb.context.entity.EconomicEventOutcomeEntity
import com.jarvis.tidb.context.entity.EconomicEventStatus
import com.jarvis.tidb.context.repository.MarketContextIntelligenceRepository
import kotlinx.coroutines.flow.Flow

class MarketContextIntelligenceRepositoryImpl(
    private val eventDao: EconomicEventDao,
    private val categoryDao: EconomicEventCategoryDao,
    private val categoryLinkDao: EconomicEventCategoryLinkDao,
    private val instrumentLinkDao: EconomicEventInstrumentLinkDao,
    private val outcomeDao: EconomicEventOutcomeDao,
    private val driftMetricDao: DriftMetricDao,
    private val calibrationMetricDao: CalibrationMetricDao
) : MarketContextIntelligenceRepository {

    // ---- economic events ----

    override suspend fun ingestEvent(
        event: EconomicEventEntity,
        categoryLinks: List<EconomicEventCategoryLinkEntity>,
        instrumentLinks: List<EconomicEventInstrumentLinkEntity>
    ): Long {
        if (event.providerCode != null && event.externalEventId != null) {
            val existing = eventDao.findByExternalId(event.providerCode, event.externalEventId)
            if (existing != null) return existing.eventId
        }

        val eventId = eventDao.insert(event)
        if (categoryLinks.isNotEmpty()) {
            categoryLinkDao.insertAll(categoryLinks.map { it.copy(eventId = eventId) })
        }
        if (instrumentLinks.isNotEmpty()) {
            instrumentLinkDao.insertAll(instrumentLinks.map { it.copy(eventId = eventId) })
        }
        return eventId
    }

    override suspend fun updateEventStatus(eventId: Long, status: EconomicEventStatus) {
        val event = eventDao.findById(eventId) ?: return
        eventDao.update(event.copy(status = status, audit = event.audit.touched()))
    }

    override suspend fun getEvent(eventId: Long): EconomicEventEntity? = eventDao.findById(eventId)

    override suspend fun findEventByExternalId(providerCode: String, externalEventId: String): EconomicEventEntity? =
        eventDao.findByExternalId(providerCode, externalEventId)

    override fun observeEventsScheduledBetween(startInclusive: Long, endInclusive: Long): Flow<List<EconomicEventEntity>> =
        eventDao.observeScheduledBetween(startInclusive, endInclusive)

    override fun observeEventsByStatus(status: EconomicEventStatus, limit: Int): Flow<List<EconomicEventEntity>> =
        eventDao.observeByStatus(status, limit)

    override fun observeRecentEvents(limit: Int): Flow<List<EconomicEventEntity>> = eventDao.observeRecent(limit)

    override fun observeEventsByKey(eventKey: String, limit: Int): Flow<List<EconomicEventEntity>> =
        eventDao.observeByEventKey(eventKey, limit)

    // ---- categories ----

    override suspend fun registerEventCategory(category: EconomicEventCategoryEntity): Long = categoryDao.insert(category)

    override suspend fun getEventCategoryByCode(code: String): EconomicEventCategoryEntity? = categoryDao.findByCode(code)

    override fun observeActiveEventCategories(): Flow<List<EconomicEventCategoryEntity>> = categoryDao.observeActive()

    override fun observeCategoryLinksForEvent(eventId: Long): Flow<List<EconomicEventCategoryLinkEntity>> =
        categoryLinkDao.observeForEvent(eventId)

    // ---- instrument / sector / asset / index links ----

    override fun observeInstrumentLinksForEvent(eventId: Long): Flow<List<EconomicEventInstrumentLinkEntity>> =
        instrumentLinkDao.observeForEvent(eventId)

    override fun observeRecentEventsForInstrument(instrumentId: Long, limit: Int): Flow<List<EconomicEventInstrumentLinkEntity>> =
        instrumentLinkDao.observeRecentForInstrument(instrumentId, limit)

    override fun observeRecentEventsForScopeLabel(scope: EconomicEventLinkScope, scopeLabel: String, limit: Int): Flow<List<EconomicEventInstrumentLinkEntity>> =
        instrumentLinkDao.observeRecentForScopeLabel(scope, scopeLabel, limit)

    // ---- outcomes ----

    override suspend fun recordEventOutcome(outcome: EconomicEventOutcomeEntity): Long = outcomeDao.insert(outcome)

    override fun observeOutcomesForEvent(eventId: Long): Flow<List<EconomicEventOutcomeEntity>> =
        outcomeDao.observeForEvent(eventId)

    override suspend fun getLatestOutcome(eventId: Long): EconomicEventOutcomeEntity? = outcomeDao.findLatestForEvent(eventId)

    override fun observeOutcomeRevisions(outcomeId: Long): Flow<List<EconomicEventOutcomeEntity>> =
        outcomeDao.observeRevisionsOf(outcomeId)

    // ---- drift monitoring ----

    override suspend fun recordDriftMetric(metric: DriftMetricEntity): Long = driftMetricDao.insert(metric)

    override fun observeDriftForSubject(subjectType: ContextMonitoringSubjectType, subjectRowId: Long): Flow<List<DriftMetricEntity>> =
        driftMetricDao.observeForSubject(subjectType, subjectRowId)

    override suspend fun getLatestDrift(subjectType: ContextMonitoringSubjectType, subjectRowId: Long, metricKey: String): DriftMetricEntity? =
        driftMetricDao.findLatest(subjectType, subjectRowId, metricKey)

    override fun observeDriftBySeverity(severity: DriftSeverity, limit: Int): Flow<List<DriftMetricEntity>> =
        driftMetricDao.observeBySeverity(severity, limit)

    // ---- calibration monitoring ----

    override suspend fun recordCalibrationMetric(metric: CalibrationMetricEntity): Long = calibrationMetricDao.insert(metric)

    override fun observeCalibrationForSubject(subjectType: ContextMonitoringSubjectType, subjectRowId: Long): Flow<List<CalibrationMetricEntity>> =
        calibrationMetricDao.observeForSubject(subjectType, subjectRowId)

    override suspend fun getLatestCalibration(subjectType: ContextMonitoringSubjectType, subjectRowId: Long, metricType: CalibrationMetricType): CalibrationMetricEntity? =
        calibrationMetricDao.findLatest(subjectType, subjectRowId, metricType)

    override fun observeCalibrationByType(metricType: CalibrationMetricType, limit: Int): Flow<List<CalibrationMetricEntity>> =
        calibrationMetricDao.observeByMetricType(metricType, limit)

    override fun observeRecalibrationEvents(limit: Int): Flow<List<CalibrationMetricEntity>> =
        calibrationMetricDao.observeRecalibrationEvents(limit)
}
