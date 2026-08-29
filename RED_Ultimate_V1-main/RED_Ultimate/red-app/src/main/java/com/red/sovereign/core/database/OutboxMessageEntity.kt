package com.red.sovereign.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * صندوق الصادر المتين — **الرسائل لا تموت بموت العملية**.
 *
 * ## المشكلة التي يحلها
 * كان الإرسال يعتمد على `RedConnectionService` في المقدمة فقط. إن قُتلت العملية
 * (سحب التطبيق، نفاد البطارية، `kill -9`) أو انقطعت الشبكة أثناء التشفير،
 * ضاعت الرسالة: `local_history` يحفظها كـ `SENDING` لكن لا أحد يعيد محاولة إرسالها.
 *
 * ## الحل — Transactional Outbox
 * كل رسالة تُكتب في نفس معاملة Room كـ `LocalHistoryEntity` + `OutboxMessageEntity`.
 * عامل `OutboxRetryWorker` يقرأ `PENDING` دوريًا ويعيد الإرسال حتى النجاح أو انتهاء
 * عدد المحاولات. المفتاح المُستقر `idempotencyKey` يمنع التكرار حتى مع إعادة التشغيل.
 *
 * ## الضمانات
 * - **ذرّي**: الرسالة والـ outbox في نفس المعاملة — لا رسالة بلا outbox ولا outbox بلا رسالة.
 * - **مُستقر**: UUID ثابت لكل رسالة — إعادة الإرسال لا تُنشئ نسخة ثانية على الخادم.
 * - **متدرج**: تأخير أسي 10 ثوانٍ → 30 ثوانٍ → 2 دقيقة → 10 دقائق → 1 ساعة (حد 24 ساعة).
 * - **مُقيّد**: لا يعمل بلا شبكة (`NetworkType.CONNECTED`) ولا يوقظ الجهاز عبثًا.
 *
 * ## الميزات المتقدمة
 * - **أولوية الرسائل**: HIGH, NORMAL, LOW
 * - **دعم الوسائط**: صور، فيديو، ملفات، رسائل صوتية
 * - **Dead Letter Queue**: رسائل فاشلة نهائياً بعد 10 محاولات
 * - **Circuit Breaker**: حماية من cascade failure
 * - **مُقاييس مفصلة**: Prometheus-ready metrics
 */
@Entity(
    tableName = "outbox_messages",
    indices = [
        Index(value = ["status", "nextAttemptAt"]),
        Index(value = ["conversationId"]),
        Index(value = ["idempotencyKey"], unique = true),
        Index(value = ["priority", "nextAttemptAt"]),
        Index(value = ["status", "createdAt"])
    ]
)
data class OutboxMessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    /** الحمولة المشفرة الجاهزة للإرسال (Signal ciphertext) — لا يُعاد تشفيرها */
    val payload: ByteArray,
    val type: String = "CHAT", // CHAT, GROUP, REACTION, MEDIA_IMAGE, MEDIA_VIDEO, MEDIA_AUDIO, MEDIA_FILE, MEDIA_VOICE
    val status: String = "PENDING", // PENDING, SENDING, SENT, FAILED, DEAD_LETTER
    val createdAt: Long = System.currentTimeMillis(),
    val retryCount: Int = 0,
    val nextAttemptAt: Long = System.currentTimeMillis(),
    /** مفتاح يمنع التكرار عبر إعادة التشغيل — نفس الرسالة = نفس المفتاح */
    val idempotencyKey: String = id,
    val lastError: String? = null,
    /** أولوية الرسالة: HIGH (1), NORMAL (2), LOW (3) */
    val priority: Int = 2,
    /** نوع الوسائط إن وجد */
    val mediaType: String? = null, // IMAGE, VIDEO, AUDIO, FILE, VOICE
    /** مسار الملف المحلي للوسائط (مؤقت، يُحذف بعد الإرسال) */
    val localMediaPath: String? = null,
    /** مفتاح تشفير الوسائط (مشتق من مفتاح المحادثة) */
    val mediaEncryptionKey: String? = null
) {
    companion object {
        const val STATUS_PENDING = "PENDING"
        const val STATUS_SENDING = "SENDING"
        const val STATUS_SENT = "SENT"
        const val STATUS_FAILED = "FAILED"
        const val STATUS_DEAD_LETTER = "DEAD_LETTER"

        const val PRIORITY_HIGH = 1
        const val PRIORITY_NORMAL = 2
        const val PRIORITY_LOW = 3

        const val MEDIA_TYPE_IMAGE = "IMAGE"
        const val MEDIA_TYPE_VIDEO = "VIDEO"
        const val MEDIA_TYPE_AUDIO = "AUDIO"
        const val MEDIA_TYPE_FILE = "FILE"
        const val MEDIA_TYPE_VOICE = "VOICE"

        const val MAX_RETRY_COUNT = 10
        const val DEAD_LETTER_THRESHOLD = 10

        fun create(
            id: String,
            conversationId: String,
            payload: ByteArray,
            type: String = "CHAT",
            priority: Int = PRIORITY_NORMAL,
            mediaType: String? = null,
            localMediaPath: String? = null,
            mediaEncryptionKey: String? = null,
            idempotencyKey: String? = null
        ): OutboxMessageEntity {
            return OutboxMessageEntity(
                id = id,
                conversationId = conversationId,
                payload = payload,
                type = type,
                priority = priority,
                mediaType = mediaType,
                localMediaPath = localMediaPath,
                mediaEncryptionKey = mediaEncryptionKey,
                idempotencyKey = idempotencyKey ?: id
            )
        }
    }
}
