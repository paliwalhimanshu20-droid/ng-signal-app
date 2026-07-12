package com.jarvis.os.app.feature.memory

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jarvis.os.app.data.model.MemoryEntry
import com.jarvis.os.app.data.repository.MemoryRepository
import com.jarvis.os.app.designsystem.JarvisSpacing
import com.jarvis.os.app.designsystem.JarvisStatusColors
import com.jarvis.os.app.designsystem.components.JarvisCard
import com.jarvis.os.app.designsystem.components.StatusPill
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MemoryViewModel @Inject constructor(private val repository: MemoryRepository) : ViewModel() {
    val entries = repository.entries.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun search(query: String) = if (query.isBlank()) entries.value else repository.search(query)
}

@Composable
fun MemoryScreen(viewModel: MemoryViewModel = hiltViewModel()) {
    val entries by viewModel.entries.collectAsState()
    var query by remember { mutableStateOf("") }
    val displayed = if (query.isBlank()) entries else viewModel.search(query)

    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(JarvisSpacing.md),
            placeholder = { Text("Search memory…") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
        )

        LazyColumn(contentPadding = PaddingValues(horizontal = JarvisSpacing.md, vertical = JarvisSpacing.sm)) {
            items(displayed, key = { it.entryId }) { entry -> MemoryCard(entry) }
        }
    }
}

@Composable
private fun MemoryCard(entry: MemoryEntry) {
    JarvisCard(modifier = Modifier.fillMaxWidth().padding(vertical = JarvisSpacing.xs)) {
        Column {
            StatusPill(entry.tier.name, JarvisStatusColors.Unknown)
            Text(entry.summary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = JarvisSpacing.xs))
            Text(entry.timestamp.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
