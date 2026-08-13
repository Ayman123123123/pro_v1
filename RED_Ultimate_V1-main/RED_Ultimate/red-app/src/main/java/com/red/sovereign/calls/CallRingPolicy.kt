package com.red.sovereign.calls

/**
 * سياسة الرنين للمكالمة الفردية عبر الإنترنت — كما في واتساب/تلجرام:
 * يرن الجهاز حتى القبول أو الرفض أو انتهاء المهلة، ثم تُسجَّل فائتة.
 * لا تُطبَّق على المساحات ولا المؤتمرات ولا البث.
 */
object CallRingPolicy {
    const val UNANSWERED_TIMEOUT_MS = 45_000L

    fun shouldExpireUnanswered(elapsedMs: Long, ringing: Boolean): Boolean =
        ringing && elapsedMs >= UNANSWERED_TIMEOUT_MS

    fun isOneToOneRingState(state: CallUiState): Boolean =
        state is CallUiState.Incoming || state is CallUiState.Connecting

    fun unansweredMessage(outgoing: Boolean): String =
        if (outgoing) "لم يتم الرد" else "مكالمة فائتة"
}
