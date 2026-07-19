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

/** Sprint 15 Phase 5: one capability, one result type, one failure message -- never a partial/stale GoogleWorkspaceStatus standing in for "only calendar was actually fetched." */
sealed interface CalendarFetchResult {
    data class Success(val events: List<GoogleCalendarEventSummary>) : CalendarFetchResult
    data class Failure(val message: String) : CalendarFetchResult
}

sealed interface GmailFetchResult {
    data class Success(val unreadCount: Int, val importantEmails: List<GoogleEmailSummary>) : GmailFetchResult
    data class Failure(val message: String) : GmailFetchResult
}

sealed interface DriveFetchResult {
    data class Success(val recentFiles: List<GoogleDriveFileSummary>) : DriveFetchResult
    data class Failure(val message: String) : DriveFetchResult
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
 * a fabricated Success.
 *
 * Depends on AuthenticationProvider, not GoogleAuthManager -- AUTH-001
 * Section 3: this class has no reason to know it's running on Android,
 * so it only asks for the platform-agnostic token/health/scope
 * surface. A future desktop or web build reuses this exact class
 * unmodified, wired to that platform's own AuthenticationProvider
 * implementation instead.
 *
 * Sprint 15 "Executive Intelligence Completion" Phase 5: previously one
 * refresh() call always fetched Gmail, Calendar, AND Drive together,
 * so "what's on my calendar" cost three API round-trips instead of
 * one. Split into getTodaysEvents()/getUnreadEmails()/
 * getRecentDriveFiles() -- each does exactly one capability's fetch,
 * nothing else. refreshAll() (renamed from the old refresh()) still
 * exists and still fetches all three together, for the one caller that
 * legitimately wants the combined picture: the Settings screen's
 * health card, which shows account/scopes/last-sync regardless of
 * what the Owner most recently asked about.
 */
interface GoogleWorkspaceStatusProvider {
    /** Full combined status, as set by the last refreshAll() call -- what the Settings health card reads. Per-capability calls (getTodaysEvents, etc.) do NOT update this; they're independent and don't pretend to refresh the other two capabilities. */
    val status: StateFlow<GoogleWorkspaceFetchResult?>
    suspend fun refreshAll()
    suspend fun getTodaysEvents(): CalendarFetchResult
    suspend fun getUnreadEmails(): GmailFetchResult
    suspend fun getRecentDriveFiles(): DriveFetchResult
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

    /** Every public capability call goes through this first -- one place that asks AuthenticationProvider for a fresh token and turns "not connected"/"revoked" into the same honest Failure message shape, instead of each capability re-implementing that check. */
    private suspend fun <T> withFreshToken(onFailure: (String) -> T, onToken: suspend (String) -> T): T {
        val accessToken = authManager.getFreshAccessToken().getOrElse { error ->
            return onFailure(error.message ?: "Google Workspace isn't connected yet. Connect it under Settings, Google Workspace.")
        }
        return onToken(accessToken)
    }

    override suspend fun getTodaysEvents(): CalendarFetchResult =
        withFreshToken(
            onFailure = { CalendarFetchResult.Failure(it) },
            onToken = { accessToken ->
                withContext(Dispatchers.IO) {
                    try {
                        CalendarFetchResult.Success(fetchTodaysEvents(accessToken))
                    } catch (e: Exception) {
                        CalendarFetchResult.Failure(e.message ?: "Couldn't reach Google Calendar. Check your connection and try again.")
                    }
                }
            },
        )

    override suspend fun getUnreadEmails(): GmailFetchResult =
        withFreshToken(
            onFailure = { GmailFetchResult.Failure(it) },
            onToken = { accessToken ->
                withContext(Dispatchers.IO) {
                    try {
                        val (count, important) = fetchUnreadEmails(accessToken)
                        GmailFetchResult.Success(count, important)
                    } catch (e: Exception) {
                        GmailFetchResult.Failure(e.message ?: "Couldn't reach Gmail. Check your connection and try again.")
                    }
                }
            },
        )

    override suspend fun getRecentDriveFiles(): DriveFetchResult =
        withFreshToken(
            onFailure = { DriveFetchResult.Failure(it) },
            onToken = { accessToken ->
                withContext(Dispatchers.IO) {
                    try {
                        DriveFetchResult.Success(fetchRecentDriveFiles(accessToken))
                    } catch (e: Exception) {
                        DriveFetchResult.Failure(e.message ?: "Couldn't reach Google Drive. Check your connection and try again.")
                    }
                }
            },
        )

    override suspend fun refreshAll() {
        _status.value = withFreshToken(
            onFailure = { GoogleWorkspaceFetchResult.Failure(it) },
            onToken = { accessToken ->
                withContext(Dispatchers.IO) {
                    try {
                        val (unreadCount, importantEmails) = fetchUnreadEmails(accessToken)
                        val todaysEvents = fetchTodaysEvents(accessToken)
                        val recentFiles = fetchRecentDriveFiles(accessToken)

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
            },
        )
    }

    // --- Single-capability fetch helpers -- each makes exactly the API calls its own capability needs, nothing more ---

    private fun fetchTodaysEvents(accessToken: String): List<GoogleCalendarEventSummary> {
        val today = LocalDate.now(ZoneId.systemDefault())
        val startOfDay = today.atStartOfDay(ZoneId.systemDefault()).toInstant()
        val endOfDay = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()
        val eventsJson = getJson(
            "https://www.googleapis.com/calendar/v3/calendars/primary/events" +
                "?timeMin=$startOfDay&timeMax=$endOfDay&singleEvents=true&orderBy=startTime",
            accessToken,
        )
        val eventsArray = eventsJson.optJSONArray("items") ?: return emptyList()
        val todaysEvents = mutableListOf<GoogleCalendarEventSummary>()
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
        return todaysEvents
    }

    private fun fetchUnreadEmails(accessToken: String): Pair<Int, List<GoogleEmailSummary>> {
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
        return unreadCount to importantEmails
    }

    private fun fetchRecentDriveFiles(accessToken: String): List<GoogleDriveFileSummary> {
        val driveJson = getJson(
            "https://www.googleapis.com/drive/v3/files?pageSize=5&orderBy=modifiedTime desc&fields=files(name,modifiedTime)",
            accessToken,
        )
        val filesArray = driveJson.optJSONArray("files") ?: return emptyList()
        val recentFiles = mutableListOf<GoogleDriveFileSummary>()
        for (i in 0 until filesArray.length()) {
            val file = filesArray.getJSONObject(i)
            recentFiles += GoogleDriveFileSummary(
                name = file.optString("name", "Untitled"),
                modifiedAt = runCatching { Instant.parse(file.optString("modifiedTime")) }.getOrNull(),
            )
        }
        return recentFiles
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
