package com.jarvis.os.app.core.deployment

import android.content.ContentResolver
import android.net.Uri
import com.jarvis.os.app.data.model.DetectedProjectType
import com.jarvis.os.app.data.model.IntakeWarning
import com.jarvis.os.app.data.model.IntakeWarningSeverity
import com.jarvis.os.app.data.model.PackageIntakeReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ASDP-001 Phase 1 "Package Intake" -- real analysis, not mocked.
 * Reads the archive TWICE, both times via ContentResolver against the
 * Storage Access Framework Uri the Owner picked (never a raw file
 * path -- SAF is what lets this run with no broad storage permission
 * at all, only access to the one file the Owner explicitly chose):
 * once as a flat byte stream to compute a real SHA-256 checksum over
 * the exact bytes on disk, once as a ZipInputStream to walk entries.
 * Two passes, not one, because computing a checksum of "the file" and
 * enumerating "the zip's entries" are different operations on the same
 * bytes -- doing both from one stream would mean choosing which one
 * gets the real stream and which gets a re-wrap, and two clean passes
 * over a re-openable content:// Uri is simpler and no slower for
 * anything this analyzer is likely to see.
 *
 * NEVER extracts, writes, or executes anything -- every ZipEntry's
 * content bytes are read only far enough to advance the stream
 * (`zipStream.closeEntry()` after reading into a bounded buffer purely
 * to compute size) and are never written to disk, matching Phase 1's
 * own "Never execute anything inside the archive" requirement exactly.
 */
@Singleton
class PackageIntakeAnalyzer @Inject constructor() {

    suspend fun analyze(uri: Uri, fileName: String, contentResolver: ContentResolver): PackageIntakeReport = withContext(Dispatchers.IO) {
        val warnings = mutableListOf<IntakeWarning>()

        val (checksum, archiveSize) = computeChecksumAndSize(uri, contentResolver)

        val entries = runCatching { readEntries(uri, contentResolver) }.getOrElse { error ->
            return@withContext PackageIntakeReport(
                fileName = fileName,
                archiveSizeBytes = archiveSize,
                sha256Checksum = checksum,
                isValidArchive = false,
                fileCount = 0,
                estimatedUncompressedBytes = 0,
                detectedProjectType = DetectedProjectType.UNKNOWN,
                projectTypeConfidence = "unknown -- archive could not be read",
                hasGitDirectory = false,
                hasReadme = false,
                duplicatePaths = emptyList(),
                unsafePaths = emptyList(),
                warnings = listOf(
                    IntakeWarning(
                        IntakeWarningSeverity.BLOCKING,
                        "Archive could not be read as a valid ZIP: ${error.message ?: error::class.simpleName}",
                    ),
                ),
            )
        }

        val paths = entries.map { it.name }
        val unsafePaths = paths.filter { isUnsafePath(it) }
        val duplicatePaths = paths.groupingBy { it.lowercase() }.eachCount().filter { it.value > 1 }.keys.toList()

        if (unsafePaths.isNotEmpty()) {
            warnings += IntakeWarning(
                IntakeWarningSeverity.BLOCKING,
                "${unsafePaths.size} unsafe path(s) detected (directory traversal or absolute path) -- this archive cannot proceed to any later phase.",
            )
        }
        if (duplicatePaths.isNotEmpty()) {
            warnings += IntakeWarning(
                IntakeWarningSeverity.WARNING,
                "${duplicatePaths.size} duplicate path(s) detected (case-insensitive) -- may indicate a corrupted or maliciously crafted archive.",
            )
        }
        if (entries.isEmpty()) {
            warnings += IntakeWarning(IntakeWarningSeverity.BLOCKING, "Archive contains no entries.")
        }

        val safePaths = paths.filterNot { isUnsafePath(it) }
        val (projectType, confidence) = detectProjectType(safePaths)
        val hasGit = safePaths.any { it.startsWith(".git/") || it == ".git" }
        val hasReadme = safePaths.any { it.substringAfterLast('/').lowercase().startsWith("readme") }

        if (hasGit) {
            warnings += IntakeWarning(
                IntakeWarningSeverity.INFO,
                "Archive already contains a .git directory -- Phase 6 (once built) must preserve it, never overwrite it, per this feature's own security requirements.",
            )
        }

        PackageIntakeReport(
            fileName = fileName,
            archiveSizeBytes = archiveSize,
            sha256Checksum = checksum,
            isValidArchive = true,
            fileCount = entries.size,
            estimatedUncompressedBytes = entries.sumOf { if (it.size >= 0) it.size else 0L },
            detectedProjectType = projectType,
            projectTypeConfidence = confidence,
            hasGitDirectory = hasGit,
            hasReadme = hasReadme,
            duplicatePaths = duplicatePaths,
            unsafePaths = unsafePaths,
            warnings = warnings,
        )
    }

