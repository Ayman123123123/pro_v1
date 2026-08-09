package com.red.features.dinstar

/**
 * 🏛️ YOUNES Dinstar UC2000-VE-8G — Data Models
 * نماذج البيانات الكاملة لبوابة DINSTAR GSM
 * 
 * الجهاز: Dinstar UC2000-VE-8G
 * المنافذ: 8 شرائح SIM (Port 0-7)
 * الاتصال: Backend → DinstarHardwareService → HTTPS Digest Auth → UC2000-VE-8G
 */

// ━━━━━━━━━━━━ حالة المنفذ ━━━━━━━━━━━━

/**
 * منفذ SIM واحد — يمثل شريحة واحدة في جهاز UC2000-VE-8G
 * 
 * @property index رقم المنفذ (0-7)
 * @property radioType نوع الراديو (GSM/UMTS/LTE)
 * @property registrationState حالة التسجيل في الشبكة (REGISTERED/UNREGISTERED/SEARCHING)
 * @property callState حالة المكالمة (IDLE/RINGING/ACTIVE/UNKNOWN)
 * @property signalPercent قوة الإشارة (0-100%)
 * @property signalRaw القيمة الخام للإشارة (0-31، من Dinstar API)
 * @property gprsState حالة البيانات (ATTACH/DETACH)
 * @property operatorName اسم المشغل (Sabafon/MTN/YemenMobile/HiTel)
 * @property numberMasked رقم الهاتف (مخفي جزئياً: ••••1234)
 * @property imsiMasked IMSI مخفي جزئياً
 * @property iccidMasked ICCID مخفي جزئياً
 * @property simType نوع الشريحة حسب البادئة اليمنية
 * @property isHealthy هل المنفذ صحي (مسجل + إشارة > 20% + بدون مكالمة نشطة)
 */
data class DinstarPort(
    val index: Int,
    val radioType: String = "GSM",
    val registrationState: String = "UNREGISTERED",
    val callState: String = "IDLE",
    val signalPercent: Int = 0,
    val signalRaw: Int = 0,
    val gprsState: String = "DETACH",
    val operatorName: String = "غير معروف",
    val numberMasked: String? = null,
    val imsiMasked: String? = null,
    val iccidMasked: String? = null,
    val simType: YemenOperator = YemenOperator.UNKNOWN,
    val isHealthy: Boolean = false
) {
    /** هل المنفذ متاح لإجراء مكالمة جديدة؟ */
    val isAvailable: Boolean
        get() = registrationState == "REGISTERED" && callState == "IDLE" && signalPercent >= 20

    /** وصف حالة المنفذ بالعربية */
    val statusDescriptionAr: String
        get() = when {
            callState == "ACTIVE" -> "في مكالمة"
            callState == "RINGING" -> "يرن"
            registrationState != "REGISTERED" -> "غير مسجل"
            signalPercent < 10 -> "إشارة ضعيفة جداً"
            signalPercent < 25 -> "إشارة ضعيفة"
            else -> "جاهز"
        }
}

// ━━━━━━━━━━━━ المشغلون اليمنيون ━━━━━━━━━━━━

/**
 * مشغلو الاتصالات اليمنيين — تصنيف حسب بادئة الرقم
 * 
 * سبأفون (Sabafon): 77X — الشبكة الأكثر استخداماً
 * MTN اليمن: 71X — ثاني أكبر شبكة
 * يمن موبايل (YemenMobile): 73X — المشغل الحكومي
 * هيتل (HiTel): 70X — مشغل CDMA→LTE
 * يو يمن (U Yemen): 77X (سبأفون سابقاً)
 */
enum class YemenOperator(
    val arabicName: String,
    val englishName: String,
    val prefixes: Set<String>,
    val colorHex: Long
) {
    SABAFON("سبأفون", "Sabafon", setOf("770", "771", "772", "773", "774", "775", "776", "777", "778", "779"), 0xFFE53935),
    MTN("إم تي إن", "MTN Yemen", setOf("710", "711", "712", "713", "714", "715", "716", "717", "718", "719"), 0xFFFFB300),
    YEMEN_MOBILE("يمن موبايل", "YemenMobile", setOf("730", "731", "732", "733", "734", "735", "736", "737", "738", "739"), 0xFF43A047),
    HITEL("هيتل", "HiTel", setOf("700", "701", "702", "703", "704", "705", "706", "707", "708", "709"), 0xFF1E88E5),
    U_YEMEN("يو يمن", "U Yemen", setOf(), 0xFFAB47BC),
    UNKNOWN("غير معروف", "Unknown", setOf(), 0xFF757575);

    companion object {
        /** تصنيف المشغل حسب بادئة الرقم اليمني */
        fun fromPrefix(prefix: String): YemenOperator {
            return entries.firstOrNull { prefix in it.prefixes } ?: UNKNOWN
        }

        /** تصنيف المشغل حسب الرقم الكامل */
        fun fromNumber(number: String): YemenOperator {
            val digits = number.filter { it.isDigit() }
            val local = when {
                digits.startsWith("967") -> digits.removePrefix("967")
                digits.startsWith("0967") -> digits.removePrefix("0967")
                digits.startsWith("0") -> digits.removePrefix("0")
                else -> digits
            }
            if (local.length >= 3) {
                return fromPrefix(local.substring(0, 3))
            }
            return UNKNOWN
        }

        /** تصنيف المشغل حسب اسم المشغل من API */
        fun fromApiOperatorName(name: String?): YemenOperator {
            if (name.isNullOrBlank()) return UNKNOWN
            return when {
                name.contains("Sabafon", ignoreCase = true) -> SABAFON
                name.contains("MTN", ignoreCase = true) -> MTN
                name.contains("Yemen", ignoreCase = true) && name.contains("Mobile", ignoreCase = true) -> YEMEN_MOBILE
                name.contains("HiTel", ignoreCase = true) || name.contains("Hitel", ignoreCase = true) -> HITEL
                name.contains("U Yemen", ignoreCase = true) || name.contains("UYemen", ignoreCase = true) -> U_YEMEN
                else -> UNKNOWN
            }
        }
    }
}

