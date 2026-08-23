package com.red.server.services

import java.util.concurrent.atomic.AtomicInteger

/**
 * عقد الرسائل مع واجهة DINSTAR HTTP API.
 *
 * يحتفظ RED بارتباط عددي فريد لكل مستلم لأن البوابة تشترط `user_id` رقمياً
 * وتعيده عند استعلام النتيجة. لا يمرَّر معرف RED النصي إلى البوابة.
 */
object DinstarSmsContract {
    private val nextSequence = AtomicInteger((System.currentTimeMillis() % Int.MAX_VALUE).toInt())
    private val destinationPattern = Regex("^\\d{1,24}$")

    data class PreparedRecipients(
        val recipients: List<Map<String, Any?>>,
        val userIds: List<Int>
    )

    fun prepare(rawRecipients: List<Map<String, Any?>>): PreparedRecipients {
        require(rawRecipients.isNotEmpty()) { "At least one SMS recipient is required" }

        val userIds = ArrayList<Int>(rawRecipients.size)
        val recipients = rawRecipients.map { raw ->
            val number = raw["number"]?.toString()?.trim().orEmpty()
            require(destinationPattern.matches(number)) { "SMS recipient must contain 1-24 digits" }
            val userId = nextUserId()
            userIds += userId

            raw.toMutableMap().apply {
                put("number", number)
                put("user_id", userId)
            }.toMap()
        }
        return PreparedRecipients(recipients, userIds)
    }

    fun isAccepted(response: Map<String, Any?>): Boolean =
        (response["error_code"] as? Number)?.toInt() in setOf(200, 202)

    private fun nextUserId(): Int {
        while (true) {
            val current = nextSequence.get()
            val next = if (current == Int.MAX_VALUE) 1 else current + 1
            if (nextSequence.compareAndSet(current, next)) return next
        }
    }
}
