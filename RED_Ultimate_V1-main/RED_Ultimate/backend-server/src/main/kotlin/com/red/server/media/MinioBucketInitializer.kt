package com.red.server.media

import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component

/**
 * 🪣 يضمن وجود bucket الوسائط عند الإقلاع.
 *
 * بدون هذا المهيئ يبقى bucket `red-media` غير موجود حتى أول عملية رفع ملف
 * (لأن [MediaService.ensureBucket] كسولة)، فيبلّغ ‎/health عن minio=DOWN
 * رغم أن خادم MinIO نفسه سليم. الفشل هنا لا يُسقط الإقلاع — يُعاد الإنشاء
 * كسولاً عند أول رفع كما كان سابقاً.
 */
@Component
class MinioBucketInitializer(
    private val mediaService: MediaService
) : ApplicationRunner {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        runCatching { mediaService.ensureBucket() }
            .onSuccess { log.info("MinIO media bucket verified/created at startup") }
            .onFailure { e ->
                log.warn("MinIO bucket init skipped (auto-retry on first upload): {}", e.message)
            }
    }
}
