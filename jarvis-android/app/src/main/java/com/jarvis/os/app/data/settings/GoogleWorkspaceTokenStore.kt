package com.jarvis.os.app.data.settings

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class GoogleWorkspaceConfig(
    val accessToken: String,
    val accountEmail: String,
)

/**
 * Sprint 13 Part 4 (Google Workspace Connector) -- EncryptedSharedPreferences,
 * same reasoning as GitHubTokenStore: a real secret, not an ordinary
 * DataStore preference.
 *
 * HONEST LIMIT, stated once here: a full in-app Google Sign-In consent
 * screen (OAuth authorization code flow with silent refresh) needs a
 * registered OAuth client ID in a Google Cloud project that this
 * sprint's owner (Ankush) has not yet created. Building a Sign-In
 * button that calls a client ID this app doesn't have would be a UI
 * that always fails -- the "no fake success" rule this whole codebase
 * follows. Until that client ID exists, the Owner supplies an OAuth
 * access token they generate themselves (e.g. via Google's own OAuth
 * 2.0 Playground at developers.google.com/oauthplayground, requesting
 * gmail.readonly, calendar.readonly, and drive.readonly scopes) and
 * pastes it here -- same shape as a GitHub PAT. Google access tokens
 * expire (typically ~1 hour); GoogleWorkspaceStatusProvider reports an
 * honest "token expired, get a new one" failure on a 401 rather than
 * silently refreshing something it has no refresh_token/client_secret
 * to refresh. Swapping this paste-a-token flow for a real Sign-In
 * button is a follow-up once Ankush registers the OAuth client -- the
 * interface below does not change either way.
 */
interface GoogleWorkspaceTokenStore {
    fun currentConfig(): GoogleWorkspaceConfig?
    fun save(config: GoogleWorkspaceConfig)
    fun clear()
}

@Singleton
class EncryptedGoogleWorkspaceTokenStore @Inject constructor(
    @ApplicationContext context: Context,
) : GoogleWorkspaceTokenStore {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "jarvis_google_workspace_secure",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    override fun currentConfig(): GoogleWorkspaceConfig? {
        val token = prefs.getString(KEY_TOKEN, null)
        val email = prefs.getString(KEY_EMAIL, null)
        if (token.isNullOrBlank()) return null
        return GoogleWorkspaceConfig(token, email.orEmpty())
    }

    override fun save(config: GoogleWorkspaceConfig) {
        prefs.edit()
            .putString(KEY_TOKEN, config.accessToken)
            .putString(KEY_EMAIL, config.accountEmail)
            .apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_TOKEN = "access_token"
        private const val KEY_EMAIL = "account_email"
    }
}
