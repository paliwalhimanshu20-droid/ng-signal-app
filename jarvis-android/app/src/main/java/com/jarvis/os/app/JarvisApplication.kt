package com.jarvis.os.app

import android.app.Application
import com.jarvis.tidb.di.TidbModule
import dagger.hilt.android.HiltAndroidApp

/**
 * JARVIS-002 Layer 0 (Runtime Initialization): before this change, [TidbModule.initialize] was
 * never called anywhere in the app -- confirmed by a full-module search during the JARVIS-002
 * planning review -- which meant the Trading Intelligence Database was never opened at runtime,
 * regardless of how correctly anything above it was wired.
 *
 * [TidbModule.initialize] is already idempotent and thread-safe on its own (a `database != null`
 * fast-path check plus a `synchronized` double-checked-lock around the real open), so this call
 * site does not need to guard against being invoked more than once -- it only needs to happen at
 * least once, as early as possible, before any Hilt-provided TIDB repository (see
 * `TradingRepositoryBridgeModule`) is first injected. `Application.onCreate()` is the correct
 * place: it runs before any Activity, and therefore before any `@Inject`-annotated class that
 * could request a TIDB repository.
 */
@HiltAndroidApp
class JarvisApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        TidbModule.initialize(applicationContext)
    }
}
