package com.red.server.services

/**
 * Hardware facts that are safe to encode in the server. Carrier compatibility is deliberately not
 * guessed: it depends on the installed radio variant, local coverage and the SIM/network profile.
 */
enum class DinstarModelProfile(
    val modelId: String,
    val portCount: Int,
    val radioCapability: String,
    val supportsVolte: Boolean,
    /** ترميزات الصوت المدعومة — تُستخدم للتفاوض مع Asterisk. */
    val codecs: List<String>,
    /** واجهة الشبكة كما في ورقة بيانات المُصنّع. */
    val ethernet: String
) {
    /**
     * UC2000-VE-8G — بوابة GSM بثمانية قنوات.
     * المصدر: ورقة بيانات DINSTAR الرسمية «UC2000-VE GSM&LTE VoIP Gateway».
     */
    UC2000_VE_8G(
        modelId = "UC2000-VE-8G",
        portCount = 8,
        radioCapability = "GSM 850/900/1800/1900 MHz",
        supportsVolte = false,
        codecs = listOf("G.711A", "G.711U", "G.723.1", "G.729A", "G.729B"),
        ethernet = "2× 10/100/1000 Base-T RJ45"
    ),

    /**
     * UC2000-VE-8T — بوابة LTE/VoLTE بثمانية قنوات، متوافقة رجوعًا مع
     * WCDMA وGSM. النطاقات الترددية تتغيّر حسب متغيّر الراديو المركّب
     * (Type A/E/V/J/AU) فلا تُخمَّن هنا، بل تُقرأ من ملصق الجهاز.
     * يضيف هذا الطراز G.722 وAMR إلى قائمة الترميزات.
     */
    UC2000_VE_8T(
        modelId = "UC2000-VE-8T",
        portCount = 8,
        radioCapability = "LTE-FDD/LTE-TDD/WCDMA/GSM — النطاقات حسب متغيّر الراديو (Type A/E/V/J/AU) من ملصق الجهاز",
        supportsVolte = true,
        codecs = listOf("G.711A", "G.711U", "G.723.1", "G.729A", "G.729B", "G.722", "AMR"),
        ethernet = "2× 10/100/1000 Base-T RJ45"
    ),

    /** المتغيّران رباعيا القنوات من العائلة نفسها. */
    UC2000_VE_4G(
        modelId = "UC2000-VE-4G",
        portCount = 4,
        radioCapability = "GSM 850/900/1800/1900 MHz",
        supportsVolte = false,
        codecs = listOf("G.711A", "G.711U", "G.723.1", "G.729A", "G.729B"),
        ethernet = "2× 10/100/1000 Base-T RJ45"
    ),
    UC2000_VE_4T(
        modelId = "UC2000-VE-4T",
        portCount = 4,
        radioCapability = "LTE-FDD/LTE-TDD/WCDMA/GSM — النطاقات حسب متغيّر الراديو من ملصق الجهاز",
        supportsVolte = true,
        codecs = listOf("G.711A", "G.711U", "G.723.1", "G.729A", "G.729B", "G.722", "AMR"),
        ethernet = "2× 10/100/1000 Base-T RJ45"
    );

    val portRange: IntRange get() = 0 until portCount

    /** عدد شقوق SIM يساوي عدد القنوات في هذه العائلة، وكلها قابلة للتبديل الساخن. */
    val simSlots: Int get() = portCount

    fun metadata(): Map<String, Any> = mapOf(
        "model" to modelId,
        "portCount" to portCount,
        "simSlots" to simSlots,
        "radioCapability" to radioCapability,
        "supportsVolte" to supportsVolte,
        "codecs" to codecs,
        "ethernet" to ethernet,
        "sipProtocol" to "SIP v2.0 (RFC3261) over UDP/TCP/TLS",
        "mediaProtocol" to "RTP/SRTP",
        "dtmf" to "RFC2833, SIP Info",
        "hotSwappableSim" to true,
        "httpApiAuth" to "HTTP Digest (Basic على الإصدارات الأقدم)",
        // التوافق مع مشغّل بعينه لا يُستنتج من الطراز: يعتمد على متغيّر
        // الراديو والتغطية المحلية وملف الشريحة. يُثبَت بالتسجيل الفعلي.
        "carrierCompatibilityRequiresLiveRegistration" to true
    )

    companion object {
        /** الطرازان المعتمدان في هذا النشر. */
        val PRIMARY = listOf(UC2000_VE_8G, UC2000_VE_8T)

        fun parse(value: String): DinstarModelProfile =
            entries.firstOrNull { it.modelId.equals(value.trim(), ignoreCase = true) }
                ?: throw IllegalArgumentException(
                    "Unsupported DINSTAR model '$value'. Supported: ${entries.joinToString(", ") { it.modelId }}"
                )
    }
}
