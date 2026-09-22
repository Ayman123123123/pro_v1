package com.red.sovereign.features.calls.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * مدير النغمات والاهتزاز للمكالمات (RedRingtoneManager).
 * يتعامل مع تشغيل نغمة الرنين عند ورود مكالمة، والاهتزاز، ونغمة الرنين المحلي (Dial tone) عند الاتصال.
 */
@Singleton
class RedRingtoneManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var mediaPlayer: MediaPlayer? = null
    private var isPlaying = false

    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    companion object {
        private const val TAG = "RedRingtoneManager"
    }

    /**
     * تشغيل نغمة الرنين الواردة (Incoming Call Ringtone).
     */
    fun startRinging() {
        if (isPlaying) return
        Log.d(TAG, "Start ringing")

        try {
            val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, ringtoneUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }
            isPlaying = true
            startVibrating()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play ringtone", e)
        }
    }

    /**
     * تشغيل نغمة الاتصال الصادرة (Outgoing Call Dial Tone).
     */
    fun startDialingTone() {
        if (isPlaying) return
        Log.d(TAG, "Start dialing tone")
        
        // TODO: استخدم نغمة مخصصة أو R.raw.dial_tone إذا وجدت
        // حالياً نستخدم نغمة إنذار مؤقتة كمثال
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, uri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }
            isPlaying = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play dial tone", e)
        }
    }

    /**
     * إيقاف أي نغمة قيد التشغيل (الرنين أو الاتصال) والاهتزاز.
     */
    fun stop() {
        Log.d(TAG, "Stop ringing/dialing")
        mediaPlayer?.let {
            if (it.isPlaying) it.stop()
            it.release()
        }
        mediaPlayer = null
        isPlaying = false
        stopVibrating()
    }

    private fun startVibrating() {
        val pattern = longArrayOf(0, 1000, 1000)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, 0)
        }
    }

    private fun stopVibrating() {
        vibrator.cancel()
    }
}
