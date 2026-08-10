package com.red.server.media

import io.minio.MinioClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class MinioConfig {
    @Bean
    fun minioClient(
        @Value("\${red.minio.endpoint}") endpoint: String,
        @Value("\${red.minio.access-key}") accessKey: String,
        @Value("\${red.minio.secret-key}") secretKey: String
    ): MinioClient {
        require(accessKey.isNotBlank() && secretKey.length >= 12) { "MinIO credentials are not configured" }
        return MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey).build()
    }
}
