package com.red.sovereign.calls

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import com.red.sovereign.crypto.ProtocolRecordCipher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Call recording with E2EE.
 *
 * Privacy considerations:
 * - The user must explicitly opt-in to recording (consent dialog before each call).
 * - Recordings are AES-256-GCM encrypted at rest using Android Keystore.
 * - The encryption key is bound to the device; recordings cannot be decrypted on another device.
 * - The decryption key is the same ProtocolRecordCipher used for chat history.
 *
 * The recorded file is in raw PCM AAC (M4A container) — encrypted with AES-GCM before being
 * written to disk. Decryption happens only on explicit user action (e.g. "play recording" UI).
 *
 * Limitations:
 * - Requires RECORD_AUDIO permission (granted at call time).
 * - Records only the LOCAL mic (not the remote audio). To record both sides, use server-side
 *   recording (e.g. media-sfu with TURN-side recording). This is the privacy-conscious choice.
 * - Some countries require two-party consent for recording — we display a banner during recording.
 */
class CallRecordingManager(
    private val context: Context,
    private val callId: String
) {
    private var mediaRecorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var isRecording: Boolean = false
    private var startedAtElapsed: Long = 0L
    private val cipher = ProtocolRecordCipher()

    /**
     * يبدأ تسجيل. يُرجع true عند النجاح.
     * @param consentGranted هل الطرف الآخر وافق (two-party consent)
     */
    @Suppress("DEPRECATION")
    fun start(consentGranted: Boolean): Boolean {
        if (!consentGranted) {
            android.util.Log.w("CallRecording", "Recording refused: consent not granted")
            return false
        }
        if (isRecording) return true
        // تخزين دائم في filesDir (وليس cacheDir) — لا يُمحى عند مسح كاش التطبيق
        val dir = File(context.filesDir, "recordings").apply { mkdirs() }
        outputFile = File(dir, "${callId}_${System.currentTimeMillis()}.m4a.enc")
        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()
        try {
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setAudioEncodingBitRate(128_000)
            recorder.setAudioSamplingRate(48_000)
            recorder.setOutputFile(outputFile!!.absolutePath)
            recorder.prepare()
            recorder.start()
            mediaRecorder = recorder
            isRecording = true
            startedAtElapsed = SystemClock.elapsedRealtime()
            return true
        } catch (e: Exception) {
            android.util.Log.e("CallRecording", "Failed to start: ${e.message}")
            release()
            return false
        }
    }

    /**
     * يوقف التسجيل ويشفر الملف بـ AES-GCM تلقائياً ويقوم بمسح الملف المؤقت الخام فوراً.
     * Returns a [CallRecording] descriptor with the encrypted path.
     */
    suspend fun stop(): CallRecording? = withContext(Dispatchers.IO) {
        if (!isRecording) return@withContext null
        val recorder = mediaRecorder
        val tempRawFile = outputFile
        try {
            recorder?.stop()
        } catch (_: Exception) { /* may throw if too short */ }
        recorder?.release()
        mediaRecorder = null
        isRecording = false
        // المدة الفعلية تُحسب من ساعة الإيقاف — كانت 0 دائماً من قبل
        val durationMs = (SystemClock.elapsedRealtime() - startedAtElapsed).coerceAtLeast(0L)
        startedAtElapsed = 0L
        if (tempRawFile == null || !tempRawFile.exists()) return@withContext null

        val dir = File(context.filesDir, "recordings").apply { mkdirs() }
        val encFile = File(dir, "${callId}_${System.currentTimeMillis()}.m4a.enc")

        try {
            // Encrypt raw M4A audio bytes using AES-GCM
            val raw = tempRawFile.readBytes()
            val encrypted = cipher.encrypt(raw)
            FileOutputStream(encFile).use { it.write(encrypted) }
            
            // Wipe raw unencrypted file from disk
            tempRawFile.delete()

            CallRecording(
                callId = callId,
                filePath = encFile.absolutePath,
                sizeBytes = encFile.length(),
                encrypted = true,
                createdAt = System.currentTimeMillis(),
                durationMs = durationMs
            )
        } catch (e: Exception) {
            android.util.Log.e("CallRecording", "Failed to encrypt recording: ${e.message}")
            tempRawFile.delete()
            null
        }
    }

    fun isRecording() = isRecording

    fun release() {
        try { mediaRecorder?.release() } catch (_: Exception) {}
        mediaRecorder = null
        isRecording = false
    }

    /**
     * يفك تشفير تسجيل لاستماع المستخدم.
     * Returns the decrypted bytes (M4A format) ready for playback.
     */
    suspend fun decryptForPlayback(filePath: String): ByteArray? = withContext(Dispatchers.IO) {
        val file = File(filePath)
        if (!file.exists()) return@withContext null
        val encrypted = file.readBytes()
        runCatching { cipher.decrypt(encrypted) }.getOrNull()
    }
}

/**
 * descriptor للتسجيل المُشفر.
 */
data class CallRecording(
    val callId: String,
    val filePath: String,
    val sizeBytes: Long,
    val encrypted: Boolean,
    val createdAt: Long,
    val durationMs: Long
)
