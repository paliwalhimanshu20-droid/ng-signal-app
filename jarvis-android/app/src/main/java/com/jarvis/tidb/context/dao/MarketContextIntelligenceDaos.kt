package com.jarvis.tidb.context.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
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

@Dao
interface EconomicEventDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(event: EconomicEventEntity): Long

    @Update
    suspend fun update(event: EconomicEventEntity)

    @Query("SELECT * FROM economic_events WHERE eventId = :eventId")
    suspend fun findById(eventId: Long): EconomicEventEntity?

    @Query("SELECT * FROM economic_events WHERE providerCode = :providerCode AND externalEventId = :externalEventId LIMIT 1")
    suspend fun findByExternalId(providerCode: String, externalEventId: String): EconomicEventEntity?

    @Query("SELECT * FROM economic_events WHERE isDeleted = 0 AND scheduledAt BETWEEN :startInclusive AND :endInclusive ORDER BY scheduledAt ASC")
    fun observeScheduledBetween(startInclusive: Long, endInclusive: Long): Flow<List<EconomicEventEntity>>

    @Query("SELECT * FROM economic_events WHERE isDeleted = 0 AND status = :status ORDER BY scheduledAt ASC LIMIT :limit")
    fun observeByStatus(status: EconomicEventStatus, limit: Int = 200): Flow<List<EconomicEventEntity>>

    @Query("SELECT * FROM economic_events WHERE isDeleted = 0 ORDER BY scheduledAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 100): Flow<List<EconomicEventEntity>>

    @Query("SELECT * FROM economic_events WHERE eventKey = :eventKey ORDER BY scheduledAt DESC LIMIT :limit")
    fun observeByEventKey(eventKey: String, limit: Int = 50): Flow<List<EconomicEventEntity>>
}

@Dao
interface EconomicEventCategoryDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(category: EconomicEventCategoryEntity): Long

    @Query("SELECT * FROM economic_event_categories WHERE categoryId = :categoryId")
    suspend fun findById(categoryId: Long): EconomicEventCategoryEntity?

    @Query("SELECT * FROM economic_event_categories WHERE code = :code")
    suspend fun findByCode(code: String): EconomicEventCategoryEntity?

    @Query("SELECT * FROM economic_event_categories WHERE isActive = 1 ORDER BY displayName ASC")
    fun observeActive(): Flow<List<EconomicEventCategoryEntity>>
}

@Dao
interface EconomicEventCategoryLinkDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(links: List<EconomicEventCategoryLinkEntity>): List<Long>

    @Query("SELECT * FROM economic_event_category_links WHERE eventId = :eventId")
    fun observeForEvent(eventId: Long): Flow<List<EconomicEventCategoryLinkEntity>>

    @Query("SELECT * FROM economic_event_category_links WHERE categoryId = :categoryId")
    fun observeForCategory(categoryId: Long): Flow<List<EconomicEventCategoryLinkEntity>>
}

@Dao
interface EconomicEventInstrumentLinkDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(links: List<EconomicEventInstrumentLinkEntity>): List<Long>

    @Query("SELECT * FROM economic_event_instrument_links WHERE linkId = :linkId")
    suspend fun findById(linkId: Long): EconomicEventInstrumentLinkEntity?

    @Query("SELECT * FROM economic_event_instrument_links WHERE eventId = :eventId")
    fun observeForEvent(eventId: Long): Flow<List<EconomicEventInstrumentLinkEntity>>

    /** Primary read pattern for the Decision Engine / Evidence Engine consumers: "upcoming/recent macro events for instrument X". */
    @Query(
        """
        SELECT l.* FROM economic_event_instrument_links l
        INNER JOIN economic_events e ON e.eventId = l.eventId
        WHERE l.scope = 'INSTRUMENT' AND l.instrumentId = :instrumentId AND e.isDeleted = 0
        ORDER BY e.scheduledAt DESC LIMIT :limit
        """
    )
    fun observeRecentForInstrument(instrumentId: Long, limit: Int = 100): Flow<List<EconomicEventInstrumentLinkEntity>>

    @Query(
        """
        SELECT l.* FROM economic_event_instrument_links l
        INNER JOIN economic_events e ON e.eventId = l.eventId
        WHERE l.scope = :scope AND l.scopeLabel = :scopeLabel AND e.isDeleted = 0
        ORDER BY e.scheduledAt DESC LIMIT :limit
        """
    )
    fun observeRecentForScopeLabel(scope: EconomicEventLinkScope, scopeLabel: String, limit: Int = 100): Flow<List<EconomicEventInstrumentLinkEntity>>
}

