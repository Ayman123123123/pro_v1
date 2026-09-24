package com.red.sovereign.core.database

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * صيانة قاعدة البيانات المحلية: فهارس ناقصة + retention/TTL محلي.
 * تُستدعى عند بدء الخدمة وبشكل دوري من Worker.
 * كل CREATE INDEX بـ IF NOT EXISTS — آمنة للتكرار ولا تتطلب Migration
 * (تُنشئ ما نقص في نسخ v8 القديمة، وتتطابق مع @Entity الجديدة للنسخ الجديدة).
 *
 * سياسات الاحتفاظ المحلية (افتراضات محافظة):
 * - call_logs: 180 يومًا
 * - stories/sovereign_stories: المنتهية تُحذف فورًا (expiresAt <= now)
 * - live_comments: 1 دقيقة، call_quality/network_stats: 7 أيام
 * - ai_summaries/scam_alerts/notifications: 30 يومًا
 * - outbox SENT: 7 أيام، FAILED/DEAD_LETTER: 30 يومًا
 * - media_uploads المكتملة: 7 أيام
 */
object DatabaseMaintenance {
    const val CALL_LOG_RETENTION_MS = 180L * 24 * 60 * 60 * 1000L
    const val SENT_OUTBOX_RETENTION_MS = 7L * 24 * 60 * 60 * 1000L
    const val FAILED_OUTBOX_RETENTION_MS = 30L * 24 * 60 * 60 * 1000L
    const val MEDIA_UPLOAD_DONE_RETENTION_MS = 7L * 24 * 60 * 60 * 1000L

    private val MISSING_INDEXES = listOf(
        "CREATE INDEX IF NOT EXISTS `index_messages_senderId` ON `messages` (`senderId`)",
        "CREATE INDEX IF NOT EXISTS `index_messages_sequence` ON `messages` (`sequence`)",
        "CREATE INDEX IF NOT EXISTS `index_messages_replyToMessageId` ON `messages` (`replyToMessageId`)",
        "CREATE INDEX IF NOT EXISTS `index_messages_conv_status_created` ON `messages` (`conversationId`, `status`, `createdAt`)",
        "CREATE INDEX IF NOT EXISTS `index_local_history_status` ON `local_history` (`status`)",
        "CREATE INDEX IF NOT EXISTS `index_local_history_conv_status` ON `local_history` (`conversationId`, `status`)",
        "CREATE INDEX IF NOT EXISTS `index_local_history_outgoing_status_created` ON `local_history` (`outgoing`, `status`, `createdAt`)",
        "CREATE INDEX IF NOT EXISTS `index_local_history_senderId` ON `local_history` (`senderId`)",
        "CREATE INDEX IF NOT EXISTS `index_conversations_peerId` ON `conversations` (`peerId`)",
        "CREATE INDEX IF NOT EXISTS `index_contacts_username` ON `contacts` (`username`)",
        "CREATE INDEX IF NOT EXISTS `index_contacts_displayName` ON `contacts` (`displayName`)",
        "CREATE INDEX IF NOT EXISTS `index_contacts_friend_name` ON `contacts` (`isFriend`, `displayName`)",
        "CREATE INDEX IF NOT EXISTS `index_contacts_isBlocked` ON `contacts` (`isBlocked`)",
        "CREATE INDEX IF NOT EXISTS `index_groups_ownerRedId` ON `groups` (`ownerRedId`)",
        "CREATE INDEX IF NOT EXISTS `index_groups_communityId` ON `groups` (`communityId`)",
        "CREATE INDEX IF NOT EXISTS `index_call_logs_status` ON `call_logs` (`status`)",
        "CREATE INDEX IF NOT EXISTS `index_stories_expiresAt` ON `stories` (`expiresAt`)",
        "CREATE INDEX IF NOT EXISTS `index_stories_user_expires` ON `stories` (`userId`, `expiresAt`)",
        "CREATE INDEX IF NOT EXISTS `index_starred_senderId` ON `starred_messages` (`senderId`)",
        "CREATE INDEX IF NOT EXISTS `index_media_uploads_conversationId` ON `media_uploads` (`conversationId`)",
        "CREATE INDEX IF NOT EXISTS `index_outbox_targetRedId` ON `outbox_messages` (`targetRedId`)",
        "CREATE INDEX IF NOT EXISTS `index_outbox_status_priority_next` ON `outbox_messages` (`status`, `priority`, `nextAttemptAt`)"
    )

    /** تُنشئ الفهارس الناقصة إن غابت — idempotent. */
    fun ensureIndexes(db: SupportSQLiteDatabase): Int {
        var created = 0
        MISSING_INDEXES.forEach { sql ->
            runCatching { db.execSQL(sql); created++ }
        }
        return created
    }

    fun ensureIndexes(ctx: Context): Int = runCatching {
        val db = RedDatabase.getInstance(ctx).openHelper.writableDatabase
        ensureIndexes(db)
    }.getOrDefault(0)

    /**
     * retention محلي شامل — يُعيد خريطة بالأعداد المحذوفة للمزامنة/المراقبة.
     * لا يحذف رسائل المستخدم أبدًا — فقط سجلات تشغيلية ومؤقتة.
     */
    suspend fun enforceRetention(ctx: Context, now: Long = System.currentTimeMillis()): Map<String, Int> {
        val db = RedDatabase.getInstance(ctx)
        val red = db.redDao()
        val outbox = db.outboxDao()
        val ultimate = db.ultimateDao()
        val out = mutableMapOf<String, Int>()
        out["call_logs"] = runCatching { red.deleteCallLogsOlderThan(now - CALL_LOG_RETENTION_MS) }.getOrDefault(0)
        out["stories"] = runCatching { red.cleanupExpiredStories(now) }.getOrDefault(0)
        out["outbox_sent"] = runCatching { outbox.cleanupSent(now - SENT_OUTBOX_RETENTION_MS) }.getOrDefault(0)
        out["outbox_failed"] = runCatching {
            outbox.cleanupFailed(now - FAILED_OUTBOX_RETENTION_MS, OutboxMessageEntity.MAX_RETRY_COUNT)
        }.getOrDefault(0)
        out["ultimate"] = runCatching { ultimate.cleanupAllExpired(now).values.sum() }.getOrDefault(0)
        // FTS يتامى: احذف فهرس رسائل لم تعد في local_history (best-effort)
        runCatching {
            db.openHelper.writableDatabase.execSQL(
                "DELETE FROM messages_fts WHERE messageId NOT IN (SELECT id FROM local_history)"
            )
        }
        return out
    }
}
