package com.jarvis.os.app.data.model

/**
 * ASDP-001 Phases 2/3/4/6/9/12 -- the models the rest of the Import &
 * Publish pipeline is built on, following straight on from Phase 1's
 * [PackageIntakeReport] (see DeploymentPackage.kt). Everything here is
 * produced from real data (a real extracted file on disk, a real
 * GitHub Git Trees API response, a real regex match against real
 * bytes) -- there is no placeholder/mocked field in this file.
 */

/** Phase 4/6 "Categorize: Added / Modified / Deleted / Unchanged." Renamed is deliberately not a fifth case here -- see ChangeAnalyzer's docstring for why rename detection is out of scope for this round rather than faked. */
enum class FileChangeType { ADDED, MODIFIED, DELETED, UNCHANGED }

/**
 * One file's outcome from comparing the extracted package against the
 * target branch's current tree. [gitBlobSha1] is computed identically
 * to how git itself hashes a blob (`sha1("blob " + size + "\u0000" +
 * bytes)`) -- that's what lets ChangeAnalyzer classify MODIFIED vs.
 * UNCHANGED by comparing hashes against GitHub's own tree entries,
 * without downloading and diffing file content. Null for DELETED
 * entries (nothing local to hash) and for [PackageExtractor] entries
 * this analysis hasn't run against yet.
 */
data class FileChangeEntry(
    val path: String,
    val type: FileChangeType,
    val sizeBytes: Long,
    val gitBlobSha1: String? = null,
)

/** Phase 5/6/16 severity, reused across security findings the same way [IntakeWarningSeverity] is reused across Phase 1 -- one severity vocabulary for the whole pipeline. BLOCKING means "cannot proceed to Owner Approval," same meaning as Phase 1's blocking warnings. */
enum class SecuritySeverity { INFO, WARNING, BLOCKING }

data class SecurityFinding(
    val path: String,
    val severity: SecuritySeverity,
    val message: String,
)

/**
 * Phase 4 "Preview Screen" -- the complete, real result of Extraction
 * + Change Analysis + Security Review for one candidate publish. Never
 * partially populated: [ImportPublishPipeline.run] only returns this
 * once every stage that can run without Owner Approval has actually
 * run.
 */
data class DeploymentPreviewReport(
    val owner: String,
    val repo: String,
    val branch: String,
    val suggestedCommitMessage: String,
    val added: List<FileChangeEntry>,
    val modified: List<FileChangeEntry>,
    val deleted: List<FileChangeEntry>,
    val unchangedCount: Int,
    val securityFindings: List<SecurityFinding>,
    val estimatedCommitBytes: Long,
) {
    val totalChangedFiles: Int get() = added.size + modified.size + deleted.size
    val blockingFindings: List<SecurityFinding> get() = securityFindings.filter { it.severity == SecuritySeverity.BLOCKING }

    /** Phase 6's own "nothing is published automatically" gate, mirrored here as data rather than left as a UI-only convention -- the Approve button (Phase 7) checks this before it's even enabled. */
    val isSafeToPublish: Boolean get() = blockingFindings.isEmpty() && totalChangedFiles > 0
}

/** Phase 4/11 "Owner Sovereignty" state machine driving DeploymentCenterScreen -- one linear pipeline, matching this feature's own architecture diagram exactly (Import -> Validate -> Extract -> Analyze -> Security -> Preview -> Approval -> Commit -> Push -> Monitor -> Brief). */
enum class DeploymentStage {
    IDLE,
    ANALYZING_PACKAGE,
    AWAITING_REPOSITORY_SELECTION,
    RESOLVING_REPOSITORY,
    BUILDING_PREVIEW,
    AWAITING_APPROVAL,
    COMMITTING,
    TRIGGERING_BUILD,
    MONITORING_BUILD,
    COMPLETE,
    FAILED,
}

/** Phase 11 "If failed: Explain which step failed / likely cause / suggested fix" -- structured, not just a raw exception string, so the UI can render all three parts every time, honestly, instead of dumping a stack trace. */
data class DeploymentFailure(
    val stage: DeploymentStage,
    val message: String,
    val likelyCause: String,
    val suggestedFix: String,
)

/** Phase 11 "Executive Brief" -- the structured data behind JARVIS's natural-language report; the screen renders this, and a future voice/chat surface can read the same fields aloud without re-deriving them. */
data class ExecutiveDeploymentBrief(
    val repoFullName: String,
    val filesChanged: Int,
    val commitSha: String,
    val commitUrl: String,
    val buildTriggered: Boolean,
    val buildSucceeded: Boolean?,
    val buildDurationSeconds: Long?,
    val buildLogsUrl: String?,
)
