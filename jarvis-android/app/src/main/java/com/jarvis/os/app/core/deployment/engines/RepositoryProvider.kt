package com.jarvis.os.app.core.deployment.engines

import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Code review RC-003 "Repository Provider Abstraction": Connection
 * Manager already supports multiple services (see ConnectionRepository,
 * Sprint 9) -- Deployment Center's own repository intelligence (ASDP-001
 * Phase 3, not yet built) must depend on THIS interface, never on
 * GitHub's REST API shape directly, so a future GitLab/Bitbucket/Azure
 * DevOps/self-hosted provider is "implement RepositoryProvider," not
 * "rewrite Phase 3."
 *
 * Only GitHubRepositoryProvider exists today -- per this review's own
 * "Today only [the current implementation] should be implemented"
 * instruction, no GitLab/Bitbucket/etc. stub classes were added; an
 * unused stub with no real behavior would just be dead code pretending
 * to be a real option.
 */
data class RemoteRepository(
    val fullName: String,
    val defaultBranch: String,
    val isPrivate: Boolean,
    val htmlUrl: String,
)

sealed interface RepositorySearchResult {
    data class Found(val repository: RemoteRepository) : RepositorySearchResult
    data object NotFound : RepositorySearchResult
    data class Failure(val message: String) : RepositorySearchResult
}

sealed interface RepositoryCreationResult {
    data class Success(val repository: RemoteRepository) : RepositoryCreationResult
    data class Failure(val message: String) : RepositoryCreationResult
}

interface RepositoryProvider {
    /** Owner-facing label -- "GitHub" today. Repository selection UI (once built) reads this rather than hardcoding a provider name, per this review's "Repository selection UI should be provider-agnostic" instruction. */
    val providerName: String

    /** Search the authenticated account's own repositories by name -- ASDP-001 Phase 3's "search existing approved GitHub repositories." */
    suspend fun findRepository(name: String): RepositorySearchResult

    /** Never called without prior explicit owner approval -- ASDP-001 Phase 3/5's own "Never create repositories without owner approval." Approval-gating is the CALLER's responsibility (ApprovalRepository, once Phase 3's UI exists); this method itself has no approval concept, it just performs the create once told to. */
    suspend fun createRepository(name: String, description: String, isPrivate: Boolean): RepositoryCreationResult
}

@Singleton
class GitHubRepositoryProvider @Inject constructor(
    private val api: GitHubApiClient,
) : RepositoryProvider {

    override val providerName: String = "GitHub"

    override suspend fun findRepository(name: String): RepositorySearchResult {
        val token = api.currentToken()
            ?: return RepositorySearchResult.Failure("GitHub isn't connected yet. Add a Personal Access Token under Settings, GitHub.")

        val userResult = api.get("https://api.github.com/user")
        val login = (userResult.getOrNull() as? JSONObject)?.optString("login")
            ?: return RepositorySearchResult.Failure(userResult.exceptionOrNull()?.message ?: "Couldn't determine the authenticated GitHub account.")

        val repoResult = api.get("https://api.github.com/repos/$login/$name")
        val json = repoResult.getOrNull() as? JSONObject
        if (json != null && json.has("full_name")) {
            return RepositorySearchResult.Found(json.toRemoteRepository())
        }

        val error = repoResult.exceptionOrNull()
        return if (error?.message?.contains("404") == true) {
            RepositorySearchResult.NotFound
        } else {
            RepositorySearchResult.Failure(error?.message ?: "Couldn't search for repository '$name'.")
        }
    }

    override suspend fun createRepository(name: String, description: String, isPrivate: Boolean): RepositoryCreationResult {
        val body = JSONObject().apply {
            put("name", name)
            put("description", description)
            put("private", isPrivate)
            put("auto_init", true) // real README + initial commit + default branch, per ASDP-001 Phase 3's own "Configure automatically: README, .gitignore, Default Branch, Initial Commit"
        }
        val result = api.post("https://api.github.com/user/repos", body)
        val json = result.getOrNull() as? JSONObject
            ?: return RepositoryCreationResult.Failure(result.exceptionOrNull()?.message ?: "Repository creation failed.")
        return RepositoryCreationResult.Success(json.toRemoteRepository())
    }

    private fun JSONObject.toRemoteRepository(): RemoteRepository = RemoteRepository(
        fullName = optString("full_name"),
        defaultBranch = optString("default_branch", "main"),
        isPrivate = optBoolean("private", false),
        htmlUrl = optString("html_url"),
    )
}
