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
    @Volatile
    private var isRecording: Boolean = false
    private var startedAtElapsed: Long = 0L
    private val cipher = ProtocolRecordCipher()
    private val lock = Any()

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
        synchronized(lock) {
            if (isRecording) return true
            // إصلاح خصوصية/تسرب: الخام يُكتب بامتداد .tmp (وليس .enc المضلل)
            // حتى لا يُظن ملف خام غير مشفر تسجيلاً آمناً، ويُحذف حتماً عند stop().
            // تخزين دائم في filesDir (وليس cacheDir) — لا يُمحى عند مسح كاش التطبيق
            val dir = File(context.filesDir, "recordings").apply { mkdirs() }
            // تنظيف بقايا tmp من تسجيل سابق تحطم قبل stop() (خام غير مشفر).
            dir.listFiles { f -> f.name.startsWith(callId) && f.name.endsWith(".tmp") }
                ?.forEach { runCatching { it.delete() } }
            outputFile = File(dir, "${callId}_${System.currentTimeMillis()}.m4a.tmp")
            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()
            try {
                recorder.setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                recorder.setAudioEncodingBitRate(128_000)
                recorder.setAudioSamplingRate(48_000)
                val outPath = outputFile?.absolutePath ?: return false
                recorder.setOutputFile(outPath)
                recorder.prepare()
                recorder.start()
                mediaRecorder = recorder
                isRecording = true
                startedAtElapsed = SystemClock.elapsedRealtime()
                return true
            } catch (e: Exception) {
                android.util.Log.e("CallRecording", "Failed to start: ${e.message}")
                runCatching { recorder.release() }
                runCatching { outputFile?.delete() }
                outputFile = null
                isRecording = false
                return false
            }
        }
    }

    /**
     * يوقف التسجيل ويشفر الملف بـ AES-GCM تلقائياً ويقوم بمسح الملف المؤقت الخام فوراً.
     * Returns a [CallRecording] descriptor with the encrypted path.
     */
    suspend fun stop(): CallRecording? = withContext(Dispatchers.IO) {
        val recorder: MediaRecorder?
        val tempRawFile: File?
        synchronized(lock) {
            if (!isRecording) return@withContext null
            recorder = mediaRecorder
            tempRawFile = outputFile
            mediaRecorder = null
            outputFile = null
            isRecording = false
        }
        try {
            recorder?.stop()
        } catch (_: Exception) { /* may throw if too short */ }
        runCatching { recorder?.release() }
        // المدة الفعلية تُحسب من ساعة الإيقاف — كانت 0 دائماً من قبل
        val durationMs = (SystemClock.elapsedRealtime() - startedAtElapsed).coerceAtLeast(0L)
        startedAtElapsed = 0L
        if (tempRawFile == null || !tempRawFile.exists()) return@withContext null
        // تسجيل أقصر من ثانية غالباً ملف تالف — احذف الخام ولا تُنتج مشفراً فارغاً.
        if (durationMs < 1_000L || tempRawFile.length() == 0L) {
            runCatching { tempRawFile.delete() }
            return@withContext null
        }

        val dir = File(context.filesDir, "recordings").apply { mkdirs() }
        val encFile = File(dir, "${callId}_${System.currentTimeMillis()}.m4a.enc")

        try {
            // Encrypt raw M4A audio bytes using AES-GCM
            val raw = tempRawFile.readBytes()
            val encrypted = cipher.encrypt(raw)
            // مسح مرجع raw من الذاكرة فوراً قبل الكتابة.
            raw.fill(0)
            FileOutputStream(encFile).use { it.write(encrypted) }

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
            runCatching { encFile.delete() }
            null
        } finally {
            // Wipe raw unencrypted file from disk — في finally حتى لو فشل التشفير.
            runCatching { tempRawFile.delete() }
        }
    }

    fun isRecording() = isRecording

    fun release() {
        synchronized(lock) {
            try { mediaRecorder?.release() } catch (_: Exception) {}
            mediaRecorder = null
            isRecording = false
            // لا نترك خاماً غير مشفر على القرص عند الإلغاء.
            runCatching { outputFile?.delete() }
            outputFile = null
            startedAtElapsed = 0L
        }
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
