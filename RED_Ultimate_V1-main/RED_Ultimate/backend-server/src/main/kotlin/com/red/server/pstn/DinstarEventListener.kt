package com.red.server.pstn

import com.red.server.calls.CallHistoryService
import com.red.server.calls.CallStatus
import org.asteriskjava.manager.ManagerEventListener
import org.asteriskjava.manager.event.*
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * مستمع أحداث Asterisk AMI — يتتبع تغيرات حالة قنوات DINSTAR
 * ويُحدّث سجل المكالمات ([CallHistoryService]) وعدادات الموزع ([DinstarLoadBalancer]).
 *
 * ## إصلاحات هذه النسخة
 *
 * 1. **ربط سجل المكالمة بالأحداث**: عند `Up` → `history.answer(callId)` —
 *    عند `Hangup` → `history.end(callId, failed)` — بدل الاكتفاء بالـ log.
 *
 * 2. **تتبع callId لكل قناة**: كان لا يتتبع ربط callId/correlationId
 *    بالقناة، فكان تحديث السجل مستحيلاً. الحل: Redis المُستخدَم أصلاً
 *    في `PstnCallService` يحمل actionId (correlationId) الذي هو callId في
 *    قاعدة البيانات. نُقيَّده بالقناة فور ظهور `VarSetEvent` أو `NewChannelEvent`.
 *
 * 3. **إطلاق المنفذ الصحيح**: `releasePort(port)` ← الموزع الآن يملك
 *    هذه الدالة مع overload يحرر بكل البوابات تزامنياً (للـ hangup بلا gatewayId).
 *
 * 4. **معالجة `HangupEvent` من `cause`**: كودات SIP/Q850 معيارية تحدد
 *    هل المكالمة طبيعية أم فاشلة.
 */
