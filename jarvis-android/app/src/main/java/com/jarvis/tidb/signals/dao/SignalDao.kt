package com.jarvis.tidb.signals.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.jarvis.tidb.signals.entity.SignalEntity
import com.jarvis.tidb.signals.entity.relation.SignalWithDetails
import com.jarvis.tidb.signals.entity.relation.SignalWithReasonsAndTags
import kotlinx.coroutines.flow.Flow

@Dao
interface SignalDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(signal: SignalEntity): Long

    @Update
    suspend fun update(signal: SignalEntity)

    /** Soft delete — never physically removes a signal, per the isDeleted/deletedAt fields. */
    @Query("UPDATE signals SET isDeleted = 1, deletedAt = :deletedAt, updatedAt = :deletedAt WHERE signalId = :signalId")
    suspend fun softDelete(signalId: Long, deletedAt: Long = System.currentTimeMillis())

    @Query("SELECT * FROM signals WHERE signalId = :signalId AND isDeleted = 0")
    fun observeById(signalId: Long): Flow<SignalEntity?>

    @Query("SELECT * FROM signals WHERE uuid = :uuid AND isDeleted = 0")
    fun observeByUuid(uuid: String): Flow<SignalEntity?>

    // ---- Active signals -----------------------------------------------------------------

    @Query("SELECT * FROM signals WHERE status = 'ACTIVE' AND isDeleted = 0 ORDER BY generatedAt DESC")
    fun observeActiveSignals(): Flow<List<SignalEntity>>

    // ---- By instrument -------------------------------------------------------------------

    @Query("SELECT * FROM signals WHERE instrumentId = :instrumentId AND isDeleted = 0 ORDER BY generatedAt DESC")
    fun observeByInstrument(instrumentId: Long): Flow<List<SignalEntity>>

    @Query(
        """
        SELECT * FROM signals
        WHERE instrumentId = :instrumentId AND status = 'ACTIVE' AND isDeleted = 0
        ORDER BY generatedAt DESC
        """
    )
    fun observeActiveByInstrument(instrumentId: Long): Flow<List<SignalEntity>>

    // ---- By timeframe -----------------------------------------------------------------

    @Query("SELECT * FROM signals WHERE timeframe = :timeframe AND isDeleted = 0 ORDER BY generatedAt DESC")
    fun observeByTimeframe(timeframe: String): Flow<List<SignalEntity>>

    // ---- By confidence ------------------------------------------------------------------

    @Query(
        """
        SELECT * FROM signals
        WHERE confidenceScore >= :minConfidence AND isDeleted = 0
        ORDER BY confidenceScore DESC
        """
    )
    fun observeByMinConfidence(minConfidence: Double): Flow<List<SignalEntity>>

    // ---- By status --------------------------------------------------------------------

    @Query("SELECT * FROM signals WHERE status = :status AND isDeleted = 0 ORDER BY generatedAt DESC")
    fun observeByStatus(status: String): Flow<List<SignalEntity>>

    // ---- By signal type -----------------------------------------------------------------

    @Query("SELECT * FROM signals WHERE signalType = :signalType AND isDeleted = 0 ORDER BY generatedAt DESC")
    fun observeBySignalType(signalType: String): Flow<List<SignalEntity>>

    // ---- Between dates ------------------------------------------------------------------

    @Query(
        """
        SELECT * FROM signals
        WHERE generatedAt BETWEEN :startTime AND :endTime AND isDeleted = 0
        ORDER BY generatedAt DESC
        """
    )
    fun observeBetweenDates(startTime: Long, endTime: Long): Flow<List<SignalEntity>>

    // ---- By tag (join through signal_tags) -----------------------------------------------

    @Query(
        """
        SELECT s.* FROM signals s
        INNER JOIN signal_tags t ON t.signalId = s.signalId
        WHERE t.tag = :tag AND s.isDeleted = 0
        ORDER BY s.generatedAt DESC
        """
    )
    fun observeByTag(tag: String): Flow<List<SignalEntity>>

    // ---- Latest signal ------------------------------------------------------------------

    @Query("SELECT * FROM signals WHERE isDeleted = 0 ORDER BY generatedAt DESC LIMIT 1")
    fun observeLatest(): Flow<SignalEntity?>

    @Query(
        """
        SELECT * FROM signals
        WHERE instrumentId = :instrumentId AND isDeleted = 0
        ORDER BY generatedAt DESC LIMIT 1
        """
    )
    fun observeLatestForInstrument(instrumentId: Long): Flow<SignalEntity?>

    // ---- Search by UUID (one-shot, not a Flow — used for exact lookups/imports) ------------

    @Query("SELECT * FROM signals WHERE uuid = :uuid AND isDeleted = 0 LIMIT 1")
    suspend fun findByUuid(uuid: String): SignalEntity?

    /** One-shot lookup by primary key — used internally by the repository for read-then-write flows. */
    @Query("SELECT * FROM signals WHERE signalId = :signalId LIMIT 1")
    suspend fun findByRowId(signalId: Long): SignalEntity?

    // ---- Hydrated reads (Signal + Reasons + Snapshot + Lifecycle + Tags + Notes) -----------

    @Transaction
    @Query("SELECT * FROM signals WHERE signalId = :signalId AND isDeleted = 0")
    fun observeWithDetails(signalId: Long): Flow<SignalWithDetails?>

    @Transaction
    @Query("SELECT * FROM signals WHERE uuid = :uuid AND isDeleted = 0")
    fun observeWithDetailsByUuid(uuid: String): Flow<SignalWithDetails?>

    @Transaction
    @Query("SELECT * FROM signals WHERE status = 'ACTIVE' AND isDeleted = 0 ORDER BY generatedAt DESC")
    fun observeActiveWithReasonsAndTags(): Flow<List<SignalWithReasonsAndTags>>

    // ---- Counting (useful for dashboards without pulling full rows) -----------------------

    @Query("SELECT COUNT(*) FROM signals WHERE status = 'ACTIVE' AND isDeleted = 0")
    fun observeActiveCount(): Flow<Int>
}
