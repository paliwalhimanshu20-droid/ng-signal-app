package com.jarvis.os.app.core.security

import com.jarvis.os.app.data.settings.GoogleConnectionHealth

/**
 * AUTH-001 "Multi-Device Authentication Architecture", Section 3: the
 * platform-agnostic half of Google authentication. Everything a
 * connector (GoogleWorkspaceStatusProvider today; a future
 * GoogleMapsStatusProvider, GooglePhotosStatusProvider, etc.) actually
 * needs -- "give me a valid token," "am I connected," "what scopes do
 * I have" -- lives here and nowhere else, so those connectors can be
 * written once and used unmodified on Android, desktop, or web.
 *
 * What's deliberately NOT here: how a sign-in actually gets kicked off.
 * That first step is unavoidably platform-shaped (an Android Intent
 * launching Chrome Custom Tabs vs. a desktop app opening a system
 * browser against a local loopback listener vs. a web app redirecting
 * the whole page) -- see GoogleAuthManager for the Android-specific
 * surface that adds exactly that one seam back on top of this
 * interface. A hypothetical DesktopGoogleAuthManager or
 * WebGoogleAuthManager would each add their own equivalent launch
 * surface the same way, while still satisfying AuthenticationProvider
 * for everything downstream.
 */
interface AuthenticationProvider {
    fun isConnected(): Boolean
    fun connectionHealth(): GoogleConnectionHealth
    fun grantedScopes(): Set<String>

    /** A currently-valid access token, silently refreshed first if the stored one has expired. This is the ONLY way any *StatusProvider should obtain a token -- never read a stored credential's access token directly. */
    suspend fun getFreshAccessToken(): Result<String>

    /** Calls Google's revoke endpoint (best-effort -- clears local state regardless of whether the network call succeeds, since an unreachable revoke endpoint must never leave a stale credential looking "connected"). */
    suspend fun revoke(): Result<Unit>

    /** Local-only disconnect: clears the stored credential without calling Google's revoke endpoint. Owner can still "Reconnect" afterward using a fresh sign-in (no cached consent is bypassed -- Google will still show its own consent screen). */
    fun disconnectLocally()
}
