package com.jarvis.os.app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Sprint 9: JarvisCore needs a coroutine scope that outlives any single
 * ViewModel to collect ConnectionRepository.transitions for as long as
 * the process is alive (a connection can change state from a
 * background retry, not just a button press in a currently-visible
 * screen). SupervisorJob so one bad collector doesn't cancel the
 * others sharing this scope.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object CoroutineScopeModule {
    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
