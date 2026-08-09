package com.red.server.auth.repository

import com.red.server.auth.model.AccountStatus
import com.red.server.auth.model.UserAccount
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface UserAccountRepository : JpaRepository<UserAccount, UUID> {
    fun findByUsernameIgnoreCase(username: String): UserAccount?
    fun findByRedId(redId: String): UserAccount?
    fun existsByUsernameIgnoreCase(username: String): Boolean
    fun existsByRedId(redId: String): Boolean
    fun findAllByStatusOrderByCreatedAtAsc(status: AccountStatus): List<UserAccount>
    fun findAllByOrderByCreatedAtDesc(): List<UserAccount>
}
