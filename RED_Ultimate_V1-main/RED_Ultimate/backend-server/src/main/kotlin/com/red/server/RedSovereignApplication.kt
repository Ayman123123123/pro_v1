package com.red.server

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.web.client.RestTemplate
import org.springframework.web.socket.config.annotation.EnableWebSocket
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import java.util.concurrent.atomic.AtomicInteger

@SpringBootApplication
@EnableScheduling
@EnableAsync
@EnableWebSocket
class RedSovereignApplication {
    @Bean
    fun restTemplate() = RestTemplate()

    @Bean
    @Suppress("unused")
    fun pstnRetryScheduler(): ScheduledExecutorService {
        var threadNum = AtomicInteger(0)
        return Executors.newScheduledThreadPool(2) { r ->
            Thread(r, "pstn-retry-${threadNum.incrementAndGet()}").apply { isDaemon = true }
        }
    }

    @Bean
    fun taskScheduler(): TaskScheduler = ThreadPoolTaskScheduler().apply {
        poolSize = 4
        setThreadNamePrefix("sched-")
        initialize()
    }

    @Bean(name = ["adminSseScheduler"])
    @Suppress("unused")
    fun adminSseScheduler(): ScheduledExecutorService {
        var threadNum = AtomicInteger(0)
        return Executors.newScheduledThreadPool(2) { r ->
            Thread(r, "admin-sse-${threadNum.incrementAndGet()}").apply { isDaemon = true }
        }
    }

}

fun main(args: Array<String>) {
    // Safety Guard: Force correct DB hosts if running in Docker
    if (java.io.File("/.dockerenv").exists()) {
        val mongoPass = System.getenv("MONGO_PASSWORD")
        if (!mongoPass.isNullOrBlank()) {
            val mongoUri = "mongodb://red_user:$mongoPass@db-mongo:27017/red_sovereign?authSource=admin"
            System.setProperty("spring.mongodb.uri", mongoUri)
        }
        System.setProperty("spring.datasource.url", "jdbc:postgresql://db-postgres:5432/red_sovereign")
        System.setProperty("spring.data.redis.host", "cache-redis")
    }
    runApplication<RedSovereignApplication>(*args)
}
