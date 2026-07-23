package com.jarvis.tidb.signals.di

import android.content.Context
import com.jarvis.tidb.signals.SignalDatabase
import com.jarvis.tidb.signals.repository.SignalLifecycleRepository
import com.jarvis.tidb.signals.repository.SignalLifecycleRepositoryImpl
import com.jarvis.tidb.signals.repository.SignalNoteRepository
import com.jarvis.tidb.signals.repository.SignalNoteRepositoryImpl
import com.jarvis.tidb.signals.repository.SignalReasonRepository
import com.jarvis.tidb.signals.repository.SignalReasonRepositoryImpl
import com.jarvis.tidb.signals.repository.SignalRepository
import com.jarvis.tidb.signals.repository.SignalRepositoryImpl
import com.jarvis.tidb.signals.repository.SignalSnapshotRepository
import com.jarvis.tidb.signals.repository.SignalSnapshotRepositoryImpl
import com.jarvis.tidb.signals.repository.SignalTagRepository
import com.jarvis.tidb.signals.repository.SignalTagRepositoryImpl

/**
 * Manual, framework-agnostic dependency provider for Module 2 — mirrors Module 1's `TidbModule`
 * so the two modules can be wired identically regardless of whether the host app eventually
 * adopts Hilt/Koin or keeps manual DI. If/when the app introduces a DI framework, this class's
 * `provide*` functions map 1:1 onto `@Provides` methods.
 */
class SignalModule(context: Context) {

    private val database: SignalDatabase = SignalDatabase.getInstance(context)

    val signalRepository: SignalRepository by lazy {
        SignalRepositoryImpl(database.signalDao(), database.signalLifecycleDao())
    }

    val signalReasonRepository: SignalReasonRepository by lazy {
        SignalReasonRepositoryImpl(database.signalReasonDao())
    }

    val signalSnapshotRepository: SignalSnapshotRepository by lazy {
        SignalSnapshotRepositoryImpl(database.signalSnapshotDao())
    }

    val signalLifecycleRepository: SignalLifecycleRepository by lazy {
        SignalLifecycleRepositoryImpl(database.signalLifecycleDao())
    }

    val signalTagRepository: SignalTagRepository by lazy {
        SignalTagRepositoryImpl(database.signalTagDao())
    }

    val signalNoteRepository: SignalNoteRepository by lazy {
        SignalNoteRepositoryImpl(database.signalNoteDao())
    }

    companion object {
        @Volatile
        private var INSTANCE: SignalModule? = null

        fun getInstance(context: Context): SignalModule {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SignalModule(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
