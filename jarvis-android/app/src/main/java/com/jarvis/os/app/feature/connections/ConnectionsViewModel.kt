package com.jarvis.os.app.feature.connections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jarvis.os.app.data.model.ConnectionHealth
import com.jarvis.os.app.data.repository.ConnectionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ConnectionsViewModel @Inject constructor(
    private val repository: ConnectionRepository,
) : ViewModel() {

    val connections = repository.connections.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Owner Sovereignty (Sprint-6 Part 3), enforced at the UI action
    // layer too: every button below maps 1:1 to a ConnectionRepository
    // method that mirrors ConnectionManager's real governance rule —
    // there is no "quick approve" shortcut that skips a state check.
    fun approve(connectionId: String) = viewModelScope.launch { repository.approve(connectionId, approvedBy = "owner") }
    fun reject(connectionId: String) = viewModelScope.launch { repository.reject(connectionId, reason = "Rejected by owner") }
    fun suspend(connectionId: String) = viewModelScope.launch { repository.suspend(connectionId, reason = "Suspended by owner") }
    fun disconnect(connectionId: String) = viewModelScope.launch { repository.disconnect(connectionId, reason = "Disconnected by owner") }
    fun reconnect(connectionId: String) = viewModelScope.launch { repository.reconnect(connectionId) }
    fun disableAll() = viewModelScope.launch { repository.disableAll(reason = "Owner disabled all connections") }

    fun testConnection(connectionId: String): ConnectionHealth = repository.testConnection(connectionId)
}
