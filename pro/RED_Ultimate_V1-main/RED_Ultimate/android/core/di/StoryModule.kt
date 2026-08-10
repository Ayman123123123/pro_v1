package com.red.sovereign.core.di

import com.red.sovereign.core.database.MasterDatabase
import com.red.sovereign.features.stories.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object StoryModule {

    @Provides
    @Singleton
    fun provideStoryDao(db: MasterDatabase): StoryDao = db.storyDao()

    @Provides
    @Singleton
    fun provideStoryRepository(impl: StoryRepositoryImpl): StoryRepository = impl
}
