package com.jarvis.os.app.feature.deployment

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jarvis.os.app.core.deployment.PackageIntakeAnalyzer
import com.jarvis.os.app.data.model.AuditEntry
import com.jarvis.os.app.data.model.IntakeWarningSeverity
import com.jarvis.os.app.data.model.PackageIntakeReport
import com.jarvis.os.app.data.repository.AuditRepository
import com.jarvis.os.app.designsystem.JarvisSpacing
import com.jarvis.os.app.designsystem.JarvisStatusColors
import com.jarvis.os.app.designsystem.components.JarvisCard
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

/**
 * ASDP-001 Phase 1 "Package Intake" -- the "Import Deployment Package"
 * entry point under Mission Control -> Deployment Center. Picks a ZIP
 * via the Storage Access Framework (no broad storage permission needed
 * -- only access to the single file the Owner explicitly chooses),
 * runs PackageIntakeAnalyzer against it, and records the result to the
 * real AuditRepository/Timeline (ASDP-001's own Phase 13 -- "ZIP
 * Imported" and "Engineering Analysis" are the first two events that
 * phase names, and this is them, wired to the timeline that already
 * exists rather than a new one).
 *
 * Phases 2-17 (repository intelligence, deployment, build, install,
 * etc.) are NOT implemented here -- see
 * docs/architecture/JARVIS/ASDP-001-Feasibility-and-Roadmap.md for why
 * each of those needs either reframing or further owner decisions
 * before being coded against.
 */
@HiltViewModel
class DeploymentCenterViewModel @Inject constructor(
    @ApplicationContext private val context: android.content.Context,
    private val analyzer: PackageIntakeAnalyzer,
    private val auditRepository: AuditRepository,
) : ViewModel() {

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _report = MutableStateFlow<PackageIntakeReport?>(null)
    val report: StateFlow<PackageIntakeReport?> = _report.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun onPackageSelected(uri: Uri) {
        viewModelScope.launch {
            _isAnalyzing.value = true
            _errorMessage.value = null
            try {
                val fileName = queryDisplayName(uri) ?: "selected_package.zip"
                val result = analyzer.analyze(uri, fileName, context.contentResolver)
                _report.value = result

                // Phase 13's first two timeline events, real ones --
                // AuditRepository is the actual "Timeline-First
                // Executive Memory System" this feature integrates
                // with (see ASDP-001's own docstring), not a
                // placeholder written for this screen alone.
                // AuditEntry is constructed directly here, not via
                // AuditFactory -- that object only ever converts a
                // CoreEvent into an AuditEntry (see its own docstring),
                // and this screen doesn't go through JarvisCore's
                // event pipeline at all, so there's no CoreEvent to
                // convert from.
                auditRepository.record(
                    AuditEntry(
                        entryId = UUID.randomUUID().toString(),
                        timestamp = Instant.now(),
                        category = "Deployment",
                        summary = "ZIP Imported: ${result.fileName} (${result.fileCount} files, ${result.archiveSizeBytes} bytes)",
                    ),
                )
                auditRepository.record(
                    AuditEntry(
                        entryId = UUID.randomUUID().toString(),
                        timestamp = Instant.now(),
                        category = "Deployment",
                        summary = "Engineering Analysis: ${result.detectedProjectType} (${result.projectTypeConfidence})" +
                            if (result.isSafeToProceed) "" else " -- BLOCKED: ${result.warnings.count { it.severity == IntakeWarningSeverity.BLOCKING }} blocking issue(s)",
                    ),
                )
            } catch (e: Exception) {
                _errorMessage.value = "Couldn't analyze that package: ${e.message ?: e::class.simpleName}"
            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        return context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index >= 0) cursor.getString(index) else null
            } else {
                null
            }
        }
    }
}

@Composable
fun DeploymentCenterScreen(viewModel: DeploymentCenterViewModel = hiltViewModel()) {
    val isAnalyzing by viewModel.isAnalyzing.collectAsState()
    val report by viewModel.report.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { viewModel.onPackageSelected(it) } }

    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(JarvisSpacing.md),
            verticalArrangement = Arrangement.spacedBy(JarvisSpacing.sm),
        ) {
            item {
                Text("Deployment Center", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Import a ZIP to have JARVIS analyze it. Phase 1 (Package Intake) only -- see ASDP-001 for what's built vs. planned.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = JarvisSpacing.sm),
                )
                Button(onClick = { pickerLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed")) }) {
                    Text(if (isAnalyzing) "Analyzing…" else "Import Deployment Package")
                }
            }

            errorMessage?.let { message ->
                item {
                    JarvisCard(modifier = Modifier.fillMaxWidth()) {
                        Text(message, color = JarvisStatusColors.Unhealthy)
                    }
                }
            }

            if (isAnalyzing) {
                item { CircularProgressIndicator() }
            }

            report?.let { r -> item { PackageIntakeReportCard(r) } }
        }
    }
}

@Composable
private fun PackageIntakeReportCard(report: PackageIntakeReport) {
    JarvisCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(report.fileName, style = MaterialTheme.typography.titleMedium)
            Text(
                if (report.isSafeToProceed) "Safe to proceed" else "Blocked -- see warnings below",
                color = if (report.isSafeToProceed) JarvisStatusColors.Healthy else JarvisStatusColors.Unhealthy,
                style = MaterialTheme.typography.labelMedium,
            )
            Text("Detected type: ${report.detectedProjectType} (${report.projectTypeConfidence})", style = MaterialTheme.typography.bodyMedium)
            Text("${report.fileCount} files, ${report.archiveSizeBytes} bytes archive, ~${report.estimatedUncompressedBytes} bytes uncompressed", style = MaterialTheme.typography.bodySmall)
            Text("SHA-256: ${report.sha256Checksum}", style = MaterialTheme.typography.labelSmall)
            if (report.hasGitDirectory) Text("Contains .git -- will be preserved, never overwritten, once Phase 6 exists.", style = MaterialTheme.typography.bodySmall)
            if (!report.hasReadme) Text("No README detected.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (report.duplicatePaths.isNotEmpty()) Text("${report.duplicatePaths.size} duplicate path(s) found.", style = MaterialTheme.typography.bodySmall, color = JarvisStatusColors.Degraded)
            if (report.unsafePaths.isNotEmpty()) {
                Text("${report.unsafePaths.size} unsafe path(s) -- rejected:", style = MaterialTheme.typography.bodySmall, color = JarvisStatusColors.Unhealthy)
            }
        }
    }
}
