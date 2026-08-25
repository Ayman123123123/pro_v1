package com.red.server.websocket

import com.red.server.calls.CallHistoryService
import com.red.server.calls.CallRoute
import com.red.server.calls.CallType
import com.red.server.pstn.DinstarEventListener
import com.red.server.services.DinstarHardwareService
import org.asteriskjava.manager.event.HangupEvent
import org.asteriskjava.manager.event.ManagerEvent
import org.asteriskjava.manager.event.NewStateEvent
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * جسر الأحداث من خدمات DINSTAR المختلفة إلى WebSocket.
 *
 * يربط بين:
 * - DinstarHardwareService (أحداث HTTP من البوابة)
 * - DinstarEventListener (أحداث Asterisk AMI)
 * - DinstarWebSocketHandler (الإرسال للعملاء)
 *
 * يستدعى من خدمات الباكند عند حدوث أحداث تستحق البثّ الحي.
 */
@Component
class DinstarEventBridge(
    private val wsHandler: DinstarWebSocketHandler,
    private val hardware: DinstarHardwareService,
    private val callHistory: CallHistoryService
) {
    companion object {
        private val log = LoggerFactory.getLogger(DinstarEventBridge::class.java)
    }

    /**
     * بثّ حالة منفذ محدّثة عند تغيّرها.
     * يُستدعى من DinstarHeartbeatService عند كل دورة فحص.
     */
    fun onPortStatusChanged(gatewayHost: String, portIndex: Int, status: Map<String, Any?>) {
        // لا نبثّ كل تغيّر منفرد — البثّ الدوري في WebSocketHandler يكفي.
        // هذه الدالة للاستثناءات فقط (مكالمة نشطة/منتهية).
        val callState = status["callState"]?.toString() ?: return
        when {
            callState.equals("ACTIVE", ignoreCase = true) -> {
                log.debug("Port {} on {} now ACTIVE", portIndex, gatewayHost)
            }
            callState.equals("IDLE", ignoreCase = true) -> {
                log.debug("Port {} on {} now IDLE", portIndex, gatewayHost)
            }
        }
    }

    /**
     * بثّ حدث CDR جديد.
     * يُستدعى من DinstarEventListener عند Hangup.
     */
    fun onCallEnded(event: HangupEvent) {
        val channel = event.channel ?: return
        val lineNumber = channel.substringAfter('/').substringBefore('@')
        val port = lineNumber.filter { it.isDigit() }.toIntOrNull()
        val cause = event.causeTxt ?: "UNKNOWN"

        broadcastEvent("DINSTAR_CDR", mapOf(
            "type" to "call_end",
            "port" to port,
            "channel" to channel,
            "cause" to cause,
            "timestamp" to System.currentTimeMillis()
        ))
    }

    /**
     * بثّ حدث تغيير حالة المكالمة.
     */
    fun onCallStateChanged(event: NewStateEvent) {
        val state = event.channelStateDesc ?: return
        val channel = event.channel ?: return

        broadcastEvent("DINSTAR_CALL_STATE", mapOf(
            "channel" to channel,
            "state" to state,
            "timestamp" to System.currentTimeMillis()
        ))
    }

    /**
     * بثّ حدث استثناء.
     */
    fun onException(type: String, details: Map<String, Any?>) {
        broadcastEvent("DINSTAR_EXCEPTION", mapOf(
            "exceptionType" to type,
            "details" to details,
            "timestamp" to System.currentTimeMillis()
        ))
    }

    /**
     * بثّ حدث SMS وارد.
     */
    fun onIncomingSms(smsData: Map<String, Any?>) {
        broadcastEvent("DINSTAR_SMS", smsData)
    }

    /**
     * بثّ حدث USSD.
     */
    fun onUssdResponse(ussdData: Map<String, Any?>) {
        broadcastEvent("DINSTAR_USSD", ussdData)
    }

    private fun broadcastEvent(eventType: String, data: Map<String, Any?>) {
        try {
            when (eventType) {
                "DINSTAR_CDR" -> wsHandler.broadcastCdr(data)
                "DINSTAR_SMS" -> wsHandler.broadcastIncomingSms(data)
                "DINSTAR_USSD" -> wsHandler.broadcastUssd(data)
                "DINSTAR_EXCEPTION" -> wsHandler.broadcastException(data)
                else -> wsHandler.broadcastException(data + ("type" to eventType))
            }
        } catch (e: Exception) {
            log.warn("Failed to bridge event {}: {}", eventType, e.message)
        }
    }
}
