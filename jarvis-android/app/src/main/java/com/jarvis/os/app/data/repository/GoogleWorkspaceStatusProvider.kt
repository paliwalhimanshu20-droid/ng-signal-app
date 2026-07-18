package com.jarvis.os.app.data.repository

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
 * not a simulation. Requires an access token from
 * GoogleWorkspaceTokenStore (see that file's docstring for the honest
 * "paste a token" limitation until a real OAuth client is registered).
 *
 * Read-only by construction (Part 4's own requirement: "Never send
 * email or modify calendar events without owner approval" -- this
 * class exposes no send/write endpoint at all, so there is nothing for
 * a caller to accidentally invoke without going through a separate,
 * explicitly write-capable class this sprint does not build).
 *
 * Same honest-failure shape as GitHubStatusProvider: no token
 * configured, an expired/invalid token, or any network failure all
 * produce a Failure with a real, specific message -- never a fabricated
 * Success.
 */
interface GoogleWorkspaceStatusProvider {
    val status: StateFlow<GoogleWorkspaceFetchResult?>
    suspend fun refresh()
}

@Singleton
class RealGoogleWorkspaceStatusProvider @Inject constructor(
    private val tokenStore: GoogleWorkspaceTokenStore,
) : GoogleWorkspaceStatusProvider {

    private val _status = MutableStateFlow<GoogleWorkspaceFetchResult?>(null)
    override val status: StateFlow<GoogleWorkspaceFetchResult?> = _status.asStateFlow()

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    override suspend fun refresh() {
        val config = tokenStore.currentConfig()
        if (config == null) {
            _status.value = GoogleWorkspaceFetchResult.Failure("Google Workspace isn't connected yet. Add an access token under Settings, Google Workspace.")
            return
        }

        _status.value = withContext(Dispatchers.IO) {
            try {
                val unreadJson = getJson(
                    "https://gmail.googleapis.com/gmail/v1/users/me/messages?maxResults=5&q=is:unread+is:important",
                    config.accessToken,
                )
                val unreadIds = unreadJson.optJSONArray("messages")
                val importantEmails = mutableListOf<GoogleEmailSummary>()
                if (unreadIds != null) {
                    for (i in 0 until unreadIds.length()) {
                        val id = unreadIds.getJSONObject(i).optString("id")
                        val detail = runCatching {
                            getJson("https://gmail.googleapis.com/gmail/v1/users/me/messages/$id?format=metadata&metadataHeaders=Subject&metadataHeaders=From", config.accessToken)
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

                val unreadCountJson = getJson("https://gmail.googleapis.com/gmail/v1/users/me/messages?maxResults=1&q=is:unread", config.accessToken)
                val unreadCount = unreadCountJson.optInt("resultSizeEstimate", importantEmails.size)

                val today = LocalDate.now(ZoneId.systemDefault())
                val startOfDay = today.atStartOfDay(ZoneId.systemDefault()).toInstant()
                val endOfDay = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()
                val eventsJson = getJson(
                    "https://www.googleapis.com/calendar/v3/calendars/primary/events" +
                        "?timeMin=$startOfDay&timeMax=$endOfDay&singleEvents=true&orderBy=startTime",
                    config.accessToken,
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
                    config.accessToken,
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

                GoogleWorkspaceFetchResult.Success(
                    GoogleWorkspaceStatus(
                        accountEmail = config.accountEmail,
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
                    401 -> "Google rejected that access token -- it has likely expired. Get a new one and reconnect."
                    403 -> "Google denied that request -- check the token has gmail.readonly, calendar.readonly, and drive.readonly scopes."
                    else -> "Google returned an error (HTTP ${response.code})."
                }
                throw IllegalStateException(message)
            }
            return JSONObject(body)
        }
    }
}
