package com.red.sovereign.calls

import android.content.Context

/**
 * Reserved interface for captions. The former implementation imported a
 * nonexistent ML Kit speech recognizer, guessed the language from the word
 * "test", and recorded the local microphone rather than the remote call.
 * None of that could deliver private, on-device captions. Fail explicitly until
 * a vetted offline recognizer and consent-driven remote-audio path are built.
 */
class LiveCaptionManager(@Suppress("UNUSED_PARAMETER") context: Context) {
    interface Listener {
        fun onCaption(text: String, isFinal: Boolean, language: String)
        fun onError(message: String)
    }

    fun setListener(@Suppress("UNUSED_PARAMETER") listener: Listener) = Unit

    fun start(listener: Listener) {
        listener.onError("Live captions are not supported in this build")
    }

    fun stop() = Unit

    data class CaptionResult(val text: String, val isFinal: Boolean, val language: String)

    companion object {
        fun isAvailable(@Suppress("UNUSED_PARAMETER") context: Context): Boolean = false
    }
}
