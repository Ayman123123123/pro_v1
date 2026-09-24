package com.red.server.calls.v1

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * The old /api/v1/calls implementation invoked nonexistent signaling methods
 * (including answer, end and recording), so it could not compile or deliver a
 * call. No active Android/admin client calls this route. Return an explicit 410
 * rather than resurrecting fabricated delivery semantics or accepting requests
 * that claim success without creating a real call. Historical code is in Git.
 * The similarly incomplete v1 messages/media facades are retired separately;
 * active /api/messages and /api/media routes remain available.
 */
@RestController
@RequestMapping("/api/v1/calls")
class CallsV1Controller {
    @RequestMapping(path = ["", "/", "/{*path}"])
    fun gone(): ResponseEntity<Map<String, String>> = ResponseEntity.status(HttpStatus.GONE).body(
        mapOf("error" to "GONE", "message" to "Legacy calls v1 is unavailable; update the client to use authenticated call signaling")
    )
}
