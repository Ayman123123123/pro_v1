package com.red.server.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature as Jackson2SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule as Jackson2JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import tools.jackson.databind.DeserializationFeature
import tools.jackson.module.kotlin.KotlinFeature
import tools.jackson.module.kotlin.KotlinModule

/**
 * Jackson Configuration - إعدادات ObjectMapper
 * 
 * يدعم Jackson 3 (Boot 4 MVC) + Jackson 2 (manual usages) + Kotlin defaults.
 * KotlinModule يُسجل صراحةً لضمان تفعيل Kotlin default parameters.
 */
@Configuration
class JacksonConfig {

    // Jackson 2 — للخدمات التي تستخدم ObjectMapper يدوياً (20+ service)
    @Bean
    @Primary
    fun objectMapper(): ObjectMapper {
        return jacksonObjectMapper().apply {
            registerModule(Jackson2JavaTimeModule())
            disable(Jackson2SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            disable(Jackson2SerializationFeature.FAIL_ON_EMPTY_BEANS)
        }
    }

    // Jackson 3 — لـ Spring MVC (@RequestBody) في Boot 4
    @Bean
    fun kotlinJacksonModule(): KotlinModule =
        KotlinModule.Builder()
            .configure(KotlinFeature.NullIsSameAsDefault, false)
            .configure(KotlinFeature.NullToEmptyCollection, false)
            .configure(KotlinFeature.NullToEmptyMap, false)
            .build()

    @Bean
    fun kotlinMapperCustomizer(): JsonMapperBuilderCustomizer =
        JsonMapperBuilderCustomizer { builder ->
            builder
                .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        }
}
