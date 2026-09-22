package com.red.sovereign.features.calls.service

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * مدير الصوتيات المخصص للمكالمات (RedAudioManager).
 * يتحكم في توجيه الصوت (سماعة الأذن، مكبر الصوت، البلوتوث) وإدارة تركيز الصوت (Audio Focus).
 */
@Singleton
class RedAudioManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var savedAudioMode = AudioManager.MODE_NORMAL
    private var savedSpeakerphoneOn = false
    private var savedMicrophoneMute = false
    private var isAudioFocused = false
    private var hasBluetoothHeadset = false
    private var hasWiredHeadset = false

    companion object {
        private const val TAG = "RedAudioManager"
    }

    /**
     * إعداد النظام الصوتي لبدء المكالمة (طلب التركيز، تغيير الوضع إلى IN_COMMUNICATION).
     */
    fun startCall() {
        Log.d(TAG, "Starting call audio setup")
        savedAudioMode = audioManager.mode
        savedSpeakerphoneOn = audioManager.isSpeakerphoneOn
        savedMicrophoneMute = audioManager.isMicrophoneMute

        requestAudioFocus()
        
        // استخدام IN_COMMUNICATION لتقليل تأخير الصوت وتحسين جودة VoIP
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        
        // التحقق من الأجهزة المتصلة لتوجيه الصوت بشكل صحيح
        updateAudioDevices()
        
        when {
            hasBluetoothHeadset -> setBluetoothAudio(true)
            hasWiredHeadset -> setSpeakerphoneOn(false)
            else -> setSpeakerphoneOn(false) // سماعة الأذن الافتراضية
        }
    }

    /**
     * إنهاء المكالمة وإعادة النظام الصوتي إلى حالته الأصلية.
     */
    fun endCall() {
        Log.d(TAG, "Ending call audio setup")
        setBluetoothAudio(false)
        audioManager.mode = savedAudioMode
        audioManager.isSpeakerphoneOn = savedSpeakerphoneOn
        audioManager.isMicrophoneMute = savedMicrophoneMute
        abandonAudioFocus()
    }

    /**
     * تفعيل/تعطيل مكبر الصوت (Speaker).
     */
    fun setSpeakerphoneOn(on: Boolean) {
        Log.d(TAG, "Setting speakerphone on: $on")
        audioManager.isSpeakerphoneOn = on
    }

    /**
     * التحقق مما إذا كان مكبر الصوت مفعلاً.
     */
    fun isSpeakerphoneOn(): Boolean = audioManager.isSpeakerphoneOn

    /**
     * تفعيل/تعطيل الميكروفون.
     */
    fun setMicrophoneMute(on: Boolean) {
        Log.d(TAG, "Setting microphone mute: $on")
        audioManager.isMicrophoneMute = on
    }

    private fun requestAudioFocus() {
        if (isAudioFocused) return
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val focusRequest = android.media.AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                .setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener { focusChange ->
                    Log.d(TAG, "Audio focus changed: $focusChange")
                }
                .build()
            audioManager.requestAudioFocus(focusRequest)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                null,
                AudioManager.STREAM_VOICE_CALL,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
            )
        }

        isAudioFocused = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        Log.d(TAG, "Audio focus requested, granted: $isAudioFocused")
    }

    private fun abandonAudioFocus() {
        if (!isAudioFocused) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Note: In real app, keep reference to the AudioFocusRequest object
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        }
        isAudioFocused = false
    }

    private fun updateAudioDevices() {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        hasBluetoothHeadset = devices.any { 
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || 
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP 
        }
        hasWiredHeadset = devices.any { 
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET || 
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
            it.type == AudioDeviceInfo.TYPE_USB_HEADSET
        }
        Log.d(TAG, "Audio devices - Bluetooth: $hasBluetoothHeadset, Wired: $hasWiredHeadset")
    }

    @Suppress("DEPRECATION")
    private fun setBluetoothAudio(on: Boolean) {
        if (on) {
            audioManager.startBluetoothSco()
            audioManager.isBluetoothScoOn = true
        } else {
            audioManager.isBluetoothScoOn = false
            audioManager.stopBluetoothSco()
        }
    }
}