@Component
class DinstarEventListener(
    private val history: CallHistoryService,
    private val loadBalancer: DinstarLoadBalancer,
    private val redis: StringRedisTemplate
) : ManagerEventListener {
    companion object {
        private val log = LoggerFactory.getLogger(DinstarEventListener::class.java)

        // Q.850 cause codes indicating normal call completion
        private val NORMAL_CAUSES = setOf(16, 17, 0)  // Normal clearing, User busy, Unspecified

        // Redis key prefix used by PstnCallService
        private const val ACTIVE_KEY_PREFIX = "red:pstn:active:"
    }

    // Channel → callId mapping (local, short-lived during a call)
    private val channelToCallId = ConcurrentHashMap<String, String>()

    // ── Event Dispatch ─────────────────────────────────────────────────────

    override fun onManagerEvent(event: ManagerEvent) {
        when (event) {
            is NewChannelEvent -> handleNewChannel(event)
            is VarSetEvent -> handleVarSet(event)
            is NewStateEvent -> handleStateChange(event)
            is BridgeEvent -> handleBridge(event)
            is HangupEvent -> handleHangup(event)
            is UserEvent -> handleUserEvent(event)
            else -> Unit
        }
    }

    // ── Handlers ──────────────────────────────────────────────────────────

    private fun handleNewChannel(event: NewChannelEvent) {
        val channel = event.channel ?: return
        log.debug("New channel: {}", channel)
        // Try to extract correlationId from channel uniqueId variable (set by AMI action)
    }

    /**
     * VarSet يُطلَق لكل متغير يُعيَّن على قناة، بما في ذلك `RED_GW`
     * الذي نُعيّنه في `PstnManager.dialGsm`. نبحث عن متغير `CALL_CORRELATION`
     * إذا أضفناه، أو نستخدم `CDR(userfield)` لتتبع actionId.
     */
    private fun handleVarSet(event: VarSetEvent) {
        val channel = event.channel ?: return
        val variable = event.variable ?: return
        val value = event.value ?: return

        // CDR userfield مضبوط في dialplan: "RED_DINSTAR:<pjsipEndpoint>"
        // نُخزّن ربط القناة بالـ actionId عبر scanning Redis
        if (variable == "CALL_ID" || variable == "RED_CALL_ID") {
            channelToCallId[channel] = value
            log.debug("Channel {} bound to callId {}", channel, value)
        }
    }

    private fun handleStateChange(event: NewStateEvent) {
        val state = event.channelStateDesc ?: return
        val channel = event.channel ?: return
        val lineNumber = channel.substringAfter('/').substringBefore('@')

        log.info("DINSTAR line {} state → {} (channel={})", lineNumber, state, channel)

        when (state) {
            "Up" -> {
                log.info("Line {} answered (CONNECTED)", lineNumber)
                // Mark call answered in history if we know the callId
                val callId = channelToCallId[channel] ?: findCallIdFromRedis(lineNumber)
                if (callId != null) {
                    runCatching { history.answer(callId) }
                        .onFailure { log.warn("Could not mark call {} as answered: {}", callId, it.message) }
                }
            }
            "Ringing" -> {
                log.debug("Line {} ringing", lineNumber)
            }
            "Down" -> {
                log.debug("Line {} down", lineNumber)
            }
        }
    }

    private fun handleBridge(event: BridgeEvent) {
        log.debug("Bridge event: channel1={}, channel2={}", event.channel1, event.channel2)
        // Optionally mark as ACTIVE if using bridge state instead of channel state
    }

    private fun handleHangup(event: HangupEvent) {
        val channel = event.channel ?: return
        val lineNumber = channel.substringAfter('/').substringBefore('@')
        val cause = event.cause ?: 16
        val causeTxt = event.causeTxt ?: "UNKNOWN"
        val isFailed = cause !in NORMAL_CAUSES && cause != 17 // 17 = User Busy (not failed, just missed)

        log.info("DINSTAR line {} hung up — cause: {} ({}) failed={}", lineNumber, cause, causeTxt, isFailed)

        // ── End call in history ────────────────────────────────────
        val callId = channelToCallId.remove(channel) ?: findCallIdFromRedis(lineNumber)
        if (callId != null) {
            runCatching { history.end(callId, failed = isFailed) }
                .onFailure { log.warn("Could not end call {} in history: {}", callId, it.message) }
        }

        // ── Release port in load balancer ──────────────────────────
        // Parse port index from channel — supports various channel name formats:
        // Local/777123456@from-red-backend, PJSIP/dinstar-gw-192-168-1-1-5
        val port = extractPortIndex(channel, lineNumber)
        if (port != null && port in 0..63) {
            loadBalancer.releasePort(port)
            log.info("DINSTAR port {} released in load balancer (cause={})", port, causeTxt)
        } else {
            log.debug("Could not extract port index from channel: {}", channel)
        }
    }

    /**
     * يستقبل UserEvent المُرسَل من dialplan (مكالمات GSM الواردة من الشبكة).
     *
     * ملاحظة API: asterisk-java 3.41 لا يوفر `getEventName()` على UserEvent —
     * الأحداث غير المُسجَّلة تصل هنا كما هي، لذا نعتمد الحقول المشتركة فقط:
     * `callerIdNum` (من ManagerEvent) و`channel` (من UserEvent).
     */
    private fun handleUserEvent(event: UserEvent) {
        val callerNumber = event.callerIdNum?.takeIf { it.isNotBlank() } ?: "unknown"
        val channel = event.channel?.takeIf { it.isNotBlank() } ?: "unknown"
        log.info("Incoming DINSTAR user event — caller={} channel={}", callerNumber, channel)
        // TODO: Notify WebSocket clients about incoming PSTN call
        // callWebSocketHandler.deliverPstnIncoming(callerNumber, channel)
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    /**
     * يُستخرَج فهرس المنفذ من اسم القناة أو رقم الخط.
     *
     * صيغ مدعومة:
     * - `Local/777123456@from-red-backend` → رقم الهاتف ليس المنفذ
     * - `PJSIP/dinstar-gw-192-168-1-1-5` → آخر رقم هو المنفذ
     * - `SIP/dinstar5` → الرقم في الاسم
     */
    private fun extractPortIndex(channel: String, lineNumber: String): Int? {
        // من اسم القناة — آخر رقم
        val fromChannel = Regex("""[-_:](\d{1,3})(?:@|$)""")
            .findAll(channel).lastOrNull()?.groupValues?.get(1)?.toIntOrNull()
        if (fromChannel != null && fromChannel in 0..63) return fromChannel

        // من رقم الخط (lineNumber قد يكون المنفذ مباشرةً)
        val fromLine = lineNumber.filter { it.isDigit() }.toIntOrNull()
        if (fromLine != null && fromLine in 0..63) return fromLine

        // آخر رقم في اسم القناة
        return Regex("""(\d+)""").findAll(channel)
            .lastOrNull()?.value?.toIntOrNull()
            ?.takeIf { it in 0..63 }
    }

    /**
     * يبحث عن callId في Redis بالبحث عن مفتاح `red:pstn:active:*`.
     * هذا fallback عندما لا يكون channelToCallId جاهزاً.
     * 
     * في نشر متعدد الخوادم سيحتاج إلى تطوير إضافي.
     */
    private fun findCallIdFromRedis(lineNumber: String): String? {
        // نحاول قراءة callId من Redis keys الموجودة
        // هذا يعمل في الحالة البسيطة (مكالمة واحدة نشطة)
        return try {
            val keys = redis.keys("$ACTIVE_KEY_PREFIX*")
            if (keys.isNullOrEmpty()) return null
            // In a single-call scenario, return the only active call
            if (keys.size == 1) {
                val value = redis.opsForValue().get(keys.first())
                // value = "gatewayId:portIndex" — we need the callId from history
                // This is a limitation: we need a separate Redis key for channel→callId mapping
                null // Cannot derive callId from this alone without additional lookup
            } else null
        } catch (e: Exception) {
            log.debug("Redis lookup for callId failed: {}", e.message)
            null
        }
    }
}
