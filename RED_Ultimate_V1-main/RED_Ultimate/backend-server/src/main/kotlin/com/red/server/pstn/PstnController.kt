package com.red.server.pstn

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.UUID

/**
 * تحليلات PSTN والخط الزمني والمقاييس.
 *
 * ⚠️ مسارات المكالمات (`POST /calls`, `/calls/{id}/hangup`, `GET /status`)
 * تعيش في [PstnCallController] وحده — كان تعريفها هنا أيضًا يُنتج
 * `duplicate mapping` فيمنع Spring من الإقلاع كليًا.
 *
 * ## ما أُصلح في هذا الملف
 *
 * 1. **`PstnCallRequest` كان مُعرَّفًا مرّتَين** — هنا وفي
 *    [PstnCallController]، وكلاهما في الحزمة `com.red.server.pstn`. الملف
 *    لا يُصرَّف أصلًا (`Redeclaration`). الصنف يخصّ متحكّم المكالمات لأنه
 *    وحده يستهلكه، فحُذف من هنا.
 *
 * 2. **`ObjectMapper()` جديد في كل نداء** — يتجاهل ضبط Spring (وحدات
 *    Kotlin وjsr310 المسجّلة في السياق) ويُعيد `Map` خامًّا فيفشل
 *    الاستنتاج (`Cannot infer type for K/V`). الآن يُحقَن المُحوّل ويُقرأ
 *    بـ`TypeReference` صريح.
 *
 * 3. **ثقب IDOR في الخط الزمني** — أي مستخدم مصادَق كان يقرأ خطّ أي
 *    مكالمة بمعرّفها، ويكتب مراحل فيه. الخطّ يكشف أرقام الوجهة وأزمنة
 *    الاتصال؛ ومعرّف المكالمة UUID لكنه يُبَثّ في WebSocket ويُسجَّل.
 *    الآن تُتحقَّق الملكية عبر [PstnCallService.findUserByCallId]، والأدمن
 *    وحده يتجاوزها.
 *
 * 4. **كتابة المراحل من العميل** — `recordStage` هي حالة داخلية للخادم
 *    (يكتبها [DinstarEventListener] من أحداث Asterisk الحقيقية). السماح
 *    للعميل بكتابتها يسمح بتلفيق خطّ زمني كامل. صارت للأدمن فقط
 *    ولأغراض التشخيص، ومرحلة غير معروفة تُرَدّ بـ400 بدل
 *    `success:true` الكاذب.
 *
 * 5. **`days` بلا حدّ** — `?days=100000` يمسح الجدول كاملًا. تُقيَّد 1..365.
 *
 * 6. **`Instant.parse` بلا حماية** — `DateTimeParseException` لا يرث
 *    `IllegalArgumentException` فكان يسقط في معالج 500 العام. صار 400
 *    برمز نطاق واضح.
 *
 * 7. **`/users/pstn-binding` كان كذبًا** — يعيد `emptyList()` دائمًا مع
 *    تعليق «simplified version». المسار الحقيقي موجود ويعمل:
 *    `GET /api/admin/dinstar/bindings` في `PstnBindingController`. حُذف
 *    الجذع بدل أن يُقرأ «لا رِبَاط» على أنه حقيقة.
 */
