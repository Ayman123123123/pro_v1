package com.red.server.auth.repository

import com.red.server.auth.model.AccountRole
import com.red.server.auth.model.AccountStatus
import com.red.server.auth.model.UserAccount
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface UserAccountRepository : JpaRepository<UserAccount, UUID>, JpaSpecificationExecutor<UserAccount> {
    fun findByUsernameIgnoreCase(username: String): UserAccount?
    fun findByRedId(redId: String): UserAccount?
    fun existsByUsernameIgnoreCase(username: String): Boolean
    fun existsByRedId(redId: String): Boolean
    fun countByStatus(status: AccountStatus): Long

    @Query("SELECT COUNT(u) FROM UserAccount u WHERE u.createdAt > :since")
    fun countCreatedAfter(@Param("since") since: Instant): Long

    /** JPQL path `createdAt` maps to `created_at`; derived Sort.by(\"createdAt\") does not. */
    @Query("SELECT u FROM UserAccount u WHERE u.status = :status ORDER BY u.createdAt ASC")
    fun findAllByStatusOrderByCreatedAtAsc(@Param("status") status: AccountStatus): List<UserAccount>

    @Query("SELECT u FROM UserAccount u ORDER BY u.createdAt DESC")
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
