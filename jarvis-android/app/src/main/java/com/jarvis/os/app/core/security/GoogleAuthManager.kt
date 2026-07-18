package com.jarvis.os.app.core.security

import android.content.Context
import android.content.Intent
import android.util.Log
import com.jarvis.os.app.data.settings.GoogleConnectionHealth
import com.jarvis.os.app.data.settings.GoogleWorkspaceTokenStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues
import net.openid.appauth.TokenRequest
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Sprint 13 "Production Google Workspace Authentication": the actual
 * OAuth engine behind Connected Systems -> Google Workspace. Owns the
 * three real moments the Owner Controls section names --
 * authorization (Connect/Re-authorize), silent refresh (the
 * "automatically obtain and refresh" requirement), and revoke -- all
 * against Google's real endpoints (see GoogleOAuthConfig), never
 * simulated.
 *
 * "Never log OAuth tokens" (this sprint's Security section): every Log
 * call in this file logs event *names* and, where an error exists, its
 * error *code/description* -- never accessToken, never refreshToken,
 * never idToken. Grep this file for "accessToken\|refreshToken" outside
 * of parameter lists/AuthState field access and you should find zero
 * log call sites; that invariant is intentional, not accidental.
 */
interface GoogleAuthManager : AuthenticationProvider {
    /** Launch this via an Activity Result contract (StartActivityForResult) -- see SettingsScreen's launcher. Android-specific launch surface; see AuthenticationProvider's own docstring for why this isn't on the shared interface. */
    fun buildAuthorizationIntent(): Intent

    /** Call from the Activity Result callback with the returned Intent. Exchanges the auth code for tokens and persists the resulting AuthState (including the refresh token). */
    suspend fun handleAuthorizationResponse(intent: Intent): Result<Unit>
}

@Singleton
class RealGoogleAuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tokenStore: GoogleWorkspaceTokenStore,
) : GoogleAuthManager {

    private val serviceConfig = AuthorizationServiceConfiguration(
        android.net.Uri.parse(GoogleOAuthConfig.AUTH_ENDPOINT),
        android.net.Uri.parse(GoogleOAuthConfig.TOKEN_ENDPOINT),
    )

    private val authService by lazy { AuthorizationService(context) }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    override fun buildAuthorizationIntent(): Intent {
        val request = AuthorizationRequest.Builder(
            serviceConfig,
            GoogleOAuthConfig.CLIENT_ID,
            ResponseTypeValues.CODE,
            android.net.Uri.parse(GoogleOAuthConfig.REDIRECT_URI),
        )
            .setScopes(GoogleOAuthConfig.SCOPES)
            // access_type=offline is what makes Google issue a refresh
            // token at all; prompt=consent forces the consent screen
            // (and therefore a fresh refresh token) even for an account
            // that already granted these exact scopes before -- without
            // it, a Re-authorize after a revoked/expired refresh token
            // can silently succeed with NO new refresh token, leaving
            // the Owner stuck in the same broken state they were
            // re-authorizing to fix.
            .setAdditionalParameters(mapOf("access_type" to "offline", "prompt" to "consent"))
            .build()

        Log.i(TAG, "Building authorization request (scopes=${GoogleOAuthConfig.SCOPES.size})")
        return authService.getAuthorizationRequestIntent(request)
    }

    override suspend fun handleAuthorizationResponse(intent: Intent): Result<Unit> {
        val response = AuthorizationResponse.fromIntent(intent)
        val exception = AuthorizationException.fromIntent(intent)

        if (exception != null) {
            Log.w(TAG, "Authorization failed: code=${exception.code} type=${exception.type}")
            return Result.failure(IllegalStateException(exception.errorDescription ?: "Google sign-in was cancelled or denied."))
        }
        if (response == null) {
            return Result.failure(IllegalStateException("No authorization response received."))
        }

        val tokenRequest = response.createTokenExchangeRequest()
        val exchangeResult = performTokenRequest(tokenRequest)

        return exchangeResult.fold(
            onSuccess = { tokenResponse ->
                val authState = AuthState(response, exception)
                authState.update(tokenResponse, null)
                if (authState.refreshToken == null) {
                    Log.w(TAG, "Token exchange succeeded but no refresh token was returned.")
                    return Result.failure(IllegalStateException("Google didn't return a refresh token. Remove this app's access at myaccount.google.com/permissions and try Connect again."))
                }
                tokenStore.saveAuthState(authState)
                tokenStore.markTokenRefreshed()
                // Best-effort only -- a failure to fetch the email label
                // must never fail the connection itself, since it's
                // display-only (Owner Controls' "view granted
                // permissions" reads scopes from AuthState directly,
                // not from this).
                runCatching { withContext(Dispatchers.IO) { fetchAccountEmail(tokenResponse.accessToken.orEmpty()) } }
                    .onSuccess { email -> if (email != null) tokenStore.updateAccountEmail(email) }
                Log.i(TAG, "Authorization complete, refresh token stored.")
                Result.success(Unit)
            },
            onFailure = { Result.failure(it) },
        )
    }

    override suspend fun getFreshAccessToken(): Result<String> {
        val authState = tokenStore.currentAuthState()
            ?: return Result.failure(IllegalStateException("Google Workspace isn't connected yet."))

        val neededRefresh = authState.needsTokenRefresh
        val result = suspendCancellableCoroutine<Result<String>> { continuation ->
            authState.performActionWithFreshTokens(authService) { accessToken, _, ex ->
                if (ex != null) {
                    Log.w(TAG, "Silent token refresh failed: code=${ex.code} type=${ex.type}")
                    continuation.resume(Result.failure(IllegalStateException(ex.errorDescription ?: "Google Workspace needs to be reconnected.")))
                } else if (accessToken == null) {
                    continuation.resume(Result.failure(IllegalStateException("No access token available.")))
                } else {
                    continuation.resume(Result.success(accessToken))
                }
            }
        }

        // performActionWithFreshTokens mutates `authState` in place on a
        // real refresh -- persist that mutation, and only stamp
        // lastTokenRefreshAt when a refresh genuinely happened (not on
        // every call, which would make "Last token refresh time" in
        // Owner Controls meaningless).
        if (result.isSuccess) {
            tokenStore.saveAuthState(authState)
            if (neededRefresh) tokenStore.markTokenRefreshed()
        }
        return result
    }

    override fun isConnected(): Boolean = tokenStore.currentAuthState()?.refreshToken != null

    override fun connectionHealth(): GoogleConnectionHealth {
        val authState = tokenStore.currentAuthState() ?: return GoogleConnectionHealth.UNKNOWN
        return when {
            authState.refreshToken == null -> GoogleConnectionHealth.NEEDS_REAUTH
            authState.authorizationException != null -> GoogleConnectionHealth.NEEDS_REAUTH
            authState.needsTokenRefresh -> GoogleConnectionHealth.DEGRADED
            else -> GoogleConnectionHealth.HEALTHY
        }
    }

    override fun grantedScopes(): Set<String> =
        tokenStore.currentAuthState()?.scope?.split(" ")?.filter { it.isNotBlank() }?.toSet().orEmpty()

    override suspend fun revoke(): Result<Unit> {
        val refreshToken = tokenStore.currentAuthState()?.refreshToken
        val networkResult: Result<Unit> = if (refreshToken == null) {
            Result.success(Unit)
        } else {
            withContext(Dispatchers.IO) {
                try {
                    val body = FormBody.Builder().add("token", refreshToken).build()
                    val request = Request.Builder().url(GoogleOAuthConfig.REVOKE_ENDPOINT).post(body).build()
                    httpClient.newCall(request).execute().use { response ->
                        if (response.isSuccessful) Result.success(Unit)
                        else {
                            Log.w(TAG, "Revoke endpoint returned HTTP ${response.code}")
                            Result.failure(IllegalStateException("Google returned HTTP ${response.code} while revoking access."))
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Revoke call failed: ${e.message}")
                    Result.failure(e)
                }
            }
        }
        // Clear local state unconditionally -- see this class's revoke() docstring.
        tokenStore.clear()
        Log.i(TAG, "Local Google Workspace credentials cleared.")
        return networkResult
    }

    override fun disconnectLocally() {
        tokenStore.clear()
        Log.i(TAG, "Google Workspace disconnected locally (no revoke call made).")
    }

    private suspend fun performTokenRequest(tokenRequest: TokenRequest): Result<net.openid.appauth.TokenResponse> =
        suspendCancellableCoroutine { continuation ->
            authService.performTokenRequest(tokenRequest) { response, exception ->
                if (exception != null) {
                    Log.w(TAG, "Token exchange failed: code=${exception.code} type=${exception.type}")
                    continuation.resume(Result.failure(IllegalStateException(exception.errorDescription ?: "Couldn't complete Google sign-in.")))
                } else if (response == null) {
                    continuation.resume(Result.failure(IllegalStateException("Empty token response from Google.")))
                } else {
                    continuation.resume(Result.success(response))
                }
            }
        }

    private fun fetchAccountEmail(accessToken: String): String? {
        if (accessToken.isBlank()) return null
        val request = Request.Builder()
            .url("https://www.googleapis.com/oauth2/v3/userinfo")
            .addHeader("Authorization", "Bearer $accessToken")
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string().orEmpty()
            return org.json.JSONObject(body).optString("email").takeUnless { it.isBlank() }
        }
    }

    companion object {
        private const val TAG = "GoogleAuthManager"
    }
}
