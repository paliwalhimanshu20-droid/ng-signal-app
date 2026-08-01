package com.jarvis.os.app.data.repository

import com.jarvis.os.app.data.model.AuditEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sprint 11 Governance: the retained store behind the cross-domain
 * audit view (see AuditEntry's docstring). JarvisCore is the sole
 * writer -- one more init-block collector alongside its notification
 * and connection-forwarding ones, same "single coordinator writes,
 * everything else only reads" shape already established for
 * NotificationRepository.insert. `record` is append-only: no update or
 * remove method exists on this interface at all, not just by
 * convention.
 */
interface AuditRepository {
    val entries: StateFlow<List<AuditEntry>>
    fun record(entry: AuditEntry)
}

@Singleton
class MockAuditRepository @Inject constructor() : AuditRepository {
    private val _entries = MutableStateFlow<List<AuditEntry>>(emptyList())
    override val entries: StateFlow<List<AuditEntry>> = _entries.asStateFlow()

    override fun record(entry: AuditEntry) {
        _entries.update { it + entry }
    }
}
