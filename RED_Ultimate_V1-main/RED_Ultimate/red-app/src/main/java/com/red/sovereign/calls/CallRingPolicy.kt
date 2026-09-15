package com.red.sovereign.calls

/**
 * سياسة الرنين للمكالمة الفردية عبر الإنترنت — كما في واتساب/تلجرام:
 * يرن الجهاز حتى القبول أو الرفض أو انتهاء المهلة، ثم تُسجَّل فائتة.
 * لا تُطبَّق على المساحات ولا المؤتمرات ولا البث.
 */
object CallRingPolicy {
    const val UNANSWERED_TIMEOUT_MS = 45_000L
    /** عمر صندوق البريد بالثواني — يطابق FORCED_TTL_SECONDS في الساحب. */
    const val MAILBOX_TTL_SECONDS = 120
    /** نافذة الرنين بالمللي — يطابق مهلة السجل والمهلة الواردة. */
    const val RING_WINDOW_MS = 45_000L

    fun shouldExpireUnanswered(elapsedMs: Long, ringing: Boolean): Boolean =
        ringing && elapsedMs >= UNANSWERED_TIMEOUT_MS

    /** انتهت حياة العرض (العمر >= 120 ثانية) — يُسقط بصمت بلا سجل. */
    fun isOfferExpired(createdAtMs: Long, now: Long = System.currentTimeMillis()): Boolean =
        now - createdAtMs >= MAILBOX_TTL_SECONDS * 1000L

    /** داخل نافذة الرنين (العمر < 45 ثانية) وحي — خارجها تُسجَّل فائتة. */
    fun shouldRingNow(createdAtMs: Long, now: Long = System.currentTimeMillis()): Boolean =
        now - createdAtMs < RING_WINDOW_MS && !isOfferExpired(createdAtMs, now)

    fun isOneToOneRingState(state: CallUiState): Boolean =
        state is CallUiState.Incoming || state is CallUiState.Connecting

    fun unansweredMessage(outgoing: Boolean): String =
        if (outgoing) "لم يتم الرد" else "مكالمة فائتة"
}
