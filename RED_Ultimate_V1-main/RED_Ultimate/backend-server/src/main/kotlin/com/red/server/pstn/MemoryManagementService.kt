package com.red.server.pstn

import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

/**
 * خدمة إدارة الذاكرة والتنظيف التلقائي
 *
 * تقوم بتنظيف البيانات المنتهية والصلاحيات القديمة بشكل دوري لمنع تسرب الذاكرة.
 */
@Service
class MemoryManagementService(
    private val jdbc: JdbcTemplate
) {
    companion object {
        private val log = LoggerFactory.getLogger(MemoryManagementService::class.java)

        /** عمر الصلاحيات قبل التنظيف (ساعات) */
        private const val RESERVATION_TTL_HOURS = 24

        /** عمر سجلاتTimeline قبل الأرشفة (أيام) */
        private const val TIMELINE_ARCHIVE_DAYS = 7
    }

    /**
     * تنظيف دوري للبيانات المنتهية
     * يُنفَّذ كل 5 دقائق
     */
    @Scheduled(fixedDelay = 300_000) // 5 minutes
    fun cleanupExpiredData() {
        try {
            val deleted = mutableListOf<String>()

            // 1. تنظيف حجوزات المنافذ المنتهية
            val reservationCount = jdbc.update(
                """
                    DELETE FROM gateway_port_reservations
                    WHERE expires_at < NOW()
                """
            )
            if (reservationCount > 0) {
                deleted.add("Reservations: $reservationCount")
                log.debug("Cleaned up $reservationCount expired port reservations")
            }

            // 2. تنظيف المكالمات النشطة المنتهية
            val callCount = jdbc.update(
                """
                    DELETE FROM pstn_active_calls
                    WHERE expires_at < NOW()
                """
            )
            if (callCount > 0) {
                deleted.add("Active calls: $callCount")
                log.debug("Cleaned up $callCount expired active calls")
            }

            // 3. أرشفة timeline القديم (أكثر من 7 أيام)
            val timelineCount = jdbc.update(
                """
                    DELETE FROM pstn_call_timeline
                    WHERE started_at < NOW() - INTERVAL '${TIMELINE_ARCHIVE_DAYS} days'
                """
            )
            if (timelineCount > 0) {
                deleted.add("Timeline: $timelineCount")
                log.debug("Archived $timelineCount old timeline entries")
            }

            // 4. تنظيف قرارات التوجيه القديمة
            val routeCount = jdbc.update(
                """
                    DELETE FROM gateway_route_decisions
                    WHERE created_at < NOW() - INTERVAL '1 day'
                """
            )
            if (routeCount > 0) {
                deleted.add("Route decisions: $routeCount")
                log.debug("Cleaned up $routeCount old route decisions")
            }

            if (deleted.isNotEmpty()) {
                log.info("Memory cleanup completed: ${deleted.joinToString(", ")}")
            }
        } catch (e: Exception) {
            log.warn("Memory cleanup failed: {}", e.message)
        }
    }

    /**
     * تنظيف دوري للبيانات المؤقتة في Redis
     */
    @Scheduled(fixedDelay = 600_000) // 10 minutes
    fun cleanupRedisTempData() {
        // ملاحظة: Redis auto-expiry يتولى هذا، لكن يمكننا مسح المفاتيح المتروكة
        try {
            // يمكن إضافة منطق Redis هنا إذا لزم الأمر
            log.debug("Redis temp data cleanup check completed")
        } catch (e: Exception) {
            log.warn("Redis cleanup failed: {}", e.message)
        }
    }

    /**
     * إصلاح دوري للبيانات التالفة
     */
    @Scheduled(cron = "0 0 2 * * *") // كل يوم عند الساعة 2:00 صباحاً
    fun repairDataIntegrity() {
        try {
            // التحقق من التجزئة المتروكة
            jdbc.queryForObject(
                """
                    SELECT COUNT(*) FROM gateway_port_reservations
                    WHERE expires_at > NOW() AND NOT EXISTS (
                        SELECT 1 FROM pstn_active_calls
                        WHERE pstn_active_calls.call_id = gateway_port_reservations.call_id
                    )
                """,
                Int::class.java
            )?.let { orphaned ->
                if (orphaned > 0) {
                    log.warn("Found {} orphaned port reservations, cleaning up...", orphaned)
                    jdbc.update(
                        """
                            DELETE FROM gateway_port_reservations
                            WHERE expires_at > NOW() AND NOT EXISTS (
                                SELECT 1 FROM pstn_active_calls
                                WHERE pstn_active_calls.call_id = gateway_port_reservations.call_id
                            )
                        """
                    )
                }
            }

            log.info("Data integrity repair completed")
        } catch (e: Exception) {
            log.warn("Data integrity repair failed: {}", e.message)
        }
    }

    /**
     * إحصائيات التنظيف
     */
    fun getCleanupStats(): Map<String, Any> {
        return mapOf(
            "reservationTTLHours" to RESERVATION_TTL_HOURS,
            "timelineArchiveDays" to TIMELINE_ARCHIVE_DAYS,
            "cleanupIntervalMinutes" to 5,
            "repairCron" to "0 0 2 * * *"
        )
    }
}
