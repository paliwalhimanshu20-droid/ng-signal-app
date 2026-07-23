package com.jarvis.os.app.testutil

import com.jarvis.os.app.data.repository.GitHubFetchResult
import com.jarvis.os.app.data.repository.GitHubStatusProvider
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * "Universal Connection Ecosystem -- Phase 1": RealGitHubStatusProvider
 * makes real GitHub API calls, wrong for a unit test. Starts at null
 * (matching the real provider's own "never fetched yet" starting
 * state); refresh() is a no-op since tests that need a specific result
 * can just set [status] directly (plain MutableStateFlow, not
 * encapsulated, same pattern as FakeNgSignalProStatusProvider).
 */
class FakeGitHubStatusProvider : GitHubStatusProvider {
    override val status = MutableStateFlow<GitHubFetchResult?>(null)

    override suspend fun refresh() {
        // No-op -- see this class's own docstring.
    }
}
