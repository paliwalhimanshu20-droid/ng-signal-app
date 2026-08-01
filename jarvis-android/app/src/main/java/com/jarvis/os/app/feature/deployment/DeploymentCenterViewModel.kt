package com.jarvis.os.app.feature.deployment

import android.content.Context
import android.net.Uri
import com.jarvis.os.app.core.deployment.ImportPublishPipeline
import com.jarvis.os.app.core.deployment.PackageExtractor
import com.jarvis.os.app.core.deployment.PackageIntakeAnalyzer
import com.jarvis.os.app.core.deployment.engines.BuildEngine
import com.jarvis.os.app.core.deployment.engines.BuildStatusResult
import com.jarvis.os.app.core.deployment.engines.BuildTriggerResult
import com.jarvis.os.app.core.deployment.engines.DeploymentEngine
import com.jarvis.os.app.core.deployment.engines.DeploymentResult
import com.jarvis.os.app.core.deployment.engines.FileChange
import com.jarvis.os.app.core.deployment.engines.RepositoryCreationResult
import com.jarvis.os.app.core.deployment.engines.RepositoryProvider
import com.jarvis.os.app.core.deployment.engines.RepositorySearchResult
import com.jarvis.os.app.data.model.ApprovalKind
import com.jarvis.os.app.data.model.AuditEntry
import com.jarvis.os.app.data.model.DeploymentFailure
import com.jarvis.os.app.data.model.DeploymentPreviewReport
import com.jarvis.os.app.data.model.DeploymentStage
import com.jarvis.os.app.data.model.ExecutiveDeploymentBrief
import com.jarvis.os.app.data.model.IntakeWarningSeverity
import com.jarvis.os.app.data.model.PackageIntakeReport
import com.jarvis.os.app.data.model.RiskLevel
import com.jarvis.os.app.data.repository.ApprovalRepository
import com.jarvis.os.app.data.repository.AuditRepository
import com.jarvis.os.app.data.settings.GitHubTokenStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

/**
 * ASDP-001 Phases 1, 3, 4, 5, 6, 7, 9, 11, 12, 13 -- the real,
 * end-to-end Import & Publish pipeline this sprint asks for, built on
 * top of everything already real in this codebase (PackageIntakeAnalyzer
 * from the previous round; DeploymentEngine/BuildEngine/
 * RepositoryProvider/GitHubApiClient from the RC-001–003 engine
 * abstractions; ApprovalRepository/AuditRepository from Sprint 9/11).
 *
 * One linear state machine ([DeploymentStage]) drives the whole
 * screen -- see that enum for the exact sequence, which mirrors the
 * sprint brief's own architecture diagram. Nothing after
 * AWAITING_APPROVAL runs without [onApprovePublish] being called,
 * which itself is Owner Sovereignty enforced twice: once as a real
 * ApprovalRepository record (so it shows up in Approval Center /
 * Timeline like every other governed action), and once structurally
 * -- there is no code path in this class that calls
 * [DeploymentEngine.deploy] except from inside [onApprovePublish].
 *
 * Phases 2 (Engineering Intelligence) and 16 (Security) are Phase 1's
 * PackageIntakeAnalyzer plus the new SecurityScanner, both already
 * folded into [ImportPublishPipeline]. Phase 10 (device install) is
 * NOT part of this class -- this pipeline's target is a GitHub
 * repository + optional CI build, not an APK on this device; see
 * ASDP-001's own sequencing note for why Phase 10/11 (install +
 * validation) is later, separate work.
 */
