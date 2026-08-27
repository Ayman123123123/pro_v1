package com.red.server.pstn

import com.red.server.calls.CallHistoryService
import com.red.server.calls.CallRoute
import com.red.server.calls.CallStatus
import com.red.server.calls.CallType
import com.red.server.services.NotificationService
import com.red.server.websocket.PstnEventWebSocketHandler
import org.asteriskjava.manager.ManagerEventListener
import org.asteriskjava.manager.event.*
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import jakarta.annotation.PreDestroy
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Lazy
import com.red.server.auth.repository.UserAccountRepository
import com.red.server.services.DinstarFleetService
import java.time.Duration
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.jdbc.core.JdbcTemplate

@Component
class DinstarEventListener(
    private val history: CallHistoryService,
    private val loadBalancer: DinstarLoadBalancer,
    private val redis: StringRedisTemplate,
    private val publisher: ApplicationEventPublisher,
    private val users: UserAccountRepository,
    @Lazy private val pstnEvents: PstnEventWebSocketHandler,
    private val notifications: NotificationService,
    @Lazy private val pstnManager: PstnManager,
    private val objectMapper: ObjectMapper,
    private val fleet: DinstarFleetService,
    private val jdbc: JdbcTemplate,
    private val progress: PstnCallProgressTracker,
    @Lazy private val eventBridge: com.red.server.websocket.DinstarEventBridge
) : ManagerEventListener {
    companion object {
        private val log = LoggerFactory.getLogger(DinstarEventListener::class.java)

        private val NORMAL_CAUSES = setOf(16, 17, 0)
        private const val ACTIVE_KEY_PREFIX = "red:pstn:active:"
        private const val INCOMING_CHANNEL_PREFIX = "red:pstn:incoming:"
        /** فهرس قصير العمر لتمرير callId فقط إلى endpoint تجهيز وسائط الوارد. */
        private const val INCOMING_CALL_PREFIX = "red:pstn:incoming-call:"
        private const val CHANNEL_CALLID_PREFIX = "red:pstn:channel-callid:"
        /** منفذ GSM الفيزيائي المرتبط بالقناة (من RED_PORT_INDEX في VarSet). */
        private const val CHANNEL_PORT_PREFIX = "red:pstn:channel-port:"
        private const val INCOMING_TTL_SECONDS = 120L
        private const val CHANNEL_TTL_HOURS = 2L
    }

    /**
     * منفّذ مخصص لمعالجة أحداث AMI الثقيلة (Mongo/JDBC/Redis/FCM).
     * خيط واحد يحافظ على ترتيب الأحداث نفسه، لكنه يفصل المعالجة عن خيط
     * قارئ asterisk-java حتى لا يعطّل FCM المتزامن كل أحداث المكالمات.
     */
    private val amiExecutor: java.util.concurrent.ExecutorService = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
        Thread(r, "dinstar-ami-events").apply { isDaemon = true }
    }

    @PreDestroy
    fun shutdownExecutor() {
        amiExecutor.shutdown()
        runCatching { amiExecutor.awaitTermination(5, TimeUnit.SECONDS) }
    }

    override fun onManagerEvent(event: ManagerEvent) {
        // تشخيص مؤقت: أي حدث يحمل سياق from-dinstar أو الرقم المطلوب يُسجَّل
        // لكشف الصيغة التي تُسلّمها asterisk-java فعلياً (UserEvent أم Generic).
        val ctx = runCatching { event.javaClass.getDeclaredField("context") }.getOrNull()
        if (event.toString().contains("from-dinstar", true) ||
            (ctx != null && runCatching { ctx.isAccessible = true; ctx.get(event)?.toString() }.getOrNull() == "from-dinstar")
        ) {
            log.info("AMI-EVENT[{}] {} | toString={}", event.javaClass.simpleName, event.dateReceived ?: "", event.toString().take(300))
        }
        when (event) {
            is NewChannelEvent -> handleNewChannel(event)
            is VarSetEvent -> handleVarSet(event)
            is NewStateEvent -> amiExecutor.submit { runCatching { handleStateChange(event) }.onFailure { log.warn("AMI NewState error: {}", it.message) } }
            is BridgeEvent -> handleBridge(event)
            is HangupEvent -> amiExecutor.submit { runCatching { handleHangup(event) }.onFailure { log.warn("AMI Hangup error: {}", it.message) } }
            is UserEvent -> if (isIncomingDinstarEvent(event)) {
                amiExecutor.submit { runCatching { handleUserEvent(event) }.onFailure { log.warn("AMI UserEvent error: {}", it.message) } }
            }
            else -> Unit
        }
    }

    /** فلترة مبكرة رخيصة على خيط القارئ — المعالجة الفعلية تتم في المنفّذ. */
    private fun isIncomingDinstarEvent(event: UserEvent): Boolean =
        event.context.equals("from-dinstar", ignoreCase = true)

    private fun handleNewChannel(event: NewChannelEvent) {
        val channel = event.channel ?: return
        log.debug("New channel: {}", channel)
    }

    private fun handleVarSet(event: VarSetEvent) {
        val channel = event.channel ?: return
        val variable = event.variable ?: return
        val value = event.value ?: return
        if (variable == "CALL_ID" || variable == "RED_CALL_ID") {
            redis.opsForValue().set("$CHANNEL_CALLID_PREFIX$channel", value, Duration.ofHours(CHANNEL_TTL_HOURS))
            pstnManager.bindChannel(value, channel)
            // اربط القناة بالمتتبِّع فور معرفتها — هذا هو المفصل الذي يجعل كل
            // حدث AMI لاحق (Ringing/Up/Hangup) قابلاً للتوجيه إلى صاحب المكالمة.
            // كان attachChannel لا يُستدعى من أي مكان فبقيت خريطة القنوات فارغة
            // ولم تصل مراحل المكالمة الصادرة إلى التطبيق إطلاقاً.
            runCatching { progress.attachChannel(value, channel) }
            log.debug("Channel {} bound to callId {}", channel, value)
        }
        // C4 FIX: التقاط RED_PORT_INDEX من dialplan — هذا هو المنفذ الحقيقي
        // وليس رقم القناة الداخلي. يُخزن لربط القناة بالمنفذ الفيزيائي.
        if (variable == "RED_PORT_INDEX") {
            val portIdx = value.toIntOrNull()
            if (portIdx != null && portIdx in 0..63) {
                redis.opsForValue().set("$CHANNEL_PORT_PREFIX$channel", portIdx.toString(), Duration.ofHours(CHANNEL_TTL_HOURS))
                log.debug("Channel {} bound to portIndex {}", channel, portIdx)
            }
        }
    }

    private fun handleStateChange(event: NewStateEvent) {
        val state = event.channelStateDesc ?: return
        val channel = event.channel ?: return
        val lineNumber = channel.substringAfter('/').substringBefore('@')

        log.info("DINSTAR line {} state -> {} (channel={})", lineNumber, state, channel)

        // حلّ callId لهذه القناة مرة واحدة: مفتاح Redis أولاً (الأدق، مضبوط
        // من RED_CALL_ID)، ثم تخمين المنفذ كاحتياط أخير.
        val callId = redis.opsForValue().get("$CHANNEL_CALLID_PREFIX$channel")
            ?: findCallIdFromPort(extractPortIndex(channel, lineNumber))

        when (state) {
            "Ringing" -> {
                log.debug("Line {} ringing", lineNumber)
                callId?.let { emitCallProgress(it, PstnCallProgressTracker.Stage.RINGING, mapOf("port" to lineNumber)) }
            }
            "Up" -> {
                log.info("Line {} answered (CONNECTED)", lineNumber)
                if (callId != null) {
                    runCatching { history.answer(callId) }
                        .onFailure { log.warn("Could not mark call {} as answered: {}", callId, it.message) }
                    emitCallProgress(callId, PstnCallProgressTracker.Stage.ACTIVE, mapOf("port" to lineNumber))
                }
            }
            "Down" -> log.debug("Line {} down", lineNumber)
        }

        // جسر لوحة الإدارة/الأسطول: بثّ حالة القناة الخام على `/ws/dinstar`.
        // كان DinstarEventBridge bean حيًّا بلا أي منتِج (كل دواله ميتة)، فلم
        // تصل حالات القنوات إلى مراقبي البوابة إطلاقًا.
        runCatching { eventBridge.onCallStateChanged(event) }
            .onFailure { log.debug("dinstar bridge state: {}", it.message) }
    }

    private fun handleBridge(event: BridgeEvent) {
        log.debug("Bridge event: channel1={}, channel2={}", event.channel1, event.channel2)
        // جسر مساري صوت = المكالمة الصادرة دخلت مرحلة BRIDGING. نوجّه الحدث
        // للطرفين المعروفين إن كان أيّهما مكالمة PSTN صادرة متتبَّعة.
        listOfNotNull(event.channel1, event.channel2).forEach { ch ->
            val callId = redis.opsForValue().get("$CHANNEL_CALLID_PREFIX$ch") ?: return@forEach
            emitCallProgress(callId, PstnCallProgressTracker.Stage.BRIDGING, emptyMap())
        }
    }

    /**
     * ينقل مكالمة PSTN صادرة إلى مرحلة جديدة ويبثّها لصاحبها عبر `/ws/pstn`.
     *
     * يوحّد الحلقة التي كانت مقطوعة: المتتبِّع كان يملك كل المنطق لكن لم
     * يُستدعَ، وبابليشر WebSocket (`pushPstnCallEvent`) كان معرَّفاً بلا
     * منادٍ. هنا يلتقيان: تقدّم محميّ من التراجع ثم بثّ حدث واحد لكل انتقال
     * فعلي فقط (لأن `advanceByCallId` يُرجع null على التكرار/التراجع).
     */
    private fun emitCallProgress(callId: String, stage: PstnCallProgressTracker.Stage, extra: Map<String, Any?>) {
        val updated = runCatching { progress.advanceByCallId(callId, stage) }.getOrNull() ?: return
        val accountId = resolveAccountIdForRedId(updated.redId) ?: return
        val data = LinkedHashMap<String, Any?>(extra)
        data["number"] = updated.number
        runCatching { pstnEvents.pushPstnCallEvent(accountId, callId, stage.name, data) }
            .onFailure { log.debug("Failed to push PSTN progress {} for {}: {}", stage, callId, it.message) }
    }

    /** يحوّل redId المسجَّل في المتتبِّع إلى معرِّف الحساب (UUID) المطلوب لبثّ WS. */
    private fun resolveAccountIdForRedId(redId: String): String? =
        runCatching { users.findByRedId(redId)?.id?.toString() }.getOrNull()

    private fun handleHangup(event: HangupEvent) {
        val channel = event.channel ?: return
        val lineNumber = channel.substringAfter('/').substringBefore('@')
        val cause = event.cause ?: 16
        val causeTxt = event.causeTxt ?: "UNKNOWN"
        val isFailed = cause !in NORMAL_CAUSES

        log.info("DINSTAR line {} hung up - cause: {} ({}) failed={}", lineNumber, cause, causeTxt, isFailed)

        val callId = redis.opsForValue().getAndDelete("$CHANNEL_CALLID_PREFIX$channel")
            ?: findCallIdFromPort(extractPortIndex(channel, lineNumber))
        callId?.let { pstnManager.forgetChannel(it) }
        // أنهِ تتبّع المراحل وابثّ ENDED لصاحب المكالمة قبل تنظيف القيود —
        // هذا آخر حدث في حلقة المكالمة الصادرة، وبدونه تبقى الواجهة عالقة
        // على «نشطة/يرنّ» حتى لو أُغلقت القناة فعلاً على الشبكة.
        callId?.let { id ->
            val ended = runCatching { progress.finishByCallId(id) }.getOrNull()
            if (ended != null) {
                resolveAccountIdForRedId(ended.redId)?.let { acc ->
                    runCatching {
                        pstnEvents.pushPstnCallEvent(
                            acc, id, PstnCallProgressTracker.Stage.ENDED.name,
                            mapOf("number" to ended.number, "cause" to causeTxt, "failed" to isFailed)
                        )
                    }
                }
            }
        }
        // التقاط ربط المكالمة (callId:gatewayId:port) قبل تنظيف مفاتيح Redis —
        // هذا هو مصدر الحقيقة المسجَّل لحظة الاتصال، أدق بكثير من تخمين
        // المنفذ من اسم القناة الذي كان يحرر منفذاً خاطئاً (عدّاد القناة).
        val boundCall = callId?.let { id ->
            redis.opsForValue().get(PstnActiveCallKeys.callKey(id))?.let { PstnActiveCallKeys.parse(it) }
        }
        var shouldRetry = false
        var targetNumber = ""
        var redId = ""

        if (callId != null) {
            val callDoc = history.findById(callId)
            if (isFailed && callDoc?.status == CallStatus.RINGING) {
                shouldRetry = true
                targetNumber = callDoc.targetId
                redId = callDoc.initiatorId
            }
            runCatching { history.end(callId, failed = isFailed) }
                .onFailure { log.warn("Could not end call {} in history: {}", callId, it.message) }
            releaseActiveReservation(callId)
        }

        // المنفذ/البوابة: أولوية للمنفذ الحقيقي الملتقط من RED_PORT_INDEX (VarSet)،
        // ثم boundCall، ثم التخمين من اسم القناة (الأقل دقة).
        val port = redis.opsForValue().getAndDelete("$CHANNEL_PORT_PREFIX$channel")?.toIntOrNull()
            ?: boundCall?.second?.takeIf { it in 0..63 }
            ?: extractPortIndex(channel, lineNumber)?.takeIf { it in 0..63 }
        val gatewayHost = extractGatewayHost(channel)
        val gatewayId = gatewayHost?.let {
            runCatching {
                jdbc.queryForObject("SELECT id FROM telecom_gateways WHERE host = ?", java.util.UUID::class.java, it)
            }.getOrNull()
        } ?: boundCall?.third?.takeIf { it != PstnActiveCallKeys.LOCAL_GATEWAY_ID }
        if (port != null && port in 0..63) {
            loadBalancer.releasePort(gatewayId, port)
            log.info("DINSTAR port {} on gateway {} released in load balancer (cause={})", port, gatewayHost ?: gatewayId ?: "unknown", causeTxt)
        } else {
            log.debug("Could not resolve port for hangup release: channel={} callId={}", channel, callId)
        }

        // سجل CDR حيّ لمراقبي البوابة على `/ws/dinstar` (الجسر كان ميتًا).
        runCatching { eventBridge.onCallEnded(event) }
            .onFailure { log.debug("dinstar bridge cdr: {}", it.message) }

        // مكالمة فائتة؟ مفتاح الوارد لا يزال موجوداً يعني انتهت المهلة/الرفض
        // من الشبكة دون قبول (القبول يحذف المفتاح). أنهِ السجل وأشعر المالك.
        runCatching {
            val incomingJson = redis.opsForValue().get("$INCOMING_CHANNEL_PREFIX$channel")
            if (!incomingJson.isNullOrBlank()) {
                val data = objectMapper.readValue(incomingJson, Map::class.java)
                val missedCallId = data["callId"]?.toString()
                val missedOwner = (data["recipientAccountIds"] as? List<*>)?.firstOrNull()?.toString()
                if (missedCallId != null && missedOwner != null) {
                    runCatching { history.end(missedCallId, failed = true) }
                    notifications.sendVoipPushNotification(
                        targetUserId = missedOwner,
                        callerId = data["caller"]?.toString() ?: "unknown",
                        callId = missedCallId,
                        mode = "MISSED_PSTN",
                        called = data["called"]?.toString()
                    )
                    log.info("Missed PSTN call recorded: callId={} owner={} caller={}", missedCallId, missedOwner, data["caller"])
                }
                redis.delete("$INCOMING_CALL_PREFIX${data["callId"]}")
            }
        }.onFailure { log.debug("missed-call handling: {}", it.message) }

        redis.delete("$INCOMING_CHANNEL_PREFIX$channel")

        if (shouldRetry && port != null) {
            log.info("Triggering Auto-Retry for callId {} to {}", callId, targetNumber)
            runCatching {
                val user = users.findByRedId(redId)
                if (user != null) {
                    publisher.publishEvent(PstnRetryEvent(callId!!, user.id, redId, targetNumber, port))
                }
            }.onFailure { log.warn("Failed to publish PstnRetryEvent: {}", it.message) }
        }
    }

    /**
     * Clears the owner reservation after Asterisk reports Hangup. The compare
     * against callId prevents an old AMI event from clearing a newer call that
     * belongs to the same user.
     */
    private fun releaseActiveReservation(callId: String) {
        val legacyReverseKey = "red:pstn:calluser:$callId"
        val ownerId = redis.opsForValue().get(legacyReverseKey)
            ?: redis.opsForValue().get(PstnActiveCallKeys.callKey(callId))
        if (!ownerId.isNullOrBlank()) {
            val activeKey = "$ACTIVE_KEY_PREFIX${ownerId.trim()}"
            val active = redis.opsForValue().get(activeKey)
            if (PstnActiveCallKeys.parse(active)?.first == callId) {
                redis.delete(activeKey)
                log.info("Released active PSTN reservation for callId={}", callId)
            }
        }
        redis.delete(legacyReverseKey)
        redis.delete(PstnActiveCallKeys.callKey(callId))
    }

    fun acceptIncomingCall(channel: String, accountId: String): Boolean {
        val incomingKey = "$INCOMING_CHANNEL_PREFIX$channel"
        val json = redis.opsForValue().get(incomingKey)
        if (json == null) {
            log.warn("No incoming call data for channel {}", channel)
            return false
        }
        return try {
            val data = objectMapper.readValue(json, Map::class.java)
            val recipients = (data["recipientAccountIds"] as? List<*>)
                ?.mapNotNull { it?.toString() }
                .orEmpty()
            if (accountId !in recipients) {
                log.warn("Rejected PSTN_ACCEPT for channel {} from unauthorized account {}", channel, accountId)
                return false
            }
            // أول مستخدم مخوّل يقبل القناة يفوز؛ يمنع سباق قبول مزدوج من جلسات متعددة.
            val claimed = redis.opsForValue().setIfAbsent("$incomingKey:claim", accountId, Duration.ofSeconds(INCOMING_TTL_SECONDS))
            if (claimed != true) {
                log.info("PSTN incoming channel {} was already claimed", channel)
                return false
            }
            val webrtcUser = data["webrtcUser"] as? String ?: "red-webrtc-client"
            val result = pstnManager.acceptIncomingCall(channel, webrtcUser)
            if (result) {
                redis.delete(incomingKey)
                data["callId"]?.toString()?.let { redis.delete("$INCOMING_CALL_PREFIX$it") }
            } else {
                redis.delete("$incomingKey:claim")
            }
            result
        } catch (e: Exception) {
            log.error("Error accepting incoming call for channel {}: {}", channel, e.message)
            false
        }
    }

    fun rejectIncomingCall(channel: String, accountId: String): Boolean {
        val incomingKey = "$INCOMING_CHANNEL_PREFIX$channel"
        val json = redis.opsForValue().get(incomingKey) ?: return false
        return try {
            val data = objectMapper.readValue(json, Map::class.java)
            val recipients = (data["recipientAccountIds"] as? List<*>)
                ?.mapNotNull { it?.toString() }
                .orEmpty()
            if (accountId !in recipients) {
                log.warn("Rejected PSTN_REJECT for channel {} from unauthorized account {}", channel, accountId)
                return false
            }
            // نموذج المالك الوحيد (ربط 1:1): رفض المالك الوحيد يعني رفض
            // المكالمة كلها — أنهِ قناة GSM فوراً لتحرير المنفذ الغالي
            // بدل تركها تستهلك Wait(RING_TIMEOUT)=30s كاملة.
            redis.delete(incomingKey)
            redis.delete("$INCOMING_CALL_PREFIX${data["callId"]}")
            runCatching { history.end(data["callId"]?.toString() ?: "", failed = true) }
                .onFailure { log.debug("missed-reject history end: {}", it.message) }
            val hungUp = pstnManager.hangupChannel(channel)
            log.info("PSTN incoming {} rejected by owner {} — channel hung up={}", channel, accountId, hungUp)
            true
        } catch (e: Exception) {
            log.error("Error rejecting incoming call for channel {}: {}", channel, e.message)
            false
        }
    }

    /**
     * مدخل المكالمة الواردة من dialplan عبر HTTP الداخلي (InternalPstnController)
     * — المسار الأساسي الموثوق بعد إثبات أن asterisk-java يُسقط UserEvent.
     * يُرجع true إذا قُبلت المكالمة ووُجد مالك مربوط.
     */
    fun handleExternalIncoming(
        caller: String,
        called: String?,
        channel: String,
        gatewayHost: String?
    ): Boolean {
        val port = extractPortIndex(channel, called ?: caller)
        log.info(
            "Incoming PSTN via internal HTTP - caller={} called={} channel={} port={} gateway={}",
            caller, called ?: "unknown", channel, port, gatewayHost ?: "unknown"
        )
        val owner = when {
            !called.isNullOrBlank() && called.length >= 6 -> resolveCalledNumberOwner(called)
            else -> null
        } ?: port?.let { resolvePortOwner(gatewayHost, it) }
        if (owner == null) {
            log.warn(
                "No permanent owner for PSTN incoming called={} port {} gateway {} — caller {} — ignoring",
                called ?: "unknown", port, gatewayHost ?: "unknown", caller
            )
            return false
        }
        processIncoming(caller, called, channel, gatewayHost, port, owner)
        return true
    }

    private fun handleUserEvent(event: UserEvent) {
        // asterisk-java لا يكشف اسم UserEvent المخصص؛ يعتمد dialplan على
        // Context:from-dinstar وحقول ManagerEvent القياسية لعزل هذا الحدث
        // (الفلترة تمت في onManagerEvent قبل الوصول إلى هنا).
        val callerNumber = event.callerIdNum?.takeIf { it.isNotBlank() } ?: "unknown"
        val channel = event.channel?.takeIf { it.isNotBlank() } ?: run {
            log.warn("Ignoring incoming DINSTAR event without channel")
            return
        }
        // exten هنا = الرقم المطلوب كاملاً (مثلاً 712064924) وليس فهرس منفذ.
        // toIntOrNull كان يعطي 712064924 «منفذاً» فيبحث عن مالك عند
        // port_index=712064924 فلا يجده أبداً ← المكالمة الواردة لا تُرن لأحد!
        // الصحيح: حلّ المالك عبر pstn_number (الربط الدائم 1:1)، واستخرج
        // رقم المنفذ الفعلي من القناة للمعلومات فقط.
        val calledNumber = event.exten?.takeIf { it.isNotBlank() }
        val port = extractPortIndex(channel, calledNumber ?: callerNumber)
        log.info(
            "Incoming DINSTAR user event - caller={} called={} channel={} port={}",
            callerNumber, calledNumber ?: "unknown", channel, port
        )

        val gatewayHost = extractGatewayHost(channel)
        // الربط الدائم 1:1 — ابحث عن مالك الشريحة فقط (لا broadcast لكل pstnEnabled).
        // الحل قبل إنشاء أي سجل مكالمة حتى لا تتراكم مستندات يتيمة لكل مكالمة
        // على منفذ غير مرتبط. الأولوية للرقم الكامل؛ fallback قديم بالمنفذ فقط.
        val owner = when {
            !calledNumber.isNullOrBlank() && calledNumber.length >= 6 ->
                resolveCalledNumberOwner(calledNumber)
            else -> null
        } ?: port?.let { resolvePortOwner(gatewayHost, it) }
        if (owner == null) {
            log.warn(
                "No permanent owner for PSTN incoming called={} port {} gateway {} — caller {} — ignoring (permanent SIM required)",
                calledNumber ?: "unknown", port, gatewayHost ?: "unknown", callerNumber
            )
            return
        }
        processIncoming(callerNumber, calledNumber, channel, gatewayHost, port, owner)
    }

    /**
     * جسم معالجة المكالمة الواردة المشترك (UserEvent + HTTP الداخلي):
     * سجل التاريخ ← مفاتيح Redis للقناة/callId ← دفع WS + FCM للمالك.
     */
    private fun processIncoming(
        callerNumber: String,
        calledNumber: String?,
        channel: String,
        gatewayHost: String?,
        port: Int?,
        owner: com.red.server.auth.model.UserAccount
    ) {
        runCatching {
            val incomingDoc = history.start("GSM:$callerNumber", "RED_ADMIN", callerNumber, CallType.AUDIO_1V1, CallRoute.DINSTAR)
            log.info("Recorded incoming DINSTAR call in history: callId={}", incomingDoc.id)

            val incomingKey = "$INCOMING_CHANNEL_PREFIX$channel"
            val incomingData = mapOf(
                "callId" to incomingDoc.id,
                "caller" to callerNumber,
                "called" to (calledNumber ?: ""),
                "port" to (port ?: -1),
                "channel" to channel,
                "gatewayHost" to (gatewayHost ?: "unknown"),
                "webrtcUser" to "red-webrtc-client",
                "recipientAccountIds" to listOf(owner.id.toString())
            )
            val incomingJson = objectMapper.writeValueAsString(incomingData)
            redis.opsForValue().set(incomingKey, incomingJson, Duration.ofSeconds(INCOMING_TTL_SECONDS))
            redis.opsForValue().set("$INCOMING_CALL_PREFIX${incomingDoc.id}", incomingJson, Duration.ofSeconds(INCOMING_TTL_SECONDS))

            val payload = mapOf(
                "callId" to incomingDoc.id,
                "caller" to callerNumber,
                "number" to callerNumber,
                "called" to (calledNumber ?: ""),
                "port" to (port ?: -1),
                "channel" to channel,
                "gatewayHost" to (gatewayHost ?: "unknown")
            )
            pstnEvents.pushPstnIncoming(owner.id.toString(), payload)
            log.info("Pushed PSTN_INCOMING to owner {} for port {} on {}", owner.redId, port, gatewayHost ?: "unknown")
            notifications.sendVoipPushNotification(
                targetUserId = owner.id.toString(),
                callerId = callerNumber,
                callId = incomingDoc.id,
                mode = "VOICE",
                called = calledNumber,
                channel = channel
            )
        }.onFailure {
            log.warn("Failed to handle incoming DINSTAR call: {}", it.message)
        }
    }

    /**
     * يحلّ مالك الرقم المطلوب الوارد عبر الربط الدائم 1:1 — المسار الأساسي
     * للوارد: exten = رقم الشريحة كاملاً (712064924) فيُطابق pstn_number مباشرة.
     * يطبّع الصيغ (+967/00967/صفر محلي) قبل البحث لأن الشبكة قد تمرّر أي صيغة.
     */
    private fun resolveCalledNumberOwner(calledNumber: String): com.red.server.auth.model.UserAccount? {
        val digits = calledNumber.filter { it.isDigit() }
        val candidates = buildSet {
            add(digits)
            add(digits.removePrefix("967"))
            if (digits.startsWith("967")) add("0" + digits.removePrefix("967"))
            if (!digits.startsWith("0")) add("0$digits")
        }.filter { it.isNotBlank() }
        for (candidate in candidates) {
            users.findByPstnNumber(candidate)?.let { return it }
        }
        return null
    }

    /**
     * يحلّ مالك منفذ وارد عبر الربط الدائم (بوابة+منفذ)، مع fallback قديم
     * أحادي البوابة بالمنفذ فقط عندما لا يُعرف مضيف البوابة من اسم القناة.
     */
    private fun resolvePortOwner(gatewayHost: String?, port: Int): com.red.server.auth.model.UserAccount? {
        if (gatewayHost != null) {
            val gatewayId = runCatching {
                jdbc.queryForObject("SELECT id FROM telecom_gateways WHERE host = ?", java.util.UUID::class.java, gatewayHost)
            }.getOrNull()
            if (gatewayId != null) {
                users.findByPstnGatewayIdAndPstnPortIndex(gatewayId, port)?.let { return it }
            }
        }
        return users.findAllByStatusOrderByCreatedAtAsc(com.red.server.auth.model.AccountStatus.APPROVED)
            .firstOrNull { it.pstnPortIndex == port && it.pstnEnabled }
    }

    private fun extractPortIndex(channel: String, lineNumber: String?): Int? {
        val fromChannel = Regex("""[-_:](\d{1,3})(?:@|$)""")
            .findAll(channel).lastOrNull()?.groupValues?.get(1)?.toIntOrNull()
        if (fromChannel != null && fromChannel in 0..63) return fromChannel

        // رقم سطر صريح قصير فقط (منفذ حقيقي 0..63) — الأرقام الطويلة أرقام
        // هواتف وليست منافذ، وعدّاد القناة (00000001) ليس منفذاً إطلاقاً:
        // النمط الفضفاض السابق كان يعيد «1» زائفة فيرن خطأً مالك المنفذ 1.
        val digits = lineNumber?.filter { it.isDigit() } ?: return null
        val fromLine = digits.toIntOrNull()
        return if (digits.length <= 2 && fromLine != null && fromLine in 0..63) fromLine else null
    }

    private fun extractGatewayHost(channel: String): String? {
        // channel مثال: "PJSIP/dinstar-gw-192-168-11-2-00000001" أو "Local/777123456@from-red-backend"
        // نبحث عن نمط dinstar-gw-IP
        val gwMatch = Regex("""dinstar-gw-(\d+-\d+-\d+-\d+)""").find(channel)
        if (gwMatch != null) {
            return gwMatch.groupValues[1].replace('-', '.')
        }
        // fallback: ابحث عن IP مباشر في القناة
        val ipMatch = Regex("""(\d+\.\d+\.\d+\.\d+)""").find(channel)
        return ipMatch?.value
    }

    /**
     * يحلّ callId من مفاتيح المكالمات النشطة بمطابقة **المنفذ** المستخرج من
     * القناة، لا بأخذ «المكالمة الوحيدة في النظام» — المطابقة العشوائية كانت
     * قد تجيب/تُنهي سجل مكالمة لا علاقة لها بهذه القناة عند تعدد المكالمات.
     */
    private fun findCallIdFromPort(port: Int?): String? {
        if (port == null) return null
        return try {
            // استخدم SCAN بدل KEYS الحاجب لتجنب تجميد Redis الإنتاجي
            val keys = mutableSetOf<String>()
            redis.execute { conn ->
                var cursor = conn.scan(
                    org.springframework.data.redis.core.ScanOptions.scanOptions()
                        .match("$ACTIVE_KEY_PREFIX*").count(100).build()
                )
                while (cursor.hasNext()) {
                    cursor.next().let { keys.add(String(it)) }
                }
                null
            }
            for (key in keys) {
                val raw = redis.opsForValue().get(key) ?: continue
                val parsed = PstnActiveCallKeys.parse(raw) ?: continue
                if (parsed.second == port && parsed.first.isNotEmpty()) return parsed.first
            }
            null
        } catch (e: Exception) {
            log.debug("Redis lookup for callId by port {} failed: {}", port, e.message)
            null
        }
    }
}
