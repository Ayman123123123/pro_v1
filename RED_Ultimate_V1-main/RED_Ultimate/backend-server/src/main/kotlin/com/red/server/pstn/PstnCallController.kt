package com.red.server.pstn

import com.red.server.calls.CallHistoryController
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/pstn")
class PstnCallController(private val calls: PstnCallService) {
    @PostMapping("/calls")
    fun dial(@RequestBody request: PstnCallRequest, authentication: Authentication): ResponseEntity<PstnCallResponse> {
        val result = calls.dial(UUID.fromString(authentication.name), request.number)
        return ResponseEntity.ok(result)
    }
}

data class PstnCallRequest(val number: String)
