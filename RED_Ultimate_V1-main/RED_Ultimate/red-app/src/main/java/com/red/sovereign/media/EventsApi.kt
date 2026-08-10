package com.red.sovereign.media

import com.red.sovereign.auth.ApiResult
import com.red.sovereign.auth.AuthorizedApiClient
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * ════════════════════════════════════════════════════════════════════════
 *  RED Sovereign — Events API
 *  - Talks to /api/admin/content/events on the backend
 *  - All payloads are real, from the events / event_attendees tables (V20)
 * ════════════════════════════════════════════════════════════════════════
 */

@Serializable
data class EventDto(
    val id: String,
    val creatorId: String,
    val title: String,
    val description: String? = null,
    val locationName: String? = null,
    val locationAddress: String? = null,
    val startsAt: String,
    val endsAt: String? = null,
    val eventType: String, // MEETING | CONFERENCE | WEBINAR | SOCIAL | CELEBRATION | OTHER
    val visibility: String, // PUBLIC | PRIVATE | INVITATION_ONLY
    val maxAttendees: Int? = null,
    val currentAttendees: Int = 0,
    val status: String, // DRAFT | SCHEDULED | LIVE | ENDED | CANCELLED
    val rsvpEnabled: Boolean = true,
    val coverImageKey: String? = null,
    val createdAt: String
)

@Serializable
data class EventAttendeeDto(
    val id: String,
    val eventId: String,
    val userId: String,
    val rsvpStatus: String, // GOING | MAYBE | NOT_GOING
    val checkedIn: Boolean = false,
    val respondedAt: String
)

@Serializable
data class EventDetailDto(
    val event: EventDto,
    val attendees: List<EventAttendeeDto> = emptyList(),
    val myRsvp: String? = null
)

@Serializable
data class CreateEventRequest(
    val title: String,
    val description: String? = null,
    val locationName: String? = null,
    val startsAt: String,
    val endsAt: String? = null,
    val eventType: String = "MEETING",
    val visibility: String = "PUBLIC",
    val maxAttendees: Int? = null,
    val rsvpEnabled: Boolean = true
)

@Serializable
data class RsvpRequest(
    val status: String  // GOING | MAYBE | NOT_GOING
)

@Serializable
data class PageResponseEvent(
    val content: List<EventDto> = emptyList(),
    val page: Int = 0,
    val size: Int = 0,
    val totalElements: Long = 0,
    val totalPages: Int = 0
)

class EventsApi(private val client: AuthorizedApiClient) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }

    suspend fun list(page: Int = 0, size: Int = 20, status: String? = null): ApiResult<PageResponseEvent> {
        val qs = buildString {
            append("?page=").append(page).append("&size=").append(size)
            if (status != null) append("&status=").append(status)
        }
        val raw = when (val r = client.request("GET", "/api/admin/content/events$qs")) {
            is ApiResult.Success -> r.value
            is ApiResult.Error -> return r
        }
        return try {
            val parsed = json.decodeFromString<PageResponseEvent>(raw)
            ApiResult.Success(200, parsed)
        } catch (_: Exception) {
            try {
                val list = json.decodeFromString<List<EventDto>>(raw)
                ApiResult.Success(200, PageResponseEvent(content = list, totalElements = list.size.toLong()))
            } catch (e: Exception) {
                ApiResult.Error(500, e.message)
            }
        }
    }

    suspend fun upcoming(): ApiResult<List<EventDto>> = parseList(
        client.request("GET", "/api/admin/content/events/upcoming")
    )

    suspend fun live(): ApiResult<List<EventDto>> = parseList(
        client.request("GET", "/api/admin/content/events/live")
    )

    suspend fun detail(eventId: String): ApiResult<EventDetailDto> {
        val raw = when (val r = client.request("GET", "/api/admin/content/events/$eventId")) {
            is ApiResult.Success -> r.value
            is ApiResult.Error -> return r
        }
        return try {
            ApiResult.Success(200, json.decodeFromString<EventDetailDto>(raw))
        } catch (e: Exception) {
            ApiResult.Error(500, e.message)
        }
    }

    suspend fun create(req: CreateEventRequest): ApiResult<EventDto> {
        val body = json.encodeToString(req).toRequestBody()
        val raw = when (val r = client.requestBody("POST", "/api/admin/content/events", body)) {
            is ApiResult.Success -> r.value
            is ApiResult.Error -> return r
        }
        return try {
            ApiResult.Success(200, json.decodeFromString<EventDto>(raw))
        } catch (e: Exception) {
            ApiResult.Error(500, e.message)
        }
    }

    suspend fun rsvp(eventId: String, status: String): ApiResult<String> {
        val body = json.encodeToString(RsvpRequest(status)).toRequestBody()
        return client.requestBody("POST", "/api/admin/content/events/$eventId/rsvp", body)
            .let { r -> r as ApiResult<String> }
    }

    suspend fun checkin(eventId: String): ApiResult<String> =
        client.request("POST", "/api/admin/content/events/$eventId/checkin")

    suspend fun cancel(eventId: String, reason: String): ApiResult<String> {
        val body = json.encodeToString(mapOf("reason" to reason)).toRequestBody()
        return client.requestBody("POST", "/api/admin/content/events/$eventId/cancel", body)
            .let { r -> r as ApiResult<String> }
    }

    suspend fun delete(eventId: String): ApiResult<String> =
        client.request("DELETE", "/api/admin/content/events/$eventId")

    private fun parseList(r: ApiResult<String>): ApiResult<List<EventDto>> = when (r) {
        is ApiResult.Success -> try {
            ApiResult.Success(200, json.decodeFromString<List<EventDto>>(r.value))
        } catch (e: Exception) {
            ApiResult.Error(500, e.message)
        }
        is ApiResult.Error -> r
    }
}
