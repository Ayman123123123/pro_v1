package com.red.server.config

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/**
 * Fails fast when the process is bound to the wrong database host.
 * A late `MongoSocketOpenException: localhost:27017` looks like a
 * password/auth bug; it is almost always a Compose-vs-host mix-up.
 */
@Component
@Order(1)
class InfrastructureBindingGuard(
    @Value("\${spring.mongodb.uri:}") private val mongoUri: String,
    @Value("\${spring.datasource.url:}") private val postgresUrl: String,
    @Value("\${spring.data.redis.host:}") private val redisHost: String,
) : ApplicationRunner {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        val runtime = InfrastructureBinding.detectRuntime()
        val mongoHost = InfrastructureBinding.mongoHost(mongoUri)
        val postgresHost = InfrastructureBinding.postgresHost(postgresUrl)
        log.info(
            "INFRA binding: runtime={} mongodb.host={} postgres.host={} redis.host={}",
            runtime,
            mongoHost ?: "(unset)",
            postgresHost ?: "(unset)",
            redisHost.ifBlank { "(unset)" },
        )
        val error = InfrastructureBinding.validate(runtime, mongoHost, postgresHost) ?: return
        throw IllegalStateException(error)
    }
}
