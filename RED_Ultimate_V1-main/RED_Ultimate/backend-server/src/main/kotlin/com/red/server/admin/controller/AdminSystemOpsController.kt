package com.red.server.admin.controller

import com.red.server.admin.service.AdminService
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisCallback
import org.springframework.data.redis.core.ScanOptions
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.nio.charset.StandardCharsets
import java.util.Properties
import java.util.UUID

/**
 * 🧰 Admin System Ops — عقود حقيقية تحت الجذر الإداري (تحميها قاعدة
 * "مسار admin كامل يتطلب دور ADMIN" في [com.red.server.config.SecurityConfig]).
 *
 * لماذا هذا الملف: صفحتا `CachePage` و`MigrationsPage` في لوحة الإدارة كانتا
 * تناديان مساري cache وmigrations تحت الجذر القديم — ولا وجود لهما؛ فكانتا
 * تعرضان بيانات وهمية بلا نهاية. هنا المصدر الحقيقي: Redis (INFO + SCAN) و
 * سجل Flyway (`flyway_schema_history`) عبر `JdbcTemplate`.
 *
 * قرارات أمان مقصودة:
 * - لا `FLUSHALL`/`FLUSHDB` إطلاقًا: الإبطال **بنمط مطلوب** وفقط بمفاتيح `UNLINK`
 *   (غير حاجب) وبسقف دفعة صريح. نمط `*` مرفوع بـ 400.
 * - كل إبطال يُسجَّل في `admin_audit_log` (فئة SYSTEM، خطورة WARNING) بلا استثناء.
 * - قيم الإعدادات (config) ليست هنا: لا مخزن إعدادات آمن بعدُ، ونشر بيئة الخادم
 *   كاملة كان سيسرّب أسرارًا. الصفحة تُعلن ذلك بدل اختراع مخزن.
 */
