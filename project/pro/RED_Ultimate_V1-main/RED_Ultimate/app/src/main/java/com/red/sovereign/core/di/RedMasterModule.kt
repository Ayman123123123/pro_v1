package com.red.sovereign.core.di

import android.content.Context
import com.red.sovereign.core.delivery.RedDeliveryEngine
import com.red.sovereign.core.database.RedMasterDatabase
import com.red.sovereign.features.calls.RedVoipMaster
import com.red.sovereign.features.pstn.PstnViewModel
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RedMasterModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): RedMasterDatabase {
        return androidx.room.Room.databaseBuilder(
            context,
            RedMasterDatabase::class.java, "red_sovereign.db"
        ).addMigrations().build()
    }

    @Provides
    @Singleton
    fun provideMasterDao(db: RedMasterDatabase) = db.dao()
}
