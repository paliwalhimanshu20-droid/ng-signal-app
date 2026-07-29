package com.jarvis.tidb.context.repository

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
import kotlinx.coroutines.flow.Flow

/**
 * Single facade over the Market Context Intelligence Platform's 7 tables. No decisioning, no
 * scoring, no Decision Engine logic implemented here — pure storage and retrieval, matching
 * every other module's convention (see `news.repository.NewsRepository`'s equivalent doc note).
 *
 * Consumers (Evidence Engine, Learning Platform, future Decision Engine) read this repository
 * directly rather than this repository reaching into theirs — keeps the dependency direction
 * one-way and avoids a circular module dependency, the same reasoning
 * `NewsRepository` documents for why article-to-evidence promotion is a caller-side
 * orchestration rather than a method here.
 */
interface MarketContextIntelligenceRepository {

    // ---- economic events ----
    /**
     * Inserts [event] along with its category links and instrument/sector/asset/index links as
     * one unit, wiring the generated eventId into each — mirrors
     * `NewsRepository.ingestArticle`'s "record the parent, then wire children" shape. Returns
     * the existing eventId instead of inserting if [event]'s `(providerCode, externalEventId)`
     * pair already exists (exact-dedup), matching the same convention.
     */
    suspend fun ingestEvent(
        event: EconomicEventEntity,
        categoryLinks: List<EconomicEventCategoryLinkEntity> = emptyList(),
        instrumentLinks: List<EconomicEventInstrumentLinkEntity> = emptyList()
    ): Long

    suspend fun updateEventStatus(eventId: Long, status: EconomicEventStatus)
    suspend fun getEvent(eventId: Long): EconomicEventEntity?
    suspend fun findEventByExternalId(providerCode: String, externalEventId: String): EconomicEventEntity?
    fun observeEventsScheduledBetween(startInclusive: Long, endInclusive: Long): Flow<List<EconomicEventEntity>>
    fun observeEventsByStatus(status: EconomicEventStatus, limit: Int = 200): Flow<List<EconomicEventEntity>>
    fun observeRecentEvents(limit: Int = 100): Flow<List<EconomicEventEntity>>
    fun observeEventsByKey(eventKey: String, limit: Int = 50): Flow<List<EconomicEventEntity>>

    // ---- categories ----
    suspend fun registerEventCategory(category: EconomicEventCategoryEntity): Long
    suspend fun getEventCategoryByCode(code: String): EconomicEventCategoryEntity?
    fun observeActiveEventCategories(): Flow<List<EconomicEventCategoryEntity>>
    fun observeCategoryLinksForEvent(eventId: Long): Flow<List<EconomicEventCategoryLinkEntity>>

    // ---- instrument / sector / asset / index links ----
    fun observeInstrumentLinksForEvent(eventId: Long): Flow<List<EconomicEventInstrumentLinkEntity>>
    /** Primary read pattern: upcoming/recent macro events for one instrument, most-recent first. */
    fun observeRecentEventsForInstrument(instrumentId: Long, limit: Int = 100): Flow<List<EconomicEventInstrumentLinkEntity>>
    /** Same read pattern, scoped by sector/asset-class/index label rather than a specific instrument. */
    fun observeRecentEventsForScopeLabel(scope: EconomicEventLinkScope, scopeLabel: String, limit: Int = 100): Flow<List<EconomicEventInstrumentLinkEntity>>

    // ---- outcomes ----
    /** Records [outcome] as-is (insert-only). To record a revision, set `outcome.revisesOutcomeId` to the row being revised. */
    suspend fun recordEventOutcome(outcome: EconomicEventOutcomeEntity): Long
    fun observeOutcomesForEvent(eventId: Long): Flow<List<EconomicEventOutcomeEntity>>
    /** The current (not-yet-revised) outcome for the event, or null if none recorded. */
    suspend fun getLatestOutcome(eventId: Long): EconomicEventOutcomeEntity?
    fun observeOutcomeRevisions(outcomeId: Long): Flow<List<EconomicEventOutcomeEntity>>

    // ---- drift monitoring ----
    suspend fun recordDriftMetric(metric: DriftMetricEntity): Long
    fun observeDriftForSubject(subjectType: ContextMonitoringSubjectType, subjectRowId: Long): Flow<List<DriftMetricEntity>>
    suspend fun getLatestDrift(subjectType: ContextMonitoringSubjectType, subjectRowId: Long, metricKey: String): DriftMetricEntity?
    fun observeDriftBySeverity(severity: DriftSeverity, limit: Int = 100): Flow<List<DriftMetricEntity>>

    // ---- calibration monitoring ----
    suspend fun recordCalibrationMetric(metric: CalibrationMetricEntity): Long
    fun observeCalibrationForSubject(subjectType: ContextMonitoringSubjectType, subjectRowId: Long): Flow<List<CalibrationMetricEntity>>
    suspend fun getLatestCalibration(subjectType: ContextMonitoringSubjectType, subjectRowId: Long, metricType: CalibrationMetricType): CalibrationMetricEntity?
    fun observeCalibrationByType(metricType: CalibrationMetricType, limit: Int = 100): Flow<List<CalibrationMetricEntity>>
    fun observeRecalibrationEvents(limit: Int = 100): Flow<List<CalibrationMetricEntity>>
}
