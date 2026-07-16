package com.jarvis.os.app.data.settings

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class AiProviderConfig(
    val baseUrl: String,
    val model: String,
    val apiKey: String,
)

/**
 * Sprint 12 "Real AI Conversation": the Owner's own API key for a real
 * AI provider, stored via EncryptedSharedPreferences (AES256-GCM,
 * hardware-backed where the device supports it) -- deliberately not
 * plain DataStore/SharedPreferences the way ordinary UI preferences
 * are (see SettingsRepository), since this is a real secret, not a
 * personalization choice.
 *
 * This project's own memory of an earlier sprint ("PR4... 5 real
 * providers using OkHttp SSE streaming and EncryptedSharedPreferences
 * for API keys") turned out not to actually be present anywhere in the
 * delivered code when this sprint checked -- confirmed by searching the
 * whole core/chat package before writing this file (see this sprint's
 * integration report). This is that infrastructure, built for real
 * this time, not an assumption carried forward from a summary.
 */
interface ApiKeyStore {
    fun currentConfig(): AiProviderConfig?
    fun save(config: AiProviderConfig)
    fun clear()
}

@Singleton
class EncryptedApiKeyStore @Inject constructor(
    @ApplicationContext context: Context,
) : ApiKeyStore {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "jarvis_ai_provider_secure",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    override fun currentConfig(): AiProviderConfig? {
        val apiKey = prefs.getString(KEY_API_KEY, null)
        if (apiKey.isNullOrBlank()) return null
        return AiProviderConfig(
            baseUrl = prefs.getString(KEY_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL,
            model = prefs.getString(KEY_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL,
            apiKey = apiKey,
        )
    }

    override fun save(config: AiProviderConfig) {
        prefs.edit()
            .putString(KEY_BASE_URL, config.baseUrl)
            .putString(KEY_MODEL, config.model)
            .putString(KEY_API_KEY, config.apiKey)
            .apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_MODEL = "model"
        private const val KEY_API_KEY = "api_key"
        const val DEFAULT_BASE_URL = "https://api.openai.com/v1"
        const val DEFAULT_MODEL = "gpt-4o-mini"
    }
}
