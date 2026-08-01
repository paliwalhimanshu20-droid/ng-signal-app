package com.jarvis.os.app.feature.deployment

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jarvis.os.app.core.deployment.engines.RepositorySearchResult
import com.jarvis.os.app.data.model.DeploymentFailure
import com.jarvis.os.app.data.model.DeploymentPreviewReport
import com.jarvis.os.app.data.model.DeploymentStage
import com.jarvis.os.app.data.model.ExecutiveDeploymentBrief
import com.jarvis.os.app.data.model.PackageIntakeReport
import com.jarvis.os.app.data.model.SecuritySeverity
import com.jarvis.os.app.designsystem.JarvisSpacing
import com.jarvis.os.app.designsystem.JarvisStatusColors
import com.jarvis.os.app.designsystem.components.JarvisCard

/**
 * ASDP-001 Phases 1, 3, 4, 5, 6, 7, 9, 11, 12 -- the full owner-facing
 * flow described in the sprint brief's own architecture diagram, one
 * screen at a time, gated on [DeploymentCenterViewModel.stage]. Every
 * screen below reads real state from the ViewModel; nothing here is a
 * static mock -- the Preview screen, in particular, only ever shows a
 * [DeploymentPreviewReport] that [com.jarvis.os.app.core.deployment.ImportPublishPipeline]
 * actually computed from the real extracted archive against the real
 * GitHub tree.
 */
