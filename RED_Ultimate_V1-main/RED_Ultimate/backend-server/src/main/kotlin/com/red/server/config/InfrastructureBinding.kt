package com.red.server.config

import java.io.File
import java.net.URI

/**
 * Prevents the two classic “wrong host” crashes on this stack:
 *
 * 1. Backend inside Compose talking to `localhost:27017` (the container
 *    itself) instead of `db-mongo`.
 * 2. Backend on the Windows host talking to `db-mongo` (Docker DNS) which
 *    does not exist outside `red-net`.
 */
object InfrastructureBinding {
    enum class Runtime { DOCKER, HOST }

    fun detectRuntime(
        env: Map<String, String> = System.getenv(),
        dockerEnvFile: File = File("/.dockerenv"),
    ): Runtime {
        val forced = env["YOUNES_RUNTIME"]?.trim()?.lowercase()
        return when (forced) {
            "docker" -> Runtime.DOCKER
            "host" -> Runtime.HOST
            else -> if (dockerEnvFile.exists()) Runtime.DOCKER else Runtime.HOST
        }
    }

    fun mongoHost(uri: String?): String? = uri?.trim()?.takeIf { it.isNotEmpty() }?.let { raw ->
        runCatching { URI(raw).host?.takeIf { it.isNotBlank() } }.getOrNull()
    }

    fun postgresHost(jdbcUrl: String?): String? {
        val raw = jdbcUrl?.trim().orEmpty()
        if (raw.isEmpty()) return null
        val match = Regex("^jdbc:postgresql://([^/:]+)").find(raw)
        return match?.groupValues?.get(1)
    }

    fun isLoopback(host: String?): Boolean {
        val h = host?.trim()?.lowercase().orEmpty()
        return h == "localhost" || h == "127.0.0.1" || h == "::1" || h == "0.0.0.0"
    }

    fun validate(runtime: Runtime, mongoHost: String?, postgresHost: String?): String? {
        if (runtime == Runtime.DOCKER) {
            if (isLoopback(mongoHost)) {
                return "MONGODB_LOCALHOST_INSIDE_CONTAINER: use db-mongo:27017 (SPRING_DATA_MONGODB_URI from docker-compose). localhost inside the JVM is the backend container, not Mongo."
            }
            if (isLoopback(postgresHost)) {
                return "POSTGRES_LOCALHOST_INSIDE_CONTAINER: use db-postgres:5432. Do not point the Compose backend at 127.0.0.1."
            }
        }
        if (runtime == Runtime.HOST) {
            if (mongoHost == "db-mongo") {
                return "MONGODB_COMPOSE_HOSTNAME_ON_HOST: db-mongo only resolves on red-net. Either run the backend in Docker, or publish Mongo with docker-compose.host-debug.yml and use profile `host`."
            }
            if (postgresHost == "db-postgres") {
                return "POSTGRES_COMPOSE_HOSTNAME_ON_HOST: db-postgres only resolves on red-net. Use Compose or the host-debug overlay."
            }
        }
        return null
    }
}
