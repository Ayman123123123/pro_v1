package com.red.server.sms

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface SmsMessageRepository : JpaRepository<SmsMessageEntity, UUID> {

    fun findByOwnerIdOrderByCreatedAtDesc(ownerId: UUID): List<SmsMessageEntity>

    fun findByOwnerIdIsNullOrderByCreatedAtDesc(): List<SmsMessageEntity>

    fun findByOwnerIdAndNumberOrderByCreatedAtAsc(ownerId: UUID, number: String): List<SmsMessageEntity>

    fun findByOwnerIdIsNullAndNumberOrderByCreatedAtAsc(number: String): List<SmsMessageEntity>

    fun findByStatusAndCreatedAtAfter(status: SmsStatus, after: Instant): List<SmsMessageEntity>

    @Query(
        "SELECT DISTINCT m.number FROM SmsMessageEntity m " +
            "WHERE (m.ownerId = :ownerId OR m.ownerId IS NULL) AND m.status = 'SENT' AND m.createdAt > :after"
    )
    fun findSentNumbersSince(@Param("ownerId") ownerId: UUID?, @Param("after") after: Instant): List<String>

    /**
     * أعلى مؤشّر وارد التُقط من هذه البوابة — نقطة الاستئناف.
     *
     * `query_incoming_sms` **يستهلك** الصندوق عند القراءة: ما يُقرأ لا يُعاد.
     * لذلك المؤشّر ليس تحسينًا للأداء بل شرطًا لعدم الفقد؛ ولو سُئل الجهاز
     * من الصفر بعد إعادة تشغيل، أعاد لا شيء وضاع ما بينهما.
     *
     * المؤشّر **لكل بوابة**: كل جهاز يعدّ وارده مستقلًا، فمؤشّر عام يجعل
     * الجهاز الأبطأ يتخطّى رسائله كلما تقدّم الأسرع.
     */
    @Query(
        "SELECT COALESCE(MAX(m.incomingSmsId), 0) FROM SmsMessageEntity m " +
            "WHERE m.direction = 'IN' AND m.gatewayId = :gatewayId"
    )
    fun maxIncomingIdForGateway(@Param("gatewayId") gatewayId: UUID): Long

    /** المؤشّر حين لا تُعرف البوابة (نشر أحادي قديم بلا `gateway_id`). */
    @Query(
        "SELECT COALESCE(MAX(m.incomingSmsId), 0) FROM SmsMessageEntity m " +
            "WHERE m.direction = 'IN' AND m.gatewayId IS NULL"
    )
    fun maxIncomingIdUnassigned(): Long

    /**
     * هل التُقطت هذه الرسالة الواردة سابقًا؟
     *
     * `incoming_sms_id` مُعرَّف من الجهاز فهو المفتاح الطبيعي، لكنه يتفرّد
     * **داخل بوابة واحدة** فقط: بوابتان تعدّان من 1 استقلالًا.
     */
    fun existsByGatewayIdAndIncomingSmsId(gatewayId: UUID?, incomingSmsId: Long): Boolean

    /** المرسَلة التي تنتظر تقرير تسليم ولها `ref_id` يُطابَق به. */
    fun findByStatusAndDinstarRefIdIsNotNullAndCreatedAtAfter(
        status: SmsStatus,
        after: Instant
    ): List<SmsMessageEntity>
}

interface SmsReadMarkerRepository : JpaRepository<SmsReadMarker, SmsReadMarkerKey> {
    fun findByUserIdAndNumber(userId: UUID, number: String): SmsReadMarker?
    fun findByUserId(userId: UUID): List<SmsReadMarker>
}