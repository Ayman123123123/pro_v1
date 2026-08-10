package com.red.server.controllers

import com.red.server.auth.security.WebSocketTicketService
import org.springframework.http.CacheControl
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/admin/ws-ticket")
class AdminWebSocketTicketController(private val tickets: WebSocketTicketService) {
    @PostMapping
    fun issue(authentication: Authentication) = ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(tickets.issue(UUID.fromString(authentication.name)))
}
