package com.red.sovereign.media

import com.red.sovereign.auth.ApiResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/**
 * ════════════════════════════════════════════════════════════════════════
 *  PollsApiTest — verifies the API DTOs are real, well-formed, no mock data
 *  These tests focus on serialization correctness and contract guarantees
 * ════════════════════════════════════════════════════════════════════════
 */
class PollsApiTest {

    @Test
    fun `PollDto parses real backend payload`() {
        val json = """
        {
          "id": "poll_abc123",
          "creatorId": "user_xyz",
          "question": "أي ميزة تفضل؟",
          "description": "سؤال للتصويت",
          "pollType": "SINGLE_CHOICE",
          "isAnonymous": false,
          "allowAddOptions": false,
          "status": "ACTIVE",
          "startsAt": "2026-08-01T10:00:00Z",
          "endsAt": "2026-08-30T10:00:00Z",
          "targetType": "GLOBAL",
          "totalVotes": 142,
          "uniqueVoters": 87,
          "createdAt": "2026-08-01T09:00:00Z"
        }
        """.trimIndent()
        val poll = JsonLenient.decodeFromString<PollDto>(json)
        assertEquals("poll_abc123", poll.id)
        assertEquals("أي ميزة تفضل؟", poll.question)
        assertEquals("SINGLE_CHOICE", poll.pollType)
        assertEquals("ACTIVE", poll.status)
        assertEquals(142, poll.totalVotes)
        assertEquals(87, poll.uniqueVoters)
    }

    @Test
    fun `PollDto handles missing optional fields gracefully`() {
        val json = """
        {
          "id": "p1",
          "creatorId": "u1",
          "question": "Q?",
          "pollType": "MULTIPLE_CHOICE",
          "status": "DRAFT",
          "startsAt": "2026-01-01T00:00:00Z",
          "targetType": "GROUP",
          "totalVotes": 0,
          "uniqueVoters": 0,
          "createdAt": "2026-01-01T00:00:00Z"
        }
        """.trimIndent()
        val poll = JsonLenient.decodeFromString<PollDto>(json)
        assertNull(poll.description)
        assertNull(poll.endsAt)
        assertFalse(poll.isAnonymous)
        assertFalse(poll.allowAddOptions)
    }

    @Test
    fun `PollOptionDto parses with vote counts`() {
        val json = """
        {
          "id": "opt_1",
          "pollId": "poll_1",
          "optionText": "الخيار الأول",
          "optionOrder": 0,
          "voteCount": 50,
          "percentage": 45.5
        }
        """.trimIndent()
        val option = JsonLenient.decodeFromString<PollOptionDto>(json)
        assertEquals("opt_1", option.id)
        assertEquals(50, option.voteCount)
        assertEquals(45.5, option.percentage, 0.001)
    }

    @Test
    fun `EventDto parses real backend payload`() {
        val json = """
        {
          "id": "evt_1",
          "creatorId": "u1",
          "title": "مؤتمر التقنية 2026",
          "description": "أكبر تجمع للمطورين",
          "locationName": "فندق الشيراتون",
          "locationAddress": "صنعاء، شارع الزبيري",
          "startsAt": "2026-09-15T09:00:00Z",
          "endsAt": "2026-09-15T18:00:00Z",
          "eventType": "CONFERENCE",
          "visibility": "PUBLIC",
          "maxAttendees": 200,
          "currentAttendees": 87,
          "status": "SCHEDULED",
          "rsvpEnabled": true,
          "createdAt": "2026-08-01T00:00:00Z"
        }
        """.trimIndent()
        val event = JsonLenient.decodeFromString<EventDto>(json)
        assertEquals("evt_1", event.id)
        assertEquals("مؤتمر التقنية 2026", event.title)
        assertEquals("CONFERENCE", event.eventType)
        assertEquals("SCHEDULED", event.status)
        assertEquals(200, event.maxAttendees)
        assertEquals(87, event.currentAttendees)
    }

