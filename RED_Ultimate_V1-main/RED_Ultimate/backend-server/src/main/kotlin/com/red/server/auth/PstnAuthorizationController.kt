package com.red.server.auth

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * PSTN/DINSTAR schema and gateway were deliberately removed (V52). The legacy
 * administration URL must fail explicitly, not claim to enable an entitlement
 * absent from UserAccount or silently write state that the call path cannot use.
 * A future optional gateway needs its own schema, tests and rollout plan.
 */
@RestController
@RequestMapping("/api/admin/users/pstn")
class PstnAuthorizationController {
    @PutMapping
    fun update(): ResponseEntity<Map<String, String>> = ResponseEntity.status(HttpStatus.GONE)
        .body(mapOf("error" to "PSTN_FEATURE_UNAVAILABLE"))
}
