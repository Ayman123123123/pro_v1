package com.red.server.config

import org.flywaydb.core.Flyway
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/**
 * Prints a grep-friendly FLYWAY line after migrations.
 *
 * Flyway 10 logs under `org.flywaydb`, so `docker logs | findstr flyway`
 * was empty even when migrations succeeded. This reporter always emits
 * `FLYWAY` in the message so operators can confirm the applied version.
 */
@Component
@Order(0)
class FlywayStartupReporter(
    private val flyway: ObjectProvider<Flyway>
) : ApplicationRunner {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        val instance = flyway.ifAvailable ?: run {
            log.warn("FLYWAY bean is not available — migrations were not run")
            return
        }
        val info = instance.info()
        val current = info.current()?.version?.toString() ?: "none"
        val applied = info.applied().size
        val pending = info.pending()
        log.info("FLYWAY status: current={} applied={} pending={}", current, applied, pending.size)
        if (pending.isNotEmpty()) {
            log.warn("FLYWAY pending scripts: {}", pending.joinToString { it.script })
        }
    }
}
