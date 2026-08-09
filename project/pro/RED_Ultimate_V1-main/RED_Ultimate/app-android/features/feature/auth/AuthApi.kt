package com.red.feature.auth

import com.red.core.models.AuthResponse
import com.red.core.models.StatusResponse
import com.red.core.models.User
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface AuthApi {
    @POST("auth/register")
    suspend fun register(@Body request: Map<String, String>): Response<AuthResponse>

    @POST("auth/login")
    suspend fun login(@Body request: Map<String, String>): Response<AuthResponse>

    @GET("auth/status")
    suspend fun getStatus(): Response<StatusResponse>

    @POST("admin/approve/{userId}")
    suspend fun approveUser(@Path("userId") userId: String): Response<Unit>
}
