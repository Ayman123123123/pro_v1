package com.red.server.config

import com.red.server.auth.security.JwtAuthenticationFilter
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

/**
 * 🔒 YOUNES Sovereign Security Configuration
 * - Argon2id لكلمات المرور
 * - JWT عديم الحالة
 * - CORS قابل للتهيئة
 * - مسارات عامة/محمية/إدارية محددة بدقة
 */
@Configuration
class SecurityConfig(
    private val jwtFilter: JwtAuthenticationFilter,
    @Value("\${red.security.allowed-origins:http://localhost,http://127.0.0.1}")
    private val configuredAllowedOrigins: String
) {
    @Bean
    fun passwordEncoder(): PasswordEncoder = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .cors { it.configurationSource(corsConfigurationSource()) }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .exceptionHandling { exceptions ->
                exceptions
                    .authenticationEntryPoint { _, response, _ ->
                        response.status = 401
                        response.contentType = MediaType.APPLICATION_JSON_VALUE
                        response.writer.write("{\"error\":\"AUTHENTICATION_REQUIRED\"}")
                    }
                    .accessDeniedHandler { _, response, _ ->
                        response.status = 403
                        response.contentType = MediaType.APPLICATION_JSON_VALUE
                        response.writer.write("{\"error\":\"ADMIN_ROLE_REQUIRED\"}")
                    }
            }
            .authorizeHttpRequests { auth ->
                auth
                    // ─── مسارات عامة (لا تحتاج مصادقة) ───
                    .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login", "/api/auth/refresh", "/api/auth/logout", "/api/auth/recover").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/identity/authority", "/api/identity/directory").permitAll()
                    .requestMatchers("/health", "/actuator/health", "/actuator/info").permitAll()
                    .requestMatchers("/ws/**").permitAll()
                    // ─── مسارات إدارية ───
                    .requestMatchers("/api/admin/**", "/api/master/admin/**", "/api/master/v1/**").hasRole("ADMIN")
                    // ─── مسارات المستخدم المصادق ───
                    .requestMatchers(HttpMethod.GET, "/api/social/status/**").authenticated()
                    .requestMatchers(HttpMethod.PUT, "/api/social/status", "/api/social/privacy").authenticated()
                    .requestMatchers(HttpMethod.GET, "/api/social/privacy", "/api/social/online-contacts").authenticated()
                    .requestMatchers("/api/notifications/**").authenticated()
                    .requestMatchers("/api/calls/**", "/api/calls/ice-servers").authenticated()
                    .requestMatchers("/api/stories/**").authenticated()
                    .requestMatchers("/api/feed/**", "/api/posts/**", "/api/social/**").authenticated()
                    .requestMatchers("/api/groups/**").authenticated()
                    .requestMatchers("/api/messages/**", "/api/contacts/**", "/api/devices/**").authenticated()
                    .requestMatchers("/api/pstn/**", "/api/dinstar/**").authenticated()
                    .requestMatchers("/api/admin/dinstar/sms/**").hasRole("ADMIN")
                    .requestMatchers("/api/media/**").authenticated()
                    .requestMatchers("/api/**").authenticated()
                    .anyRequest().authenticated()
            }
            .headers { headers ->
                headers
                    .xssProtection { it.headerValue(org.springframework.security.config.Elements.XSS_PROTECTION_HEADER_VALUE_BLOCK) }
                    .contentSecurityPolicy { it.policyDirectives("default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; connect-src 'self' ws: wss:; font-src 'self' data:; media-src 'self' blob:; frame-ancestors 'none'") }
                    .referrerPolicy { it.policy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER) }
            }
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter::class.java)

        return http.build()
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration().apply {
            allowedOriginPatterns = configuredAllowedOrigins.split(',').map(String::trim).filter(String::isNotEmpty)
            allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            allowedHeaders = listOf("Authorization", "Content-Type", "X-Requested-With", "X-Device-Id")
            exposedHeaders = listOf("Location", "X-Total-Count")
            allowCredentials = true
            maxAge = 3600
        }
        return UrlBasedCorsConfigurationSource().also {
            it.registerCorsConfiguration("/**", configuration)
        }
    }
}
