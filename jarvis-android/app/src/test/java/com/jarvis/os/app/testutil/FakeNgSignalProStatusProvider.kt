package com.jarvis.os.app.testutil

import com.jarvis.os.app.data.repository.NgSignalProStatus
import com.jarvis.os.app.data.repository.NgSignalProStatusProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * "Universal Connection Ecosystem -- Phase 1": RealNgSignalProStatusProvider
 * makes real GitHub API calls, wrong for a unit test. This fake starts
 * in the same honest "not connected" state the real one starts in
 * before refresh() is ever called, and refresh() is a no-op (tests
 * that need a specific value can set [status] directly since it's a
 * plain MutableStateFlow here, not the encapsulated one the real class
 * uses).
 */
class FakeNgSignalProStatusProvider : NgSignalProStatusProvider {
    override val status = MutableStateFlow(
        NgSignalProStatus(
            scannerStatusSummary = "Not connected yet.",
            scannerHealthy = false,
            warehouseSynchronized = false,
            alertPipelineHealthy = false,
            lastUpdated = null,
        ),
    )

    override suspend fun refresh() {
        // No-op -- see this class's own docstring.
    }
}
