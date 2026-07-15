package com.jarvis.os.app.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sprint 12 "Executive Briefing Engine" needs an NG Signal Pro status
 * for its briefing (see that sprint's own example: "NG Signal Pro
 * completed overnight scanning... Warehouse synchronized. Telegram
 * healthy."). NG Signal Pro is a separate Python/Streamlit application
 * with its own GitHub repo and GitHub Actions workflows -- there is no
 * live API bridge from this Android app into it, and building one is
 * out of this sprint's scope ("no architectural rewrites").
 *
 * Same honesty note as MockChatProvider/ContextManager's own
 * docstrings: this is a MOCK, not a live connection, and
 * [NgSignalProStatus.lastUpdated] is deliberately nullable and starts
 * null specifically so a consumer (ExecutiveBriefingEngine) can tell
 * the difference between "really has fresh data" and "stub with
 * nothing behind it yet", and say so honestly in the briefing rather
 * than presenting invented market data as if it were live. Wiring a
 * real bridge (e.g. NG Signal Pro pushing its daily summary to a
 * shared endpoint this app polls) is future work.
 */
data class NgSignalProStatus(
    val marketBias: String,
    val confidencePercent: Int,
    val buyCandidateCount: Int,
    val warehouseSynchronized: Boolean,
    val telegramHealthy: Boolean,
    val lastUpdated: Instant?,
)

interface NgSignalProStatusProvider {
    val status: StateFlow<NgSignalProStatus>
}

@Singleton
class MockNgSignalProStatusProvider @Inject constructor() : NgSignalProStatusProvider {
    private val _status = MutableStateFlow(
        NgSignalProStatus(
            marketBias = "unknown -- no live connection",
            confidencePercent = 0,
            buyCandidateCount = 0,
            warehouseSynchronized = false,
            telegramHealthy = false,
            lastUpdated = null,
        ),
    )
    override val status: StateFlow<NgSignalProStatus> = _status.asStateFlow()
}
