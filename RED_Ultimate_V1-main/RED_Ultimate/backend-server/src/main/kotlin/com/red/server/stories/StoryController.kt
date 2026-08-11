package com.red.server.stories

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
    @PostMapping fun create(@RequestBody request: CreateStoryRequest, auth: Authentication) = stories.create(UUID.fromString(auth.name), request)
    @GetMapping fun active(auth: Authentication) = stories.active(UUID.fromString(auth.name))
    @PostMapping("/{id}/view") fun viewed(@PathVariable id: String, auth: Authentication) = stories.viewed(UUID.fromString(auth.name), id)
    @PostMapping("/{id}/react") fun react(@PathVariable id: String, @RequestBody request: StoryReactionRequest, auth: Authentication) = stories.react(UUID.fromString(auth.name), id, request)
    @DeleteMapping("/{id}") fun delete(@PathVariable id: String, auth: Authentication) = stories.delete(UUID.fromString(auth.name), id)
}
