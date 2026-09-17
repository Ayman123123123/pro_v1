package com.red.server.groups

import com.red.server.calls.CallTelemetryDocument
import com.red.server.database.ChannelDocument
import com.red.server.database.NotificationArchiveDocument
import com.red.server.social.FollowDocument
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.Index
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

/**
 * المرجع الوحيد لفهارس المجموعات وما يتصل بها (Single Source of Truth).
 *
 * هذا الـ Initializer هو ما يُنشئ الفهارس فعليًا عند الإقلاع ويُصلح المتعارض
 * (فحص unique/sparse ثم إسقاط + إعادة). تعليقات @Indexed/@CompoundIndex على
 * النماذج عقد توثيقي فقط وليست مصدر الإنشاء — أي اسم جديد يجب أن يُضاف هنا
 * أولًا وباسم كانوني واحد (يُسقط الاسم القديم إن وُجد).
 *
 * يغطي أيضًا الفهارس العابرة للمجالات التي لا تملك Initializer خاصًا:
 * - channels.username فريد sparse (يقبل null/غائب دون تعارض).
 * - follows مركّب فريد (followerId,followedId).
 * - call_telemetry.receivedAt للاستعلامات الزمنية والحذف المجدول.
 * - notification_archive retention عبر TTL (90 يومًا على createdAt).
 */
@Component
class GroupIndexInitializer(private val mongo: MongoTemplate) {
    private val log = LoggerFactory.getLogger(javaClass)

    private fun ensureIndex(clazz: Class<*>, index: Index, name: String) {
        runCatching { mongo.indexOps(clazz).createIndex(index) }.onFailure { e ->
            val msg = e.message ?: ""
            if (msg.contains("IndexKeySpecsConflict") || msg.contains("already exists")) {
                runCatching {
                    mongo.indexOps(clazz).dropIndex(name)
                    mongo.indexOps(clazz).createIndex(index)
                    log.info("Recreated conflicting index {} on {}", name, clazz.simpleName)
                }.onFailure { e2 -> log.warn("Failed to recreate index {} on {}: {}", name, clazz.simpleName, e2.message) }
            } else {
                log.warn("Failed to create index {} on {}: {}", name, clazz.simpleName, msg)
            }
        }
    }

    private fun ensureUnique(clazz: Class<*>, fields: List<Pair<String, Sort.Direction>>, uniqueName: String) {
        val ops = mongo.indexOps(clazz)
        val existing = runCatching { ops.indexInfo }.getOrNull() ?: emptyList()
        if (existing.any { it.name == uniqueName && !it.isUnique }) {
            runCatching { ops.dropIndex(uniqueName) }.onSuccess {
                log.info("Dropped legacy non-unique index {} on {} to upgrade to unique", uniqueName, clazz.simpleName)
            }
        }
        ensureIndex(clazz, Index().apply { fields.forEach { (f, d) -> on(f, d) }; unique(); named(uniqueName) }, uniqueName)
    }

    private fun ensureSparseUnique(clazz: Class<*>, field: String, indexName: String) {
        val ops = mongo.indexOps(clazz)
        val existing = runCatching { ops.indexInfo }.getOrNull()?.firstOrNull { it.name == indexName }
        // الفريد على username الاختياري يجب أن يكون sparse — القديم غير-sparse يُسقط.
        if (existing != null && (!existing.isUnique || !existing.isSparse)) {
            runCatching { ops.dropIndex(indexName) }.onSuccess {
                log.info("Dropped legacy non-sparse/non-unique index {} on {} to upgrade to sparse unique", indexName, clazz.simpleName)
            }
        }
        ensureIndex(clazz, Index().on(field, Sort.Direction.ASC).unique().sparse().named(indexName), indexName)
    }

    @PostConstruct
    fun init() {
        // فهرس انتهاء الدعوات — تنظيف تلقائي للدعوات المنتهية (TTL 0 يعني حذف عند expiresAt)
        ensureIndex(
            GroupInviteDocument::class.java,
            Index().on("expiresAt", Sort.Direction.ASC).expire(0).named("expiresAt_1"),
            "expiresAt_1"
        )
        // مركب لطلبات الانضمام — بحث سريع عن المعلقة حسب المجموعة
        ensureIndex(
            GroupJoinRequestDocument::class.java,
            Index().on("groupId", Sort.Direction.ASC).on("status", Sort.Direction.ASC).on("createdAt", Sort.Direction.ASC).named("groupId_1_status_1_createdAt_1"),
            "groupId_1_status_1_createdAt_1"
        )
        // فهرس الأعضاء حسب redId للبحث السريع
        ensureIndex(
            GroupMember::class.java,
            Index().on("redId", Sort.Direction.ASC).named("redId_1"),
            "redId_1"
        )
        // فهرس المالك للاستعلام السريع عن مجموعات المالك
        ensureIndex(
            GroupDocument::class.java,
            Index().on("ownerRedId", Sort.Direction.ASC).named("ownerRedId_1"),
            "ownerRedId_1"
        )
        // channels.username فريد sparse — قنوات بلا username (null/غائب) لا تتعارض.
        ensureSparseUnique(ChannelDocument::class.java, "username", "username_1")
        // follows مركّب فريد — يمنع المتابعة المكررة (followerId,followedId).
        ensureUnique(
            FollowDocument::class.java,
            listOf("followerId" to Sort.Direction.ASC, "followedId" to Sort.Direction.ASC),
            "followerId_1_followedId_1"
        )
        // call_telemetry.receivedAt — إحصائيات النافذة الزمنية + حذف RetentionScheduler.
        ensureIndex(
            CallTelemetryDocument::class.java,
            Index().on("receivedAt", Sort.Direction.ASC).named("receivedAt_1"),
            "receivedAt_1"
        )
        // retention: أرشيف الإشعارات — TTL 90 يومًا على createdAt.
        ensureIndex(
            NotificationArchiveDocument::class.java,
            Index().on("createdAt", Sort.Direction.ASC).expire(90, TimeUnit.DAYS).named("createdAt_ttl_90d"),
            "createdAt_ttl_90d"
        )
    }
}
