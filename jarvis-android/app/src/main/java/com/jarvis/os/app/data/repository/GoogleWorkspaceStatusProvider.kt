package com.jarvis.os.app.data.repository

import com.jarvis.os.app.core.security.AuthenticationProvider
import com.jarvis.os.app.data.settings.GoogleWorkspaceTokenStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class GoogleCalendarEventSummary(
    val title: String,
    val startTime: String,
    val location: String?,
)

data class GoogleEmailSummary(
    val subject: String,
    val from: String,
    val snippet: String,
)

data class GoogleDriveFileSummary(
    val name: String,
    val modifiedAt: Instant?,
)

data class GoogleWorkspaceStatus(
    val accountEmail: String,
    val unreadEmailCount: Int,
    val importantEmails: List<GoogleEmailSummary>,
    val todaysEvents: List<GoogleCalendarEventSummary>,
    val recentDriveFiles: List<GoogleDriveFileSummary>,
    val fetchedAt: Instant,
)

sealed interface GoogleWorkspaceFetchResult {
    data class Success(val status: GoogleWorkspaceStatus) : GoogleWorkspaceFetchResult
    data class Failure(val message: String) : GoogleWorkspaceFetchResult
}

/**
 * Sprint 13 Part 4: real REST calls to gmail.googleapis.com,
 * www.googleapis.com/calendar/v3, and www.googleapis.com/drive/v3 --
 * not a simulation. Every call goes through
 * GoogleAuthManager.getFreshAccessToken(), which silently refreshes an
 * expired token using the stored refresh token before this class ever
 * sees it -- this class never reads or caches a token itself (see that
 * interface's own docstring for why it's the only allowed path).
 *
 * Read-only by construction (Part 4's own requirement: "Never send
 * email or modify calendar events without owner approval" -- this
 * class exposes no send/write endpoint at all, so there is nothing for
 * a caller to accidentally invoke without going through a separate,
 * explicitly write-capable class this sprint does not build).
 *
 * Same honest-failure shape as GitHubStatusProvider: not connected, a
 * refresh token that's been revoked/expired (surfaced by
 * GoogleAuthManager as a Result.failure, not a crash), or any network
 * failure all produce a Failure with a real, specific message -- never
 * a fabricated Success. A successful refresh here also stamps
 * GoogleWorkspaceTokenStore.markSynced(), which is what "Last
 * synchronization time" in Owner Controls actually reads.
 * Depends on AuthenticationProvider, not GoogleAuthManager -- AUTH-001
 * Section 3: this class has no reason to know it's running on Android,
 * so it only asks for the platform-agnostic token/health/scope
 * surface. A future desktop or web build reuses this exact class
 * unmodified, wired to that platform's own AuthenticationProvider
 * implementation instead.
 */
interface GoogleWorkspaceStatusProvider {
    val status: StateFlow<GoogleWorkspaceFetchResult?>
    suspend fun refresh()
}

