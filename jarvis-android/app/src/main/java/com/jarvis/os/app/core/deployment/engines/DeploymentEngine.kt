package com.jarvis.os.app.core.deployment.engines

import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One file to write ([contentBase64] non-null, Base64-encoded content
 * -- added/modified) or remove ([contentBase64] null -- deleted) as
 * part of a single commit. See
 * [com.jarvis.os.app.feature.deployment.DeploymentCenterViewModel.commitAndPush]
 * for how [com.jarvis.os.app.data.model.FileChangeEntry] (Added/
 * Modified/Deleted, from [com.jarvis.os.app.core.deployment.ChangeAnalyzer])
 * is turned into this list.
 */
data class FileChange(val path: String, val contentBase64: String?)

sealed interface DeploymentResult {
    data class Success(val commitSha: String, val commitUrl: String) : DeploymentResult
    data class Failure(val message: String) : DeploymentResult
}

/**
 * RC-002 "Deployment engine abstraction" (ASDP-001): commits and
 * pushes a set of [FileChange]s to a branch in a single atomic commit.
 * Same "swap point" interface reasoning as [BuildEngine] -- one real
 * implementation today ([GitHubApiDeploymentEngine]).
 */
interface DeploymentEngine {
    suspend fun deploy(owner: String, repo: String, branch: String, commitMessage: String, changes: List<FileChange>): DeploymentResult
}

/**
 * Real implementation, built on GitHub's Git Data API (blobs -> tree
 * -> commit -> ref update) rather than the Contents API -- the Contents
 * API only writes one file per request/commit, which would turn one
 * Owner-approved publish into N separate commits for N changed files.
 * The Git Data API lets every changed file in [FileChange] land in
 * exactly one commit, matching what the Preview screen actually showed
 * the Owner and approved.
 *
 * Deletions are represented in the new tree by omitting that path from
 * [buildTreeEntries]'s base_tree carry-forward -- GitHub trees are
 * built from a `base_tree` plus an explicit list of entries to
 * add/change, and a path simply absent from both is understood as
 * removed relative to the base ref's own resolution of unspecified
 * paths, matching the same base_tree contract this class already
 * relies on for added/modified paths ([entry.sha] present, no
 * base_tree re-listing needed for anything unchanged).
 */
@Singleton
class GitHubApiDeploymentEngine @Inject constructor(
    private val api: GitHubApiClient,
) : DeploymentEngine {

    override suspend fun deploy(owner: String, repo: String, branch: String, commitMessage: String, changes: List<FileChange>): DeploymentResult {
        val baseCommitSha = getRefCommitSha(owner, repo, branch)
            ?: return DeploymentResult.Failure("Couldn't find branch '$branch' in $owner/$repo. Create the branch first, or check the repository/branch name.")

        val baseTreeSha = getCommitTreeSha(owner, repo, baseCommitSha)
            ?: return DeploymentResult.Failure("Couldn't read the current tree for $owner/$repo@$branch.")

        val newTreeSha = createTree(owner, repo, baseTreeSha, changes)
            ?: return DeploymentResult.Failure("GitHub rejected the new tree for this commit.")

        val newCommitSha = createCommit(owner, repo, commitMessage, newTreeSha, baseCommitSha)
            ?: return DeploymentResult.Failure("GitHub rejected the new commit.")

        val refUpdated = updateRef(owner, repo, branch, newCommitSha)
        if (!refUpdated) {
            return DeploymentResult.Failure("The commit was created but the branch couldn't be updated to point at it -- someone else may have pushed to '$branch' at the same time.")
        }

        return DeploymentResult.Success(
            commitSha = newCommitSha,
            commitUrl = "https://github.com/$owner/$repo/commit/$newCommitSha",
        )
    }

    private suspend fun getRefCommitSha(owner: String, repo: String, branch: String): String? {
        val result = api.get("https://api.github.com/repos/$owner/$repo/git/ref/heads/$branch")
        return (result.getOrNull() as? JSONObject)?.optJSONObject("object")?.optString("sha")?.takeUnless { it.isBlank() }
    }

    private suspend fun getCommitTreeSha(owner: String, repo: String, commitSha: String): String? {
        val result = api.get("https://api.github.com/repos/$owner/$repo/git/commits/$commitSha")
        return (result.getOrNull() as? JSONObject)?.optJSONObject("tree")?.optString("sha")?.takeUnless { it.isBlank() }
    }

    private suspend fun createTree(owner: String, repo: String, baseTreeSha: String, changes: List<FileChange>): String? {
        val treeEntries = JSONArray()

        for (change in changes) {
            val entry = JSONObject()
                .put("path", change.path)
                .put("mode", "100644")
                .put("type", "blob")

            if (change.contentBase64 != null) {
                val blobSha = createBlob(owner, repo, change.contentBase64) ?: return null
                entry.put("sha", blobSha)
            } else {
                // Deleting a path via the Git Data API: an explicit
                // null sha on that path's tree entry.
                entry.put("sha", JSONObject.NULL)
            }
            treeEntries.put(entry)
        }

        val body = JSONObject()
            .put("base_tree", baseTreeSha)
            .put("tree", treeEntries)

        val result = api.post("https://api.github.com/repos/$owner/$repo/git/trees", body)
        return (result.getOrNull() as? JSONObject)?.optString("sha")?.takeUnless { it.isBlank() }
    }

    private suspend fun createBlob(owner: String, repo: String, contentBase64: String): String? {
        val body = JSONObject()
            .put("content", contentBase64)
            .put("encoding", "base64")
        val result = api.post("https://api.github.com/repos/$owner/$repo/git/blobs", body)
        return (result.getOrNull() as? JSONObject)?.optString("sha")?.takeUnless { it.isBlank() }
    }

    private suspend fun createCommit(owner: String, repo: String, message: String, treeSha: String, parentSha: String): String? {
        val body = JSONObject()
            .put("message", message)
            .put("tree", treeSha)
            .put("parents", JSONArray().put(parentSha))
        val result = api.post("https://api.github.com/repos/$owner/$repo/git/commits", body)
        return (result.getOrNull() as? JSONObject)?.optString("sha")?.takeUnless { it.isBlank() }
    }

    private suspend fun updateRef(owner: String, repo: String, branch: String, commitSha: String): Boolean {
        val body = JSONObject()
            .put("sha", commitSha)
            .put("force", false)
        val result = api.patch("https://api.github.com/repos/$owner/$repo/git/refs/heads/$branch", body)
        return result.isSuccess
    }
}
