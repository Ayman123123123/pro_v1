package com.red.server.auth

import com.red.server.auth.model.AccountRole
import com.red.server.auth.model.AccountStatus
import com.red.server.auth.model.UserAccount
import com.red.server.auth.repository.UserAccountRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component

@Component
class AdminBootstrap(
    private val users: UserAccountRepository,
    private val passwordEncoder: PasswordEncoder,
    private val redIdGenerator: RedIdGenerator,
    @Value("\${red.admin.username:}") private val username: String,
    @Value("\${red.admin.password:}") private val password: String
) : ApplicationRunner {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        if (username.isBlank() || password.isBlank()) {
            log.warn("RED bootstrap admin is disabled. Configure RED_ADMIN_USERNAME and RED_ADMIN_PASSWORD before first use.")
            return
        }
        if (users.findByUsernameIgnoreCase(username) != null) return
        require(password.length >= 14) { "RED_ADMIN_PASSWORD must contain at least 14 characters" }

        users.save(
            UserAccount(
                redId = redIdGenerator.next(),
                username = username.trim().lowercase(),
                passwordHash = requireNotNull(passwordEncoder.encode(password)) { "PasswordEncoder returned null" },
                displayName = "YOUNES Administrator",
                status = AccountStatus.APPROVED,
                role = AccountRole.ADMIN
            )
        )
        log.info("Created the initial RED administrator account")
    }
}
