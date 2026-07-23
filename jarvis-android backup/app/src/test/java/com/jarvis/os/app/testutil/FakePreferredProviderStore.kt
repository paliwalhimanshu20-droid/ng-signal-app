package com.jarvis.os.app.testutil

import com.jarvis.os.app.data.settings.PreferredProviderStore

/**
 * "Why this is not selecting the AI key which is verified": AiRouter
 * now depends on PreferredProviderStore for real persistence across
 * app restarts. This fake is a plain in-memory stand-in for tests --
 * starts with nothing saved (null), same as a fresh install.
 */
class FakePreferredProviderStore : PreferredProviderStore {
    private var stored: String? = null
    override fun currentProviderId(): String? = stored
    override fun save(providerId: String) {
        stored = providerId
    }
}
