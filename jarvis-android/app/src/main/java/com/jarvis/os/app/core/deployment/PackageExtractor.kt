package com.jarvis.os.app.core.deployment

import android.content.ContentResolver
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ASDP-001 Phase 3 "Extraction" -- "Extract into an isolated
 * workspace. Never modify the Git repository directly during
 * extraction," taken literally: this class has no knowledge of Git,
 * GitHub, or any repository at all, and only ever writes under
 * [android.content.Context.getCacheDir]/deployment_workspace/<importId>,
 * a directory this class owns exclusively for one import. Nothing it
 * writes is ever read by anything except [ChangeAnalyzer] and the
 * commit step in DeploymentCenterViewModel, both of which are told
 * exactly this directory, never a bare filename they could resolve
 * anywhere else.
 *
 * Re-validates path safety itself (BLOCKING-severity findings from
 * [PackageIntakeAnalyzer] already reject unsafe archives before this
 * class is ever called, but an extractor that trusted a prior class's
 * validation instead of checking its own writes would be exactly the
 * kind of "trust the caller" bug path-traversal defenses exist to
 * avoid) via a canonical-path containment check on every single entry,
 * not just the ".."/leading-"/" substring check Phase 1 already did.
 */
sealed interface ExtractionResult {
    data class Success(val workspaceRoot: File, val files: List<ExtractedFile>) : ExtractionResult
    data class Failure(val message: String) : ExtractionResult
}

/** One extracted file, real bytes already on disk at [absolutePath]. [gitBlobSha1] is computed once, here, at extraction time -- the one place this pipeline reads every byte of every file, so ChangeAnalyzer and the commit step never need to re-read a file just to hash it again. */
data class ExtractedFile(
    val relativePath: String,
    val absolutePath: File,
    val sizeBytes: Long,
    val gitBlobSha1: String,
)

@Singleton
class PackageExtractor @Inject constructor() {

    suspend fun extract(
        uri: Uri,
        contentResolver: ContentResolver,
        cacheDir: File,
    ): ExtractionResult = withContext(Dispatchers.IO) {
        val workspaceRoot = File(File(cacheDir, "deployment_workspace"), UUID.randomUUID().toString())
        val canonicalRoot = try {
            workspaceRoot.mkdirs()
            workspaceRoot.canonicalFile
        } catch (e: Exception) {
            return@withContext ExtractionResult.Failure("Couldn't create an extraction workspace: ${e.message}")
        }

        val extracted = mutableListOf<ExtractedFile>()
        var totalBytes = 0L

        try {
            val stream = contentResolver.openInputStream(uri)
                ?: return@withContext ExtractionResult.Failure("Couldn't reopen the archive for extraction.")
            ZipInputStream(stream).use { zip ->
                var entry: ZipEntry? = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val targetFile = resolveWithinRoot(canonicalRoot, entry.name)
                            ?: return@withContext ExtractionResult.Failure(
                                "Rejected during extraction: '${entry.name}' resolves outside the workspace. " +
                                    "This should already have been caught during Package Intake -- treating it as fatal rather than silently skipping it.",
                            )

                        targetFile.parentFile?.mkdirs()
                        val digest = MessageDigest.getInstance("SHA-1")
                        var entryBytes = 0L
                        targetFile.outputStream().use { out ->
                            val buffer = ByteArray(8192)
                            while (true) {
                                val read = zip.read(buffer)
                                if (read == -1) break
                                entryBytes += read
                                totalBytes += read
                                if (entryBytes > MAX_SINGLE_FILE_BYTES) {
                                    throw ExtractionLimitExceeded("'${entry.name}' exceeds the ${MAX_SINGLE_FILE_BYTES / (1024 * 1024)} MB single-file limit.")
                                }
                                if (totalBytes > MAX_TOTAL_EXTRACTED_BYTES) {
                                    throw ExtractionLimitExceeded("Extracted contents exceed the ${MAX_TOTAL_EXTRACTED_BYTES / (1024 * 1024)} MB total limit -- possible zip bomb.")
                                }
                                out.write(buffer, 0, read)
                                // Blob sha1 must cover the exact same bytes as
                                // git's own algorithm: "blob <size>\0<content>".
                                // The header is fed in once the size is known
                                // (below), not per-chunk -- see the comment
                                // after this loop.
                            }
                        }
                        zip.closeEntry()

                        val gitBlobSha1 = gitBlobSha1(targetFile, entryBytes)
                        extracted += ExtractedFile(
                            relativePath = entry.name.trimStart('/'),
                            absolutePath = targetFile,
                            sizeBytes = entryBytes,
                            gitBlobSha1 = gitBlobSha1,
                        )
                    } else {
                        zip.closeEntry()
                    }
                    entry = zip.nextEntry
                }
            }
        } catch (e: ExtractionLimitExceeded) {
            workspaceRoot.deleteRecursively()
            return@withContext ExtractionResult.Failure(e.message ?: "Extraction limit exceeded.")
        } catch (e: Exception) {
            workspaceRoot.deleteRecursively()
            return@withContext ExtractionResult.Failure("Extraction failed: ${e.message ?: e::class.simpleName}")
        }

        if (extracted.isEmpty()) {
            workspaceRoot.deleteRecursively()
            return@withContext ExtractionResult.Failure("Archive contained no files to extract.")
        }

        ExtractionResult.Success(workspaceRoot, extracted)
    }

    /** Called once the commit/preview step is fully done with a workspace, success or failure -- extraction workspaces are scratch space, never a long-lived cache. */
    fun cleanup(workspaceRoot: File) {
        runCatching { workspaceRoot.deleteRecursively() }
    }

    private class ExtractionLimitExceeded(message: String) : Exception(message)

    /**
     * The actual path-traversal defense: resolve [entryName] against
     * [canonicalRoot] and require the result's OWN canonical path to
     * still start with the root's. A raw `File(root, entryName)`
     * followed by a string `startsWith` check (without canonicalizing
     * first) is the well-known broken version of this check --
     * `..`-segments and symlink-like entries can defeat a string
     * comparison that never resolves `.`/`..` -- so this always calls
     * `.canonicalFile` on the candidate before comparing.
     */
    private fun resolveWithinRoot(canonicalRoot: File, entryName: String): File? {
        val candidate = File(canonicalRoot, entryName)
        val canonicalCandidate = try {
            candidate.canonicalFile
        } catch (e: Exception) {
            return null
        }
        return if (canonicalCandidate.path.startsWith(canonicalRoot.path + File.separator)) canonicalCandidate else null
    }

    /** git's blob hash: sha1("blob " + <decimal byte count> + "\0" + <content>). Computed by re-reading the just-written file once, deliberately -- streaming the header into the digest before the content bytes are known would require buffering the whole entry in memory first (defeating the point of streaming straight to disk above), and re-reading a file that's at most [MAX_SINGLE_FILE_BYTES] is cheap. */
    private fun gitBlobSha1(file: File, sizeBytes: Long): String {
        val digest = MessageDigest.getInstance("SHA-1")
        digest.update("blob $sizeBytes\u0000".toByteArray(Charsets.UTF_8))
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val MAX_SINGLE_FILE_BYTES = 50L * 1024 * 1024
        const val MAX_TOTAL_EXTRACTED_BYTES = 300L * 1024 * 1024
    }
}
