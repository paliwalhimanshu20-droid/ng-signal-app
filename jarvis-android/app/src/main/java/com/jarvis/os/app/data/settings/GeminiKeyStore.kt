package com.jarvis.os.app.data.settings

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class GeminiConfig(val apiKey: String, val model: String)

/**
 * "Universal Connection Ecosystem -- Phase 1": "Google Gemini... API
 * key through Connection Manager... Separate provider adapter." Kept
 * as its own EncryptedSharedPreferences store, same reasoning as
 * GitHubTokenStore -- a Gemini key is a distinct secret from
 * ApiKeyStore's OpenAI-compatible provider config or GitHub's PAT, and
 * an Owner clearing one shouldn't clear the others.
 */
interface GeminiKeyStore {
    fun currentConfig(): GeminiConfig?
    fun save(config: GeminiConfig)
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
        return GeminiConfig(apiKey, model)
    }

    override fun save(config: GeminiConfig) {
        prefs.edit()
            .putString(KEY_API_KEY, config.apiKey)
            .putString(KEY_MODEL, config.model)
            .apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_API_KEY = "api_key"
        private const val KEY_MODEL = "model"
        const val DEFAULT_MODEL = "gemini-2.0-flash"
    }
}
