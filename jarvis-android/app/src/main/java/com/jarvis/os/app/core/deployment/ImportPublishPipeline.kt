package com.jarvis.os.app.core.deployment

import android.content.ContentResolver
import android.net.Uri
import com.jarvis.os.app.data.model.DeploymentPreviewReport
import com.jarvis.os.app.data.model.FileChangeType
import com.jarvis.os.app.data.model.PackageIntakeReport
import java.io.File
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The sprint brief's own "Design this as a generic Import & Publish
 * Framework... Do not hard-code behavior specifically for Claude
 * ZIPs" requirement, taken literally: this class knows nothing about
 * where the archive came from (Claude, another AI, a manual export) --
 * it only depends on [PackageIntakeReport] (already-validated Phase 1
 * output) plus a target owner/repo/branch, and produces a
 * [DeploymentPreviewReport]. Every future package type the roadmap
 * names (patches, templates, plugin packages, ProjectOS exports) is
 * "produce a valid PackageIntakeReport-shaped input for this same
 * pipeline," not a new pipeline.
 *
 * Owns exactly the middle of the architecture diagram --
 * Extract -> Analyze -> Compute Changes -> Security Review -> Preview
 * -- and nothing on either side of it: it never touches Owner
 * Approval, Commit, or Push (DeploymentCenterViewModel drives those,
 * explicitly gated on the Owner tapping Approve), and it never re-runs
 * Phase 1/2 validation (the caller must only invoke this with a
 * [PackageIntakeReport] whose `isSafeToProceed` is already true).
 */
@Singleton
class ImportPublishPipeline @Inject constructor(
    private val extractor: PackageExtractor,
    private val changeAnalyzer: ChangeAnalyzer,
    private val securityScanner: SecurityScanner,
) {

    sealed interface PipelineResult {
        data class Success(val workspaceRoot: File, val preview: DeploymentPreviewReport) : PipelineResult
        data class Failure(val stage: String, val message: String) : PipelineResult
    }

    /**
     * [intakeReport] must already be safe-to-proceed (Phase 1) -- this
     * is asserted, not re-validated, because re-deciding safety here
     * would mean two different classes could disagree about what
     * "safe" means. Returns the extraction workspace root alongside
     * the preview so the caller (DeploymentCenterViewModel) can read
     * file content from it at commit time and is responsible for
     * calling [PackageExtractor.cleanup] on it once done, success or
     * failure.
     */
    suspend fun run(
        uri: Uri,
        contentResolver: ContentResolver,
        cacheDir: File,
        intakeReport: PackageIntakeReport,
        owner: String,
        repo: String,
        branch: String,
    ): PipelineResult {
        check(intakeReport.isSafeToProceed) { "ImportPublishPipeline.run called with an unsafe PackageIntakeReport -- the caller must gate on isSafeToProceed before reaching here." }

        val extraction = extractor.extract(uri, contentResolver, cacheDir)
        val (workspaceRoot, extractedFiles) = when (extraction) {
            is ExtractionResult.Failure -> return PipelineResult.Failure("Extraction", extraction.message)
            is ExtractionResult.Success -> extraction.workspaceRoot to extraction.files
        }

        val analysis = changeAnalyzer.analyze(owner, repo, branch, extractedFiles)
        val (added, modified, deleted, unchangedCount) = when (analysis) {
            is ChangeAnalyzer.AnalysisResult.Failure -> {
                extractor.cleanup(workspaceRoot)
                return PipelineResult.Failure("Change Analysis", analysis.message)
            }
            is ChangeAnalyzer.AnalysisResult.Success -> analysis
        }

        val candidatePaths = (added.map { it.path } + modified.map { it.path }).toSet()
        val candidateFiles = extractedFiles.filter { it.relativePath in candidatePaths }
        val securityFindings = securityScanner.scan(candidateFiles)

        val estimatedCommitBytes = (added + modified).sumOf { it.sizeBytes }

        val preview = DeploymentPreviewReport(
            owner = owner,
            repo = repo,
            branch = branch,
            suggestedCommitMessage = suggestCommitMessage(added.size, modified.size, deleted.size, intakeReport.detectedProjectType.name),
            added = added,
            modified = modified,
            deleted = deleted,
            unchangedCount = unchangedCount,
            securityFindings = securityFindings,
            estimatedCommitBytes = estimatedCommitBytes,
        )

        return PipelineResult.Success(workspaceRoot, preview)
    }

    /** Reads a candidate file's bytes back from the extraction workspace and base64-encodes them, for exactly one file, at commit time -- called by DeploymentCenterViewModel once per ADDED/MODIFIED entry, never eagerly for the whole set (holding every changed file's base64 in memory at once for a large publish would be wasteful; each is read right before its blob upload). */
    fun readAsBase64(workspaceRoot: File, relativePath: String): String {
        val file = File(workspaceRoot, relativePath)
        return Base64.getEncoder().encodeToString(file.readBytes())
    }

    private fun suggestCommitMessage(addedCount: Int, modifiedCount: Int, deletedCount: Int, projectType: String): String {
        val parts = mutableListOf<String>()
        if (addedCount > 0) parts += "$addedCount added"
        if (modifiedCount > 0) parts += "$modifiedCount modified"
        if (deletedCount > 0) parts += "$deletedCount deleted"
        val summary = if (parts.isEmpty()) "no file changes" else parts.joinToString(", ")
        return "Deploy via JARVIS: $summary ($projectType project)"
    }
}