@Dao
interface EconomicEventOutcomeDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(outcome: EconomicEventOutcomeEntity): Long

    @Query("SELECT * FROM economic_event_outcomes WHERE outcomeId = :outcomeId")
    suspend fun findById(outcomeId: Long): EconomicEventOutcomeEntity?

    @Query("SELECT * FROM economic_event_outcomes WHERE eventId = :eventId ORDER BY recordedAt DESC")
    fun observeForEvent(eventId: Long): Flow<List<EconomicEventOutcomeEntity>>

    /** Latest-reported outcome for the event — i.e. not itself revised by any other row. Mirrors how `NewsRepository` resolves a correction chain to its newest entry. */
    @Query(
        """
        SELECT * FROM economic_event_outcomes o
        WHERE o.eventId = :eventId
        AND NOT EXISTS (SELECT 1 FROM economic_event_outcomes r WHERE r.revisesOutcomeId = o.outcomeId)
        ORDER BY o.recordedAt DESC LIMIT 1
        """
    )
    suspend fun findLatestForEvent(eventId: Long): EconomicEventOutcomeEntity?

    @Query("SELECT * FROM economic_event_outcomes WHERE revisesOutcomeId = :outcomeId")
    fun observeRevisionsOf(outcomeId: Long): Flow<List<EconomicEventOutcomeEntity>>
}

@Dao
interface DriftMetricDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(metric: DriftMetricEntity): Long

    @Query("SELECT * FROM drift_metrics WHERE subjectType = :subjectType AND subjectRowId = :subjectRowId ORDER BY measuredAt DESC")
    fun observeForSubject(subjectType: ContextMonitoringSubjectType, subjectRowId: Long): Flow<List<DriftMetricEntity>>

    @Query("SELECT * FROM drift_metrics WHERE subjectType = :subjectType AND subjectRowId = :subjectRowId AND metricKey = :metricKey ORDER BY measuredAt DESC LIMIT 1")
    suspend fun findLatest(subjectType: ContextMonitoringSubjectType, subjectRowId: Long, metricKey: String): DriftMetricEntity?

    @Query("SELECT * FROM drift_metrics WHERE severity = :severity ORDER BY measuredAt DESC LIMIT :limit")
    fun observeBySeverity(severity: DriftSeverity, limit: Int = 100): Flow<List<DriftMetricEntity>>
}

@Dao
interface CalibrationMetricDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(metric: CalibrationMetricEntity): Long

    @Query("SELECT * FROM calibration_metrics WHERE subjectType = :subjectType AND subjectRowId = :subjectRowId ORDER BY measuredAt DESC")
    fun observeForSubject(subjectType: ContextMonitoringSubjectType, subjectRowId: Long): Flow<List<CalibrationMetricEntity>>

    @Query("SELECT * FROM calibration_metrics WHERE subjectType = :subjectType AND subjectRowId = :subjectRowId AND metricType = :metricType ORDER BY measuredAt DESC LIMIT 1")
    suspend fun findLatest(subjectType: ContextMonitoringSubjectType, subjectRowId: Long, metricType: CalibrationMetricType): CalibrationMetricEntity?

    @Query("SELECT * FROM calibration_metrics WHERE metricType = :metricType ORDER BY measuredAt DESC LIMIT :limit")
    fun observeByMetricType(metricType: CalibrationMetricType, limit: Int = 100): Flow<List<CalibrationMetricEntity>>

    @Query("SELECT * FROM calibration_metrics WHERE triggeredRecalibration = 1 ORDER BY measuredAt DESC LIMIT :limit")
    fun observeRecalibrationEvents(limit: Int = 100): Flow<List<CalibrationMetricEntity>>
}
