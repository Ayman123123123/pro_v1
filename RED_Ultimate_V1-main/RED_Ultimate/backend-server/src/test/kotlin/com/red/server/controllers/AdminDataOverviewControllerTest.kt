package com.red.server.controllers

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.jdbc.core.JdbcTemplate

class AdminDataOverviewControllerTest {
    private val postgres: JdbcTemplate = mock()
    private val mongo: MongoTemplate = mock()
    private val redis: StringRedisTemplate = mock()
    private val controller = AdminDataOverviewController(postgres, mongo, redis)

    @Test
    fun `postgres outage is explicit and never reported as zero`() {
        whenever(postgres.queryForObject(any<String>(), eq(Long::class.java)))
            .thenThrow(DataAccessResourceFailureException("connection refused"))

        val overview = controller.overview()

        @Suppress("UNCHECKED_CAST")
        val users = overview.getValue("users") as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val sources = overview.getValue("dataSources") as Map<String, Map<String, Any?>>
        val postgresql = sources.getValue("postgresql")

        assertNull(users["total"])
        assertEquals(false, postgresql["available"])
        assertEquals("UNAVAILABLE", postgresql["error"])
        assertFalse((postgresql["observedAt"] as String).isBlank())
    }
}
