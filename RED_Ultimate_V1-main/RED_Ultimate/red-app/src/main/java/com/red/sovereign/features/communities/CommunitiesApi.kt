package com.red.sovereign.features.communities

import com.red.sovereign.auth.ApiResult
import com.red.sovereign.auth.AuthorizedApiClient
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * ════════════════════════════════════════════════════════════════════════
 *  Communities API — Android client
 *  - Talks to /api/communities on the backend
 *  - Real data only — no fixtures, no mock data
 * ════════════════════════════════════════════════════════════════════════
 */

@Serializable
data class Community(
    val id: String,
    val name: String,
    val description: String? = null,
    val category: String = "GENERAL",
    val tags: List<String> = emptyList(),
    val isPublic: Boolean = true,
    val createdBy: String,
    val createdByUsername: String,
    val avatarColor: String = "#45B7D1",
    val rules: String? = null,
    val memberCount: Long = 0L,
    val myRole: String? = null,
    val isJoined: Boolean = false,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
data class CreateCommunityBody(
    val name: String,
    val description: String? = null,
    val category: String? = null,
    val tags: List<String>? = null,
    val isPublic: Boolean = true,
    val rules: String? = null,
    val avatarColor: String? = null
)

class CommunitiesApi(private val client: AuthorizedApiClient) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }
    private val JSON = "application/json; charset=utf-8".toRequestBody()

    suspend fun list(search: String? = null, page: Int = 0, size: Int = 30): ApiResult<List<Community>> {
        val qs = buildString {
            append("?page=").append(page).append("&size=").append(size)
            if (!search.isNullOrBlank()) {
                append("&search=").append(java.net.URLEncoder.encode(search.trim(), "UTF-8"))
            }
        }
        val raw = when (val r = client.request("GET", "/api/communities$qs")) {
            is ApiResult.Success -> r.value
            is ApiResult.Error -> return r
        }
        return try {
            ApiResult.Success(200, json.decodeFromString<List<Community>>(raw))
        } catch (e: Exception) {
            ApiResult.Error(500, e.message)
        }
    }

    suspend fun create(body: CreateCommunityBody): ApiResult<Community> {
        val payload = json.encodeToString(body).toRequestBody()
        val raw = when (val r = client.requestBody("POST", "/api/communities", payload)) {
            is ApiResult.Success -> r.value
            is ApiResult.Error -> return r
        }
        return try {
            ApiResult.Success(200, json.decodeFromString<Community>(raw))
        } catch (e: Exception) {
            ApiResult.Error(500, e.message)
        }
    }

    suspend fun join(id: String): ApiResult<Community> = parseCommunity(
        client.request("POST", "/api/communities/$id/join")
    )

    suspend fun leave(id: String): ApiResult<String> =
        client.request("POST", "/api/communities/$id/leave")

    suspend fun delete(id: String): ApiResult<String> =
        client.request("DELETE", "/api/communities/$id")

    suspend fun details(id: String): ApiResult<Community> {
        val raw = when (val r = client.request("GET", "/api/communities/$id")) {
            is ApiResult.Success -> r.value
            is ApiResult.Error -> return r
        }
        return try {
            ApiResult.Success(200, json.decodeFromString<Community>(raw))
        } catch (e: Exception) {
            ApiResult.Error(500, e.message)
        }
    }

    private fun parseCommunity(r: ApiResult<String>): ApiResult<Community> = when (r) {
        is ApiResult.Success -> try {
            ApiResult.Success(200, json.decodeFromString<Community>(r.value))
        } catch (e: Exception) {
            ApiResult.Error(500, e.message)
        }
        is ApiResult.Error -> r
    }
}
