package com.red.server.config

import com.red.server.services.EmailProperties
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class NotificationConfig {

    @Bean
    fun emailProperties(): EmailProperties {
        return EmailProperties(
            smtpHost = "${SMTP_HOST}",
            smtpPort = SMTP_PORT.toIntOrNull() ?: 587,
            smtpAuth = SMTP_AUTH.toBoolean(),
            startTls = START_TLS.toBoolean(),
            username = SMTP_USERNAME,
            password = SMTP_PASSWORD,
            fromAddress = SMTP_FROM
        )
    }

    companion object {
        // These would come from environment variables or application.yml
        private const val SMTP_HOST = "smtp.gmail.com"
        private const val SMTP_PORT = "587"
        private const val SMTP_AUTH = "true"
        private const val START_TLS = "true"
        private const val SMTP_USERNAME = ""
        private const val SMTP_PASSWORD = ""
        private const val SMTP_FROM = "noreply@red.local"
    }
}