@RestController
@RequestMapping("/api/pstn")
class PstnController(
    private val enhancedManager: EnhancedPstnManager,
    private val timelineService: CallTimelineService,
    private val analyticsService: PstnAnalyticsService,
    private val memoryManager: MemoryManagementService,
    private val calls: PstnCallService,
    private val objectMapper: ObjectMapper,
) {
    companion object {
        private val log = LoggerFactory.getLogger(PstnController::class.java)

        /** أقصى نافذة تحليل: سنة. أبعد من ذلك مسحٌ كامل بلا فائدة تشغيلية. */
        private const val MAX_ANALYTICS_DAYS = 365

        /** أقصى مدى بين تاريخَين في استعلام التوجيه. */
        private const val MAX_RANGE_DAYS = 365L
    }

    // ── التحليلات ──────────────────────────────────────────────────────────
    //
    // كلّها تحت `/api/pstn/**` أي `authenticated()` في SecurityConfig، وهي
    // مجاميع لا بيانات مستخدمٍ بعينه — لا تكشف رقمًا ولا هوية.

    @GetMapping("/analytics/summary")
    fun getSummary(
        @RequestParam(defaultValue = "7") days: Int,
    ): ResponseEntity<Map<String, Any>> =
        ResponseEntity.ok(analyticsService.getPstnSummary(days.coerceIn(1, MAX_ANALYTICS_DAYS)))

    @GetMapping("/analytics/routing")
    fun getRoutingStats(
        @RequestParam startDate: String,
        @RequestParam endDate: String,
    ): ResponseEntity<Map<String, Any>> {
        val start = parseInstant(startDate, "startDate")
        val end = parseInstant(endDate, "endDate")
        require(!end.isBefore(start)) { "END_DATE_BEFORE_START_DATE" }
        require(java.time.Duration.between(start, end).toDays() <= MAX_RANGE_DAYS) {
            "DATE_RANGE_EXCEEDS_365_DAYS"
        }
        return ResponseEntity.ok(analyticsService.getRoutingStats(start, end))
    }

    @GetMapping("/analytics/cdr")
    fun getCdrStats(
        @RequestParam(defaultValue = "7") days: Int,
    ): ResponseEntity<Map<String, Any>> =
        ResponseEntity.ok(analyticsService.getCdrStats(days.coerceIn(1, MAX_ANALYTICS_DAYS)))

    @GetMapping("/analytics/gateways")
    fun getGatewayStats(): ResponseEntity<List<Map<String, Any?>>> =
        ResponseEntity.ok(analyticsService.getGatewayStats())

    // ── الخط الزمني ────────────────────────────────────────────────────────

    /**
     * خطّ مكالمة واحدة — لصاحبها أو للأدمن.
     *
     * الرَدّ 404 لا 403 عند عدم الملكية: تمييزهما يُفصح عن وجود المكالمة،
     * وهو ما يسمح بتعداد المعرّفات.
     */
    @GetMapping("/calls/{callId}/timeline")
    fun getCallTimeline(
        @PathVariable callId: String,
        authentication: Authentication,
    ): ResponseEntity<List<Map<String, Any?>>> {
        if (!isOwnerOrAdmin(callId, authentication)) {
            log.warn("Timeline access denied for call {} by {}", callId, authentication.name)
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(emptyList())
        }
        return ResponseEntity.ok(timelineService.getTimeline(callId))
    }

    /**
     * تسجيل مرحلة يدويًا — للأدمن فقط.
     *
     * المصدر الطبيعي للمراحل هو [DinstarEventListener] من أحداث Asterisk؛
     * هذا المسار للتشخيص وإعادة البناء اليدوي عند فقد حدث.
     */
    @PostMapping("/calls/{callId}/timeline")
    fun recordTimelineStage(
        @PathVariable callId: String,
        @RequestBody request: Map<String, String>,
        authentication: Authentication,
    ): ResponseEntity<Map<String, Any?>> {
        if (!isAdmin(authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(mapOf("error" to "ADMIN_ROLE_REQUIRED"))
        }
        val stageRaw = request["stage"]?.trim()
        if (stageRaw.isNullOrEmpty()) {
            return ResponseEntity.badRequest().body(mapOf("error" to "STAGE_REQUIRED"))
        }
        // مرحلة مجهولة كانت تُقابَل بـ`success:true` بلا كتابة شيء.
        val stage = CallTimelineService.Stage.entries.find { it.name.equals(stageRaw, ignoreCase = true) }
            ?: return ResponseEntity.badRequest().body(
                mapOf(
                    "error" to "UNKNOWN_STAGE",
                    "allowed" to CallTimelineService.Stage.entries.map { it.name },
                )
            )

        val data: Map<String, Any?> = request["data"]?.takeIf { it.isNotBlank() }?.let { raw ->
            runCatching {
                objectMapper.readValue(raw, object : TypeReference<Map<String, Any?>>() {})
            }.getOrElse {
                return ResponseEntity.badRequest().body(mapOf("error" to "INVALID_STAGE_DATA_JSON"))
            }
        } ?: emptyMap()

        timelineService.recordStage(callId, stage, data)
        return ResponseEntity.ok(mapOf("success" to true, "stage" to stage.name))
    }

    // ── الصحّة والمقاييس ───────────────────────────────────────────────────
    //
    // تكشف حجم مجمّع اتصالات AMI وإخفاقاته وإحصاء التنظيف — بصمة تشغيلية
    // تُعين على توقيت هجوم استنزاف. للأدمن فقط عبر SecurityConfig.

    @GetMapping("/health")
    fun healthCheck(): ResponseEntity<Map<String, Any>> =
        ResponseEntity.ok(
            mapOf(
                "enhancedManager" to enhancedManager.getMetrics(),
                "memoryManager" to memoryManager.getCleanupStats(),
                "timestamp" to Instant.now().toString(),
            )
        )

    @GetMapping("/metrics")
    fun metrics(): ResponseEntity<Map<String, Any>> =
        ResponseEntity.ok(
            mapOf(
                "pstn" to enhancedManager.getMetrics(),
                "memory" to memoryManager.getCleanupStats(),
                "timestamp" to Instant.now().toString(),
            )
        )

    // ── مساعدات ────────────────────────────────────────────────────────────

    private fun isAdmin(authentication: Authentication): Boolean =
        authentication.authorities.any { it.authority == "ROLE_ADMIN" }

    private fun isOwnerOrAdmin(callId: String, authentication: Authentication): Boolean {
        if (isAdmin(authentication)) return true
        val caller = runCatching { UUID.fromString(authentication.name) }.getOrNull() ?: return false
        return calls.findUserByCallId(callId) == caller
    }

    /**
     * `Instant.parse` يرمي [DateTimeParseException] وهي لا ترث
     * `IllegalArgumentException`، فكانت تسقط في معالج 500 العام بلا سبب
     * مفهوم للعميل. تُحوَّل هنا إلى رمز نطاق يعبر الحدود بأمان.
     */
    private fun parseInstant(value: String, field: String): Instant =
        try {
            Instant.parse(value)
        } catch (_: DateTimeParseException) {
            throw IllegalArgumentException("INVALID_ISO8601_${field.uppercase()}")
        }
}
