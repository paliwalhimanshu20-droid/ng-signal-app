package com.jarvis.os.app.data.settings

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class AnthropicConfig(val apiKey: String, val model: String, val lastSuccessAt: Long? = null)

/**
 * "JARVIS Goes Live": "Anthropic Claude... API Key... Configuration
 * validation." Own dedicated EncryptedSharedPreferences store, same
 * reasoning as GeminiKeyStore/GitHubTokenStore -- a Claude key is a
 * distinct secret an Owner may want to manage independently of the
 * other providers.
 *
 * [recordSuccess] is called by AnthropicChatProvider itself after a
 * real successful reply -- "Last successful connection" (this sprint's
 * own requirement) means an actual completed conversation, not just
 * "a key is saved."
 */
interface AnthropicKeyStore {
    fun currentConfig(): AnthropicConfig?
    fun save(config: AnthropicConfig)
    fun recordSuccess()
    fun clear()
}

@Singleton
class EncryptedAnthropicKeyStore @Inject constructor(
    @ApplicationContext context: Context,
) : AnthropicKeyStore {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "jarvis_anthropic_secure",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    override fun currentConfig(): AnthropicConfig? {
        val apiKey = prefs.getString(KEY_API_KEY, null)
        if (apiKey.isNullOrBlank()) return null
        val model = prefs.getString(KEY_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL
        val lastSuccess = prefs.getLong(KEY_LAST_SUCCESS, -1L).takeIf { it > 0 }
        return AnthropicConfig(apiKey, model, lastSuccess)
    }

    override fun save(config: AnthropicConfig) {
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
        const val DEFAULT_MODEL = "claude-sonnet-4-5"
    }
}
