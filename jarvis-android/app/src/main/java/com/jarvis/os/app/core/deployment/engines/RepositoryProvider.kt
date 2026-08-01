package com.jarvis.os.app.core.deployment.engines

import com.jarvis.os.app.data.settings.GitHubTokenStore
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/** The subset of a GitHub repository's fields the Deployment Center actually shows/uses -- not a full API mirror. */
data class GitHubRepository(
    val fullName: String,
    val htmlUrl: String,
    val defaultBranch: String,
    val isPrivate: Boolean,
)

sealed interface RepositorySearchResult {
    data class Found(val repository: GitHubRepository) : RepositorySearchResult
    data object NotFound : RepositorySearchResult
    data class Failure(val message: String) : RepositorySearchResult
}

sealed interface RepositoryCreationResult {
    data class Success(val repository: GitHubRepository) : RepositoryCreationResult
    data class Failure(val message: String) : RepositoryCreationResult
}

/**
 * RC-003 "Repository provider abstraction" (ASDP-001): find-or-create
 * for the target GitHub repository in the Import & Publish pipeline
 * (Phase 3, see
 * [com.jarvis.os.app.feature.deployment.DeploymentCenterViewModel]).
 * Same "swap point" interface reasoning as [BuildEngine]/
 * [DeploymentEngine].
 */
interface RepositoryProvider {
    suspend fun findRepository(name: String): RepositorySearchResult
    suspend fun createRepository(name: String, description: String, isPrivate: Boolean): RepositoryCreationResult
}

/**
 * Real implementation. Repositories are looked up and created under
 * the Owner's own GitHub account -- the same [owner] already
 * configured in [GitHubTokenStore] (see
 * [com.jarvis.os.app.data.repository.RealGitHubStatusProvider] for the
 * same "owner/repo already connected" assumption), not an arbitrary
 * org, matching Phase 3's own scope ("Never create repositories
 * without owner approval" -- creation is only ever reachable from an
 * explicit Owner tap, enforced in the ViewModel, not here).
 */
@Singleton
class GitHubRepositoryProvider @Inject constructor(
    private val api: GitHubApiClient,
    private val tokenStore: GitHubTokenStore,
) : RepositoryProvider {

    override suspend fun findRepository(name: String): RepositorySearchResult {
        val owner = tokenStore.currentConfig()?.owner
            ?: return RepositorySearchResult.Failure("No GitHub account is connected yet. Add a Personal Access Token under Settings, GitHub.")

        val result = api.get("https://api.github.com/repos/$owner/$name")
        return result.fold(
            onSuccess = { json -> RepositorySearchResult.Found((json as JSONObject).toGitHubRepository()) },
            onFailure = { error ->
                if (error is GitHubHttpException && error.code == 404) {
                    RepositorySearchResult.NotFound
                } else {
                    RepositorySearchResult.Failure(error.message ?: "Couldn't reach GitHub to search for that repository.")
                }
            },
        )
    }

    override suspend fun createRepository(name: String, description: String, isPrivate: Boolean): RepositoryCreationResult {
        if (tokenStore.currentConfig() == null) {
            return RepositoryCreationResult.Failure("No GitHub account is connected yet. Add a Personal Access Token under Settings, GitHub.")
        }

        val body = JSONObject()
            .put("name", name)
            .put("description", description)
            .put("private", isPrivate)
            .put("auto_init", true)

        val result = api.post("https://api.github.com/user/repos", body)
        return result.fold(
            onSuccess = { json -> RepositoryCreationResult.Success((json as JSONObject).toGitHubRepository()) },
            onFailure = { error -> RepositoryCreationResult.Failure(error.message ?: "GitHub declined to create that repository.") },
        )
    }

    private fun JSONObject.toGitHubRepository(): GitHubRepository = GitHubRepository(
        fullName = optString("full_name"),
        htmlUrl = optString("html_url"),
        defaultBranch = optString("default_branch", "main"),
        isPrivate = optBoolean("private", false),
    )
}
