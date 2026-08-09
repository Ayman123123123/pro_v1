package com.red.server.pstn

import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/pstn")
class PstnCallController(private val calls: PstnCallService) {
    @PostMapping("/calls")
    fun dial(@RequestBody request: PstnCallRequest, authentication: Authentication) =
        calls.dial(UUID.fromString(authentication.name), request.number)
}

data class PstnCallRequest(val number: String)
