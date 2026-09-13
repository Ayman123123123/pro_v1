package com.red.sovereign.features.sms

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 📏 تنسيق SMS الموحد — المصدر الوحيد لحساب المقاطع والوقت.
 *
 * التوحيد (2026-09-10): كان `smsSegmentCount` مكرراً حرفياً في
 * `SmsChatScreen.kt` و`SmsScreens.kt` (عبر `features/pstn`)، وكان
 * `formatTime`/`formatSmsTime` بنسختين (بسيطة HH:mm، وذكية بنفس اليوم).
 * الآن الكل يستورد من هنا — القاعدة واحدة (GSM‑7: 160/153، عربي/UCS2:
 * 70/67) فلا يتفرّع تقديران مختلفان للتكلفة.
 */

/**
 * تقدير مقاطع SMS وفق 3GPP TS 23.038 (بالحروف لا بالبايتات):
 * GSM‑7 مقطع واحد ≤160 ثم 153 لكل مقطع (7 خانات لرأس التسلسل)؛
 * UCS2 (عربي) مقطع واحد ≤70 ثم 67 لكل مقطع.
 * القسمة الصحيحة الساذجة (`length / 160`) كانت تُظهر «1» لـ161 حرفاً.
 */
fun smsSegmentCount(text: String): Int {
    if (text.isEmpty()) return 0
    val ucs2 = text.any { it.code > 0x7F }
    return if (!ucs2) {
        if (text.length <= 160) 1 else 1 + (text.length - 160 + 152) / 153
    } else {
        if (text.length <= 70) 1 else 1 + (text.length - 70 + 66) / 67
    }
}

private val SMS_TIME_FORMAT = SimpleDateFormat("HH:mm", Locale.getDefault())
private val SMS_DATE_TIME_FORMAT = SimpleDateFormat("d/M HH:mm", Locale.getDefault())
private val SMS_DAY_FORMAT = SimpleDateFormat("yyyy-D", Locale.ROOT)

/** وقت ذكي: ساعة اليوم لرسائل اليوم، ويوم+ساعة للأقدم — بالعربية ضمنياً. */
fun formatSmsTime(epochSeconds: Long): String {
    if (epochSeconds <= 0) return ""
    val date = Date(epochSeconds * 1000)
    val now = Date()
    return if (SMS_DAY_FORMAT.format(date) == SMS_DAY_FORMAT.format(now)) SMS_TIME_FORMAT.format(date)
    else SMS_DATE_TIME_FORMAT.format(date)
}

/** ساعة فقط HH:mm — للفقاعات المدمجة التي تعرض التاريخ بفاصل مستقل. */
fun formatTime(epochSeconds: Long): String =
    SMS_TIME_FORMAT.format(Date(epochSeconds * 1000))
