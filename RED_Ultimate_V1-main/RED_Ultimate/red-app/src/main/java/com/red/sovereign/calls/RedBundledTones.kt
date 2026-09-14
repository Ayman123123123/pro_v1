package com.red.sovereign.calls

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import com.red.sovereign.R

/**
 * Bundled RED call-progress tones (Phase 11 extraction from the legacy tree).
 *
 * Origin: Signal-Android (GPL-3.0, §13-compatible with this AGPL-3.0 work) —
 * see attribution in red-app/README.md. System [android.media.ToneGenerator]
 * covers busy/congestion/ringback; these raws cover the earcons it cannot:
 * a selectable classic incoming ringtone + connected/ended/message blips.
 */
object RedBundledTones {
    private const val TAG = "RedBundledTones"

    /** Classic incoming ringtone (loops via RingtoneManager like any picked tone). */
    const val RAW_INCOMING_CLASSIC = R.raw.redphone_outring

    /** Short blips (one-shot, never looped). */
    const val RAW_CONNECTED = R.raw.webrtc_completed
    const val RAW_ENDED = R.raw.webrtc_disconnected
    const val RAW_MESSAGE = R.raw.notification_simple_01

    const val TITLE_INCOMING_CLASSIC = "RED الكلاسيكية (مضمّنة)"

    /** `android.resource://` URI playable by RingtoneManager/MediaPlayer. */
    fun bundledUri(context: Context, resId: Int): Uri =
        Uri.parse("android.resource://${context.packageName}/$resId")

    fun incomingClassicUri(context: Context): Uri = bundledUri(context, RAW_INCOMING_CLASSIC)

    /** True when [uriString] points at one of our bundled raws. */
    fun isBundled(uriString: String?, context: Context): Boolean {
        if (uriString.isNullOrBlank()) return false
        if (!uriString.startsWith("android.resource://${context.packageName}/")) return false
        val id = uriString.substringAfterLast('/').toIntOrNull() ?: return isBundledName(uriString)
        return id == RAW_INCOMING_CLASSIC || id == RAW_CONNECTED || id == RAW_ENDED || id == RAW_MESSAGE
    }

    private fun isBundledName(uriString: String): Boolean =
        uriString.substringAfterLast('/').substringBefore('?') in
            setOf("redphone_outring", "webrtc_completed", "webrtc_disconnected", "notification_simple_01")

    /** Arabic display title for a bundled ringtone URI, or null when not bundled. */
    fun bundledTitle(uriString: String?, context: Context): String? {
        if (!isBundled(uriString, context)) return null
        return TITLE_INCOMING_CLASSIC
    }

    /**
     * Fire-and-forget one-shot blip (connected/ended/message). Never throws,
     * never loops, always releases the player. Safe to call from any thread.
     */
    fun playOneShot(context: Context, resId: Int, usage: Int = AudioAttributes.USAGE_VOICE_COMMUNICATION) {
        try {
            val attrs = AudioAttributes.Builder()
                .setUsage(usage)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val player = MediaPlayer.create(context.applicationContext, resId, attrs, 0)
            if (player == null) {
                Log.w(TAG, "one-shot create failed res=$resId")
                return
            }
            player.setOnCompletionListener { runCatching { it.release() } }
            player.setOnErrorListener { mp, _, _ -> runCatching { mp.release() }; true }
            player.start()
        } catch (t: Throwable) {
            Log.w(TAG, "one-shot play failed res=$resId", t)
        }
    }
}
