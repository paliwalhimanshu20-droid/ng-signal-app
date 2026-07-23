package com.jarvis.os.app.core.deployment

import com.jarvis.os.app.core.deployment.engines.GitHubApiClient
import com.jarvis.os.app.data.model.FileChangeEntry
import com.jarvis.os.app.data.model.FileChangeType
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ASDP-001 Phase 4/6 "Compute File Changes" -- "Compare extracted
 * contents with the repository. Categorize: Added / Modified /
 * Deleted / Unchanged."
 *
 * Deliberately does NOT `git clone` or download every remote file's
 * content to diff byte-for-byte -- there's no local git on stock
 * Android (the same wall ASDP-001's own feasibility doc names for
 * Phase 6), and it's unnecessary: GitHub's recursive Git Trees API
 * returns every blob's real git SHA-1 for free, and [PackageExtractor]
 * already computed the identical hash for every local file during
 * extraction (git blob hashing is a pure function of "blob <size>\0
 * <bytes>," so the same input always produces the same hash whether
 * git, GitHub, or this class computed it). Comparing hashes is exactly
 * as correct as comparing content, at a fraction of the network cost.
 *
 * Renamed detection (mentioned in the sprint brief as "if detectable")
 * is deliberately NOT implemented here -- a real rename detector needs
 * similarity heuristics (e.g. comparing an added file's hash against
 * every deleted file's hash for an exact match, or a content-similarity
 * score for near-matches) that add real complexity for a "nice to
 * have, if detectable" requirement; a renamed file today is correctly,
 * if less elegantly, reported as one DELETED entry + one ADDED entry,
 * which is still accurate, just not collapsed into a single "renamed"
 * row.
 */
@Singleton
class ChangeAnalyzer @Inject constructor(
    private val api: GitHubApiClient,
) {

    sealed interface AnalysisResult {
        data class Success(
            val added: List<FileChangeEntry>,
            val modified: List<FileChangeEntry>,
            val deleted: List<FileChangeEntry>,
            val unchangedCount: Int,
        ) : AnalysisResult
        data class Failure(val message: String) : AnalysisResult
    }

    suspend fun analyze(owner: String, repo: String, branch: String, extractedFiles: List<ExtractedFile>): AnalysisResult {
        val remoteBlobShas = fetchRemoteTree(owner, repo, branch)
            ?: return AnalysisResult.Failure(
                "Couldn't read the current contents of $owner/$repo@$branch to compare against. " +
                    "Either the branch doesn't exist yet (create the repository/branch first) or the token lacks read access.",
            )

        val localByPath = extractedFiles.associateBy { it.relativePath }

        val added = mutableListOf<FileChangeEntry>()
        val modified = mutableListOf<FileChangeEntry>()
        var unchanged = 0

        for (file in extractedFiles) {
            val remoteSha = remoteBlobShas[file.relativePath]
            when {
                remoteSha == null -> added += FileChangeEntry(file.relativePath, FileChangeType.ADDED, file.sizeBytes, file.gitBlobSha1)
                remoteSha == file.gitBlobSha1 -> unchanged += 1
                else -> modified += FileChangeEntry(file.relativePath, FileChangeType.MODIFIED, file.sizeBytes, file.gitBlobSha1)
            }
        }

        val deleted = remoteBlobShas.keys
            .filterNot { localByPath.containsKey(it) }
            .map { FileChangeEntry(it, FileChangeType.DELETED, sizeBytes = 0L, gitBlobSha1 = null) }

        return AnalysisResult.Success(added, modified, deleted, unchanged)
    }

    /** Null return means "couldn't be read" (distinct from an empty, real, brand-new-repo tree, which returns an empty map successfully) -- the caller treats those two cases very differently (empty tree: everything is ADDED, which is correct for a fresh repo; unreadable tree: stop, don't silently treat every remote file as if it didn't exist). */
    private suspend fun fetchRemoteTree(owner: String, repo: String, branch: String): Map<String, String>? {
        val refResult = api.get("https://api.github.com/repos/$owner/$repo/git/ref/heads/$branch")
        val commitSha = (refResult.getOrNull() as? JSONObject)?.optJSONObject("object")?.optString("sha")
            ?: return null

        val treeResult = api.get("https://api.github.com/repos/$owner/$repo/git/trees/$commitSha?recursive=1")
        val treeJson = treeResult.getOrNull() as? JSONObject ?: return null
        if (treeJson.optBoolean("truncated", false)) {
            // Honest limit, stated rather than silently mishandled: a
            // truncated response means GitHub didn't return the whole
            // tree (repos with an enormous number of files) -- every
            // path this pipeline didn't see would be wrongly treated
            // as ADDED/unchanged-by-omission, so this is surfaced as a
            // hard failure rather than a partial, misleading diff.
            return null
        }

        val entries = treeJson.optJSONArray("tree") ?: return emptyMap()
        val result = mutableMapOf<String, String>()
        for (i in 0 until entries.length()) {
            val entry = entries.optJSONObject(i) ?: continue
            if (entry.optString("type") == "blob") {
                result[entry.optString("path")] = entry.optString("sha")
            }
        }
        return result
    }
}
