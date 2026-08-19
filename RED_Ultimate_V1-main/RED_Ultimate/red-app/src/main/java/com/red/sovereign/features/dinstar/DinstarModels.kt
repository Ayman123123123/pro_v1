package com.red.sovereign.features.dinstar

import androidx.compose.ui.graphics.Color

/**
 * نماذج بوابة Dinstar UC2000-VE.
 *
 * ⚠️ حالة الاستعمال (تحقّق 2026-08-19): المستعمَل حيًّا من هذا الملف هو
 * [YemenOperator] وحده — يعتمد عليه `calls/YemeniOperatorDetector.kt`
 * و`ui/components/SovereignUiComponents.kt` في 19 موضعًا.
 *
 * أما بقيّة النماذج ([DinstarPort]، [DinstarGatewayStatus]،
 * [DinstarFleetStatus]، [DinstarCdr]، [DinstarStatistics]،
 * [DinstarIncomingSms]، [DinstarDeviceStatus]، [DinstarCommandResult])
 * فكان مستهلكها الوحيد `DinstarViewModel`، وقد أُرشف: كان يستدعي أحد
 * عشر مسارًا تحت `/api/admin/**` من تطبيق المستخدم العادي، وكلها
 * تتطلب دور ADMIN في `SecurityConfig` فتردّ 403. إدارة أسطول البوابات
 * مكانها لوحة الإدارة لا جهاز المستخدم.
 *
 * أُبقيت هذه النماذج لأنها تصف عقد API البوابة الفعلي كما وثّقته
 * «Dinstar GSM Gateway HTTP API»، فهي مرجع صحيح لأي وصلٍ قادم ولا
 * تُدخل سلوكًا في التطبيق ما دامت غير مستدعاة.
 */

data class DinstarPort(
    val index: Int,
    val radioType: String = "GSM",
    val registrationState: String = "UNREGISTERED",
    val callState: String = "IDLE",
    /**
     * النسبة المئوية للعرض. **قابلة لأن تكون null**: حين تُبلّغ البوابة
     * قراءة «غير قابلة للكشف» لا يوجد قياس أصلًا، وعرض صفر أو مئة
     * كلاهما كذب. الصفر السابق كان يُخفي الفرق بين «إشارة معدومة»
     * و«لا قياس».
     */
    val signalPercent: Int? = null,
    /** القوة الفعلية بالـ dBm، أو null عند تعذّر القياس. */
    val signalDbm: Int? = null,
    /** القراءة الخام من `AT+CSQ`. القيمة 99 تعني «غير قابلة للكشف». */
    val signalRaw: Int? = null,
    /** هل الإشارة كافية لحمل مكالمة (‎≥ -100 dBm)؟ */
    val signalUsable: Boolean = false,
    val gprsState: String = "DETACH",
    val operatorName: String = "غير معروف",
    val numberMasked: String? = null,
    val imsiMasked: String? = null,
    val iccidMasked: String? = null,
    val simType: YemenOperator = YemenOperator.UNKNOWN
) {
    /**
     * جاهز لحمل مكالمة.
     *
     * كان الشرط `signalPercent >= 20` محسوبًا من نسبة مغلوطة: القراءة
     * 99 (لا شبكة) كانت تُقصر على 31 فتُنتج 100%، فتبدو الشريحة الميتة
     * الأفضل. الشرط الآن يعتمد قياسًا فعليًا بالـ dBm.
     */
    val isAvailable: Boolean
        get() = registrationState == "REGISTERED" && callState == "IDLE" && signalUsable

    /** مسجّلة على الشبكة لكن بلا إشارة صالحة — حالة تستحق التنبيه. */
    val isRegisteredButUnusable: Boolean
        get() = registrationState == "REGISTERED" && !signalUsable

    val statusDescriptionAr: String
        get() = when {
            callState == "ACTIVE" -> "في مكالمة"
            callState == "RINGING" -> "يرن"
            registrationState != "REGISTERED" -> "غير مسجل"
            signalDbm == null -> "لا يوجد قياس إشارة"
            signalDbm < -100 -> "إشارة غير كافية"
            signalDbm < -95 -> "إشارة ضعيفة"
            else -> "جاهز"
        }

    /** نص القوة للعرض — لا يختلق رقمًا حين لا يوجد قياس. */
    val signalLabelAr: String
        get() = signalDbm?.let { "$it dBm" } ?: "—"
}

/**
 * مشغلو الهاتف المحمول في اليمن.
 *
 * **تصحيح البادئات:** كانت الخريطة السابقة تنسب `77x` إلى سبأفون
 * و`73x` إلى يمن موبايل و`71x` إلى MTN — وكلها معكوسة. البادئة الصحيحة
 * تُحدَّد بأول رقمين بعد `+967` حسب خطة الترقيم اليمنية:
 *
 * | البادئة | المشغل |
 * |---------|--------|
 * | 71 | سبأفون |
 * | 73 | يو (كانت MTN حتى إعادة التسمية في 2021) |
 * | 77، 78 | يمن موبايل |
 * | 70 | واي (كانت HiTel) |
 *
 * الخطأ لم يكن تجميليًا: `fromNumber` تُستخدم لعرض مشغل المتصل ولمطابقة
 * الشريحة، فكان الاختيار يقع على شريحة شبكة أخرى وتُحتسب المكالمة
 * بتعرفة خارج الشبكة.
 */
