package com.jarvis.os.app.data.settings

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class GitHubConfig(
    val personalAccessToken: String,
    val owner: String,
    val repo: String,
)

/**
 * "Universal Connection Ecosystem -- Phase 1": the Owner's own GitHub
 * Personal Access Token, [owner], and [repo] -- stored via
 * EncryptedSharedPreferences, same real-secret-not-a-preference
 * reasoning as ApiKeyStore (Sprint 12), kept as its own separate store
 * rather than folded into that one since a GitHub PAT and an AI
 * provider key are unrelated secrets an Owner may want to manage
 * independently (e.g. clearing one shouldn't clear the other).
 *
 * Shared by two real integrations that both need the same repo access:
 * GitHubStatusProvider (repo/PR/issue/workflow status) and
 * NgSignalProStatusProvider's real implementation (NG Signal Pro's
 * actual GitHub Actions workflows, confirmed to live in this same
 * repository -- see that class's own docstring for how that was
 * verified before being assumed).
 */
interface GitHubTokenStore {
    fun currentConfig(): GitHubConfig?
    fun save(config: GitHubConfig)
    fun clear()
}

@Singleton
class EncryptedGitHubTokenStore @Inject constructor(
    @ApplicationContext context: Context,
) : GitHubTokenStore {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "jarvis_github_secure",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    override fun currentConfig(): GitHubConfig? {
        val token = prefs.getString(KEY_TOKEN, null)
        val owner = prefs.getString(KEY_OWNER, null)
        val repo = prefs.getString(KEY_REPO, null)
        if (token.isNullOrBlank() || owner.isNullOrBlank() || repo.isNullOrBlank()) return null
        return GitHubConfig(token, owner, repo)
    }

    override fun save(config: GitHubConfig) {
        prefs.edit()
            .putString(KEY_TOKEN, config.personalAccessToken)
            .putString(KEY_OWNER, config.owner)
            .putString(KEY_REPO, config.repo)
            .apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_TOKEN = "pat"
        private const val KEY_OWNER = "owner"
        private const val KEY_REPO = "repo"
    }
}