    private fun computeChecksumAndSize(uri: Uri, contentResolver: ContentResolver): Pair<String, Long> {
        val digest = MessageDigest.getInstance("SHA-256")
        var totalBytes = 0L
        contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
                totalBytes += read
            }
        }
        val hex = digest.digest().joinToString("") { "%02x".format(it) }
        return hex to totalBytes
    }

    /** ZipEntry name + declared uncompressed size only -- entry.size is what the archive itself claims, used purely for the estimate this phase reports; never trusted beyond that estimate (a later phase actually extracting must still enforce real limits during extraction, not just read this number). */
    private data class SimpleEntry(val name: String, val size: Long)

    private fun readEntries(uri: Uri, contentResolver: ContentResolver): List<SimpleEntry> {
        val entries = mutableListOf<SimpleEntry>()
        val stream = contentResolver.openInputStream(uri) ?: throw ZipException("Could not open archive stream.")
        ZipInputStream(stream).use { zip ->
            var entry: ZipEntry? = zip.nextEntry
            while (entry != null) {
                entries += SimpleEntry(entry.name, entry.size)
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return entries
    }

    /**
     * Rejects anything that could escape the eventual extraction root:
     * ".." path segments (directory traversal, this feature's own
     * explicitly named threat), a leading "/" (absolute path escaping
     * the archive root), and a leading "~" (home-directory expansion
     * on some extractors). Checked on the raw ZipEntry name, before any
     * normalization -- normalizing first would risk silently
     * "fixing" a malicious path into a safe-looking one instead of
     * flagging it.
     */
    private fun isUnsafePath(path: String): Boolean {
        if (path.startsWith("/") || path.startsWith("\\") || path.startsWith("~")) return true
        val segments = path.split('/', '\\')
        return segments.any { it == ".." }
    }

    private fun detectProjectType(paths: List<String>): Pair<DetectedProjectType, String> {
        val topLevelAndShallow = paths.filter { it.count { c -> c == '/' } <= 2 }.map { it.lowercase() }

        fun has(vararg markers: String): Boolean = topLevelAndShallow.any { path -> markers.any { path.endsWith(it) } }

        val moduleCount = paths.count { it.lowercase().endsWith("/build.gradle") || it.lowercase().endsWith("/build.gradle.kts") }

        return when {
            moduleCount > 1 -> DetectedProjectType.MULTI_MODULE to "high -- ${moduleCount} Gradle modules detected"
            has("pubspec.yaml") -> DetectedProjectType.FLUTTER to "high -- pubspec.yaml present"
            has("package.json") && paths.any { it.lowercase().contains("react-native") } -> DetectedProjectType.REACT_NATIVE to "medium -- package.json plus a react-native reference"
            has("package.json") -> DetectedProjectType.NODE to "high -- package.json present"
            has("build.gradle.kts", "build.gradle") && paths.any { it.lowercase().contains("compose") } -> DetectedProjectType.JETPACK_COMPOSE to "medium -- Gradle build plus a compose reference"
            has("build.gradle.kts", "build.gradle") && paths.any { it.lowercase().endsWith("androidmanifest.xml") } -> DetectedProjectType.ANDROID to "high -- Gradle build plus AndroidManifest.xml"
            has("build.gradle.kts", "build.gradle") -> DetectedProjectType.KOTLIN to "medium -- Gradle build, no Android manifest found"
            has("pom.xml") -> DetectedProjectType.JAVA to "high -- pom.xml present"
            has("requirements.txt", "pyproject.toml", "setup.py") -> DetectedProjectType.PYTHON to "high -- Python project marker present"
            paths.any { it.lowercase().endsWith(".csproj") || it.lowercase().endsWith(".sln") } -> DetectedProjectType.DESKTOP to "medium -- .NET project file present"
            else -> DetectedProjectType.UNKNOWN to "low -- no recognized project marker found at or near the archive root"
        }
    }
}