enum class YemenOperator(
    val arabicName: String,
    val englishName: String,
    /** البادئات المكوّنة من رقمين بعد رمز الدولة. */
    val prefixes: Set<String>,
    val color: Color
) {
    /**
     * `71` النطاق الأصلي (صنعاء وعموم البلاد، ومنه `718` عدن القديم).
     * `722` نطاق عدن للجيل الرابع (VoLTE) — أُطلق مستقلًّا لا امتدادًا
     * لـ`71`، فيجب ذكره صراحةً وإلا قُرئ `72` وسقط في «غير معروف».
     */
    SABAFON("سبأفون", "Sabafon", setOf("71", "722"), Color(0xFFE53935)),
    YOU("يو", "YOU", setOf("73"), Color(0xFFFFB300)),
    YEMEN_MOBILE("يمن موبايل", "YemenMobile", setOf("77", "78"), Color(0xFF43A047)),
    Y_TELECOM("واي", "YTelecom", setOf("70"), Color(0xFF1E88E5)),
    UNKNOWN("غير معروف", "Unknown", setOf(), Color(0xFF757575));

    companion object {
        fun fromPrefix(prefix: String): YemenOperator =
            entries.firstOrNull { prefix in it.prefixes } ?: UNKNOWN

        fun fromNumber(number: String): YemenOperator {
            val digits = number.filter { it.isDigit() }
            val local = when {
                digits.startsWith("00967") -> digits.removePrefix("00967")
                digits.startsWith("967") -> digits.removePrefix("967")
                digits.startsWith("0") -> digits.removePrefix("0")
                else -> digits
            }
            // الأطول أولًا: 722 (سبأفون عدن 4G) يسبق 72 وإلا سقط في UNKNOWN
            if (local.length >= 3) {
                val three = fromPrefix(local.substring(0, 3))
                if (three != UNKNOWN) return three
            }
            return if (local.length >= 2) fromPrefix(local.substring(0, 2)) else UNKNOWN
        }

        fun fromApiOperatorName(name: String?): YemenOperator {
            if (name.isNullOrBlank()) return UNKNOWN
            return when {
                name.contains("Sabafon", ignoreCase = true) || name.contains("سبأفون") -> SABAFON
                // MTN اليمن صارت YOU في 2021 — الاسمان لمشغل واحد
                name.contains("YOU", ignoreCase = true) || name.contains("MTN", ignoreCase = true) ||
                    name.contains("Yemeni Omani", ignoreCase = true) || name.contains("يو") -> YOU
                name.contains("Yemen", ignoreCase = true) && name.contains("Mobile", ignoreCase = true) -> YEMEN_MOBILE
                name.contains("يمن موبايل") -> YEMEN_MOBILE
                // HiTel أُعيد تسميتها Y Telecom
                name.contains("HiTel", ignoreCase = true) || name.contains("Y Telecom", ignoreCase = true) ||
                    name.contains("واي") -> Y_TELECOM
                else -> UNKNOWN
            }
        }
    }
}

data class DinstarGatewayStatus(
    /** معرّف البوابة في سجل الأسطول — لازم للتمييز بين عدة أجهزة. */
    val gatewayId: String? = null,
    val name: String = "",
    val isOnline: Boolean = false,
    val gatewayIp: String = "",
    /** الطراز كما هو مسجَّل، لا قيمة مثبَّتة: قد يكون 8G أو 8T أو رباعيًا. */
    val model: String = "",
    val firmware: String = "",
    val ports: List<DinstarPort> = emptyList(),
    val lastUpdated: Long = 0L
) {
    val registeredCount: Int get() = ports.count { it.registrationState == "REGISTERED" }
    val activeCallCount: Int get() = ports.count { it.callState == "ACTIVE" }
    val availableCount: Int get() = ports.count { it.isAvailable }

    /** مسجّلة لكن بلا إشارة صالحة — الفجوة التي كانت مخفية خلف نسبة 100%. */
    val registeredButUnusableCount: Int get() = ports.count { it.isRegisteredButUnusable }

    /**
     * متوسط الإشارة بالـ dBm للمنافذ التي **لها قياس فعلي**.
     * المنافذ بلا قياس تُستبعد بدل احتسابها صفرًا (كان يجرّ المتوسط
     * إلى الأسفل) أو 100% (كان يرفعه كذبًا).
     */
    val averageSignalDbm: Int? get() {
        val measured = ports.mapNotNull { it.signalDbm }
        return if (measured.isEmpty()) null else measured.average().toInt()
    }

    /**
     * أفضل منفذ للمكالمة. الترتيب بالـ dBm لا بالنسبة، و`isAvailable`
     * يضمن استبعاد ما لا يحمل مكالمة. القرار النهائي للتوجيه يتخذه
     * الخادم — هذا للعرض والتشخيص فقط.
     */
    val bestPortForCall: DinstarPort? get() =
        ports.filter { it.isAvailable }.maxByOrNull { it.signalDbm ?: Int.MIN_VALUE }
}

