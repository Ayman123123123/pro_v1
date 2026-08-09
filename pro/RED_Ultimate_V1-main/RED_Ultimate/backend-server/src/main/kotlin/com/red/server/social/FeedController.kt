package com.red.server.social

import org.springframework.format.annotation.DateTimeFormat
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/feed")
class FeedController(private val feed: FeedService) {
    @GetMapping
    fun feed(@RequestParam(defaultValue = "ALL") scope: FeedScope,
             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) before: Instant?,
             @RequestParam(defaultValue = "20") limit: Int,
             auth: Authentication) = feed.feed(UUID.fromString(auth.name), scope, before, limit)

    @PostMapping("/posts")
    fun create(@RequestBody request: CreatePostRequest, auth: Authentication) = feed.create(UUID.fromString(auth.name), request)

    @GetMapping("/posts/{postId}/thread")
    fun thread(@PathVariable postId: String) = feed.thread(postId)

    @PostMapping("/posts/{postId}/reactions")
    fun react(@PathVariable postId: String, @RequestBody request: ReactionRequest, auth: Authentication) =
        feed.react(UUID.fromString(auth.name), postId, request)

    @PostMapping("/posts/{postId}/vote")
    fun vote(@PathVariable postId: String, @RequestBody request: PollVoteRequest, auth: Authentication) =
        feed.vote(UUID.fromString(auth.name), postId, request)

    @PostMapping("/following/{redId}")
    fun follow(@PathVariable redId: String, auth: Authentication) = feed.follow(UUID.fromString(auth.name), redId)

    @DeleteMapping("/following/{redId}")
    fun unfollow(@PathVariable redId: String, auth: Authentication) = feed.unfollow(UUID.fromString(auth.name), redId)

    @GetMapping("/following")
    fun following(auth: Authentication) = feed.following(UUID.fromString(auth.name))

    @DeleteMapping("/posts/{postId}")
    fun delete(@PathVariable postId: String, auth: Authentication) = feed.delete(UUID.fromString(auth.name), postId)
}