@RestController
@RequestMapping("/api/admin/system")
class AdminSystemOpsController(
    private val jdbcTemplate: JdbcTemplate,
    private val redisTemplate: StringRedisTemplate,
    private val adminService: AdminService
) {
    private val log = LoggerFactory.getLogger(AdminSystemOpsController::class.java)

    // ━━━━━━━━━━━━━━━━ 🗄️ Cache (Redis) ━━━━━━━━━━━━━━━━

    /**
     * نظرة كاش حقيقية: مقاييس الذاكرة من `INFO memory`/`INFO stats` + `DBSIZE`،
     * ومسح مفاتيح مقيّد بـ `SCAN` (لا `KEYS` الحاجب) بحد أقصى 500 مفتاح للطلب.
     */
    @GetMapping("/cache")
    fun cacheOverview(
        @RequestParam(required = false) pattern: String?,
        @RequestParam(defaultValue = "100") count: Int
    ): ResponseEntity<Map<String, Any?>> {
        val safeCount = count.coerceIn(1, 500)
        val match = pattern?.trim()?.takeIf { it.isNotEmpty() } ?: "*"

        val keys: List<Map<String, Any?>> = try {
            redisTemplate.execute(RedisCallback<List<Map<String, Any?>>> { conn ->
                val scan = ScanOptions.scanOptions().match(match).count(safeCount.toLong()).build()
                val rows = mutableListOf<Map<String, Any?>>()
                conn.keyCommands().scan(scan).use { cursor ->
                    while (cursor.hasNext() && rows.size < safeCount) {
                        val raw = cursor.next()
                        // فشل قياس مفتاح واحد لا يُسقط القائمة كلها
                        val ttl = runCatching { conn.keyCommands().ttl(raw) }.getOrNull()
                        val type = runCatching { conn.keyCommands().type(raw)?.code()?.uppercase() }.getOrNull()
                        rows.add(
                            mapOf(
                                "key" to String(raw, StandardCharsets.UTF_8),
                                "type" to (type ?: "STRING"),
                                "ttl" to (ttl ?: -1L),
                                // MEMORY USAGE غير متاح في واجهة Spring Data الحالية —
                                // `null` صريح أفضل من رقم مُختلق (الصفحة تعرض «—»).
                                "size" to null,
                                "memory" to null
                            )
                        )
                    }
                }
                rows
            }) ?: emptyList()
        } catch (e: Exception) {
            log.warn("admin/system/cache: redis scan failed — {}", e.message)
            emptyList()
        }

        val memory = Properties()
        val stats = Properties()
        val dbSize: Long = try {
            redisTemplate.execute(RedisCallback<Long> { conn ->
                memory.putAll(conn.serverCommands().info("memory") ?: Properties())
                stats.putAll(conn.serverCommands().info("stats") ?: Properties())
                conn.serverCommands().dbSize() ?: 0L
            }) ?: 0L
        } catch (e: Exception) {
            log.warn("admin/system/cache: redis info failed — {}", e.message)
            0L
        }

        return ResponseEntity.ok(
            mapOf(
                "keys" to keys,
                "truncated" to (keys.size >= safeCount),
                "pattern" to match,
                "memory" to mapOf(
                    "used" to memory.num("used_memory"),
                    "peak" to memory.num("used_memory_peak"),
                    "limit" to memory.num("maxmemory"),
                    "fragmentation" to memory.numDouble("mem_fragmentation_ratio"),
                    "keys" to dbSize,
                    "expired" to stats.num("expired_keys"),
                    "evicted" to stats.num("evicted_keys")
                )
            )
        )
    }

    /**
     * إبطال مقيّد بالنمط (بديل آمن عن FLUSHALL).
     * مثال: `{"pattern": "session:*", "limit": 500}`.
     */
    @PostMapping("/cache/invalidate")
    fun invalidateCache(
        @RequestBody body: Map<String, String>,
        authentication: Authentication
    ): ResponseEntity<Map<String, Any?>> {
        val pattern = body["pattern"]?.trim().orEmpty()
        if (pattern.isEmpty() || pattern == "*" || pattern == "*:*") {
            return ResponseEntity.badRequest().body(
                mapOf(
                    "success" to false,
                    "error" to "pattern مطلوب ولا يجوز '*' — المسح الشامل ممنوع عبر هذا المسار"
                )
            )
        }
        val limit = (body["limit"]?.toIntOrNull() ?: 500).coerceIn(1, 2000)

        val deleted: Long = try {
            redisTemplate.execute(RedisCallback<Long> { conn ->
                val scan = ScanOptions.scanOptions().match(pattern).count(limit.toLong()).build()
                var removed = 0L
                conn.keyCommands().scan(scan).use { cursor ->
                    while (cursor.hasNext() && removed < limit) {
                        // UNLINK غير حاجب — لا يجمّد Redis على مفاتيح كبيرة
                        removed += conn.keyCommands().unlink(cursor.next()) ?: 0L
                    }
                }
                removed
            }) ?: 0L
        } catch (e: Exception) {
            log.error("admin/system/cache/invalidate failed for pattern {} — {}", pattern, e.message)
            return ResponseEntity.internalServerError().body(
                mapOf("success" to false, "error" to "تعذر تنفيذ الإبطال على Redis: ${e.message}")
            )
        }

        // تدقيق إلزامي: الإبطال عملية مؤثرة — يُسجَّل حتى لو حذف صفرًا
        val adminId = runCatching { UUID.fromString(authentication.name) }.getOrNull()
        adminService.recordAudit(
            adminId = adminId,
            adminUsername = authentication.principal?.toString() ?: authentication.name,
            action = "CACHE_INVALIDATED",
            category = "SYSTEM",
            targetType = "CACHE",
            targetId = pattern,
            description = "Redis invalidation by pattern: deleted=$deleted (limit=$limit)",
            severity = "WARNING"
        )

        return ResponseEntity.ok(mapOf("success" to true, "deleted" to deleted, "pattern" to pattern, "limit" to limit))
    }

    // ━━━━━━━━━━━━━━━━ 🧬 Migrations (Flyway) ━━━━━━━━━━━━━━━━

    /**
     * سجل Flyway الحقيقي من `flyway_schema_history` (معكوسًا: الأحدث أولًا).
     * غياب الجدول (قاعدة جديدة/غير مُهاجَرة) يُعاد كـ `available=false` بلا 500.
     */
    @GetMapping("/migrations")
    fun migrations(): ResponseEntity<Map<String, Any?>> {
        val rows: List<Map<String, Any?>> = try {
            jdbcTemplate.queryForList(
                """
                SELECT installed_rank, version, description, type, script, checksum,
                       installed_by, installed_on, execution_time, success
                FROM flyway_schema_history
                ORDER BY installed_rank DESC
                """.trimIndent()
            ).map { r ->
                mapOf(
                    "id" to "migration-${r["installed_rank"] ?: "?"}",
                    "version" to (r["version"]?.toString() ?: "—"),
                    "description" to (r["description"]?.toString() ?: ""),
                    "type" to (r["type"]?.toString() ?: "SQL"),
                    "status" to if (r["success"] == true) "SUCCESS" else "FAILED",
                    "checksum" to (r["checksum"]?.toString() ?: ""),
                    "executedBy" to (r["installed_by"]?.toString() ?: "system"),
                    "executedAt" to (r["installed_on"]?.toString() ?: ""),
                    "executionTime" to ((r["execution_time"] as? Number)?.toLong() ?: 0L),
                    "script" to (r["script"]?.toString() ?: "")
                )
            }
        } catch (e: Exception) {
            log.warn("admin/system/migrations: flyway_schema_history unavailable — {}", e.message)
            return ResponseEntity.ok(
                mapOf(
                    "available" to false,
                    "migrations" to emptyList<Map<String, Any?>>(),
                    "total" to 0,
                    "failed" to 0,
                    "reason" to "flyway_schema_history غير موجود — القاعدة ليست مُهاجَرة بـ Flyway"
                )
            )
        }

        return ResponseEntity.ok(
            mapOf(
                "available" to true,
                "migrations" to rows,
                "total" to rows.size,
                "failed" to rows.count { it["status"] == "FAILED" },
                "currentVersion" to rows.firstOrNull()?.get("version")
            )
        )
    }

    // ━━━━━━━━━━━━━━━━ مساعدات تحليل INFO ━━━━━━━━━━━━━━━━

    private fun Properties.num(key: String): Long =
        getProperty(key)?.trim()?.toLongOrNull() ?: 0L

    private fun Properties.numDouble(key: String): Double =
        getProperty(key)?.trim()?.toDoubleOrNull() ?: 0.0
}