/**
 * حالة الأسطول كاملًا — عدة بوابات معًا.
 *
 * النموذج السابق كان يفترض بوابة واحدة (`DinstarGatewayStatus` مفردة)،
 * فلم يكن ممكنًا عرض جهازين أو معرفة أيّهما حمل المكالمة.
 */
data class DinstarFleetStatus(
    val gateways: List<DinstarGatewayStatus> = emptyList(),
    val lastUpdated: Long = 0L
) {
    val gatewayCount: Int get() = gateways.size
    val onlineCount: Int get() = gateways.count { it.isOnline }
    val totalPorts: Int get() = gateways.sumOf { it.ports.size }
    val registeredPorts: Int get() = gateways.sumOf { it.registeredCount }
    val usablePorts: Int get() = gateways.sumOf { it.availableCount }
    val activeCalls: Int get() = gateways.sumOf { it.activeCallCount }

    /** «14 مسجّلة، منها 10 جاهزة» — الفرق الذي يحتاجه المسؤول. */
    val summaryAr: String
        get() = "$registeredPorts شريحة مسجّلة، منها $usablePorts جاهزة"
}

data class DinstarCdr(
    val id: String = "",
    val port: Int,
    val phoneNumber: String,
    val direction: String = "outgoing",
    val durationSeconds: Int = 0,
    val startTime: Long = 0L,
    val callState: String = "COMPLETED",
    val costYer: Int = 0
) {
    val operator: YemenOperator get() = YemenOperator.fromNumber(phoneNumber)
}

data class DinstarStatistics(
    val totalCallsToday: Int = 0,
    val totalDurationMinutesToday: Int = 0,
    val totalCostYerToday: Int = 0,
    val callsByOperator: Map<YemenOperator, Int> = emptyMap(),
    val avgSignalAllPorts: Int = 0,
    val successRate: Float = 0f,
    val peakConcurrency: Int = 0
)

/**
 * رسالة SMS واردة على إحدى شرائح البوابة.
 *
 * `port` هو فهرس المنفذ الذي استقبلها — يُعرَّف بـ -1 حين لا ترسله
 * البوابة، فلا يُخلط بالمنفذ 0 الحقيقي.
 */
data class DinstarIncomingSms(
    val port: Int = -1,
    val number: String = "",
    val text: String = "",
    val timestamp: String = ""
)

/**
 * قياسات عتاد البوابة نفسها — لا حالة المنافذ.
 *
 * مصدرها `/api/get_status` على الجهاز، يمرّرها الخادم عبر
 * `/api/admin/dinstar/device-status` ويحفظها في `dinstar_device_status`.
 *
 * كل الحقول نصية `String?` عمدًا: البوابة تُرجعها بوحدات مُلحقة
 * (`"45%"`, `"128MB"`, `"47C"`) وتتفاوت بين الإصدارات، فتحويلها إلى أرقام
 * هنا يفقد الوحدة ويكسر عند صيغة غير متوقَّعة. العرض يبقى كما أرسله الجهاز.
 *
 * استُعيد هذا النموذج في 2026-08-19: كانت السلسلة مقطوعة عند التطبيق —
 * الجهاز يُنتج القياسات والخادم يخزّنها ولا شيء يستهلكها.
 */
data class DinstarDeviceStatus(
    val cpuUsed: String? = null,
    val memoryTotal: String? = null,
    val memoryUsed: String? = null,
    val memoryFree: String? = null,
    val flashTotal: String? = null,
    val flashUsed: String? = null,
    val flashFree: String? = null,
    /** حرارة اللوحة — المؤشر الأبكر على اختناق حراري يسبق سقوط المنافذ. */
    val temperature: String? = null,
    val uptime: String? = null
) {
    /** true حين لم تصل أي قيمة — للتمييز بين "لم يُستعلم بعد" و"جهاز صامت". */
    val isEmpty: Boolean
        get() = listOf(
            cpuUsed, memoryTotal, memoryUsed, memoryFree,
            flashTotal, flashUsed, flashFree, temperature, uptime
        ).all { it.isNullOrBlank() }
}

sealed class DinstarCommandResult {
    data class Success(val message: String, val data: Map<String, Any?> = emptyMap()) : DinstarCommandResult()
    data class Error(val message: String, val code: Int? = null) : DinstarCommandResult()
    data object Loading : DinstarCommandResult()
}