@Singleton
class RealGoogleWorkspaceStatusProvider @Inject constructor(
    private val authManager: AuthenticationProvider,
    private val tokenStore: GoogleWorkspaceTokenStore,
) : GoogleWorkspaceStatusProvider {

    private val _status = MutableStateFlow<GoogleWorkspaceFetchResult?>(null)
    override val status: StateFlow<GoogleWorkspaceFetchResult?> = _status.asStateFlow()

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    override suspend fun refresh() {
        val tokenResult = authManager.getFreshAccessToken()
        val accessToken = tokenResult.getOrElse { error ->
            _status.value = GoogleWorkspaceFetchResult.Failure(error.message ?: "Google Workspace isn't connected yet. Connect it under Settings, Google Workspace.")
            return
        }

        _status.value = withContext(Dispatchers.IO) {
            try {
                val unreadJson = getJson(
                    "https://gmail.googleapis.com/gmail/v1/users/me/messages?maxResults=5&q=is:unread+is:important",
                    accessToken,
                )
                val unreadIds = unreadJson.optJSONArray("messages")
                val importantEmails = mutableListOf<GoogleEmailSummary>()
                if (unreadIds != null) {
                    for (i in 0 until unreadIds.length()) {
                        val id = unreadIds.getJSONObject(i).optString("id")
                        val detail = runCatching {
                            getJson("https://gmail.googleapis.com/gmail/v1/users/me/messages/$id?format=metadata&metadataHeaders=Subject&metadataHeaders=From", accessToken)
                        }.getOrNull() ?: continue
                        val headers = detail.optJSONObject("payload")?.optJSONArray("headers")
                        var subject = "(no subject)"
                        var from = "unknown"
                        if (headers != null) {
                            for (h in 0 until headers.length()) {
                                val header = headers.getJSONObject(h)
                                when (header.optString("name")) {
                                    "Subject" -> subject = header.optString("value", subject)
                                    "From" -> from = header.optString("value", from)
                                }
                            }
                        }
                        importantEmails += GoogleEmailSummary(subject = subject, from = from, snippet = detail.optString("snippet", ""))
                    }
                }

                val unreadCountJson = getJson("https://gmail.googleapis.com/gmail/v1/users/me/messages?maxResults=1&q=is:unread", accessToken)
                val unreadCount = unreadCountJson.optInt("resultSizeEstimate", importantEmails.size)

                val today = LocalDate.now(ZoneId.systemDefault())
                val startOfDay = today.atStartOfDay(ZoneId.systemDefault()).toInstant()
                val endOfDay = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()
                val eventsJson = getJson(
                    "https://www.googleapis.com/calendar/v3/calendars/primary/events" +
                        "?timeMin=$startOfDay&timeMax=$endOfDay&singleEvents=true&orderBy=startTime",
                    accessToken,
                )
                val eventsArray = eventsJson.optJSONArray("items")
                val todaysEvents = mutableListOf<GoogleCalendarEventSummary>()
                if (eventsArray != null) {
                    for (i in 0 until eventsArray.length()) {
                        val event = eventsArray.getJSONObject(i)
                        val start = event.optJSONObject("start")
                        val startLabel = start?.optString("dateTime")?.takeUnless { it.isBlank() }
                            ?: start?.optString("date").orEmpty()
                        todaysEvents += GoogleCalendarEventSummary(
                            title = event.optString("summary", "(untitled event)"),
                            startTime = startLabel,
                            location = event.optString("location", "").takeUnless { it.isBlank() },
                        )
                    }
                }

                val driveJson = getJson(
                    "https://www.googleapis.com/drive/v3/files?pageSize=5&orderBy=modifiedTime desc&fields=files(name,modifiedTime)",
                    accessToken,
                )
                val filesArray = driveJson.optJSONArray("files")
                val recentFiles = mutableListOf<GoogleDriveFileSummary>()
                if (filesArray != null) {
                    for (i in 0 until filesArray.length()) {
                        val file = filesArray.getJSONObject(i)
                        recentFiles += GoogleDriveFileSummary(
                            name = file.optString("name", "Untitled"),
                            modifiedAt = runCatching { Instant.parse(file.optString("modifiedTime")) }.getOrNull(),
                        )
                    }
                }

                tokenStore.markSynced()

                GoogleWorkspaceFetchResult.Success(
                    GoogleWorkspaceStatus(
                        accountEmail = tokenStore.currentConnectionInfo()?.accountEmail.orEmpty(),
                        unreadEmailCount = unreadCount,
                        importantEmails = importantEmails,
                        todaysEvents = todaysEvents,
                        recentDriveFiles = recentFiles,
                        fetchedAt = Instant.now(),
                    ),
                )
            } catch (e: Exception) {
                GoogleWorkspaceFetchResult.Failure(e.message ?: "Couldn't reach Google Workspace. Check your connection and try again.")
            }
        }
    }

    private fun getJson(url: String, token: String): JSONObject {
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = when (response.code) {
                    401 -> "Google rejected that access token -- it has likely expired or been revoked. Reconnect Google Workspace under Settings."
                    403 -> "Google denied that request -- check the granted permissions under Settings, Google Workspace."
                    else -> "Google returned an error (HTTP ${response.code})."
                }
                throw IllegalStateException(message)
            }
            return JSONObject(body)
        }
    }
}
