package com.red.server.config

import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.annotation.Order
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component
import javax.sql.DataSource

@Component
@Order(0)
class FlywayStartupReporter(
    private val dataSource: DataSource,
    private val environment: Environment
) : ApplicationRunner {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        if (!environment.getProperty("spring.flyway.enabled", Boolean::class.java, true)) {
            log.warn("FLYWAY disabled — skipping")
            return
        }
        log.info("FLYWAY checking migration status via JDBC")
        try {
            dataSource.connection.use { conn ->
                val rs = conn.prepareStatement(
                    "SELECT EXISTS(SELECT 1 FROM information_schema.tables WHERE table_name = 'flyway_schema_history')"
                ).executeQuery()
                rs.next()
                if (rs.getBoolean(1)) {
                    val rs2 = conn.prepareStatement(
                        "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 5"
                    ).executeQuery()
                    while (rs2.next()) {
                        log.info("FLYWAY: v{} {} success={}", rs2.getString("version"), rs2.getString("description"), rs2.getBoolean("success"))
                    }
                } else {
                    log.warn("FLYWAY: flyway_schema_history NOT found — migrations were not applied")
                }
            }
        } catch (e: Exception) {
            log.warn("FLYWAY status check failed: {}", e.message)
        }
    }
}
