package com.red.sovereign.calls

/** يحدد متى يبدأ إنشاء OFFER لمكالمة صادرة من دون انتظار callback لن يأتي. */
internal object OutgoingOfferStartPolicy {
    fun shouldStart(
        outgoingPending: Boolean,
        signalingConnected: Boolean,
        currentCallId: String?,
        offerStartedForCallId: String?
    ): Boolean = outgoingPending &&
        signalingConnected &&
        !currentCallId.isNullOrBlank() &&
        currentCallId != offerStartedForCallId
}
