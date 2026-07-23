package com.jarvis.os.app.core.deployment.engines

import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Code review RC-002 "Introduce Deployment Engine Abstraction":
 * Deployment Center (ASDP-001 Phase 6, not yet built) must depend on
 * this interface, never bind directly to a specific host's API shape.
 * Only GitHubApiDeploymentEngine exists today, matching this review's
 * "today only [current] should be implemented" instruction -- no
 * GitLab/Bitbucket/Azure DevOps stub classes.
 *
 * "GitApiDeploymentEngine" not "GitDeploymentEngine" as the current
 * class name -- see ASDP-001's own reasoning: there is no git CLI
 * reachable on stock Android, so what this class actually does is
 * call GitHub's Git Data REST API (blobs -> tree -> commit -> ref
 * update) to achieve the same *result* a local `git commit && git
 * push` would, without ever invoking git itself. A future
 * GitDeploymentEngine (an actual local git implementation) is a real,
 * separate future option this interface leaves room for -- e.g. if
 * this app ever runs somewhere a git binary genuinely is reachable
 * (Termux, desktop) -- and is deliberately not confused with this
 * one's API-only approach by sharing its name.
 */
data class FileChange(val path: String, val contentBase64: String)

sealed interface DeploymentResult {
    data class Success(val commitSha: String, val commitUrl: String) : DeploymentResult
    data class Failure(val message: String) : DeploymentResult
}

interface DeploymentEngine {
    val engineName: String

    /**
     * Never called without prior explicit owner approval (ASDP-001
     * Phase 5's "Require explicit approval before: Git Push") --
     * approval-gating is the CALLER's responsibility, same as
     * RepositoryProvider.createRepository's own docstring explains.
     * [changes] is a full list of files to create/update in this one
     * commit -- there is no incremental "stage then commit" step,
     * since the underlying Git Data API commits a whole tree at once.
     */
    suspend fun deploy(owner: String, repo: String, branch: String, commitMessage: String, changes: List<FileChange>): DeploymentResult
}

@Singleton
class GitHubApiDeploymentEngine @Inject constructor(
    private val api: GitHubApiClient,
) : DeploymentEngine {

    override val engineName: String = "GitHub API"

    override suspend fun deploy(owner: String, repo: String, branch: String, commitMessage: String, changes: List<FileChange>): DeploymentResult {
        if (changes.isEmpty()) return DeploymentResult.Failure("No file changes to deploy.")

        val base = "https://api.github.com/repos/$owner/$repo"

        // Step 1: resolve the branch's current commit + tree, so the
        // new tree is built ON TOP OF what's already there -- this is
        // what "preserve .git directory" (ASDP-001 Phase 6) actually
        // means via this API: every file NOT in [changes] is inherited
        // unchanged from base_tree, never deleted or reset.
        val refResult = api.get("$base/git/ref/heads/$branch")
        val refJson = refResult.getOrNull() as? JSONObject
            ?: return DeploymentResult.Failure(refResult.exceptionOrNull()?.message ?: "Couldn't resolve branch '$branch'.")
        val baseCommitSha = refJson.optJSONObject("object")?.optString("sha")
            ?: return DeploymentResult.Failure("Branch '$branch' has no resolvable commit.")

        val baseCommitResult = api.get("$base/git/commits/$baseCommitSha")
        val baseTreeSha = (baseCommitResult.getOrNull() as? JSONObject)?.optJSONObject("tree")?.optString("sha")
            ?: return DeploymentResult.Failure(baseCommitResult.exceptionOrNull()?.message ?: "Couldn't resolve base tree.")

        // Step 2: one blob per changed file -- each is a real, separate
        // GitHub API call; a failure partway through means SOME blobs
        // were created (harmless, orphaned objects GitHub garbage
        // collects on its own) but no tree/commit/ref update happens,
        // so the branch itself is never left in a half-deployed state.
        val treeEntries = JSONArray()
        for (change in changes) {
            val blobBody = JSONObject().apply {
                put("content", change.contentBase64)
                put("encoding", "base64")
            }
            val blobResult = api.post("$base/git/blobs", blobBody)
            val blobSha = (blobResult.getOrNull() as? JSONObject)?.optString("sha")
                ?: return DeploymentResult.Failure("Failed to upload '${change.path}': ${blobResult.exceptionOrNull()?.message}")

            treeEntries.put(
                JSONObject().apply {
                    put("path", change.path)
                    put("mode", "100644")
                    put("type", "blob")
                    put("sha", blobSha)
                },
            )
        }

        // Step 3: one new tree, based on the branch's existing tree.
        val treeBody = JSONObject().apply {
            put("base_tree", baseTreeSha)
            put("tree", treeEntries)
        }
        val treeResult = api.post("$base/git/trees", treeBody)
        val newTreeSha = (treeResult.getOrNull() as? JSONObject)?.optString("sha")
            ?: return DeploymentResult.Failure("Failed to create tree: ${treeResult.exceptionOrNull()?.message}")

        // Step 4: one new commit, parented on the branch's prior commit.
        val commitBody = JSONObject().apply {
            put("message", commitMessage)
            put("tree", newTreeSha)
            put("parents", JSONArray().put(baseCommitSha))
        }
        val commitResult = api.post("$base/git/commits", commitBody)
        val commitJson = commitResult.getOrNull() as? JSONObject
            ?: return DeploymentResult.Failure("Failed to create commit: ${commitResult.exceptionOrNull()?.message}")
        val newCommitSha = commitJson.optString("sha")

        // Step 5: move the branch ref to point at the new commit --
        // this is the actual "push." A fast-forward update
        // (sha=newCommitSha, force not set) rather than a force-push,
        // matching ASDP-001 Phase 5's separate approval gate for
        // "Force Push" -- this method never force-pushes.
        val refUpdateBody = JSONObject().apply { put("sha", newCommitSha) }
        val refUpdateResult = api.patch("$base/git/refs/heads/$branch", refUpdateBody)
        if (refUpdateResult.isFailure) {
            return DeploymentResult.Failure("Commit created (${newCommitSha.take(7)}) but updating branch '$branch' failed: ${refUpdateResult.exceptionOrNull()?.message}")
        }

        return DeploymentResult.Success(
            commitSha = newCommitSha,
            commitUrl = "https://github.com/$owner/$repo/commit/$newCommitSha",
        )
    }
}
