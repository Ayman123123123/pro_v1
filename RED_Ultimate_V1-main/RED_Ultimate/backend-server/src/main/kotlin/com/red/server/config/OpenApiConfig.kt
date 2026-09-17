package com.red.server.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.info.License
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import io.swagger.v3.oas.models.servers.Server
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.beans.factory.annotation.Value

@Configuration
class OpenApiConfig {

    @Bean
    fun openAPI(
        @Value("\${server.port:8080}") port: Int,
        @Value("\${spring.application.name:red-backend}") appName: String
    ): OpenAPI {
        val serverUrl = "http://localhost:$port"
        
        return OpenAPI()
            .info(Info()
                .title("RED Sovereign API")
                .version("1.0.0")
                .description("""
                    REST API for RED Sovereign - Secure messaging, calls, and live streaming platform.
                    
                    ## Authentication
                    All endpoints (except public auth/identity) require JWT Bearer token:
                    ```
                    Authorization: Bearer <access_token>
                    ```
                    
                    ## Rate Limiting
                    - Default: 60 requests/minute per IP
                    - Auth endpoints: stricter limits (see individual endpoints)
                    
                    ## Idempotency
                    For write operations, include `Idempotency-Key` header to prevent duplicate processing.
                    
                    ## WebSocket Endpoints
                    - `/ws/calls` - 1:1 and group call signaling
                    - `/ws/conference` - Conference/Space signaling  
                    - `/ws/livestream` - Live stream signaling
                    
                    ## Error Codes
                    - `AUTHENTICATION_REQUIRED` - Missing or invalid JWT
                    - `ADMIN_ROLE_REQUIRED` - Admin role needed
                    - `RATE_LIMITED` - Too many requests
                    - `USERNAME_TAKEN` - Username already in use
                    - `NOT_A_PARTICIPANT` - Not part of conversation/group
                    - `MESSAGE_DELETED` - Message was deleted
                    - `EDIT_WINDOW_EXPIRED` - 15min edit window passed
                    - `STREAM_NOT_FOUND` - Live stream not found/ended
                    - `NOT_OWNER` - Only stream owner can broadcast
                    """.trimIndent())
                .license(License().name("Proprietary").url("https://red-sovereign.com/license"))
            )
            .addServersItem(Server().url(serverUrl).description("Development server"))
            .addServersItem(Server().url("https://api.red-sovereign.com").description("Production server"))
            .components(Components()
                .addSecuritySchemes("bearerAuth", SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .description("JWT access token from /api/auth/login or /api/auth/refresh")
                )
                .addSecuritySchemes("idempotencyKey", SecurityScheme()
                    .type(SecurityScheme.Type.APIKEY)
                    .in(SecurityScheme.In.HEADER)
                    .name("Idempotency-Key")
                    .description("Optional idempotency key for write operations (max 64 chars)")
                )
            )
            .addSecurityItem(SecurityRequirement().addList("bearerAuth"))
    }
}