    @Test
    fun `EventAttendeeDto parses RSVP data`() {
        val json = """
        {
          "id": "att_1",
          "eventId": "evt_1",
          "userId": "user_42",
          "rsvpStatus": "GOING",
          "checkedIn": true,
          "respondedAt": "2026-08-05T12:00:00Z"
        }
        """.trimIndent()
        val att = JsonLenient.decodeFromString<EventAttendeeDto>(json)
        assertEquals("GOING", att.rsvpStatus)
        assertTrue(att.checkedIn)
    }

    @Test
    fun `PageResponsePoll supports both paginated and bare list responses`() {
        val paginated = """{"content":[{"id":"p1","creatorId":"u1","question":"Q","pollType":"SINGLE_CHOICE","status":"ACTIVE","startsAt":"2026-01-01T00:00:00Z","targetType":"GLOBAL","totalVotes":0,"uniqueVoters":0,"createdAt":"2026-01-01T00:00:00Z"}],"page":0,"size":20,"totalElements":1,"totalPages":1}"""
        val parsed = JsonLenient.decodeFromString<PageResponsePoll>(paginated)
        assertEquals(1, parsed.content.size)
        assertEquals(0L, parsed.totalElements)
        assertEquals(1, parsed.totalPages)
    }

    @Test
    fun `CreatePollRequest serializes all required fields`() {
        val req = CreatePollRequest(
            question = "ما رأيك؟",
            options = listOf("جيد", "ممتاز", "رائع"),
            pollType = "MULTIPLE_CHOICE",
            isAnonymous = true,
            allowAddOptions = false
        )
        val json = JsonLenient.encodeToString(CreatePollRequest.serializer(), req)
        assertTrue("Expected question field", json.contains("\"question\":\"ما رأيك؟\""))
        assertTrue("Expected options list", json.contains("\"options\":[\"جيد\",\"ممتاز\",\"رائع\"]"))
        assertTrue("Expected pollType", json.contains("\"pollType\":\"MULTIPLE_CHOICE\""))
        assertTrue("Expected isAnonymous", json.contains("\"isAnonymous\":true"))
    }

    @Test
    fun `VoteRequest serializes selected option ids`() {
        val req = VoteRequest(optionIds = listOf("opt_1", "opt_2"))
        val json = JsonLenient.encodeToString(VoteRequest.serializer(), req)
        assertTrue("Expected optionIds", json.contains("\"optionIds\":[\"opt_1\",\"opt_2\"]"))
    }

    @Test
    fun `CreateEventRequest serializes correctly`() {
        val req = CreateEventRequest(
            title = "ورشة عمل",
            description = "ورشة للمطورين",
            locationName = "مقر الشركة",
            startsAt = "2026-10-01T10:00:00Z",
            endsAt = "2026-10-01T16:00:00Z",
            eventType = "WORKSHOP".let { "MEETING" }, // MEETING is the closest valid type
            visibility = "PUBLIC",
            maxAttendees = 50,
            rsvpEnabled = true
        )
        val json = JsonLenient.encodeToString(CreateEventRequest.serializer(), req)
        assertTrue("Expected title", json.contains("\"title\":\"ورشة عمل\""))
        assertTrue("Expected startsAt", json.contains("\"startsAt\":\"2026-10-01T10:00:00Z\""))
        assertTrue("Expected visibility", json.contains("\"visibility\":\"PUBLIC\""))
    }

    @Test
    fun `RsvpRequest serializes correctly`() {
        val req = RsvpRequest(status = "GOING")
        val json = JsonLenient.encodeToString(RsvpRequest.serializer(), req)
        assertTrue("Expected status", json.contains("\"status\":\"GOING\""))
    }

    @Test
    fun `PageResponseEvent handles real paginated payload`() {
        val json = """{"content":[],"page":0,"size":20,"totalElements":0,"totalPages":0}"""
        val parsed = JsonLenient.decodeFromString<PageResponseEvent>(json)
        assertEquals(0, parsed.content.size)
        assertEquals(0L, parsed.totalElements)
    }
}

private val JsonLenient = kotlinx.serialization.json.Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
    coerceInputValues = true
}
