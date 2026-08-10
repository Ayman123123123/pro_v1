package com.red.server.auth

import com.red.server.auth.model.AccountStatus
import com.red.server.auth.repository.UserAccountRepository
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/directory")
class PublicDirectoryController(private val users: UserAccountRepository) {
    @GetMapping("/search")
    fun search(@RequestParam query: String, authentication: Authentication): List<PublicRedProfile> {
        val term = query.trim()
        require(term.length in 3..32) { "Search query must contain 3-32 characters" }
        val caller = UUID.fromString(authentication.name)
        val matches = if (term.startsWith("RED-", ignoreCase = true)) {
            listOfNotNull(users.findByRedId(term.uppercase())).filter { it.status == AccountStatus.APPROVED }
        } else {
            listOfNotNull(users.findByUsernameIgnoreCase(term)).filter { it.status == AccountStatus.APPROVED }
        }
        return matches.filter { it.id != caller }.map { PublicRedProfile(it.redId, it.username, it.displayName) }
    }
}

data class PublicRedProfile(val redId: String, val username: String, val displayName: String)
