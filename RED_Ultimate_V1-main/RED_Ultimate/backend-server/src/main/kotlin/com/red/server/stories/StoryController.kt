package com.red.server.stories

import jakarta.validation.Valid
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/stories")
class StoryController(private val stories: StoryService) {
    @PostMapping fun create(@Valid @RequestBody request: CreateStoryRequest, auth: Authentication) = stories.create(UUID.fromString(auth.name), request)
    @GetMapping fun active(
        auth: Authentication,
        @org.springframework.web.bind.annotation.RequestParam(required = false) limit: Int?,
        @org.springframework.web.bind.annotation.RequestParam(required = false) cursor: String?
    ) = stories.active(UUID.fromString(auth.name), limit ?: 50, cursor)
    @PostMapping("/{id}/view") fun viewed(@PathVariable id: String, auth: Authentication) = stories.viewed(UUID.fromString(auth.name), id)
    @PostMapping("/{id}/react") fun react(@PathVariable id: String, @RequestBody request: StoryReactionRequest, auth: Authentication) = stories.react(UUID.fromString(auth.name), id, request)
    @GetMapping("/{id}/viewers") fun viewers(@PathVariable id: String, auth: Authentication) = stories.viewers(UUID.fromString(auth.name), id)
    @DeleteMapping("/{id}") fun delete(@PathVariable id: String, auth: Authentication) = stories.delete(UUID.fromString(auth.name), id)
    // LEGENDARY: هدف الرد المشفر (النص يُشفر عميلاً — الخادم يمنح الإذن فقط)
    @GetMapping("/{id}/reply-target") fun replyTarget(@PathVariable id: String, auth: Authentication) =
        stories.replyTarget(UUID.fromString(auth.name), id)
}
