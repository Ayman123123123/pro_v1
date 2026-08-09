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

    private fun calculateSize(path: File): Long {
        if (!path.exists()) return 0L
        return path.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
    }
}
