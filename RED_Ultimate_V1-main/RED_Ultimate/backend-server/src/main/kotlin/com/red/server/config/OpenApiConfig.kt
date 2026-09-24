package com.red.server.config

import org.springframework.context.annotation.Configuration

/**
 * RED Sovereign API metadata — dependency-free stub.
 *
 * Why there is no `OpenAPI` @Bean here (CI fix):
 * - `build.gradle.kts` does NOT declare any springdoc artifact (only
 *   `gradle/libs.versions.toml` defines `springdoc-openapi-starter-webmvc:2.6.0`),
 *   so any reference to `io.swagger.v3.oas.models.*` fails compilation.
 * - The previous version also had missing swagger imports AND misplaced
 *   `.license(...)` on `OpenAPI` instead of `Info` with an unbalanced
 *   parenthesis around lines 52-54/73, which is where kotlinc stopped.
 * - To re-enable full Swagger UI, the build owner must add the dependency
 *   (e.g. `implementation(libs.springdoc.openapi.starter.webmvc)`) and then
 *   restore the `openAPI()` @Bean with `.info(Info()...license(...))` inside `Info`.
 *
 * Legal (AGPL unification):
 * - Project license on disk `RED_Ultimate/LICENSE` is AGPL-3.0 (verified, 661 lines).
 * - Previous code declared `License().name("Proprietary")` — contradiction, removed.
 * - Canonical: `AGPL-3.0-only`, https://www.gnu.org/licenses/agpl-3.0.html
 */
@Configuration
class OpenApiConfig {

    companion object {
        const val API_TITLE = "RED Sovereign API"
        const val API_VERSION = "1.0.0"
        const val LICENSE_NAME = "AGPL-3.0-only"
        const val LICENSE_URL = "https://www.gnu.org/licenses/agpl-3.0.html"
        const val DESCRIPTION = "REST API for RED Sovereign - Secure messaging, calls, and live streaming platform."
    }
}