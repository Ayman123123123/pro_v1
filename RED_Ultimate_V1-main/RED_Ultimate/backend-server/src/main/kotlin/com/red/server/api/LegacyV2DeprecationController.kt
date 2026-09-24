package com.red.server.api

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * The two /v2 facades had conflicting call mappings, trusted a caller-supplied
 * X-RED-ID, invented delivery without sending it, and stored groups only in
 * process memory. No product client calls them; make old integrations fail
 * explicitly rather than create phantom calls/groups or silently hit a catch-all.
 * The supported APIs are /api/calls (signaling), /api/conference and /api/groups.
 */
@RestController
class LegacyV2DeprecationController {
    @RequestMapping(path = ["/api/calls/v2", "/api/calls/v2/**"])
    fun callsGone(): ResponseEntity<Map<String, String>> = ResponseEntity.status(HttpStatus.GONE)
        .body(mapOf("error" to "UNSUPPORTED_CALLS_V2", "use" to "/api/calls"))

    @RequestMapping(path = ["/api/groups/v2", "/api/groups/v2/**"])
    fun groupsGone(): ResponseEntity<Map<String, String>> = ResponseEntity.status(HttpStatus.GONE)
        .body(mapOf("error" to "UNSUPPORTED_GROUPS_V2", "use" to "/api/groups"))
}
