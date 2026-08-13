package com.red.server.auth

import com.red.server.auth.repository.AdminUserSort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.data.domain.Sort

class AdminUserSortTest {
    @Test
    fun `maps camelCase api fields to postgres columns`() {
        assertEquals("created_at", AdminUserSort.columnFor("createdAt"))
        assertEquals("updated_at", AdminUserSort.columnFor("updatedAt"))
        assertEquals("full_name", AdminUserSort.columnFor("displayName"))
        assertEquals("red_id", AdminUserSort.columnFor("redId"))
        assertEquals("created_at", AdminUserSort.columnFor(null))
        assertEquals("created_at", AdminUserSort.columnFor("unknown"))
    }

    @Test
    fun `pageable sort uses physical column names not createdAt`() {
        val pageable = AdminUserSort.pageable(0, 20, "createdAt", Sort.Direction.DESC)
        val order = pageable.sort.first()
        assertEquals("created_at", order.property)
        assertEquals(Sort.Direction.DESC, order.direction)
        assertTrue(order.property.contains('_') || order.property == "username" || order.property == "status")
    }
}
