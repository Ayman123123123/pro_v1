package com.red.server.sms

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Table
import java.io.Serializable
import java.time.Instant
import java.util.UUID

/** اتجاه الرسالة: صادرة من بوابة RED أو واردة من الشبكة. */
enum class SmsDirection { OUT, IN }

/**
 * حالة الرسالة:
 * - PENDING: أُدخلت للطابور
 * - SENT: قَبِلها DINSTAR (أُرسلت للشبكة)
 * - DELIVERED: وصلت للجهاز المستلم (تقرير تسليم)
 * - FAILED: رفضتها البوابة أو انتهت مهلتها
 * - RECEIVED: رسالة واردة من الشبكة (القراءة تُتتبع بـ sms_conversation_read)
 */
enum class SmsStatus { PENDING, SENT, DELIVERED, FAILED, RECEIVED }

/**
 * رسالة SMS مخزّنة. الواردة مشتركة (owner_id = NULL) لأن شرائح البوابة
 * مشتركة بين المستخدمين؛ الصادرة منسوبة لمستخدمها فقط.
 */
@Entity
@Table(name = "sms_messages")
class SmsMessageEntity(
    @Id
    var id: UUID = UUID.randomUUID(),

    @Column(name = "owner_id")
    var ownerId: UUID? = null,

    @Column(nullable = false, length = 20)
    var number: String = "",

    @Column(nullable = false, columnDefinition = "TEXT")
    var content: String = "",

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 4)
    var direction: SmsDirection = SmsDirection.OUT,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    var status: SmsStatus = SmsStatus.PENDING,

    @Column
    var port: Int? = null,

    @Column(name = "gateway_id")
    var gatewayId: UUID? = null,

    @Column(name = "sms_parts", nullable = false)
    var smsParts: Int = 1,

    @Column(name = "error_text", length = 255)
    var errorText: String? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "sent_at")
    var sentAt: Instant? = null,

    @Column(name = "delivered_at")
    var deliveredAt: Instant? = null,

    @Column(name = "read_at")
    var readAt: Instant? = null,

    // ── ربط تقارير الشبكة (هجرة V39) ──────────────────────────────────────
    //
    // الأعمدة العشرة التالية أضافتها V39 «Sms Delivery Correlation» في القاعدة،
    // لكن الكيان لم يُحدَّث فبقيت **فارغة أبديًا**: JPA لا يكتب عمودًا لا يعرفه.
    // النتيجة أن كل ما بُنيت من أجله الهجرة كان معطوبًا صامتًا:
    //
    //  * `incomingSmsId` هو مؤشّر الوارد. بدونه لا سبيل لمعرفة أين توقّفت
    //    القراءة، فكان الالتقاط يعتمد حيلة «نفس النص خلال دقيقتين» — تُسقِط
    //    رسالتين متطابقتين مشروعتين، وتُدخل مكرّرًا بعد الدقيقتين. والأخطر أن
    //    `query_incoming_sms` **يستهلك** الوارد عند القراءة، فالمفقود يُفقد نهائيًا.
    //  * `dinstarRefId` هو المفتاح الوحيد الذي تُطابَق به تقارير التسليم
    //    (3GPP TS 23.040). بدونه كانت المطابقة بالرقم وحده، فتقرير رسالة
    //    يُنسَب لأخرى إلى الرقم نفسه.
    //  * `deliveryStatusCode` الرمز الرقمي الخام — يُبقي التشخيص ممكنًا حين
    //    يتغيّر تصنيفنا للحالة.
    @Column(name = "dinstar_user_id")
    var dinstarUserId: Long? = null,

    @Column(name = "dinstar_ref_id")
    var dinstarRefId: Long? = null,

    @Column(name = "dinstar_task_id")
    var dinstarTaskId: Long? = null,

    @Column(name = "incoming_sms_id")
    var incomingSmsId: Long? = null,

    @Column(name = "encoding", length = 12)
    var encoding: String? = null,

    @Column(name = "delivery_status_code")
    var deliveryStatusCode: Int? = null,

    @Column(name = "parts_confirmed")
    var partsConfirmed: Int? = null,

    /** IMSI الشريحة التي حملت الرسالة — يُثبّت المنفذ حتى لو أُعيد ترتيبه. */
    @Column(name = "sender_imsi", length = 32)
    var senderImsi: String? = null,

    @Column(name = "last_polled_at")
    var lastPolledAt: Instant? = null,

    @Column(name = "retry_count", nullable = false)
    var retryCount: Int = 0
)

/** مفتاح مركّب لعلامة القراءة: (مستخدم، رقم). */
class SmsReadMarkerKey : Serializable {
    var userId: UUID? = null
    var number: String? = null

    constructor()
    constructor(userId: UUID, number: String) {
        this.userId = userId
        this.number = number
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SmsReadMarkerKey) return false
        return userId == other.userId && number == other.number
    }

    override fun hashCode(): Int = (userId?.hashCode() ?: 0) * 31 + (number?.hashCode() ?: 0)
}

/**
 * علامة قراءة محادثة SMS لكل مستخدم على حدة — لأن الرسائل الواردة
 * مشتركة عبر الشرائح، قراءةُ أحدهما لا تعني قراءةَ الآخر.
 */
@Entity
@Table(name = "sms_conversation_read")
@IdClass(SmsReadMarkerKey::class)
class SmsReadMarker(
    @Id
    @Column(name = "user_id")
    var userId: UUID,

    @Id
    @Column(name = "number", length = 20)
    var number: String,

    @Column(name = "last_read_at", nullable = false)
    var lastReadAt: Instant = Instant.now()
)