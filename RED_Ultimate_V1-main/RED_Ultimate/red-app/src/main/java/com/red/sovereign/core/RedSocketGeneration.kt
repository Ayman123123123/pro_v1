package com.red.sovereign.core

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * عدّاد أجيال لاتصال WebSocket — يرفض callbacks المتأخرة من مقبس قديم
 * بعد استبداله بآخر، حتى لا يعطّل وصول متأخر (onFailure/onClosed لمقبس
 * ملغى) حالة الاتصال الأحدث.
 *
 * العقد:
 * - begin() يعيد رمزاً للجيل الحالي (ويعيد التهيئة إذا كان مسبقاً ملغى).
 * - invalidate() يُبطل الرموز الصادرة سابقاً فوراً.
 * - isCurrent(token) صحيح فقط للرمز الأخير في الجيل غير الملغى.
 */
class RedSocketGeneration {
    private val generation = AtomicLong(0)
    private val invalidated = AtomicBoolean(false)
    private val tokenSeq = AtomicLong(0)

    @Volatile private var currentToken = 0L

    fun begin(): Long = synchronized(this) {
        if (invalidated.get()) {
            generation.incrementAndGet()
            invalidated.set(false)
        }
        currentToken = tokenSeq.incrementAndGet()
        currentToken
    }

    fun invalidate() {
        invalidated.set(true)
    }

    fun isCurrent(token: Long): Boolean =
        !invalidated.get() && token == currentToken
}
