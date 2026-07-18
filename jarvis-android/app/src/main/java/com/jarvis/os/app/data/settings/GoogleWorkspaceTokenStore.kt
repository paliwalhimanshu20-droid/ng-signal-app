package com.jarvis.os.app.data.settings

import android.content.Context
import android.os.Build
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import net.openid.appauth.AuthState
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Connection health as the Owner Controls section (Sprint 13) actually
 * shows it -- computed, not stored, from AuthState + the two
 * timestamps below (see GoogleAuthManager for where each value is set).
 */
enum class GoogleConnectionHealth { HEALTHY, DEGRADED, NEEDS_REAUTH, UNKNOWN }

data class GoogleWorkspaceConnectionInfo(
    val accountEmail: String,
    val grantedScopes: Set<String>,
    val lastSyncAt: Instant?,
    val lastTokenRefreshAt: Instant?,
    /** AUTH-002 "Device Management Center" Section 2: the two fields a future backend's DeviceRecord reads from this device -- stable across disconnect/reconnect (see clear()'s own docstring for why they survive it). */
    val deviceId: String,
    val deviceName: String,
)

/**
 * Sprint 13 "Production Google Workspace Authentication": replaces the
 * previous paste-an-access-token store. What's persisted now is
 * AppAuth's own AuthState -- its `jsonSerializeString()` includes the
 * refresh token, the last access token, its expiry, and the granted
 * scopes all in one object AppAuth already knows how to serialize
 * and, more importantly, deserialize back into something
 * performActionWithFreshTokens can use directly (see
 * GoogleAuthManager). Re-inventing a hand-rolled
 * {accessToken, refreshToken, expiry} tuple here would just be a worse
 * copy of what AuthState already does correctly (safe refresh-token
 * handling, expiry math) -- storing its serialized form whole is the
 * boring, correct choice.
 *
 * Still EncryptedSharedPreferences with an AndroidKeystore-backed
 * MasterKey, same as GitHubTokenStore -- "Encrypt all stored
 * credentials" (Part 7 / this sprint's Security section) applies
 * exactly the same way to a refresh token as to a PAT.
 *
 * AUTH-002: also owns this device's identity (deviceId, deviceName) --
 * not a credential, but co-located here because it's the same
 * EncryptedSharedPreferences file and the same "one store per Google
 * Workspace connection" seam. deviceId is generated once, lazily, on
 * first read and never regenerated -- see deviceId()'s own docstring.
 */
interface GoogleWorkspaceTokenStore {
    fun currentAuthState(): AuthState?
    fun saveAuthState(authState: AuthState)
    fun currentConnectionInfo(): GoogleWorkspaceConnectionInfo?
    fun updateAccountEmail(email: String)
    fun markTokenRefreshed()
    fun markSynced()

    /** Stable for the lifetime of the app install -- generated once, never reused, survives clear(). This is the identity a future backend's DeviceRecord keys on. */
    fun deviceId(): String
    fun deviceName(): String
    fun renameDevice(name: String)

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

    override fun currentAuthState(): AuthState? {
        val json = prefs.getString(KEY_AUTH_STATE, null) ?: return null
        return runCatching { AuthState.jsonDeserialize(json) }.getOrNull()
    }

    override fun saveAuthState(authState: AuthState) {
        prefs.edit().putString(KEY_AUTH_STATE, authState.jsonSerializeString()).apply()
    }

    override fun currentConnectionInfo(): GoogleWorkspaceConnectionInfo? {
        val authState = currentAuthState() ?: return null
        val scopes = authState.scope?.split(" ")?.filter { it.isNotBlank() }?.toSet().orEmpty()
        return GoogleWorkspaceConnectionInfo(
            accountEmail = prefs.getString(KEY_EMAIL, null).orEmpty(),
            grantedScopes = scopes,
            lastSyncAt = prefs.getLong(KEY_LAST_SYNC, -1L).takeIf { it >= 0 }?.let { Instant.ofEpochMilli(it) },
            lastTokenRefreshAt = prefs.getLong(KEY_LAST_REFRESH, -1L).takeIf { it >= 0 }?.let { Instant.ofEpochMilli(it) },
            deviceId = deviceId(),
            deviceName = deviceName(),
        )
    }

    override fun updateAccountEmail(email: String) {
        prefs.edit().putString(KEY_EMAIL, email).apply()
    }

    override fun markTokenRefreshed() {
        prefs.edit().putLong(KEY_LAST_REFRESH, System.currentTimeMillis()).apply()
    }

    override fun markSynced() {
        prefs.edit().putLong(KEY_LAST_SYNC, System.currentTimeMillis()).apply()
    }

    override fun deviceId(): String {
        prefs.getString(KEY_DEVICE_ID, null)?.let { return it }
        val generated = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_DEVICE_ID, generated).apply()
        return generated
    }

    override fun deviceName(): String =
        prefs.getString(KEY_DEVICE_NAME, null) ?: "${Build.MANUFACTURER} ${Build.MODEL}".trim()

    override fun renameDevice(name: String) {
        if (name.isBlank()) return
        prefs.edit().putString(KEY_DEVICE_NAME, name).apply()
    }

    /**
     * Disconnect/Revoke both call this. Deliberately does NOT touch
     * KEY_DEVICE_ID or KEY_DEVICE_NAME -- AUTH-002's whole point is
     * that a device's identity outlives any one Google connection
     * (the Owner reconnecting later should see the same device name
     * they set before, not a re-generated id that would look like a
     * brand-new device to a future backend). Only the credential and
     * sync-state keys are cleared here.
     */
    override fun clear() {
        prefs.edit()
            .remove(KEY_AUTH_STATE)
            .remove(KEY_EMAIL)
            .remove(KEY_LAST_SYNC)
            .remove(KEY_LAST_REFRESH)
            .apply()
    }

    companion object {
        private const val KEY_AUTH_STATE = "auth_state_json"
        private const val KEY_EMAIL = "account_email"
        private const val KEY_LAST_SYNC = "last_sync_at_millis"
        private const val KEY_LAST_REFRESH = "last_token_refresh_at_millis"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_DEVICE_NAME = "device_name"
    }
}
