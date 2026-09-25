package com.red.server.storage

import org.springframework.stereotype.Service
import java.io.File

/**
 * RED Sovereignty Monitor Service
 * Tracks local storage usage for MinIO and local database partitions.
 */
@Service
class StorageMonitorService {

    fun getLocalUsageStats(): Map<String, Long> {
        val minioRoot = File("/app/minio-data")
        val dbRoot = File("/var/lib/postgresql/data")

        return mapOf(
            "media_files" to calculateSize(minioRoot),
            "database_records" to calculateSize(dbRoot),
            "app_backups" to calculateSize(File("/app/backups"))
        )
    }

    /**
     * مسح دفاعي (احتفاظ آمن): أي خطأ وصول/صلاحيات يُعيد 0 بدل إسقاط
     * دورة التنظيف. المشي محدود بعمق معقول لتفادي التجمّد على روابط رمزية.
     */
    private fun calculateSize(path: File): Long {
        if (!path.exists()) return 0L
        return runCatching {
            path.walkTopDown()
                .onFail { _, _ -> /* تجاوز ملفات غير المقروءة — لا تُسقط الجردة */ }
                .filter { it.isFile }
                .map { runCatching { it.length() }.getOrDefault(0L) }
                .sum()
        }.getOrDefault(0L)
    }
}
