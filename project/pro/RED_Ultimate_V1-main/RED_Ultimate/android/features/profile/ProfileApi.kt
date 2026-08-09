package com.red.sovereign.features.profile

import retrofit2.Response
import retrofit2.http.*

interface ProfileApi {
    @GET("profile/me")
    suspend fun getMyProfile(): Response<ProfileResponse>

    @POST("profile/update")
    suspend fun updateProfile(@Body updates: Map<String, String>): Response<Unit>

    @GET("profile/storage/usage")
    suspend fun getStorageUsage(): Response<Map<String, Long>>
}

data class ProfileResponse(
    val id: String,
    val name: String,
    val avatarUrl: String?,
    val about: String?,
    val status: String
)
