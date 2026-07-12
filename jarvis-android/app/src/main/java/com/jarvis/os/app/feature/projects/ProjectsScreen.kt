package com.jarvis.os.app.feature.projects

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jarvis.os.app.data.model.Project
import com.jarvis.os.app.data.model.ProjectStatus
import com.jarvis.os.app.data.repository.ProjectRepository
import com.jarvis.os.app.designsystem.JarvisSpacing
import com.jarvis.os.app.designsystem.JarvisStatusColors
import com.jarvis.os.app.designsystem.components.JarvisCard
import com.jarvis.os.app.designsystem.components.StatusPill
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ProjectsViewModel @Inject constructor(repository: ProjectRepository) : ViewModel() {
    val projects = repository.projects.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@Composable
fun ProjectsScreen(viewModel: ProjectsViewModel = hiltViewModel()) {
    val projects by viewModel.projects.collectAsState()
    LazyColumn(contentPadding = PaddingValues(horizontal = JarvisSpacing.md, vertical = JarvisSpacing.sm)) {
        items(projects, key = { it.projectId }) { project -> ProjectCard(project) }
    }
}

@Composable
private fun ProjectCard(project: Project) {
    JarvisCard(modifier = Modifier.fillMaxWidth().padding(vertical = JarvisSpacing.xs)) {
        Column {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(project.name, style = MaterialTheme.typography.titleMedium)
                StatusPill(project.status.name, project.status.toColor())
            }
            LinearProgressIndicator(
                progress = { project.progressPercent / 100f },
                modifier = Modifier.fillMaxWidth().padding(top = JarvisSpacing.sm),
            )
            Text(
                "${project.progressPercent}% · Priority: ${project.priority.name}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (project.pendingTasks.isNotEmpty()) {
                Text(
                    "Pending: " + project.pendingTasks.joinToString { it.title },
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = JarvisSpacing.xs),
                )
            }
        }
    }
}

private fun ProjectStatus.toColor() = when (this) {
    ProjectStatus.ACTIVE -> JarvisStatusColors.Healthy
    ProjectStatus.BLOCKED -> JarvisStatusColors.Unhealthy
    ProjectStatus.PAUSED -> JarvisStatusColors.Degraded
    ProjectStatus.COMPLETED -> JarvisStatusColors.Unknown
}
