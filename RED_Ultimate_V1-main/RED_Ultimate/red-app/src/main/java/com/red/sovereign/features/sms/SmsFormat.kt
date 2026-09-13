package com.red.sovereign.features.sms

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

fun formatSmsTime(epochSeconds: Long): String {
    if (epochSeconds <= 0) return ""
    val date = Date(epochSeconds * 1000)
    val now = Date()
    return if (SMS_DAY_FORMAT.format(date) == SMS_DAY_FORMAT.format(now)) SMS_TIME_FORMAT.format(date)
    else SMS_DATE_TIME_FORMAT.format(date)
}

fun formatTime(epochSeconds: Long): String =
    SMS_TIME_FORMAT.format(Date(epochSeconds * 1000))
