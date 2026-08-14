package com.red.server

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.web.client.RestTemplate
import org.springframework.web.socket.config.annotation.EnableWebSocket

@SpringBootApplication
@EnableScheduling
@EnableWebSocket
class RedSovereignApplication {
    @Bean
    fun restTemplate() = RestTemplate()

    @Bean
    @Primary
    fun objectMapper(): ObjectMapper = jacksonObjectMapper()
}

fun main(args: Array<String>) {
    // 🛡️ Safety Guard: Force correct DB hosts if running in Docker
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
