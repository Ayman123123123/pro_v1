package com.red.server.services

import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicLong

@Component
class DinstarSmsContract(private val jdbc: JdbcTemplate) {

    companion object {
        private val log = LoggerFactory.getLogger(DinstarSmsContract::class.java)
        private val DESTINATION_PATTERN = Regex("^\\d{1,24}$")
        private val fallbackSequence = AtomicLong(1)
        @JvmStatic
        fun isAccepted(response: Map<String, Any?>): Boolean =
            DinstarApiContract.isAccepted(response)
        @JvmStatic
        fun taskId(response: Map<String, Any?>): Long? =
            (response[DinstarApiContract.Sms.RES_TASK_ID] as? Number)?.toLong()
        @JvmStatic
        fun queueLength(response: Map<String, Any?>): Int? =
            (response[DinstarApiContract.Sms.RES_IN_QUEUE_SEND] as? Number)?.toInt()
                ?: (response[DinstarApiContract.Sms.RES_IN_QUEUE_QUERY] as? Number)?.toInt()
        @JvmStatic
        fun parseSendResults(response: Map<String, Any?>): List<SendResult> {
            @Suppress("UNCHECKED_CAST")
            val rows = response[DinstarApiContract.Sms.RES_RESULT] as? List<Map<String, Any?>>
                ?: return emptyList()
            return rows.mapNotNull { row ->
                val userId = (row["user_id"] as? Number)?.toLong() ?: return@mapNotNull null
                SendResult(
                    userId = userId,
                    port = (row["port"] as? Number)?.toInt(),
                    number = row["number"]?.toString(),
                    time = DinstarTime.parse(row["time"]?.toString()),
                    status = row["status"]?.toString(),
                    parts = (row["count"] as? Number)?.toInt(),
                    partsSucceeded = (row["succ_count"] as? Number)?.toInt(),
                    refId = (row["ref_id"] as? Number)?.toLong(),
                    imsi = row["imsi"]?.toString()
                )
            }
        }
        @JvmStatic
        fun parseDeliveryReports(response: Map<String, Any?>): List<DeliveryReport> {
            @Suppress("UNCHECKED_CAST")
            val rows = response[DinstarApiContract.Sms.RES_RESULT] as? List<Map<String, Any?>>
                ?: return emptyList()
            return rows.mapNotNull { row ->
                val refId = (row["ref_id"] as? Number)?.toLong() ?: return@mapNotNull null
                val code = (row["status_code"] as? Number)?.toInt()
                DeliveryReport(
                    refId = refId,
                    port = (row["port"] as? Number)?.toInt(),
                    number = row["number"]?.toString(),
                    time = DinstarTime.parse(row["time"]?.toString()),
                    statusCode = code,
                    outcome = DinstarApiContract.Sms.deliveryOutcome(code),
                    imsi = row["imsi"]?.toString()
                )
            }
        }
        @JvmStatic
        fun parseIncoming(response: Map<String, Any?>): List<IncomingMessage> {
            @Suppress("UNCHECKED_CAST")
            val rows = response[DinstarApiContract.Sms.RES_SMS] as? List<Map<String, Any?>>
                ?: (response["messages"] as? List<Map<String, Any?>>)
                ?: return emptyList()
            return rows.mapNotNull { row ->
                val number = (row["number"] ?: row["sender"] ?: row["from"])
                    ?.toString()?.trim()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val text = (row["text"] ?: row["content"] ?: row["msg"])
                    ?.toString()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                IncomingMessage(
                    incomingSmsId = (row["incoming_sms_id"] as? Number)?.toLong(),
                    port = (row["port"] as? Number)?.toInt(),
                    number = number,
                    smsc = row["smsc"]?.toString(),
                    text = text,
                    time = DinstarTime.parse(
                        (row["timestamp"] ?: row["time"] ?: row["datetime"])?.toString()
                    ),
                    imsi = row["imsi"]?.toString()
                )
            }
        }
        @JvmStatic
        fun inboxCounters(response: Map<String, Any?>): Pair<Int?, Int?> =
            (response[DinstarApiContract.Sms.RES_READ] as? Number)?.toInt() to
                (response[DinstarApiContract.Sms.RES_UNREAD] as? Number)?.toInt()
    }

    data class SendResult(
        val userId: Long,
        val port: Int?,
        val number: String?,
        val time: java.time.Instant?,
        val status: String?,
        val parts: Int?,
        val partsSucceeded: Int?,
        val refId: Long?,
        val imsi: String?
    ) {
        val handedToNetwork: Boolean get() = DinstarApiContract.Sms.isHandedToNetwork(status)
        val rejected: Boolean get() = DinstarApiContract.Sms.isNetworkRejected(status)
    }

    data class DeliveryReport(
        val refId: Long,
        val port: Int?,
        val number: String?,
        val time: java.time.Instant?,
        val statusCode: Int?,
        val outcome: DinstarApiContract.DeliveryOutcome,
        val imsi: String?
    )

    data class IncomingMessage(
        val incomingSmsId: Long?,
        val port: Int?,
        val number: String,
        val smsc: String?,
        val text: String,
        val time: java.time.Instant?,
        val imsi: String?
    )

    data class PreparedRecipients(
        val recipients: List<Map<String, Any?>>,
        val userIds: List<Long>
    )

    fun prepare(rawRecipients: List<Map<String, Any?>>): PreparedRecipients {
        require(rawRecipients.isNotEmpty()) { "At least one SMS recipient is required" }
        require(rawRecipients.size <= DinstarApiContract.Limits.MAX_SMS_RECIPIENTS) {
            "الحد الأقصى ${DinstarApiContract.Limits.MAX_SMS_RECIPIENTS} مستلمًا في الطلب الواحد"
        }
        val userIds = ArrayList<Long>(rawRecipients.size)
        val recipients = rawRecipients.map { raw ->
            val number = raw["number"]?.toString()?.trim().orEmpty()
            require(DESTINATION_PATTERN.matches(number)) { "SMS recipient must contain 1-24 digits" }
            val userId = nextUserId()
            userIds += userId
            raw.toMutableMap().apply {
                put(DinstarApiContract.Sms.REQ_NUMBER, number)
                put(DinstarApiContract.Sms.REQ_USER_ID, userId.toInt())
            }.toMap()
        }
        return PreparedRecipients(recipients, userIds)
    }

    fun prepareSingle(number: String): Pair<Map<String, Any?>, Long> {
        val prepared = prepare(listOf(mapOf(DinstarApiContract.Sms.REQ_NUMBER to number)))
        return prepared.recipients.first() to prepared.userIds.first()
    }

    private fun nextUserId(): Long =
        runCatching {
            jdbc.queryForObject("SELECT nextval(''dinstar_sms_user_id_seq'')", Long::class.java)
        }.getOrNull() ?: run {
            log.warn("dinstar_sms_user_id_seq غير متاح — استخدام عدّاد ذاكرة مؤقت")
            fallbackSequence.getAndIncrement()
        }
}
