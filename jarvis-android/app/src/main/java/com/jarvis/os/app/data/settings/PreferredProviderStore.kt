package com.jarvis.os.app.data.settings

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "Why this is not selecting the AI key which is verified": the real,
 * previously-undiscovered root cause. AiRouter's active provider lived
 * only in an in-memory MutableStateFlow, seeded fresh from
 * `providers.firstOrNull()` every time the app process restarts --
 * which is genuinely unordered for a Hilt multibinding Set, so which
 * provider "wins" by default was never guaranteed to be Claude
 * specifically, just whichever happened to bind first. The previous
 * fix (switchProvider() called on a successful test) correctly updated
 * this for the rest of that one app session -- but closing and
 * reopening the app recreates the @Singleton AiRouter from scratch,
 * and the choice was never written down anywhere durable. Every
 * restart silently discarded a real, verified selection.
 *
 * Plain (unencrypted) SharedPreferences, not EncryptedSharedPreferences
 * like the API key stores -- a provider id ("groq") is not a secret,
 * it's an ordinary durable preference, the same category as
 * SettingsRepository's DataStore-backed settings. Kept as its own tiny
 * synchronous store rather than added to SettingsRepository because
 * AiRouter has no CoroutineScope of its own and this needs to be
 * readable synchronously at construction time, before any coroutine
 * could run -- DataStore's Flow-based API doesn't fit that without
 * pulling AiRouter into a much bigger async-initialization change than
 * this fix calls for.
 */
interface PreferredProviderStore {
    fun currentProviderId(): String?
    fun save(providerId: String)
}

@Singleton
class SharedPrefsPreferredProviderStore @Inject constructor(
    @ApplicationContext context: Context,
) : PreferredProviderStore {

    private val prefs = context.getSharedPreferences("jarvis_preferred_provider", Context.MODE_PRIVATE)

    override fun currentProviderId(): String? = prefs.getString(KEY_PROVIDER_ID, null)

    override fun save(providerId: String) {
        prefs.edit().putString(KEY_PROVIDER_ID, providerId).apply()
    }

    private companion object {
        const val KEY_PROVIDER_ID = "provider_id"
    }
}
