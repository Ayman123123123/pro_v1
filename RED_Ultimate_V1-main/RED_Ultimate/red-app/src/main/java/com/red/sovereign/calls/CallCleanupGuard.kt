package com.red.sovereign.calls

/**
 * يمنع coroutine التنظيف المؤجل لمكالمة منتهية من مسح حالة مكالمة بدأت لاحقاً.
 * كل مكالمة جديدة تحصل على جيل جديد؛ ولا يحق لـ cleanup التصرف إلا في جيله.
 */
internal class CallCleanupGuard {
    private var generation: Long = 0L

    fun beginNewCall(): Long {
        generation += 1L
        return generation
    }

    fun currentGeneration(): Long = generation

    fun isCurrent(candidate: Long): Boolean = candidate == generation
}