@HiltViewModel
class DeploymentCenterViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val intakeAnalyzer: PackageIntakeAnalyzer,
    private val pipeline: ImportPublishPipeline,
    private val extractor: PackageExtractor,
    private val repositoryProvider: RepositoryProvider,
    private val deploymentEngine: DeploymentEngine,
    private val buildEngine: BuildEngine,
    private val approvalRepository: ApprovalRepository,
    private val auditRepository: AuditRepository,
    private val gitHubTokenStore: GitHubTokenStore,
) : ViewModel() {

    private val _stage = MutableStateFlow(DeploymentStage.IDLE)
    val stage: StateFlow<DeploymentStage> = _stage.asStateFlow()

    private val _intakeReport = MutableStateFlow<PackageIntakeReport?>(null)
    val intakeReport: StateFlow<PackageIntakeReport?> = _intakeReport.asStateFlow()

    private val _repoLookup = MutableStateFlow<RepositorySearchResult?>(null)
    val repoLookup: StateFlow<RepositorySearchResult?> = _repoLookup.asStateFlow()

    private val _preview = MutableStateFlow<DeploymentPreviewReport?>(null)
    val preview: StateFlow<DeploymentPreviewReport?> = _preview.asStateFlow()

    private val _buildProgressMessage = MutableStateFlow<String?>(null)
    val buildProgressMessage: StateFlow<String?> = _buildProgressMessage.asStateFlow()

    private val _executiveBrief = MutableStateFlow<ExecutiveDeploymentBrief?>(null)
    val executiveBrief: StateFlow<ExecutiveDeploymentBrief?> = _executiveBrief.asStateFlow()

    private val _failure = MutableStateFlow<DeploymentFailure?>(null)
    val failure: StateFlow<DeploymentFailure?> = _failure.asStateFlow()

    /** Prefilled from the same GitHub PAT/owner/repo config GitHubStatusProvider already uses -- Repository Selection (Phase 3) starts from what's already connected rather than asking the Owner to retype it every time. */
    val defaultOwner: String? get() = gitHubTokenStore.currentConfig()?.owner
    val defaultRepo: String? get() = gitHubTokenStore.currentConfig()?.repo

    private var selectedUri: Uri? = null
    private var workspaceRoot: File? = null
    private var buildMonitorJob: Job? = null

    // ---------------------------------------------------------------
    // Step 1/2: Import + Validate (Phase 1, already-real analysis)
    // ---------------------------------------------------------------

    fun onPackageSelected(uri: Uri) {
        selectedUri = uri
        viewModelScope.launch {
            reset()
            _stage.value = DeploymentStage.ANALYZING_PACKAGE
            try {
                val fileName = queryDisplayName(uri) ?: "selected_package.zip"
                val result = intakeAnalyzer.analyze(uri, fileName, context.contentResolver)
                _intakeReport.value = result

                auditRepository.record(deploymentAudit("ZIP Imported: ${result.fileName} (${result.fileCount} files, ${result.archiveSizeBytes} bytes)"))
                auditRepository.record(
                    deploymentAudit(
                        "Engineering Analysis: ${result.detectedProjectType} (${result.projectTypeConfidence})" +
                            if (result.isSafeToProceed) "" else " -- BLOCKED: ${result.warnings.count { it.severity == IntakeWarningSeverity.BLOCKING }} blocking issue(s)",
                    ),
                )

                if (result.isSafeToProceed) {
                    _stage.value = DeploymentStage.AWAITING_REPOSITORY_SELECTION
                } else {
                    fail(
                        DeploymentStage.ANALYZING_PACKAGE,
                        message = "This package failed validation and cannot proceed.",
                        likelyCause = result.warnings.filter { it.severity == IntakeWarningSeverity.BLOCKING }.joinToString("; ") { it.message }.ifBlank { "Archive could not be read." },
                        suggestedFix = "Fix the archive (remove unsafe/duplicate paths, ensure it's a valid ZIP) and import again.",
                    )
                }
            } catch (e: Exception) {
                fail(DeploymentStage.ANALYZING_PACKAGE, "Couldn't analyze that package.", e.message ?: e::class.simpleName ?: "Unknown error", "Try selecting the file again, or confirm it's a valid ZIP.")
            }
        }
    }

    // ---------------------------------------------------------------
    // Step 3 (repository half of Phase 3): find or create the target
    // ---------------------------------------------------------------

    fun lookupRepository(name: String) {
        viewModelScope.launch {
            _stage.value = DeploymentStage.RESOLVING_REPOSITORY
            _repoLookup.value = repositoryProvider.findRepository(name)
            when (val result = _repoLookup.value) {
                is RepositorySearchResult.Found -> {} // Owner sees it and taps "Use this repository" in the UI.
                is RepositorySearchResult.NotFound -> {} // Owner sees "not found" and taps "Create repository" in the UI -- see createAndUseRepository.
                is RepositorySearchResult.Failure -> fail(DeploymentStage.RESOLVING_REPOSITORY, "Couldn't search GitHub for that repository.", result.message, "Check the GitHub token under Settings, GitHub, then try again.")
                null -> {}
            }
            if (_stage.value == DeploymentStage.RESOLVING_REPOSITORY) {
                _stage.value = DeploymentStage.AWAITING_REPOSITORY_SELECTION
            }
        }
    }

    /** Only reachable from an explicit "Create repository" tap in the UI, never automatically -- ASDP-001 Phase 3's own "Never create repositories without owner approval," enforced structurally: there is no other call site for [RepositoryProvider.createRepository] in this class. */
    fun createAndUseRepository(name: String, isPrivate: Boolean) {
        viewModelScope.launch {
            _stage.value = DeploymentStage.RESOLVING_REPOSITORY
            when (val result = repositoryProvider.createRepository(name, "Created by JARVIS Deployment Center.", isPrivate)) {
                is RepositoryCreationResult.Success -> {
                    auditRepository.record(deploymentAudit("Repository Created: ${result.repository.fullName}"))
                    _repoLookup.value = RepositorySearchResult.Found(result.repository)
                    _stage.value = DeploymentStage.AWAITING_REPOSITORY_SELECTION
                }
                is RepositoryCreationResult.Failure -> fail(DeploymentStage.RESOLVING_REPOSITORY, "Couldn't create the repository.", result.message, "Check the repository name is available and the token has 'repo' scope, then try again.")
            }
        }
    }

    // ---------------------------------------------------------------
    // Steps 4/5/6 (Phase 4/6 pipeline): Extract, Analyze, Secure, Preview
    // ---------------------------------------------------------------

    fun buildPreview(owner: String, repo: String, branch: String) {
        val uri = selectedUri ?: return
        val report = _intakeReport.value ?: return

        viewModelScope.launch {
            _stage.value = DeploymentStage.BUILDING_PREVIEW
            when (val result = pipeline.run(uri, context.contentResolver, context.cacheDir, report, owner, repo, branch)) {
                is ImportPublishPipeline.PipelineResult.Success -> {
                    workspaceRoot = result.workspaceRoot
                    _preview.value = result.preview
                    if (result.preview.isSafeToPublish) {
                        _stage.value = DeploymentStage.AWAITING_APPROVAL
                    } else {
                        val reason = if (result.preview.totalChangedFiles == 0) {
                            "extracted contents. Every file already matches what's on $owner/$repo@$branch."
                        } else {
                            "${result.preview.blockingFindings.size} blocking security finding(s): " +
                                result.preview.blockingFindings.joinToString("; ") { "${it.path} -- ${it.message}" }
                        }
                        fail(
                            DeploymentStage.BUILDING_PREVIEW,
                            "This publish cannot proceed to Owner Approval.",
                            reason,
                            if (result.preview.totalChangedFiles == 0) "Nothing to publish -- import a package with real changes." else "Remove or replace the flagged file(s) in the source package and import again.",
                        )
                    }
                }
                is ImportPublishPipeline.PipelineResult.Failure -> fail(DeploymentStage.BUILDING_PREVIEW, "Couldn't build a deployment preview.", "${result.stage}: ${result.message}", suggestedFixFor(result.stage))
            }
        }
    }

    // ---------------------------------------------------------------
    // Step 7 (Phase 5): Owner Approval -- the one gate everything below depends on
    // ---------------------------------------------------------------

    fun onApprovePublish() {
        val preview = _preview.value ?: return
        val root = workspaceRoot ?: return
        if (!preview.isSafeToPublish) return

        viewModelScope.launch {
            val approval = approvalRepository.requestApproval(
                kind = ApprovalKind.PERMISSION_REQUEST,
                title = "Publish ${preview.totalChangedFiles} file(s) to ${preview.owner}/${preview.repo}@${preview.branch}",
                reason = preview.suggestedCommitMessage,
                riskLevel = RiskLevel.HIGH,
                requestedBy = "owner",
            )
            // The Owner tapping "Approve & Publish" on the Preview screen
            // IS the approval act (Phase 7's own two buttons: Approve &
            // Publish / Cancel) -- immediately resolving it here creates
            // a real, permanent ApprovalRepository/Timeline record of
            // that act, rather than a second, redundant confirmation
            // dialog for something the Owner already explicitly decided.
            approvalRepository.approve(approval.approvalId, actor = "owner", reason = "Approved via Deployment Center preview screen")
            auditRepository.record(deploymentAudit("Owner Approval: publish approved for ${preview.owner}/${preview.repo}@${preview.branch}"))

            commitAndPush(preview, root)
        }
    }

    private suspend fun commitAndPush(preview: DeploymentPreviewReport, root: File) {
        _stage.value = DeploymentStage.COMMITTING

        val changes = buildList {
            for (entry in preview.added + preview.modified) {
                add(FileChange(entry.path, pipeline.readAsBase64(root, entry.path)))
            }
            for (entry in preview.deleted) {
                add(FileChange(entry.path, null))
            }
        }

        when (val result = deploymentEngine.deploy(preview.owner, preview.repo, preview.branch, preview.suggestedCommitMessage, changes)) {
            is DeploymentResult.Success -> {
                auditRepository.record(deploymentAudit("Commit + Push: ${result.commitSha.take(7)} to ${preview.owner}/${preview.repo}@${preview.branch} -- ${preview.totalChangedFiles} file(s)"))
                triggerBuildAndMonitor(preview, result)
            }
            is DeploymentResult.Failure -> {
                fail(DeploymentStage.COMMITTING, "The publish failed while committing/pushing to GitHub.", result.message, "Check the GitHub token's scopes and that the branch still exists, then try again from Preview.")
            }
        }
    }

    // ---------------------------------------------------------------
    // Steps 9/10 (Phase 7/9): trigger + monitor the CI build
    // ---------------------------------------------------------------

    private fun triggerBuildAndMonitor(preview: DeploymentPreviewReport, deployResult: DeploymentResult.Success) {
        _stage.value = DeploymentStage.TRIGGERING_BUILD
        buildMonitorJob = viewModelScope.launch {
            val started = Instant.now()
            when (val trigger = buildEngine.triggerBuild(preview.owner, preview.repo, preview.branch)) {
                is BuildTriggerResult.Failure -> {
                    // The publish itself already succeeded -- a build
                    // trigger failure is reported as part of a COMPLETE
                    // brief, not as an overall FAILED pipeline, since
                    // the thing Owner Approval actually authorized
                    // (the commit/push) is done and real.
                    auditRepository.record(deploymentAudit("GitHub Actions: not triggered -- ${trigger.message}"))
                    finishWithBrief(preview, deployResult, buildTriggered = false, buildSucceeded = null, durationSeconds = null, logsUrl = null)
                }
                is BuildTriggerResult.Triggered -> {
                    _stage.value = DeploymentStage.MONITORING_BUILD
                    pollBuild(preview, deployResult, trigger.buildId, started)
                }
            }
        }
    }

    private suspend fun pollBuild(preview: DeploymentPreviewReport, deployResult: DeploymentResult.Success, buildId: String, started: Instant) {
        repeat(MAX_BUILD_POLL_ATTEMPTS) { attempt ->
            when (val status = buildEngine.checkStatus(preview.owner, preview.repo, buildId)) {
                is BuildStatusResult.InProgress -> {
                    _buildProgressMessage.value = status.progress.message
                }
                is BuildStatusResult.Success -> {
                    val duration = java.time.Duration.between(started, Instant.now()).seconds
                    auditRepository.record(deploymentAudit("GitHub Actions: succeeded in ${duration}s"))
                    finishWithBrief(preview, deployResult, buildTriggered = true, buildSucceeded = true, durationSeconds = duration, logsUrl = "https://github.com/${preview.owner}/${preview.repo}/actions/runs/$buildId")
                    return
                }
                is BuildStatusResult.Failure -> {
                    val duration = java.time.Duration.between(started, Instant.now()).seconds
                    auditRepository.record(deploymentAudit("GitHub Actions: failed -- ${status.message}"))
                    finishWithBrief(preview, deployResult, buildTriggered = true, buildSucceeded = false, durationSeconds = duration, logsUrl = "https://github.com/${preview.owner}/${preview.repo}/actions/runs/$buildId")
                    return
                }
            }
            delay(BUILD_POLL_INTERVAL_MS)
        }
        // Real limit stated honestly rather than polling forever: after
        // ~10 minutes of polling this stops and reports "still running"
        // rather than pretending to know the outcome.
        _buildProgressMessage.value = "Still running after ${MAX_BUILD_POLL_ATTEMPTS * BUILD_POLL_INTERVAL_MS / 60000} minutes -- check GitHub Actions directly."
        finishWithBrief(preview, deployResult, buildTriggered = true, buildSucceeded = null, durationSeconds = null, logsUrl = "https://github.com/${preview.owner}/${preview.repo}/actions")
    }

    // ---------------------------------------------------------------
    // Step 11 (Phase 12/13): Executive Brief
    // ---------------------------------------------------------------

    private fun finishWithBrief(
        preview: DeploymentPreviewReport,
        deployResult: DeploymentResult.Success,
        buildTriggered: Boolean,
        buildSucceeded: Boolean?,
        durationSeconds: Long?,
        logsUrl: String?,
    ) {
        _executiveBrief.value = ExecutiveDeploymentBrief(
            repoFullName = "${preview.owner}/${preview.repo}",
            filesChanged = preview.totalChangedFiles,
            commitSha = deployResult.commitSha,
            commitUrl = deployResult.commitUrl,
            buildTriggered = buildTriggered,
            buildSucceeded = buildSucceeded,
            buildDurationSeconds = durationSeconds,
            buildLogsUrl = logsUrl,
        )
        _stage.value = DeploymentStage.COMPLETE
        workspaceRoot?.let { extractor.cleanup(it) }
        workspaceRoot = null
    }

    // ---------------------------------------------------------------
    // Cancel / Back / Reset
    // ---------------------------------------------------------------

    fun onCancel() {
        buildMonitorJob?.cancel()
        workspaceRoot?.let { extractor.cleanup(it) }
        reset()
    }

    fun onBackToRepositorySelection() {
        _preview.value = null
        _failure.value = null
        workspaceRoot?.let { extractor.cleanup(it) }
        workspaceRoot = null
        _stage.value = DeploymentStage.AWAITING_REPOSITORY_SELECTION
    }

    fun startOver() {
        buildMonitorJob?.cancel()
        workspaceRoot?.let { extractor.cleanup(it) }
        reset()
    }

    private fun reset() {
        selectedUri = null
        workspaceRoot = null
        _intakeReport.value = null
        _repoLookup.value = null
        _preview.value = null
        _buildProgressMessage.value = null
        _executiveBrief.value = null
        _failure.value = null
        _stage.value = DeploymentStage.IDLE
    }

    private fun fail(stage: DeploymentStage, message: String, likelyCause: String, suggestedFix: String) {
        _failure.value = DeploymentFailure(stage, message, likelyCause, suggestedFix)
        _stage.value = DeploymentStage.FAILED
        auditRepository.record(deploymentAudit("Deployment Failed at $stage: $message"))
    }

    private fun suggestedFixFor(pipelineStage: String): String = when (pipelineStage) {
        "Extraction" -> "The archive may be corrupt or too large. Re-export it and try again."
        "Change Analysis" -> "Confirm the target branch exists (create the repository first if it's brand new) and that the GitHub token can read it."
        else -> "Review the error above and try again."
    }

    private fun deploymentAudit(summary: String): AuditEntry = AuditEntry(
        entryId = UUID.randomUUID().toString(),
        timestamp = Instant.now(),
        category = "Deployment",
        summary = summary,
    )

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

    override fun onCleared() {
        super.onCleared()
        buildMonitorJob?.cancel()
        workspaceRoot?.let { extractor.cleanup(it) }
    }

    companion object {
        private const val BUILD_POLL_INTERVAL_MS = 5_000L
        private const val MAX_BUILD_POLL_ATTEMPTS = 120 // 10 minutes at 5s intervals
    }
}