@Composable
fun DeploymentCenterScreen(viewModel: DeploymentCenterViewModel = hiltViewModel()) {
    val stage by viewModel.stage.collectAsState()
    val intakeReport by viewModel.intakeReport.collectAsState()
    val repoLookup by viewModel.repoLookup.collectAsState()
    val preview by viewModel.preview.collectAsState()
    val buildProgressMessage by viewModel.buildProgressMessage.collectAsState()
    val executiveBrief by viewModel.executiveBrief.collectAsState()
    val failure by viewModel.failure.collectAsState()

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
                    "Import a ZIP, review what would change, and publish it to GitHub -- nothing happens without your explicit approval.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = JarvisSpacing.sm),
                )
            }

            if (stage == DeploymentStage.IDLE) {
                item {
                    Button(onClick = { pickerLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed")) }) {
                        Text("Import Deployment Package")
                    }
                }
            }

            if (stage == DeploymentStage.ANALYZING_PACKAGE || stage == DeploymentStage.RESOLVING_REPOSITORY || stage == DeploymentStage.BUILDING_PREVIEW || stage == DeploymentStage.COMMITTING || stage == DeploymentStage.TRIGGERING_BUILD) {
                item {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(JarvisSpacing.sm)) {
                        CircularProgressIndicator()
                        Text(stageLabel(stage), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            intakeReport?.let { report -> item { PackageIntakeReportCard(report) } }

            if (stage == DeploymentStage.AWAITING_REPOSITORY_SELECTION) {
                item {
                    RepositorySelectionCard(
                        defaultRepo = viewModel.defaultRepo,
                        repoLookup = repoLookup,
                        onFind = { name -> viewModel.lookupRepository(name) },
                        onCreate = { name, isPrivate -> viewModel.createAndUseRepository(name, isPrivate) },
                        onUseFound = { owner, repo, branch -> viewModel.buildPreview(owner, repo, branch) },
                    )
                }
            }

            if (stage == DeploymentStage.MONITORING_BUILD) {
                item {
                    JarvisCard(modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(JarvisSpacing.sm)) {
                                CircularProgressIndicator()
                                Text("Monitoring GitHub Actions", style = MaterialTheme.typography.titleSmall)
                            }
                            Text(buildProgressMessage ?: "Waiting for status…", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            if (stage == DeploymentStage.AWAITING_APPROVAL) {
                preview?.let { p ->
                    item { DeploymentPreviewCard(p) }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(JarvisSpacing.sm)) {
                            Button(onClick = { viewModel.onApprovePublish() }, enabled = p.isSafeToPublish) { Text("Approve & Publish") }
                            OutlinedButton(onClick = { viewModel.onBackToRepositorySelection() }) { Text("Back") }
                            TextButton(onClick = { viewModel.onCancel() }) { Text("Cancel") }
                        }
                    }
                }
            }

            if (stage == DeploymentStage.COMPLETE) {
                executiveBrief?.let { brief -> item { ExecutiveBriefCard(brief) } }
                item { Button(onClick = { viewModel.startOver() }) { Text("Import Another Package") } }
            }

            if (stage == DeploymentStage.FAILED) {
                failure?.let { f -> item { DeploymentFailureCard(f) } }
                item { Button(onClick = { viewModel.startOver() }) { Text("Start Over") } }
            }
        }
    }
}

private fun stageLabel(stage: DeploymentStage): String = when (stage) {
    DeploymentStage.ANALYZING_PACKAGE -> "Analyzing package…"
    DeploymentStage.RESOLVING_REPOSITORY -> "Talking to GitHub…"
    DeploymentStage.BUILDING_PREVIEW -> "Extracting, comparing against the repository, and running a security review…"
    DeploymentStage.COMMITTING -> "Committing and pushing to GitHub…"
    DeploymentStage.TRIGGERING_BUILD -> "Triggering a GitHub Actions build…"
    else -> ""
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
            if (report.hasGitDirectory) Text("Contains .git -- preserved, never overwritten.", style = MaterialTheme.typography.bodySmall)
            if (!report.hasReadme) Text("No README detected.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (report.duplicatePaths.isNotEmpty()) Text("${report.duplicatePaths.size} duplicate path(s) found.", style = MaterialTheme.typography.bodySmall, color = JarvisStatusColors.Degraded)
            if (report.unsafePaths.isNotEmpty()) Text("${report.unsafePaths.size} unsafe path(s) -- rejected.", style = MaterialTheme.typography.bodySmall, color = JarvisStatusColors.Unhealthy)
        }
    }
}

@Composable
private fun RepositorySelectionCard(
    defaultRepo: String?,
    repoLookup: RepositorySearchResult?,
    onFind: (String) -> Unit,
    onCreate: (String, Boolean) -> Unit,
    onUseFound: (owner: String, repo: String, branch: String) -> Unit,
) {
    var repoName by rememberSaveable { mutableStateOf(defaultRepo.orEmpty()) }
    var isPrivate by rememberSaveable { mutableStateOf(true) }

    JarvisCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(JarvisSpacing.sm)) {
            Text("Target repository", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = repoName,
                onValueChange = { repoName = it },
                label = { Text("Repository name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Button(onClick = { onFind(repoName) }, enabled = repoName.isNotBlank()) {
                Text("Find Repository")
            }

            when (repoLookup) {
                is RepositorySearchResult.Found -> {
                    val owner = repoLookup.repository.fullName.substringBefore('/')
                    Text("Found: ${repoLookup.repository.fullName} (default branch: ${repoLookup.repository.defaultBranch})", style = MaterialTheme.typography.bodyMedium, color = JarvisStatusColors.Healthy)
                    Button(onClick = { onUseFound(owner, repoName, repoLookup.repository.defaultBranch) }) {
                        Text("Use this repository")
                    }
                }
                is RepositorySearchResult.NotFound -> {
                    Text("No repository named '$repoName' found on this account.", style = MaterialTheme.typography.bodyMedium, color = JarvisStatusColors.Degraded)
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Checkbox(checked = isPrivate, onCheckedChange = { isPrivate = it })
                        Text("Private")
                    }
                    Button(onClick = { onCreate(repoName, isPrivate) }) {
                        Text("Create repository '$repoName'")
                    }
                }
                is RepositorySearchResult.Failure -> {
                    Text(repoLookup.message, style = MaterialTheme.typography.bodySmall, color = JarvisStatusColors.Unhealthy)
                }
                null -> {}
            }
        }
    }
}

