package com.red.core.di

import com.red.feature.auth.AuthApi
import com.red.feature.pstn.DuminApi
import com.red.core.delivery.REDDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val BASE_URL = "http://192.168.1.50:8080/api/" // Local Server IP

    @Provides
    @Singleton
    fun provideRetrofit(): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(MoshiConverterFactory.create())
            .client(OkHttpClient.Builder().build())
            .build()
    }

    @Provides
    @Singleton
    fun provideAuthApi(retrofit: Retrofit): AuthApi = retrofit.create(AuthApi::class.java)

    @Provides
    @Singleton
    fun provideDuminApi(retrofit: Retrofit): DuminApi = retrofit.create(DuminApi::class.java)

    @Provides
    @Singleton
    fun provideMessageDao(db: REDDatabase) = db.messageDao()
}
