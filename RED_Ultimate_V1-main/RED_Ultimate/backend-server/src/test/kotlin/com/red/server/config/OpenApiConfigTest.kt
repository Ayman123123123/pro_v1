// SPDX-License-Identifier: AGPL-3.0-only
package com.red.server.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.Configuration

class OpenApiConfigTest {
    @Test
    fun `config is dependency-free spring configuration`() {
        val ann = OpenApiConfig::class.java.getAnnotation(Configuration::class.java)
        assertTrue(ann != null, "OpenApiConfig must retain @Configuration without springdoc dependency")
    }

    @Test
    fun `license metadata is unified to AGPL`() {
        assertEquals("AGPL-3.0-only", OpenApiConfig.LICENSE_NAME)
        assertEquals("https://www.gnu.org/licenses/agpl-3.0.html", OpenApiConfig.LICENSE_URL)
        assertTrue(OpenApiConfig.API_TITLE.isNotBlank())
        assertTrue(OpenApiConfig.API_VERSION.isNotBlank())
        assertTrue(OpenApiConfig.DESCRIPTION.isNotBlank())
    }
}
