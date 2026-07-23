package com.jarvis.os.app.core.security.di

import com.jarvis.os.app.core.security.InMemorySecretVault
import com.jarvis.os.app.core.security.SecretVault
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SecurityModule {
    @Binds
    @Singleton
    abstract fun bindSecretVault(impl: InMemorySecretVault): SecretVault
}
