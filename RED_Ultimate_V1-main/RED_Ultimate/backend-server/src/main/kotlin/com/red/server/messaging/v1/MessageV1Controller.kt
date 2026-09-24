package com.red.server.messaging.v1

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * The former v1 message facade called methods that do not exist on the active
 * services (sendEncryptedEnvelope, syncMessages, etc.). No shipped client uses
 * /api/v1/messages. Silently adapting its payload to a different encryption
 * protocol would be unsafe; refuse all legacy operations explicitly instead.
 * The supported messaging endpoints are under /api/messages.
 */
@RestController
@RequestMapping("/api/v1/messages")
class MessageV1Controller {
    @RequestMapping(path = ["", "/", "/{*path}"])
    fun gone(): ResponseEntity<Map<String, String>> = ResponseEntity.status(HttpStatus.GONE).body(
        mapOf("error" to "GONE", "message" to "Legacy messages v1 is unavailable; use /api/messages")
    )
}