@Composable
private fun DeploymentPreviewCard(preview: DeploymentPreviewReport) {
    JarvisCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Preview: ${preview.owner}/${preview.repo}@${preview.branch}", style = MaterialTheme.typography.titleMedium)
            Text(preview.suggestedCommitMessage, style = MaterialTheme.typography.bodyMedium)
            Text(
                "${preview.added.size} added, ${preview.modified.size} modified, ${preview.deleted.size} deleted, ${preview.unchangedCount} unchanged",
                style = MaterialTheme.typography.bodySmall,
            )
            Text("Estimated commit size: ${preview.estimatedCommitBytes} bytes", style = MaterialTheme.typography.bodySmall)

            if (preview.added.isNotEmpty()) FileListSection("Added", preview.added.map { it.path }, JarvisStatusColors.Healthy)
            if (preview.modified.isNotEmpty()) FileListSection("Modified", preview.modified.map { it.path }, JarvisStatusColors.Degraded)
            if (preview.deleted.isNotEmpty()) FileListSection("Deleted", preview.deleted.map { it.path }, JarvisStatusColors.Unhealthy)

            if (preview.securityFindings.isNotEmpty()) {
                Text("Security review", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = JarvisSpacing.sm))
                preview.securityFindings.forEach { finding ->
                    val color = when (finding.severity) {
                        SecuritySeverity.BLOCKING -> JarvisStatusColors.Unhealthy
                        SecuritySeverity.WARNING -> JarvisStatusColors.Degraded
                        SecuritySeverity.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Text("[${finding.severity}] ${finding.path}: ${finding.message}", style = MaterialTheme.typography.bodySmall, color = color)
                }
            }

            if (!preview.isSafeToPublish) {
                Text(
                    "Blocked -- resolve the blocking finding(s) above before this can be published.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = JarvisStatusColors.Unhealthy,
                )
            }
        }
    }
}

@Composable
private fun FileListSection(label: String, paths: List<String>, color: androidx.compose.ui.graphics.Color) {
    Text("$label (${paths.size})", style = MaterialTheme.typography.labelMedium, color = color, modifier = Modifier.padding(top = JarvisSpacing.xs))
    paths.take(20).forEach { path -> Text(path, style = MaterialTheme.typography.bodySmall) }
    if (paths.size > 20) Text("…and ${paths.size - 20} more", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ExecutiveBriefCard(brief: ExecutiveDeploymentBrief) {
    JarvisCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Upload complete", style = MaterialTheme.typography.titleMedium, color = JarvisStatusColors.Healthy)
            Text("Repository: ${brief.repoFullName}", style = MaterialTheme.typography.bodyMedium)
            Text("${brief.filesChanged} file(s) changed", style = MaterialTheme.typography.bodyMedium)
            Text("Commit: ${brief.commitSha.take(7)}", style = MaterialTheme.typography.bodySmall)
            Text(brief.commitUrl, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)

            when {
                !brief.buildTriggered -> Text("GitHub Actions build was not triggered -- the publish itself still succeeded.", style = MaterialTheme.typography.bodySmall, color = JarvisStatusColors.Degraded)
                brief.buildSucceeded == true -> Text("GitHub Actions completed successfully in ${brief.buildDurationSeconds}s.", style = MaterialTheme.typography.bodyMedium, color = JarvisStatusColors.Healthy)
                brief.buildSucceeded == false -> Text("GitHub Actions failed after ${brief.buildDurationSeconds}s.", style = MaterialTheme.typography.bodyMedium, color = JarvisStatusColors.Unhealthy)
                else -> Text("GitHub Actions is still running -- check it directly.", style = MaterialTheme.typography.bodyMedium, color = JarvisStatusColors.Degraded)
            }
            brief.buildLogsUrl?.let { url -> Text(url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
        }
    }
}

@Composable
private fun DeploymentFailureCard(failure: DeploymentFailure) {
    JarvisCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Deployment failed at ${failure.stage}", style = MaterialTheme.typography.titleMedium, color = JarvisStatusColors.Unhealthy)
            Text(failure.message, style = MaterialTheme.typography.bodyMedium)
            Text("Likely cause: ${failure.likelyCause}", style = MaterialTheme.typography.bodySmall)
            Text("Suggested fix: ${failure.suggestedFix}", style = MaterialTheme.typography.bodySmall)
        }
    }
}
