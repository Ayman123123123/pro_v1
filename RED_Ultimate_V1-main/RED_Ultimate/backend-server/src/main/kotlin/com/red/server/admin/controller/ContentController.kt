package com.red.server.admin.controller

import com.red.server.admin.model.*
import com.red.server.admin.repository.*
import com.red.server.admin.service.ContentService
import org.springframework.data.domain.PageRequest
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.util.UUID

/**
 * 📊 Content Controller - APIs المحتوى (Polls, Events, Stickers, Hashtags, Saved Messages, Highlights)
 */
@RestController
@RequestMapping("/api/admin/content")
class ContentController(
    private val service: ContentService,
    private val polls: PollRepository,
    private val events: EventRepository,
    private val hashtags: HashtagRepository,
    private val stickerPacks: StickerPackRepository
) {
    // ━━━━━━━━━━━━━━━━ Polls ━━━━━━━━━━━━━━━━
    @GetMapping("/polls")
    fun getPolls(
        @RequestParam(required = false) page: Int = 0,
        @RequestParam(required = false) size: Int = 20,
        @RequestParam(required = false) status: String? = null
    ): ResponseEntity<Map<String, Any>> {
        val pageable = PageRequest.of(page, size)
        val result = service.getPolls(status, pageable)
        return ResponseEntity.ok(mapOf(
            "content" to result.content,
            "page" to page, "size" to size,
            "totalElements" to result.totalElements,
            "totalPages" to result.totalPages
        ))
    }

    @GetMapping("/polls/active")
    fun getActivePolls(): ResponseEntity<List<Poll>> =
        ResponseEntity.ok(service.getActivePolls())

    @GetMapping("/polls/{pollId}")
    fun getPollDetail(@PathVariable pollId: String): ResponseEntity<Map<String, Any>> {
        val results = service.getPollResults(UUID.fromString(pollId))
        return if (results.isEmpty()) ResponseEntity.notFound().build()
        else ResponseEntity.ok(results)
    }

    @PostMapping("/polls")
    fun createPoll(
        @RequestBody body: Map<String, Any?>,
        authentication: Authentication
    ): ResponseEntity<Map<String, Any>> {
        val adminId = UUID.fromString(authentication.name)
        val question = body["question"] as String
        @Suppress("UNCHECKED_CAST")
        val options = (body["options"] as List<String>).filter { it.isNotBlank() }
        val poll = service.createPoll(
            creatorId = adminId,
            question = question,
            options = options,
            pollType = body["pollType"] as? String ?: "SINGLE_CHOICE",
            isAnonymous = body["isAnonymous"] as? Boolean ?: false,
            allowAddOptions = body["allowAddOptions"] as? Boolean ?: false,
            endsAt = (body["endsAt"] as? String)?.let { Instant.parse(it) }
        )
        return ResponseEntity.ok(mapOf("success" to true, "poll" to poll))
    }

    @PostMapping("/polls/{pollId}/close")
    fun closePoll(
        @PathVariable pollId: String,
        authentication: Authentication
    ): ResponseEntity<Map<String, Any>> {
        service.closePoll(UUID.fromString(pollId))
        return ResponseEntity.ok(mapOf("success" to true))
    }

    @DeleteMapping("/polls/{pollId}")
    fun deletePoll(
        @PathVariable pollId: String,
        authentication: Authentication
    ): ResponseEntity<Map<String, Any>> {
        val success = service.deletePoll(UUID.fromString(pollId))
        return ResponseEntity.ok(mapOf("success" to success))
    }

    @PostMapping("/polls/{pollId}/vote")
    fun votePoll(
        @PathVariable pollId: String,
        @RequestBody body: Map<String, Any?>,
        authentication: Authentication
    ): ResponseEntity<Map<String, Any>> {
        val userId = UUID.fromString(authentication.name)
        @Suppress("UNCHECKED_CAST")
        val optionIds = (body["optionIds"] as List<String>).map { UUID.fromString(it) }
        service.vote(UUID.fromString(pollId), userId, optionIds)
        return ResponseEntity.ok(mapOf("success" to true))
    }

    // ━━━━━━━━━━━━━━━━ Events ━━━━━━━━━━━━━━━━
    @GetMapping("/events")
    fun getEvents(
        @RequestParam(required = false) page: Int = 0,
        @RequestParam(required = false) size: Int = 20,
        @RequestParam(required = false) status: String? = null
    ): ResponseEntity<Map<String, Any>> {
        val pageable = PageRequest.of(page, size)
        val result = service.getEvents(status, pageable)
        return ResponseEntity.ok(mapOf(
            "content" to result.content,
            "page" to page, "size" to size,
            "totalElements" to result.totalElements,
            "totalPages" to result.totalPages
        ))
    }

    @GetMapping("/events/upcoming")
    fun getUpcomingEvents(): ResponseEntity<List<Event>> =
        ResponseEntity.ok(service.getUpcomingEvents())

    @GetMapping("/events/live")
    fun getLiveEvents(): ResponseEntity<List<Event>> =
        ResponseEntity.ok(service.getLiveEvents())

    @PostMapping("/events")
    fun createEvent(
        @RequestBody body: Map<String, Any?>,
        authentication: Authentication
    ): ResponseEntity<Map<String, Any>> {
        val adminId = UUID.fromString(authentication.name)
        val event = service.createEvent(
            creatorId = adminId,
            title = body["title"] as String,
            description = body["description"] as? String,
            locationName = body["locationName"] as? String,
            locationAddress = body["locationAddress"] as? String,
            startsAt = Instant.parse(body["startsAt"] as String),
            endsAt = (body["endsAt"] as? String)?.let { Instant.parse(it) },
            eventType = body["eventType"] as? String ?: "MEETING",
            visibility = body["visibility"] as? String ?: "PUBLIC",
            maxAttendees = (body["maxAttendees"] as? Number)?.toInt(),
            rsvpEnabled = body["rsvpEnabled"] as? Boolean ?: true
        )
        return ResponseEntity.ok(mapOf("success" to true, "event" to event))
    }

    @PostMapping("/events/{eventId}/rsvp")
    fun rsvpEvent(
        @PathVariable eventId: String,
        @RequestBody body: Map<String, String?>,
        authentication: Authentication
    ): ResponseEntity<Map<String, Any>> {
        val userId = UUID.fromString(authentication.name)
        val attendee = service.rsvp(
            eventId = UUID.fromString(eventId),
            userId = userId,
            status = body["status"] ?: "GOING"
        )
        return if (attendee != null) ResponseEntity.ok(mapOf("success" to true, "attendee" to attendee))
        else ResponseEntity.badRequest().body(mapOf("success" to false, "error" to "RSVP غير متاح"))
    }

    @PostMapping("/events/{eventId}/checkin")
    fun checkIn(
        @PathVariable eventId: String,
        authentication: Authentication
    ): ResponseEntity<Map<String, Any>> {
        val userId = UUID.fromString(authentication.name)
        val success = service.checkInAttendee(UUID.fromString(eventId), userId)
        return ResponseEntity.ok(mapOf("success" to success))
    }

    @PostMapping("/events/{eventId}/cancel")
    fun cancelEvent(
        @PathVariable eventId: String,
        @RequestBody body: Map<String, String>,
        authentication: Authentication
    ): ResponseEntity<Map<String, Any>> {
        val success = service.cancelEvent(
            eventId = UUID.fromString(eventId),
            reason = body["reason"] ?: "ADMIN_CANCELLED"
        )
        return ResponseEntity.ok(mapOf("success" to success))
    }

    @DeleteMapping("/events/{eventId}")
    fun deleteEvent(
        @PathVariable eventId: String,
        authentication: Authentication
    ): ResponseEntity<Map<String, Any>> {
        val success = service.deleteEvent(UUID.fromString(eventId))
        return ResponseEntity.ok(mapOf("success" to success))
    }

    // ━━━━━━━━━━━━━━━━ Hashtags ━━━━━━━━━━━━━━━━
    @GetMapping("/hashtags/trending")
    fun getTrendingHashtags(
        @RequestParam(required = false) limit: Int = 50
    ): ResponseEntity<List<Hashtag>> =
        ResponseEntity.ok(service.getTrendingHashtags(limit))

    @GetMapping("/hashtags/popular")
    fun getPopularHashtags(
        @RequestParam(required = false) limit: Int = 50
    ): ResponseEntity<List<Hashtag>> =
        ResponseEntity.ok(service.getPopularHashtags(limit))

    @GetMapping("/hashtags/search")
    fun searchHashtags(
        @RequestParam query: String,
        @RequestParam(required = false) page: Int = 0,
        @RequestParam(required = false) size: Int = 20
    ): ResponseEntity<Map<String, Any>> {
        val pageable = PageRequest.of(page, size)
        val result = service.searchHashtags(query, pageable)
        return ResponseEntity.ok(mapOf(
            "content" to result.content,
            "page" to page, "size" to size,
            "totalElements" to result.totalElements
        ))
    }

    @PostMapping("/hashtags/{hashtagId}/block")
    fun blockHashtag(
        @PathVariable hashtagId: String,
        @RequestBody body: Map<String, String>,
        authentication: Authentication
    ): ResponseEntity<Map<String, Any>> {
        val adminId = UUID.fromString(authentication.name)
        service.blockHashtag(
            hashtagId = UUID.fromString(hashtagId),
            reason = body["reason"] ?: "ADMIN_BLOCK",
            adminId = adminId
        )
        return ResponseEntity.ok(mapOf("success" to true))
    }

    @PostMapping("/hashtags/{hashtagId}/unblock")
    fun unblockHashtag(
        @PathVariable hashtagId: String,
        authentication: Authentication
    ): ResponseEntity<Map<String, Any>> {
        service.unblockHashtag(UUID.fromString(hashtagId))
        return ResponseEntity.ok(mapOf("success" to true))
    }

    // ━━━━━━━━━━━━━━━━ Sticker Packs ━━━━━━━━━━━━━━━━
    @GetMapping("/sticker-packs")
    fun getStickerPacks(
        @RequestParam(required = false) official: Boolean = false
    ): ResponseEntity<List<StickerPack>> {
        return ResponseEntity.ok(if (official) service.getOfficialStickerPacks() else service.getAllStickerPacks())
    }

    @PostMapping("/sticker-packs")
    fun createStickerPack(
        @RequestBody body: Map<String, Any?>,
        authentication: Authentication
    ): ResponseEntity<Map<String, Any>> {
        val adminId = UUID.fromString(authentication.name)
        val pack = service.createStickerPack(
            name = body["name"] as String,
            description = body["description"] as? String,
            coverMediaKey = body["coverMediaKey"] as String,
            creatorId = adminId,
            isOfficial = body["isOfficial"] as? Boolean ?: false,
            isFree = body["isFree"] as? Boolean ?: true,
            priceCents = (body["priceCents"] as? Number)?.toInt() ?: 0
        )
        return ResponseEntity.ok(mapOf("success" to true, "pack" to pack))
    }

    @PostMapping("/sticker-packs/{packId}/publish")
    fun publishStickerPack(
        @PathVariable packId: String,
        authentication: Authentication
    ): ResponseEntity<Map<String, Any>> {
        service.publishStickerPack(UUID.fromString(packId))
        return ResponseEntity.ok(mapOf("success" to true))
    }

    @DeleteMapping("/sticker-packs/{packId}")
    fun deleteStickerPack(
        @PathVariable packId: String,
        authentication: Authentication
    ): ResponseEntity<Map<String, Any>> {
        val success = service.deleteStickerPack(UUID.fromString(packId))
        return ResponseEntity.ok(mapOf("success" to success))
    }

    // ━━━━━━━━━━━━━━━━ Story Highlights ━━━━━━━━━━━━━━━━
    @GetMapping("/highlights/{userId}")
    fun getUserHighlights(@PathVariable userId: String): ResponseEntity<List<StoryHighlight>> =
        ResponseEntity.ok(service.getUserHighlights(UUID.fromString(userId)))

    @PostMapping("/highlights")
    fun createHighlight(
        @RequestBody body: Map<String, String>,
        authentication: Authentication
    ): ResponseEntity<Map<String, Any>> {
        val userId = UUID.fromString(authentication.name)
        val highlight = service.createHighlight(
            userId = userId,
            title = body["title"] ?: "Untitled",
            coverMediaKey = body["coverMediaKey"] ?: "",
            visibility = body["visibility"] ?: "EVERYONE"
        )
        return ResponseEntity.ok(mapOf("success" to true, "highlight" to highlight))
    }

    @DeleteMapping("/highlights/{highlightId}")
    fun deleteHighlight(
        @PathVariable highlightId: String,
        authentication: Authentication
    ): ResponseEntity<Map<String, Any>> {
        val success = service.deleteHighlight(UUID.fromString(highlightId))
        return ResponseEntity.ok(mapOf("success" to success))
    }

    // ━━━━━━━━━━━━━━━━ Saved Messages ━━━━━━━━━━━━━━━━
    @GetMapping("/saved-messages")
    fun getSavedMessages(
        @RequestParam(required = false) page: Int = 0,
        @RequestParam(required = false) size: Int = 20,
        @RequestParam(required = false) collection: String? = null,
        authentication: Authentication
    ): ResponseEntity<Map<String, Any>> {
        val userId = UUID.fromString(authentication.name)
        val pageable = PageRequest.of(page, size)
        val result = service.getSavedMessages(userId, collection, pageable)
        return ResponseEntity.ok(mapOf(
            "content" to result.content,
            "page" to page, "size" to size,
            "totalElements" to result.totalElements
        ))
    }

    @PostMapping("/saved-messages")
    fun saveMessage(
        @RequestBody body: Map<String, String?>,
        authentication: Authentication
    ): ResponseEntity<Map<String, Any>> {
        val userId = UUID.fromString(authentication.name)
        val messageId = UUID.fromString(body["messageId"] ?: return ResponseEntity.badRequest().body(mapOf("error" to "messageId required")))
        val saved = service.saveMessage(
            userId = userId,
            messageId = messageId,
            collection = body["collection"] ?: "DEFAULT",
            notes = body["notes"]
        )
        return if (saved != null) ResponseEntity.ok(mapOf("success" to true, "saved" to saved))
        else ResponseEntity.ok(mapOf("success" to false, "error" to "Already saved"))
    }

    @DeleteMapping("/saved-messages/{messageId}")
    fun unsaveMessage(
        @PathVariable messageId: String,
        authentication: Authentication
    ): ResponseEntity<Map<String, Any>> {
        val userId = UUID.fromString(authentication.name)
        val success = service.unsaveMessage(userId, UUID.fromString(messageId))
        return ResponseEntity.ok(mapOf("success" to success))
    }
}
