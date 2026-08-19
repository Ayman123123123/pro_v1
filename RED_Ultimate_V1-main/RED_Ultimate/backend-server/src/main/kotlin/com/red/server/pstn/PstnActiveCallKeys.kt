package com.red.server.pstn

import java.util.UUID

/**
 * موحّد صيغة مفتاح المكالمة النشطة في Redis.
 *
 * الصيغة القياسية: `callId:gatewayId:port` (ثلاثة أجزاء) — يكتبها
 * PstnCallService وPstnBridgeController.
 *
 * الصيغة القديمة (فترة انتقال): `gatewayId:port` (جزءان) — تُقرأ
 * للتوافق مع مفاتيح وُجدت قبل التوحيد، ولا يمكن استرجاع callId منها
 * فيُرجع callId فارغ.
 *
 * بوابة بلا معرّف (نشر بوابة واحدة) تُخزّن كنص `local` وتُترجَم إلى
 * معرّف ثابت حتى تبقى الدالة المُرجِعة `Triple<String, Int, UUID>`.
 */
object PstnActiveCallKeys {
    const val ACTIVE_PREFIX = "red:pstn:active:"
    const val CALL_PREFIX = "red:pstn:call:"
    const val BRIDGE_PREFIX = "red:pstn:bridge:"

    /** معرّف ثابت للبوابة المحلية (legacy single-gateway) عند غياب UUID. */
    val LOCAL_GATEWAY_ID: UUID = UUID.nameUUIDFromBytes("DINSTAR:LOCAL".toByteArray())

    fun activeKey(userId: UUID): String = "$ACTIVE_PREFIX$userId"

    /** فهرس عكسي callId → userId ليُتيح للمستمع تجديد TTL وحذف المفتاح. */
    fun callKey(callId: String): String = "$CALL_PREFIX$callId"

    /** مفتاح سرّ SIP المؤقت لمكالمة WebRTC-PSTN bridge. */
    fun bridgeSecretKey(userId: UUID, callId: String): String = "$BRIDGE_PREFIX$userId:$callId"

    fun format(callId: String, gatewayId: UUID?, port: Int): String =
        "$callId:${gatewayId ?: "local"}:$port"

    /**
     * يحلل قيمة المفتاح إلى (callId, port, gatewayId).
     * الصيغة القديمة بلا callId تُرجع callId فارغًا للتوافق.
     */
    fun parse(raw: String?): Triple<String, Int, UUID>? {
        if (raw.isNullOrBlank()) return null
        val parts = raw.split(":")
        return when (parts.size) {
            3 -> try {
                Triple(parts[0], parts[2].toInt(), gatewayId(parts[1]))
            } catch (_: Exception) { null }
            2 -> try {
                Triple("", parts[1].toInt(), gatewayId(parts[0]))
            } catch (_: Exception) { null }
            else -> null
        }
    }

    private fun gatewayId(raw: String): UUID = when (raw.lowercase()) {
        "local", "null", "configured" -> LOCAL_GATEWAY_ID
        else -> UUID.fromString(raw)
    }
}
