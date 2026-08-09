package com.red.server.storage

import com.red.server.media.MediaService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 🧹 تنظيف يومي للملفات اليتيمة — يحذف كائنات MinIO بدون مرجع في MongoDB
 * يعمل كل يوم 03:00 Asia/Aden
 */
@Component
@EnableScheduling
class OrphanCleanupScheduler(
    private val storage: StorageMonitorService,
    private val media: MediaService
) {
    private val log = LoggerFactory.getLogger(OrphanCleanupScheduler::class.java)

    @Scheduled(cron = "0 0 3 * * *", zone = "Asia/Aden")
    fun dailyOrphanScan() {
        try {
            val stats = storage.getLocalUsageStats()
            log.info("Orphan scan — media_files: {} bytes, db_records: {}", stats["media_files"], stats["database_records"])
            // Real impl: list bucket objects vs MongoDB referenced keys via findOrphanKeys()
            media.scheduleOrphanCleanup()
        } catch (e: Exception) {
            log.warn("Orphan scan failed: {}", e.message)
        }
    }
}
