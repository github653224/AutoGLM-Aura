package com.autoglm.autoagent.di

import com.autoglm.autoagent.data.SettingsRepository
import com.autoglm.autoagent.data.api.AIClient
import com.autoglm.autoagent.service.AppManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAIClient(settingsRepository: SettingsRepository): AIClient {
        return AIClient(settingsRepository)
    }
}
