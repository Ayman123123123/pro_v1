package com.red.server.auth.repository

import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.JpaSort

/**
 * Admin user listing must sort on PostgreSQL column names.
 *
 * Spring Data appends [Sort] properties as SQL identifiers. Hibernate then
 * lowercases `createdAt` to `createdat`, which does not exist — the table
 * column is `created_at`. [JpaSort.unsafe] emits the physical name as-is.
 */
object AdminUserSort {
    private val columns = mapOf(
        "createdAt" to "created_at",
        "updatedAt" to "updated_at",
        "username" to "username",
        "displayName" to "full_name",
        "redId" to "red_id",
        "status" to "status",
        "role" to "role",
    )

    fun pageable(page: Int, size: Int, sortBy: String?, sortDir: Sort.Direction): Pageable {
        val column = columns[sortBy] ?: "created_at"
        return PageRequest.of(page, size, JpaSort.unsafe(sortDir, column))
    }

    fun columnFor(sortBy: String?): String = columns[sortBy] ?: "created_at"
}
