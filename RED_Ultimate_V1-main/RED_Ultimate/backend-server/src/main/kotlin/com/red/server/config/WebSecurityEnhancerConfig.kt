package com.red.server.config

import com.red.server.security.SecurityEnhancer
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebSecurityEnhancerConfig(
    private val securityEnhancer: SecurityEnhancer
) : WebMvcConfigurer {
    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(securityEnhancer)
            .addPathPatterns("/api/**", "/health", "/actuator/**")
    }
}
