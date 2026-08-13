package com.red.server.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InfrastructureBindingTest {
    @Test
    fun `parses mongo and postgres hosts`() {
        assertEquals(
            "db-mongo",
            InfrastructureBinding.mongoHost("mongodb://red_user:secret@db-mongo:27017/red_sovereign?authSource=admin"),
        )
        assertEquals(
            "localhost",
            InfrastructureBinding.mongoHost("mongodb://localhost:27017/red_sovereign"),
        )
        assertEquals(
            "db-postgres",
            InfrastructureBinding.postgresHost("jdbc:postgresql://db-postgres:5432/red_sovereign"),
        )
        assertNull(InfrastructureBinding.mongoHost(" "))
    }

    @Test
    fun `docker runtime rejects localhost databases`() {
        val mongo = InfrastructureBinding.validate(
            InfrastructureBinding.Runtime.DOCKER,
            "localhost",
            "db-postgres",
        )
        assertNotNull(mongo)
        assertTrue(mongo!!.contains("MONGODB_LOCALHOST_INSIDE_CONTAINER"))

        val postgres = InfrastructureBinding.validate(
            InfrastructureBinding.Runtime.DOCKER,
            "db-mongo",
            "127.0.0.1",
        )
        assertNotNull(postgres)
        assertTrue(postgres!!.contains("POSTGRES_LOCALHOST_INSIDE_CONTAINER"))
    }

    @Test
    fun `host runtime rejects compose DNS names`() {
        val mongo = InfrastructureBinding.validate(
            InfrastructureBinding.Runtime.HOST,
            "db-mongo",
            "127.0.0.1",
        )
        assertNotNull(mongo)
        assertTrue(mongo!!.contains("MONGODB_COMPOSE_HOSTNAME_ON_HOST"))
    }

    @Test
    fun `matching bindings are accepted`() {
        assertNull(
            InfrastructureBinding.validate(
                InfrastructureBinding.Runtime.DOCKER,
                "db-mongo",
                "db-postgres",
            ),
        )
        assertNull(
            InfrastructureBinding.validate(
                InfrastructureBinding.Runtime.HOST,
                "127.0.0.1",
                "127.0.0.1",
            ),
        )
    }

    @Test
    fun `YOUNES_RUNTIME overrides dockerenv detection`() {
        assertEquals(
            InfrastructureBinding.Runtime.DOCKER,
            InfrastructureBinding.detectRuntime(mapOf("YOUNES_RUNTIME" to "docker")),
        )
        assertEquals(
            InfrastructureBinding.Runtime.HOST,
            InfrastructureBinding.detectRuntime(mapOf("YOUNES_RUNTIME" to "host")),
        )
    }
}
