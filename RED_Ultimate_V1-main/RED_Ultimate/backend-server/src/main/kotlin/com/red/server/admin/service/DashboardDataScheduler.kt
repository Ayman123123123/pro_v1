package com.red.server.admin.service

import com.red.server.admin.model.SystemAnalytics
import com.red.server.admin.repository.SystemAnalyticsRepository
import com.red.server.auth.model.AccountStatus
import com.red.server.auth.repository.UserAccountRepository
import com.red.server.storage.StorageMonitorService
import io.minio.BucketExistsArgs
import io.minio.MinioClient
import org.bson.Document
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import jakarta.annotation.PostConstruct

/**
 * يملأ بيانات لوحة الإدارة تلقائياً:
 *  - سجلات `system_health` كل 5 دقائق (نفس فحوصات `/health`).
 *  - لقطة `system_analytics` يومياً في نهاية اليوم (UTC 23:50).
 *
 * بدونه تبقى الرسوم البيانية وبطاقات الصحة فارغة للأبد.
 */
@Component
class DashboardDataScheduler(
    private val adminService: AdminService,
    private val jdbcTemplate: JdbcTemplate,
    private val mongoTemplate: MongoTemplate,
    private val redisTemplate: RedisTemplate<String, String>,
    private val minioClient: MinioClient,
    private val users: UserAccountRepository,
    private val analyticsRepository: SystemAnalyticsRepository,
    private val storageMonitor: StorageMonitorService,
    @Value("\${red.minio.bucket:red-media}") private val minioBucket: String
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @PostConstruct
    fun warmUp() {
        // لقطة فورية عند الإقلاع حتى تظهر بيانات اليوم في اللوحة فوراً،
        // ثم يعيد كرون نهاية اليوم كتابتها بالقيم الختامية.
        runCatching { writeDailyAnalytics() }
            .onSuccess { log.info("Analytics warm-up snapshot recorded for {}", it) }
            .onFailure { log.info("Analytics warm-up deferred ({}). Will be retried by cron", it.message) }
    }

    @Scheduled(fixedDelay = 300_000, initialDelay = 60_000)
    fun recordHealthChecks() {
        runCatching { writeHealthChecks() }.onFailure { log.warn("Health recording failed: {}", it.message) }
    }

    private fun writeHealthChecks() {
        val probes = mapOf(
            "POSTGRESQL" to probe { jdbcTemplate.queryForObject("SELECT 1", Int::class.java) == 1 },
            "MONGODB" to probe {
                val reply = mongoTemplate.executeCommand(Document("ping", 1))
                (reply["ok"] as? Number)?.toDouble() == 1.0
            },
            "REDIS" to probe {
                val connection = requireNotNull(redisTemplate.connectionFactory?.connection) { "Redis connection unavailable" }
                try {
                    connection.ping()
                    true
                } finally {
                    connection.close()
                }
            },
            "MINIO" to probe { minioClient.bucketExists(BucketExistsArgs.builder().bucket(minioBucket).build()) },
            "MEDIA_SFU" to probe { httpOk("http://media-sfu:4000/health") },
            "COTURN" to probe { tcpOk("coturn", 3478) },
            "PSTN_GATEWAY" to probe { tcpOk("pstn-gateway", 5038) },
            "BACKEND" to probe { true }
        )

        val runtime = Runtime.getRuntime()
        val usedMb = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
        val maxMb = runtime.maxMemory() / 1024 / 1024
        val cpuUsage = if (maxMb == 0L) null else (usedMb * 100 / maxMb).toFloat()
        val disk = runCatching { storageMonitor.getLocalUsageStats()["media_files"] ?: 0L }.getOrDefault(0L)
        val activeConnections = redisTemplate.opsForZSet()?.zCard("red:presence:index")?.toInt()

        probes.forEach { (component, ok) ->
            adminService.recordHealth(
                component = component,
                status = if (ok) "HEALTHY" else "DOWN",
                cpuUsage = cpuUsage,
                memoryUsage = if (maxMb == 0L) null else (usedMb * 100f / maxMb),
                diskUsage = if (disk == 0L) null else (disk / 1024 / 1024 / 1024).toFloat(),
                activeConnections = activeConnections,
                details = if (ok) emptyMap() else mapOf("error" to "$component unreachable")
            )
        }
        log.info("Recorded {} health checks", probes.size)
    }

    private fun probe(block: () -> Boolean): Boolean = runCatching(block).getOrDefault(false)

    private fun httpOk(url: String): Boolean {
        val connection = java.net.URI.create(url).toURL().openConnection() as HttpURLConnection
        connection.connectTimeout = 2_000
        connection.readTimeout = 3_000
        connection.requestMethod = "GET"
        try {
            return connection.responseCode < 400
        } finally {
            connection.disconnect()
        }
    }

    private fun tcpOk(host: String, port: Int): Boolean {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, port), 2_000)
            return true
        }
    }

    @Scheduled(cron = "0 50 23 * * *", zone = "UTC")
    fun snapshotDailyAnalytics() {
        runCatching { writeDailyAnalytics() }
            .onSuccess { log.info("Daily analytics snapshot recorded for {}", it) }
            .onFailure { log.warn("Daily analytics snapshot failed: {}", it.message) }
    }

    private fun writeDailyAnalytics(): LocalDate {
        val today = LocalDate.now(ZoneOffset.UTC)
        val dayStart = today.atStartOfDay(ZoneOffset.UTC).toInstant()
        val nextDayStart = today.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()

        val totalUsers = users.count()
        val pending = users.findAllByStatusOrderByCreatedAtAsc(AccountStatus.PENDING).size
        val banned = users.findAllByStatusOrderByCreatedAtAsc(AccountStatus.BANNED).size
        val newUsers = runCatching {
            jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users WHERE created_at >= ?", Int::class.java, dayStart) ?: 0
        }.getOrDefault(0)

        val messagesToday = countMongo("messages", Criteria.where("createdAt").gte(dayStart))
        val voiceToday = countMongo("messages", Criteria.where("createdAt").gte(dayStart).and("messageType").`is`("VOICE"))
        val mediaToday = countMongo("messages", Criteria.where("createdAt").gte(dayStart).and("attachments").ne(listOf<Any>()))
        val callsToday = countMongo("call_history", Criteria.where("startedAt").gte(dayStart))
        val callsPstnToday = countMongo("call_history", Criteria.where("startedAt").gte(dayStart).and("type").`is`("PSTN_DINSTAR"))

        val activeDau = runCatching {
            if (mongoTemplate.collectionExists("messages")) {
                mongoTemplate.findDistinct(
                    Query(Criteria.where("createdAt").gte(dayStart)),
                    "senderId",
                    "messages",
                    String::class.java
                ).size
            } else 0
        }.getOrDefault(0)

        val storageUsed = runCatching { storageMonitor.getLocalUsageStats()["media_files"] ?: 0L }.getOrDefault(0L)

        analyticsRepository.findByStatDateOrderByStatDateDesc(today)?.let { analyticsRepository.delete(it) }

        analyticsRepository.save(
            SystemAnalytics(
                statDate = today,
                totalUsers = totalUsers.toInt(),
                newUsers = newUsers,
                activeUsersDau = activeDau,
                pendingApprovals = pending,
                bannedUsers = banned,
                messagesSent = messagesToday,
                voiceMessages = voiceToday,
                mediaUploads = mediaToday,
                callsTotal = callsToday,
                callsPstn = callsPstnToday,
                storageUsedBytes = storageUsed
            )
        )
        return today
    }

    private fun countMongo(collection: String, criteria: Criteria): Int =
        runCatching { mongoTemplate.count(Query(criteria), collection).toInt() }.getOrDefault(0)
}
