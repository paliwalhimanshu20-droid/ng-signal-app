package com.jarvis.os.app.data.settings

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sprint 13 Part 3 (Streamlit Connector): just the deployment's public
 * URL -- not a secret, unlike GitHubTokenStore/GoogleWorkspaceTokenStore,
 * so this deliberately uses plain SharedPreferences rather than
 * EncryptedSharedPreferences. StreamlitStatusProvider.refresh(url) took
 * a URL parameter with nowhere durable to read it from by default; this
 * is that missing durable store, same shape as every other Settings-
 * screen-backed config in this codebase.
 */
interface StreamlitDeploymentStore {
    fun currentUrl(): String?
    fun save(url: String)
    fun clear()
}

@Singleton
class SharedPrefsStreamlitDeploymentStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : StreamlitDeploymentStore {

    private val prefs = context.getSharedPreferences("jarvis_streamlit", Context.MODE_PRIVATE)

    override fun currentUrl(): String? = prefs.getString(KEY_URL, null)?.takeUnless { it.isBlank() }

    override fun save(url: String) {
        prefs.edit { putString(KEY_URL, url) }
    }

    override fun clear() {
        prefs.edit { remove(KEY_URL) }
    }

    companion object {
        private const val KEY_URL = "deployment_url"
    }
}
