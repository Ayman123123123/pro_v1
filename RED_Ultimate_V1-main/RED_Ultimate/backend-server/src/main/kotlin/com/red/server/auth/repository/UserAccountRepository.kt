package com.red.server.auth.repository

import com.red.server.auth.model.AccountRole
import com.red.server.auth.model.AccountStatus
import com.red.server.auth.model.UserAccount
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface UserAccountRepository : JpaRepository<UserAccount, UUID> {
    fun findByUsernameIgnoreCase(username: String): UserAccount?
    fun findByRedId(redId: String): UserAccount?
    fun existsByUsernameIgnoreCase(username: String): Boolean
    fun existsByRedId(redId: String): Boolean
    fun findAllByStatusOrderByCreatedAtAsc(status: AccountStatus): List<UserAccount>
    fun findAllByOrderByCreatedAtDesc(): List<UserAccount>

    /**
     * Database-side administration search. Filtering before pageable is essential:
     * filtering a single fetched page produces empty pages and incorrect totals.
     * Values are bound parameters, never concatenated SQL.
     */
    @Query(
        """
        SELECT u FROM UserAccount u
        WHERE (:status IS NULL OR u.status = :status)
          AND (:role IS NULL OR u.role = :role)
          AND (:search IS NULL OR
               LOWER(u.username) LIKE LOWER(CONCAT('%', :search, '%')) OR
               LOWER(u.displayName) LIKE LOWER(CONCAT('%', :search, '%')) OR
               LOWER(u.redId) LIKE LOWER(CONCAT('%', :search, '%')))
        """,
        countQuery = """
        SELECT COUNT(u) FROM UserAccount u
        WHERE (:status IS NULL OR u.status = :status)
          AND (:role IS NULL OR u.role = :role)
          AND (:search IS NULL OR
               LOWER(u.username) LIKE LOWER(CONCAT('%', :search, '%')) OR
               LOWER(u.displayName) LIKE LOWER(CONCAT('%', :search, '%')) OR
               LOWER(u.redId) LIKE LOWER(CONCAT('%', :search, '%')))
        """
    )
    fun searchForAdmin(
        @Param("status") status: AccountStatus?,
        @Param("role") role: AccountRole?,
        @Param("search") search: String?,
        pageable: Pageable
    ): Page<UserAccount>
}
