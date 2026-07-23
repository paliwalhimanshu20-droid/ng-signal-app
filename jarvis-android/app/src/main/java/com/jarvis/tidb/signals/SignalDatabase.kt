package com.jarvis.tidb.signals

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.jarvis.tidb.signals.dao.SignalDao
import com.jarvis.tidb.signals.dao.SignalLifecycleDao
import com.jarvis.tidb.signals.dao.SignalNoteDao
import com.jarvis.tidb.signals.dao.SignalReasonDao
import com.jarvis.tidb.signals.dao.SignalSnapshotDao
import com.jarvis.tidb.signals.dao.SignalTagDao
import com.jarvis.tidb.signals.entity.SignalEntity
import com.jarvis.tidb.signals.entity.SignalLifecycleEntity
import com.jarvis.tidb.signals.entity.SignalNoteEntity
import com.jarvis.tidb.signals.entity.SignalReasonEntity
import com.jarvis.tidb.signals.entity.SignalSnapshotEntity
import com.jarvis.tidb.signals.entity.SignalTagEntity

/**
 * JARVIS Trading Intelligence Database — Module 2: Signal Intelligence Engine.
 *
 * A separate Room database from Module 1's `TidbDatabase` on purpose. Module 2 consumes Module 1
 * exclusively through `com.jarvis.tidb.core.repository.*` interfaces (e.g. to validate an
 * `instrumentId` exists before recording a signal against it) and never opens Module 1's tables
 * or database instance directly — see `SignalRepositoryImpl` and the Module 2 implementation
 * prompt's "Reuse Module 1 repositories. Never access Module 1 tables directly." requirement.
 *
 * Two independent Room databases backed by the same underlying SQLite engine is a normal and
 * safe pattern here: neither database declares a `@ForeignKey` into the other's tables (SQLite
 * cannot enforce cross-database foreign keys across separate `.db` files anyway), and all
 * cross-module reads/writes go through Kotlin interfaces instead.
 *
 * Destructive fallback is disabled on purpose, same rationale as Module 1: signal history
 * (including the immutable snapshot and lifecycle audit trail) must never be silently wiped by
 * a schema change. Every future structural change ships an explicit [androidx.room.migration.Migration].
 */
@Database(
    entities = [
        SignalEntity::class,
        SignalReasonEntity::class,
        SignalSnapshotEntity::class,
        SignalLifecycleEntity::class,
        SignalTagEntity::class,
        SignalNoteEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class SignalDatabase : RoomDatabase() {

    abstract fun signalDao(): SignalDao
    abstract fun signalReasonDao(): SignalReasonDao
    abstract fun signalSnapshotDao(): SignalSnapshotDao
    abstract fun signalLifecycleDao(): SignalLifecycleDao
    abstract fun signalTagDao(): SignalTagDao
    abstract fun signalNoteDao(): SignalNoteDao

    companion object {
        private const val DATABASE_NAME = "jarvis_tidb_signals.db"

        @Volatile
        private var INSTANCE: SignalDatabase? = null

        fun getInstance(context: Context): SignalDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    SignalDatabase::class.java,
                    DATABASE_NAME
                )
                    // No destructive fallback — see class doc. Add MIGRATION_x_y objects here
                    // as schema versions increase.
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
