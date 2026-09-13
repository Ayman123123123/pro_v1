package com.red.core.models

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

enum class UserStatus {
    @Json(name = "PENDING") PENDING,
    @Json(name = "APPROVED") APPROVED,
    @Json(name = "REJECTED") REJECTED,
    @Json(name = "BANNED") BANNED
}

@JsonClass(generateAdapter = true)
data class User(
    val id: String,
    val email: String,
    val name: String,
    val avatarUrl: String?,
    val status: UserStatus,
    val createdAt: Long
)

@JsonClass(generateAdapter = true)
data class AuthResponse(
    val token: String,
    val user: User
)

@JsonClass(generateAdapter = true)
data class StatusResponse(
    val status: UserStatus
)
