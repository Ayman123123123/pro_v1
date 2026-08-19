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
    var readAt: Instant? = null
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