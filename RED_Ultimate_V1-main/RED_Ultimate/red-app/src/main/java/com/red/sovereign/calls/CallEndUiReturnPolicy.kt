package com.red.sovereign.calls

/** لا نعيد واجهة RED إلا إذا كانت شاشة CallEnded ما زالت تخص جيل المكالمة نفسه. */
internal object CallEndUiReturnPolicy {
    const val TELECOM_SETTLE_DELAY_MS = 850L

    fun shouldReturnToRed(generationMatches: Boolean, state: CallUiState): Boolean =
        generationMatches && state is CallUiState.CallEnded
}
