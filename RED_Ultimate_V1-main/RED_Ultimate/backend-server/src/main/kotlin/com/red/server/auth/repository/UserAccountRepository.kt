package com.red.server.auth.repository

import com.red.server.auth.model.AccountRole
import com.red.server.auth.model.AccountStatus
import com.red.server.auth.model.UserAccount
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import java.time.Instant
import java.util.UUID

interface UserAccountRepository : JpaRepository<UserAccount, UUID>, JpaSpecificationExecutor<UserAccount> {
    fun findByUsernameIgnoreCase(username: String): UserAccount?
    fun findByRedId(redId: String): UserAccount?
    fun existsByUsernameIgnoreCase(username: String): Boolean
    fun existsByRedId(redId: String): Boolean
    fun findAllByStatusOrderByCreatedAtAsc(status: AccountStatus): List<UserAccount>
    fun countByStatus(status: AccountStatus): Long
    fun countByCreatedAtAfter(createdAt: Instant): Long
    fun findAllByOrderByCreatedAtDesc(): List<UserAccount>
}

/**
 * Database-side administration search. Filtering before pageable is essential:
 * filtering a single fetched page produces empty pages and incorrect totals.
 * Predicates are assembled in [UserAccountSpecs] so unused filters never
 * become untyped SQL NULLs (the Hibernate + Postgres `lower(bytea)` crash).
 */
fun UserAccountRepository.searchForAdmin(
    status: AccountStatus?,
    role: AccountRole?,
    search: String?,
    pageable: Pageable,
): Page<UserAccount> = findAll(UserAccountSpecs.adminSearch(status, role, search), pageable)
