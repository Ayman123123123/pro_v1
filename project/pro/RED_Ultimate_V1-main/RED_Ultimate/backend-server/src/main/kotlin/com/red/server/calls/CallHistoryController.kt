package com.red.server.calls

import com.red.server.auth.repository.UserAccountRepository
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/calls")
class CallHistoryController(private val history: CallHistoryService, private val users: UserAccountRepository) {
    @GetMapping("/history")
    fun history(@RequestParam(defaultValue = "50") limit: Int, auth: Authentication): List<CallHistoryItem> {
        val user = users.findById(UUID.fromString(auth.name)).orElseThrow { NoSuchElementException("User not found") }
        return history.history(user.redId, limit)
    }
}