// ━━━━━━━━━━━━ حالة الجهاز الكاملة ━━━━━━━━━━━━

/**
 * الحالة الكاملة لجهاز Dinstar UC2000-VE-8G
 * 
 * @property isOnline هل الجهاز متصل بالسيرفر
 * @property gatewayIp عنوان IP للبوابة
 * @property model طراز الجهاز (UC2000-VE-8G)
 * @property firmware إصدار البرنامج الثابت
 * @property ports حالة جميع المنافذ الثمانية
 * @property lastUpdated آخر تحديث للبيانات
 * @property discoveredAt وقت اكتشاف الجهاز
 */
data class DinstarGatewayStatus(
    val isOnline: Boolean = false,
    val gatewayIp: String = "192.168.11.1",
    val model: String = "UC2000-VE-8G",
    val firmware: String = "",
    val ports: List<DinstarPort> = emptyList(),
    val lastUpdated: Long = 0L,
    val discoveredAt: Long = 0L
) {
    /** عدد المنافذ المسجلة */
    val registeredCount: Int get() = ports.count { it.registrationState == "REGISTERED" }
    
    /** عدد المنافذ في مكالمة نشطة */
    val activeCallCount: Int get() = ports.count { it.callState == "ACTIVE" }
    
    /** عدد المنافذ المتاحة للمكالمات */
    val availableCount: Int get() = ports.count { it.isAvailable }
    
    /** متوسط الإشارة عبر جميع المنافذ المسجلة */
    val averageSignal: Int get() {
        val registered = ports.filter { it.registrationState == "REGISTERED" }
        if (registered.isEmpty()) return 0
        return registered.map { it.signalPercent }.average().toInt()
    }
    
    /** أفضل منفذ للاتصال (أعلى إشارة + بدون مكالمة) */
    val bestPortForCall: DinstarPort?
        get() = ports
            .filter { it.isAvailable }
            .maxByOrNull { it.signalPercent }
    
    /** هل يمكن إجراء مكالمة جديدة؟ */
    val canMakeCall: Boolean get() = availableCount > 0
    
    /** توزيع المشغلين */
    val operatorDistribution: Map<YemenOperator, Int>
        get() = ports.groupingBy { it.simType }.eachCount()
}

// ━━━━━━━━━━━━ سجل المكالمات CDR ━━━━━━━━━━━━

/**
 * سجل تفاصيل المكالمة من Dinstar CDR
 */
data class DinstarCdr(
    val id: String = "",
    val port: Int,
    val phoneNumber: String,
    val direction: String = "outgoing",
    val durationSeconds: Int = 0,
    val startTime: Long = 0L,
    val endTime: Long = 0L,
    val callState: String = "COMPLETED",
    val signalStrength: Int = 0,
    val operatorName: String = "",
    val costYer: Int = 0 // التكلفة بالريال اليمني
) {
    val operator: YemenOperator
        get() = YemenOperator.fromNumber(phoneNumber)

    val formattedDuration: String
        get() {
            val min = durationSeconds / 60
            val sec = durationSeconds % 60
            return if (min > 0) "${min}د ${sec}ث" else "${sec}ث"
        }
}

// ━━━━━━━━━━━━ إحصائيات Dinstar ━━━━━━━━━━━━

/**
 * إحصائيات مجمعة لبوابة Dinstar
 * تُحسب من CDR + حالة المنافذ الحالية
 */
data class DinstarStatistics(
    val totalCallsToday: Int = 0,
    val totalDurationMinutesToday: Int = 0,
    val totalCostYerToday: Int = 0,
    val callsByOperator: Map<YemenOperator, Int> = emptyMap(),
    val avgSignalAllPorts: Int = 0,
    val uptime: String = "",
    val successRate: Float = 0f, // نسبة نجاح المكالمات
    val peakConcurrency: Int = 0 // أقصى عدد مكالمات متزامنة
)

// ━━━━━━━━━━━━ أوامر Dinstar ━━━━━━━━━━━━

/**
 * أمر يُرسل إلى جهاز Dinstar عبر الباكند
 */
sealed class DinstarCommand {
    data class ResetPort(val port: Int) : DinstarCommand()
    data class SendUssd(val port: Int, val code: String) : DinstarCommand()
    data object RefreshStatus : DinstarCommand()
    data object QueryCdr : DinstarCommand()
    data object DiscoverGateway : DinstarCommand()
}

/**
 * نتيجة تنفيذ أمر Dinstar
 */
sealed class DinstarCommandResult {
    data class Success(val message: String, val data: Map<String, Any?> = emptyMap()) : DinstarCommandResult()
    data class Error(val message: String, val code: Int? = null) : DinstarCommandResult()
    data object Loading : DinstarCommandResult()
}

// ━━━━━━━━━━━━ حالة اتصال الباكند ━━━━━━━━━━━━

/**
 * حالة الاتصال بين التطبيق والباكند (وليس بين الباكند والدستار)
 */
enum class BackendConnectionState(val labelAr: String) {
    CONNECTED("متصل"),
    CONNECTING("جاري الاتصال"),
    DISCONNECTED("غير متصل"),
    ERROR("خطأ في الاتصال")
}
