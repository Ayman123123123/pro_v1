package com.red.sovereign.features.pstn

/**
 * تنسيق أرقام الهاتف للعرض.
 *
 * فُصلت هذه الدالة عن `IncomingPstnCallScreen` قبل أرشفة تلك الشاشة:
 * الشاشة كانت واجهةً ميتة بلا مصدر بيانات، أما التنسيق فمنطق سليم
 * قائم بذاته ولا سبب لضياعه معها. النمط نفسه المتّبع مع
 * `PstnCallModels.kt`: يُفصل الرمز المشترك ثم تُؤرشف الواجهة.
 */

/**
 * ينسّق الرقم اليمني بمجموعات مقروءة.
 *
 * الصيغة الدولية (التي تبدأ بـ`+`) تُعرض كما هي لأن تقسيمها يختلف
 * باختلاف رمز الدولة، فتقسيمها بقاعدة محلية واحدة يُنتج تنسيقًا خاطئًا.
 */
internal fun formatPhoneNumber(number: String): String {
    if (number.startsWith("+")) return number
    return when (number.length) {
        in 4..6 -> "${number.take(3)} ${number.drop(3)}"
        in 7..9 -> "${number.take(3)} ${number.substring(3, minOf(6, number.length))} ${number.drop(6)}"
        else -> number
    }
}
