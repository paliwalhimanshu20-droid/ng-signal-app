package com.jarvis.os.app.data.settings

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class GeminiConfig(val apiKey: String, val model: String, val lastSuccessAt: Long? = null)

/**
 * "Universal Connection Ecosystem -- Phase 1": "Google Gemini... API
 * key through Connection Manager... Separate provider adapter." Kept
 * as its own EncryptedSharedPreferences store, same reasoning as
 * GitHubTokenStore -- a Gemini key is a distinct secret from
 * ApiKeyStore's OpenAI-compatible provider config or GitHub's PAT, and
 * an Owner clearing one shouldn't clear the others.
 *
 * "JARVIS Goes Live": [recordSuccess] added -- "Last successful
 * connection" means an actual completed conversation, not just "a key
 * is saved." Called by GeminiChatProvider itself after a real
 * successful reply.
 */
interface GeminiKeyStore {
    fun currentConfig(): GeminiConfig?
    fun save(config: GeminiConfig)
    fun recordSuccess()
    fun clear()
}

@Singleton
class EncryptedGeminiKeyStore @Inject constructor(
    @ApplicationContext context: Context,
) : GeminiKeyStore {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "jarvis_gemini_secure",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    override fun currentConfig(): GeminiConfig? {
        val apiKey = prefs.getString(KEY_API_KEY, null)
        if (apiKey.isNullOrBlank()) return null
        val model = prefs.getString(KEY_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL
        val lastSuccess = prefs.getLong(KEY_LAST_SUCCESS, -1L).takeIf { it > 0 }
        return GeminiConfig(apiKey, model, lastSuccess)
    }

    override fun save(config: GeminiConfig) {
        prefs.edit()
            .putString(KEY_API_KEY, config.apiKey)
            .putString(KEY_MODEL, config.model)
            .apply()
    }

    override fun recordSuccess() {
        prefs.edit().putLong(KEY_LAST_SUCCESS, System.currentTimeMillis()).apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_API_KEY = "api_key"
        private const val KEY_MODEL = "model"
        private const val KEY_LAST_SUCCESS = "last_success_at"
        const val DEFAULT_MODEL = "gemini-2.0-flash"
    }
}
