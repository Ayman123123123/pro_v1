package com.red.server.pstn

import com.red.server.calls.CallHistoryService
import com.red.server.websocket.CallWebSocketHandler
import org.asteriskjava.manager.ManagerEventListener
import org.asteriskjava.manager.event.ManagerEvent
import org.asteriskjava.manager.event.NewStateEvent
import org.asteriskjava.manager.event.HangupEvent
import org.asteriskjava.manager.event.BridgeEvent
import org.asteriskjava.manager.event.OriginateResponseEvent
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Component

/**
 * Asterisk AMI event listener that tracks DINSTAR channel state changes
 * and correlates them with call history records.
 *
 * ## دور هذا المستمع في تدفّق المراحل
 *
 * كانت أحداث AMI تُكتب في اللوق فقط ولا تصل إلى صاحب المكالمة، فبقيت
 * شاشات PSTN في التطبيق بلا مصدر بيانات. صار كل حدث هنا يُترجَم إلى
 * مرحلة عبر [PstnCallProgressTracker] ثم يُدفع إلى صاحبها وحده على
 * `/ws/calls`.
 *
 * التبعيتان الجديدتان عبر [ObjectProvider] تجنّبًا لدورة حقن: يُنشئ
 * `PstnManager` هذا المستمع، وسلسلة `CallWebSocketHandler` تعود إليه.
 */
@Component
class DinstarEventListener(
    private val history: CallHistoryService,
    private val loadBalancer: DinstarLoadBalancer,
    private val tracker: PstnCallProgressTracker,
    private val callSockets: ObjectProvider<CallWebSocketHandler>
) : ManagerEventListener {
    companion object { private val log = LoggerFactory.getLogger(DinstarEventListener::class.java) }

    override fun onManagerEvent(event: ManagerEvent) {
        when (event) {
            is OriginateResponseEvent -> handleOriginateResponse(event)
            is NewStateEvent -> handleStateChange(event)
            is HangupEvent -> handleHangup(event)
            is BridgeEvent -> handleBridge(event)
            else -> Unit // Ignore unhandled event types
        }
    }

    /**
     * الحدث الوحيد الذي يحمل `actionId` و`channel` معًا — به يُربط
     * اسم القناة بالمكالمة، فتصبح كل الأحداث اللاحقة قابلة للتوجيه.
     */
    private fun handleOriginateResponse(event: OriginateResponseEvent) {
        val callId = event.actionId ?: return
        val channel = event.channel ?: return
        val entry = tracker.attachChannel(callId, channel) ?: return
        log.info("PSTN originate response: call={} channel={}", callId, channel)
        publish(entry.redId, callId, PstnCallProgressTracker.Stage.INVITING, entry.number)
    }

    private fun handleStateChange(event: NewStateEvent) {
        val state = event.channelStateDesc ?: return
        val channel = event.channel ?: return
        val lineNumber = channel.substringAfter('/').substringBefore('@')

        log.info("DINSTAR line {} state → {} (channel={})", lineNumber, state, channel)

        val stage = when (state) {
            "Up" -> PstnCallProgressTracker.Stage.ACTIVE
            "Ringing", "Ring" -> PstnCallProgressTracker.Stage.RINGING
            else -> return
        }
        val entry = tracker.advanceByChannel(channel, stage) ?: return

        // المكالمة أُجيبت فعلًا: يُسجَّل ذلك في السجلّ ليحتسب المدّة
        if (stage == PstnCallProgressTracker.Stage.ACTIVE) {
            runCatching { history.answer(entry.callId) }
                .onFailure { log.debug("PSTN answer bookkeeping skipped for {}: {}", entry.callId, it.message) }
        }
        publish(entry.redId, entry.callId, stage, entry.number)
    }

    private fun handleHangup(event: HangupEvent) {
        val channel = event.channel ?: return
        val lineNumber = channel.substringAfter('/').substringBefore('@')
        val cause = event.causeTxt ?: "UNKNOWN"

        log.info("DINSTAR line {} hung up — cause: {}", lineNumber, cause)

        tracker.finishByChannel(channel)?.let { entry ->
            runCatching { history.end(entry.callId) }
                .onFailure { log.debug("PSTN end bookkeeping skipped for {}: {}", entry.callId, it.message) }
            publish(
                entry.redId,
                entry.callId,
                PstnCallProgressTracker.Stage.ENDED,
                entry.number,
                mapOf("cause" to cause)
            )
        }

        // Automatic port release in load balancer when channel hangs up from Asterisk / Dinstar
        // Supports up to 32 ports (UC2000-VE-32G) not just 0..7
        val port = lineNumber.filter { it.isDigit() }.toIntOrNull()
        if (port != null && port in 0..31) {
            loadBalancer.releasePort(port)
            log.info("DINSTAR port {} released in load balancer on Asterisk hangup (broad release)", port)
        } else {
            // Fallback: try to extract port from channel name like PJSIP/dinstar-gw-192-168-1-1-5
            val fallbackPort = Regex("""[-_](\d+)$""").find(channel)?.groupValues?.get(1)?.toIntOrNull()
            if (fallbackPort != null && fallbackPort in 0..31) {
                loadBalancer.releasePort(fallbackPort)
                log.info("DINSTAR port {} released via fallback parsing", fallbackPort)
            }
        }
    }

    private fun handleBridge(event: BridgeEvent) {
        log.debug("Bridge event: channel1={}, channel2={}", event.channel1, event.channel2)
        // الجسر يُنشأ بين قناتين؛ أيّهما كانت قناتنا فهي التي تتقدّم.
        listOfNotNull(event.channel1, event.channel2).forEach { channel ->
            tracker.advanceByChannel(channel, PstnCallProgressTracker.Stage.BRIDGING)?.let { entry ->
                publish(entry.redId, entry.callId, PstnCallProgressTracker.Stage.BRIDGING, entry.number)
            }
        }
    }

    private fun publish(
        redId: String,
        callId: String,
        stage: PstnCallProgressTracker.Stage,
        number: String,
        payload: Map<String, Any?> = emptyMap()
    ) {
        val handler = callSockets.ifAvailable ?: return
        runCatching { handler.deliverPstnProgress(redId, callId, stage.name, number, payload) }
            .onFailure { log.warn("Failed to publish PSTN stage {} for {}: {}", stage, callId, it.message) }
    }
}
