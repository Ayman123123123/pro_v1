package com.red.server.config

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.DeserializationFeature
import tools.jackson.module.kotlin.KotlinFeature
import tools.jackson.module.kotlin.KotlinModule

/**
 * Jackson Configuration — Jackson 3 (tools.jackson) موحّد بالكامل.
 *
 * (2026-09-15) كان الملف يصون حزمة مزدوجة: فاصلك Jackson 2 (com.fasterxml)
 * للاستخدام اليدوي + Jackson 3 لـ MVC — أُزيلت Jackson 2 نهائياً وهاجر كل
 * الكود إلى tools.jackson (Boot 4 يدير الإصدار عبر BOM). KotlinModule يُسجَّل
 * كـ Bean ليتعرف عليه الـ auto-configuration ويُطبَّق على الـ JsonMapper الرسمي.
 */
@Configuration
class JacksonConfig {

    // Jackson 3 — Kotlin defaults (default parameters، null-safety) لكل الـ MVC والاستخدام اليدوي.
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
