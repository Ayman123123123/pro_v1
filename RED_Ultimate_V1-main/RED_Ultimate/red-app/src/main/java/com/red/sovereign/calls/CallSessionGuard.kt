package com.red.sovereign.calls

import java.util.concurrent.atomic.AtomicLong

/**
 * حارس جلسات المكالمات — يمنع مؤقت التنظيف المؤجل من تدمير مكالمة جديدة.
 *
 * السياق: بعد إنهاء مكالمة (رن/رد/مشغول/خطأ...) يجدول [YounesCallService]
 * تنظيفاً مؤجلاً (3-4 ثوانٍ) يعيد الحالة إلى [CallUiState.Idle] ويفرغ محرك
 * WebRTC. إذا بدأ المستخدم مكالمة جديدة خلال تلك النافذة، كان التنظيف القديم
 * يقتل المكالمة الجديدة (الطرف الآخر لا يصل أبداً + تجمّد/انهيار).
 *
 * الحل: كل مكالمة جديدة ترفع [currentToken]. عند جدولة تنظيف نلتقط الرمز
 * الحالي عبر [captureToken]، وعند الاستحقاق لا تُنفَّذ إعادة الضبط إلا إذا
 * لم تبدأ جلسة أحدث ([isCurrent]).
 */
class CallSessionGuard {
    private val token = AtomicLong(0L)

    val currentToken: Long get() = token.get()

    /** يبدأ جلسة مكالمة جديدة ويرفع الرمز. يعيد الرمز الجديد. */
    fun beginNewSession(): Long = token.incrementAndGet()

    /** يلتقط رمز الجلسة الحالية لجدولة تنظيف مؤجل لاحق. */
    fun captureToken(): Long = token.get()

    /** هل الرمز الملتقط ما زال يخص الجلسة الحالية (لم تبدأ جلسة أحدث)؟ */
    fun isCurrent(tokenAtSchedule: Long): Boolean = token.get() == tokenAtSchedule
}
