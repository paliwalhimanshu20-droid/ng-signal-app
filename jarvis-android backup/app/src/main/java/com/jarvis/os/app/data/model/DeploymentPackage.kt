package com.jarvis.os.app.data.model

/**
 * ASDP-001 Phase 1 "Package Intake" -- the only phase of the
 * Autonomous Software Delivery Platform spec that ships as real code
 * this round (see docs/architecture/JARVIS/ASDP-001-Feasibility-and-Roadmap.md
 * for why every later phase either needs reframing or is blocked
 * outright on stock Android, and shouldn't be coded against until that
 * reframing is agreed).
 */
enum class DetectedProjectType {
    ANDROID, JETPACK_COMPOSE, FLUTTER, REACT_NATIVE, KOTLIN, JAVA, PYTHON, NODE, LIBRARY, DESKTOP, MULTI_MODULE, UNKNOWN
}

enum class IntakeWarningSeverity { INFO, WARNING, BLOCKING }

data class IntakeWarning(
    val severity: IntakeWarningSeverity,
    val message: String,
)

/**
 * The full, honest result of Phase 1 -- every field here is something
 * PackageIntakeAnalyzer actually computed from the real archive bytes,
 * never a placeholder. `blockingWarnings` (severity == BLOCKING) means
 * the archive must not be used for anything downstream -- Phase 1's
 * own "reject malformed archives" and "detect unsafe paths"
 * requirements, both enforced here rather than left for a later phase
 * to discover the hard way.
 */
data class PackageIntakeReport(
    val fileName: String,
    val archiveSizeBytes: Long,
    val sha256Checksum: String,
    val isValidArchive: Boolean,
    val fileCount: Int,
    val estimatedUncompressedBytes: Long,
    val detectedProjectType: DetectedProjectType,
    val projectTypeConfidence: String,
    val hasGitDirectory: Boolean,
    val hasReadme: Boolean,
    val duplicatePaths: List<String>,
    val unsafePaths: List<String>,
    val warnings: List<IntakeWarning>,
) {
    val isSafeToProceed: Boolean
        get() = isValidArchive && unsafePaths.isEmpty() && warnings.none { it.severity == IntakeWarningSeverity.BLOCKING }
}
