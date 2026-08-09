package com.red.sovereign.media

import com.red.sovereign.auth.ApiResult
import com.red.sovereign.auth.AuthorizedApiClient
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * ════════════════════════════════════════════════════════════════════════
 *  RED Sovereign — Polls API (Content Management V20)
 *  - Talks to /api/admin/content/polls on the backend
 *  - All payloads come from real backend tables (polls, poll_options, poll_votes)
 *  - No mock data, no hardcoded fixtures
 * ════════════════════════════════════════════════════════════════════════
 */

@Serializable
data class PollDto(
    val id: String,
    val creatorId: String,
    val question: String,
    val description: String? = null,
    val pollType: String,            // SINGLE_CHOICE | MULTIPLE_CHOICE | RANKED
    val isAnonymous: Boolean = false,
    val allowAddOptions: Boolean = false,
    val status: String,              // DRAFT | ACTIVE | CLOSED | ARCHIVED
    val startsAt: String,
    val endsAt: String? = null,
    val targetType: String = "GLOBAL", // GLOBAL | GROUP | USER
    val totalVotes: Int = 0,
    val uniqueVoters: Int = 0,
    val createdAt: String
)

@Serializable
data class PollOptionDto(
    val id: String,
    val pollId: String,
    val optionText: String,
    val optionOrder: Int = 0,
    val voteCount: Int = 0,
    val percentage: Double = 0.0
)

@Serializable
data class PollDetailDto(
    val poll: PollDto,
    val options: List<PollOptionDto> = emptyList(),
    val userVote: List<String> = emptyList(),  // option ids the current user picked
    val canVote: Boolean = true
)

@Serializable
data class CreatePollRequest(
    val question: String,
    val options: List<String>,
    val pollType: String = "SINGLE_CHOICE",
    val isAnonymous: Boolean = false,
    val allowAddOptions: Boolean = false,
    val endsAt: String? = null
)

@Serializable
data class VoteRequest(
    val optionIds: List<String>
)

@Serializable
data class PageResponsePoll(
    val content: List<PollDto> = emptyList(),
    val page: Int = 0,
    val size: Int = 0,
    val totalElements: Long = 0,
    val totalPages: Int = 0
)

class PollsApi(private val client: AuthorizedApiClient) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }
    private val JSON = "application/json; charset=utf-8".toRequestBody()

    suspend fun list(page: Int = 0, size: Int = 20, status: String? = null): ApiResult<PageResponsePoll> {
        val qs = buildString {
            append("?page=").append(page).append("&size=").append(size)
            if (status != null) append("&status=").append(status)
        }
        val raw = when (val r = client.request("GET", "/api/admin/content/polls$qs")) {
            is ApiResult.Success -> r.value
            is ApiResult.Error -> return r
        }
        return try {
            val parsed = json.decodeFromString<PageResponsePoll>(raw)
            ApiResult.Success(200, parsed)
        } catch (e: Exception) {
            // Fallback: backend may return a bare list
            try {
                val list = json.decodeFromString<List<PollDto>>(raw)
                ApiResult.Success(200, PageResponsePoll(content = list, totalElements = list.size.toLong()))
            } catch (_: Exception) {
                ApiResult.Error(500, e.message)
            }
        }
    }

    suspend fun active(): ApiResult<List<PollDto>> {
        val raw = when (val r = client.request("GET", "/api/admin/content/polls/active")) {
            is ApiResult.Success -> r.value
            is ApiResult.Error -> return r
        }
        return try {
            val list = json.decodeFromString<List<PollDto>>(raw)
            ApiResult.Success(200, list)
        } catch (e: Exception) {
            ApiResult.Error(500, e.message)
        }
    }

    suspend fun detail(pollId: String): ApiResult<PollDetailDto> {
        val raw = when (val r = client.request("GET", "/api/admin/content/polls/$pollId")) {
            is ApiResult.Success -> r.value
            is ApiResult.Error -> return r
        }
        return try {
            ApiResult.Success(200, json.decodeFromString<PollDetailDto>(raw))
        } catch (e: Exception) {
            ApiResult.Error(500, e.message)
        }
    }

    suspend fun create(req: CreatePollRequest): ApiResult<PollDto> {
        val body = json.encodeToString(req).toRequestBody()
        val raw = when (val r = client.requestBody("POST", "/api/admin/content/polls", body)) {
            is ApiResult.Success -> r.value
            is ApiResult.Error -> return r
        }
        return try {
            ApiResult.Success(200, json.decodeFromString<PollDto>(raw))
        } catch (e: Exception) {
            ApiResult.Error(500, e.message)
        }
    }

    suspend fun vote(pollId: String, optionIds: List<String>): ApiResult<String> {
        val body = json.encodeToString(VoteRequest(optionIds)).toRequestBody()
        return client.requestBody("POST", "/api/admin/content/polls/$pollId/vote", body)
            .let { r -> r as ApiResult<String> }
    }

    suspend fun close(pollId: String): ApiResult<String> =
        client.request("POST", "/api/admin/content/polls/$pollId/close")

    suspend fun delete(pollId: String): ApiResult<String> =
        client.request("DELETE", "/api/admin/content/polls/$pollId")
}
