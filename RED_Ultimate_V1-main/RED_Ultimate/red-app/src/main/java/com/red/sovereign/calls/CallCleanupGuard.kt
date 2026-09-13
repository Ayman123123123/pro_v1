package com.red.sovereign.calls

import java.util.concurrent.atomic.AtomicLong

/**
 * يمنع coroutine التنظيف المؤجل لمكالمة منتهية من مسح حالة مكالمة بدأت لاحقاً.
 * كل مكالمة جديدة تحصل على جيل جديد؛ ولا يحق لـ cleanup التصرف إلا في جيله.
 */
internal class CallCleanupGuard {
    private val generation = AtomicLong(0L)

    fun beginNewCall(): Long = generation.incrementAndGet()

    fun currentGeneration(): Long = generation.get()

    fun isCurrent(candidate: Long): Boolean = candidate == generation.get()
}
