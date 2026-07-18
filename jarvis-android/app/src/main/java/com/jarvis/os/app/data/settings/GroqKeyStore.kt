package com.jarvis.os.app.data.settings

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class GroqConfig(val apiKey: String, val model: String, val lastSuccessAt: Long? = null)

/**
 * "Add Groq as a fourth AI provider": the Owner explicitly asked for a
 * genuine, capable provider with no billing requirement, after
 * confirming OpenAI's account has no payment method configured (every
 * OpenAI call was failing on quota, not a bug in this codebase) and
 * before Gemini's free-tier rate limit had cleared. Groq fits that
 * requirement for real: no card needed for its free/dev tier, and it
 * hosts full-size, genuinely capable open-weight models (Llama 3.3 70B
 * by default here, not a distilled or limited variant) -- not a
 * fallback tier, an actual alternative.
 *
 * Same EncryptedSharedPreferences pattern as every other real
 * provider's key store in this codebase, kept as its own store for the
 * same reason as the others -- an Owner clearing one key shouldn't
 * clear another.
 */
interface GroqKeyStore {
    fun currentConfig(): GroqConfig?
    fun save(config: GroqConfig)
    fun recordSuccess()
    fun clear()
}

@Singleton
class EncryptedGroqKeyStore @Inject constructor(
    @ApplicationContext context: Context,
) : GroqKeyStore {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "jarvis_groq_secure",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    override fun currentConfig(): GroqConfig? {
        val apiKey = prefs.getString(KEY_API_KEY, null)
        if (apiKey.isNullOrBlank()) return null
        val model = prefs.getString(KEY_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL
        val lastSuccess = prefs.getLong(KEY_LAST_SUCCESS, -1L).takeIf { it > 0 }
        return GroqConfig(apiKey, model, lastSuccess)
    }

    override fun save(config: GroqConfig) {
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
        const val DEFAULT_MODEL = "llama-3.3-70b-versatile"
    }
}
