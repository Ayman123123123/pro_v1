package com.red.sovereign.core.di

import android.content.Context
import com.red.sovereign.core.database.RedMasterDatabase
import com.red.sovereign.features.calls.CallOrchestrator
import com.red.sovereign.features.calls.RedVoipMaster
import com.red.sovereign.features.calls.data.CallRepository
import com.red.sovereign.features.calls.signaling.CallSignalingClient
import com.red.sovereign.features.pstn.PstnViewModel
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt DI Module — يوفّر كل المكوّنات المشتركة بدورة حياة Singleton.
 *
 * ViewModels (@HiltViewModel) لا تحتاج تسجيلاً يدوياً هنا.
 * فقط @Singleton components تحتاج @Provides.
 */
@Module
@InstallIn(SingletonComponent::class)
object RedMasterModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): RedMasterDatabase {
        return androidx.room.Room.databaseBuilder(
            context,
            RedMasterDatabase::class.java,
            "red_sovereign.db"
        ).addMigrations().build()
    }

    @Provides
    @Singleton
    fun provideMasterDao(db: RedMasterDatabase) = db.dao()

    @Provides
    @Singleton
    fun provideCallRepository(@ApplicationContext context: Context): CallRepository {
        return CallRepository(context)
    }

    @Provides
    @Singleton
    fun provideCallSignalingClient(@ApplicationContext context: Context): CallSignalingClient {
        return CallSignalingClient(context)
    }

    @Provides
    @Singleton
    fun provideRedVoipMaster(
        @ApplicationContext context: Context,
        signalingClient: CallSignalingClient,
        callRepository: CallRepository
    ): RedVoipMaster {
        return RedVoipMaster(context, signalingClient, callRepository)
    }
}
