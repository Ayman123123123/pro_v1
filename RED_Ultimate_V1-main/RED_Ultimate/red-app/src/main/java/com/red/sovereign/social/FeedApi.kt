package com.red.sovereign.social

import com.red.sovereign.auth.ApiResult
import com.red.sovereign.auth.AuthorizedApiClient
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.URLEncoder

class FeedApi(private val client: AuthorizedApiClient) {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    suspend fun load(scope: String?, cursor: String? = null): ApiResult<FeedResponse> {
        val query = buildList {
            add("scope=${URLEncoder.encode(scope ?: "ALL", "UTF-8")}")
            cursor?.let { add("before=${URLEncoder.encode(it, "UTF-8")}") }
            add("limit=20")
        }.joinToString("&")
        return client.request("GET", "/api/feed?$query").decode { json.decodeFromString<FeedResponse>(it) }
    }

    suspend fun create(text: String, visibility: String = "LOCAL_YEMEN"): ApiResult<Post> =
        create(CreatePostRequest(text, visibility))

    suspend fun create(request: CreatePostRequest): ApiResult<Post> =
        client.request("POST", "/api/feed/posts", json.encodeToString(request)).decode { json.decodeFromString<Post>(it) }

    suspend fun thread(postId: String): ApiResult<List<Post>> =
        client.request("GET", "/api/feed/posts/$postId/thread").decode { json.decodeFromString<List<Post>>(it) }

    suspend fun edit(postId: String, text: String): ApiResult<Post> =
        client.request("PUT", "/api/feed/posts/$postId", json.encodeToString(EditPostRequest(text))).decode { json.decodeFromString<Post>(it) }

    suspend fun hide(postId: String): ApiResult<Unit> = when (val result = client.request("POST", "/api/feed/posts/$postId/hide")) {
        is ApiResult.Success -> ApiResult.Success(result.code, Unit)
        is ApiResult.Error -> result
    }

    suspend fun mute(authorRedId: String): ApiResult<Unit> = when (val result = client.request("POST", "/api/feed/mute/$authorRedId")) {
        is ApiResult.Success -> ApiResult.Success(result.code, Unit)
        is ApiResult.Error -> result
    }

    suspend fun report(postId: String, reason: String): ApiResult<Unit> = when (val result = client.request("POST", "/api/feed/posts/$postId/report", json.encodeToString(HidePostRequest(reason)))) {
        is ApiResult.Success -> ApiResult.Success(result.code, Unit)
        is ApiResult.Error -> result
    }

    suspend fun delete(postId: String): ApiResult<Unit> = when (val result = client.request("DELETE", "/api/feed/posts/$postId")) {
        is ApiResult.Success -> ApiResult.Success(result.code, Unit)
        is ApiResult.Error -> result
    }

    suspend fun react(postId: String, type: String, active: Boolean): ApiResult<Post> =
        client.request("POST", "/api/feed/posts/$postId/reactions", json.encodeToString(ReactionRequest(type, active))).decode { json.decodeFromString<Post>(it) }

    suspend fun vote(postId: String, optionId: String): ApiResult<Post> =
        client.request("POST", "/api/feed/posts/$postId/vote", json.encodeToString(PollVoteRequest(optionId))).decode { json.decodeFromString<Post>(it) }

    suspend fun requestFriend(redId: String): ApiResult<Unit> = when (val result = client.request("POST", "/api/contacts/requests/$redId")) {
        is ApiResult.Success -> ApiResult.Success(result.code, Unit)
        is ApiResult.Error -> result
    }

    private inline fun <T> ApiResult<String>.decode(block: (String) -> T): ApiResult<T> = when (this) {
        is ApiResult.Success -> runCatching { ApiResult.Success(code, block(value)) }.getOrElse { ApiResult.Error(code, "INVALID_SERVER_RESPONSE") }
        is ApiResult.Error -> this
    }
}
