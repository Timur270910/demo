package com.aiagent.client.di

import com.aiagent.client.data.repository.AiRepositoryImpl
import com.aiagent.client.data.repository.SshRepositoryImpl
import com.aiagent.client.data.security.SecureStorage
import com.aiagent.client.domain.repository.AiRepository
import com.aiagent.client.domain.repository.SshRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindSshRepository(impl: SshRepositoryImpl): SshRepository

    @Binds
    @Singleton
    abstract fun bindAiRepository(impl: AiRepositoryImpl): AiRepository

    companion object {
        @Provides
        @Singleton
        fun provideSecureStorage(
            encryptedSharedPreferences: androidx.preference.SharedPreferences
        ): SecureStorage {
            return SecureStorage(encryptedSharedPreferences)
        }
    }
}
