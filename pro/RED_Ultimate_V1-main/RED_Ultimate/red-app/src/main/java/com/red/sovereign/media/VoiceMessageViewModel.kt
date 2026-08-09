package com.red.sovereign.media

import android.app.Application
import android.media.MediaRecorder
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.red.sovereign.auth.ApiResult
import com.red.sovereign.auth.AuthorizedApiClient
import com.red.sovereign.auth.TokenStore
import com.red.sovereign.core.RedConnectionService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.CipherOutputStream
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class VoiceMessageViewModel(application: Application) : AndroidViewModel(application) {
    private val media = MediaApi(application, AuthorizedApiClient(TokenStore(application)))
    private val random = SecureRandom()
    private var recorder: MediaRecorder? = null
    private var recordingFile: File? = null
    private var ticker: Job? = null
    var state: VoiceMessageState by mutableStateOf(VoiceMessageState.Idle); private set
    var elapsedSeconds by mutableIntStateOf(0); private set
    var waveform: List<Int> by mutableStateOf(emptyList()); private set
    private var recordingPaused = false

    fun start(targetRedId: String, conversationId: String) {
        if (recorder != null || state is VoiceMessageState.Sending) return
        pendingTarget = Triple(targetRedId, conversationId, "VOICE")
        val file = File.createTempFile("voice-", ".m4a", getApplication<Application>().cacheDir)
        val instance = runCatching {
            createRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(96_000)
                setAudioSamplingRate(44_100)
                setOutputFile(file.absolutePath)
                setMaxDuration(MAX_DURATION_SECONDS * 1000)
                setOnInfoListener { _, what, _ -> if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) stopAndSendPendingTarget() }
                prepare()
                start()
            }
        }.getOrElse {
            file.delete(); pendingTarget = null; state = VoiceMessageState.Error(it.message ?: "VOICE_RECORDER_START_FAILED"); return
        }
        recordingFile = file
        recorder = instance
        elapsedSeconds = 0
        waveform = emptyList()
        recordingPaused = false
        state = VoiceMessageState.Recording(paused = false)
        ticker = viewModelScope.launch {
            var quarterSeconds = 0
            while (isActive && recorder != null) {
                delay(250)
                if (!recordingPaused) {
                    quarterSeconds++
                    elapsedSeconds = quarterSeconds / 4
                    val amplitude = runCatching { recorder?.maxAmplitude ?: 0 }.getOrDefault(0)
                    val normalized = ((amplitude / 32767f) * 100).toInt().coerceIn(2, 100)
                    waveform = (waveform + normalized).takeLast(96)
                }
            }
        }
    }

    private var pendingTarget: Triple<String, String, String>? = null

    private fun createRecorder(): MediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        MediaRecorder(getApplication<Application>())
    } else {
        @Suppress("DEPRECATION") MediaRecorder()
    }

    fun togglePause() {
        val instance = recorder ?: return
        runCatching {
            if (recordingPaused) instance.resume() else instance.pause()
            recordingPaused = !recordingPaused
            state = VoiceMessageState.Recording(recordingPaused)
        }.onFailure { state = VoiceMessageState.Error(it.message ?: "VOICE_PAUSE_FAILED") }
    }

    fun stopAndSend(targetRedId: String, conversationId: String) {
        pendingTarget = Triple(targetRedId, conversationId, "VOICE")
        stopAndSendPendingTarget()
    }

    private fun stopAndSendPendingTarget() {
        val target = pendingTarget ?: return
        val file = recordingFile ?: return
        val duration = elapsedSeconds
        val recordedWaveform = waveform
        releaseRecorder(deleteFile = false)
        if (duration < 1 || file.length() <= 0) { file.delete(); state = VoiceMessageState.Error("VOICE_TOO_SHORT"); return }
        viewModelScope.launch {
            state = VoiceMessageState.Sending
            when (val result = encryptUploadAndGrant(file, target.first, duration, recordedWaveform)) {
                is ApiResult.Error -> state = VoiceMessageState.Error(result.message)
                is ApiResult.Success -> {
                    RedConnectionService.sendPayload(
                        getApplication(), target.first, target.second, target.third,
                        result.value.toByteArray(Charsets.UTF_8)
                    )
                    state = VoiceMessageState.Sent(duration)
                }
            }
            file.delete()
            pendingTarget = null
        }
    }

    fun cancel() {
        releaseRecorder(deleteFile = true)
        pendingTarget = null
        waveform = emptyList()
        elapsedSeconds = 0
        state = VoiceMessageState.Idle
    }

    fun permissionDenied() { if (recorder == null) state = VoiceMessageState.Error("MICROPHONE_PERMISSION_REQUIRED") }
    fun clear() { if (recorder == null) state = VoiceMessageState.Idle }

    private suspend fun encryptUploadAndGrant(file: File, targetRedId: String, duration: Int, waveform: List<Int>): ApiResult<String> {
        val key = ByteArray(32).also(random::nextBytes)
        val nonce = ByteArray(12).also(random::nextBytes)
        val encrypted = File.createTempFile("voice-encrypted-", ".bin", getApplication<Application>().cacheDir)
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
            }
            FileInputStream(file).use { input -> CipherOutputStream(FileOutputStream(encrypted), cipher).use { output ->
                val buffer = ByteArray(32 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                    output.write(buffer, 0, count)
                }
            } }
            when (val uploaded = media.uploadEncrypted(encrypted, "voice-note")) {
                is ApiResult.Error -> uploaded
                is ApiResult.Success -> when (val grant = media.grant(uploaded.value.objectKey, targetRedId)) {
                    is ApiResult.Error -> { media.delete(uploaded.value.url); grant }
                    is ApiResult.Success -> ApiResult.Success(uploaded.code, Json.encodeToString(
                        VoiceManifest(
                            objectKey = uploaded.value.objectKey,
                            url = uploaded.value.url,
                            name = "voice-${System.currentTimeMillis()}.m4a",
                            size = file.length(),
                            durationSeconds = duration,
                            waveform = waveform.map { it.coerceIn(0, 100) }.take(96),
                            sha256 = digest.digest().joinToString("") { "%02x".format(it) },
                            key = Base64.getEncoder().encodeToString(key),
                            nonce = Base64.getEncoder().encodeToString(nonce)
                        )
                    ))
                }
            }
        } catch (error: Exception) {
            ApiResult.Error(null, error.message ?: "VOICE_ENCRYPTION_FAILED")
        } finally {
            key.fill(0); nonce.fill(0); encrypted.delete()
        }
    }

    private fun releaseRecorder(deleteFile: Boolean) {
        ticker?.cancel(); ticker = null
        runCatching { recorder?.stop() }
        runCatching { recorder?.reset() }
        runCatching { recorder?.release() }
        recorder = null
        if (deleteFile) recordingFile?.delete()
        recordingFile = null
    }

    override fun onCleared() { releaseRecorder(deleteFile = true); super.onCleared() }

    companion object { private const val MAX_DURATION_SECONDS = 600 }
}

@kotlinx.serialization.Serializable
data class VoiceManifest(
    val version: Int = 1,
    val objectKey: String,
    val url: String,
    val name: String,
    val mimeType: String = "audio/mp4",
    val size: Long,
    val durationSeconds: Int,
    val waveform: List<Int> = emptyList(),
    val sha256: String,
    val key: String,
    val nonce: String
)

sealed interface VoiceMessageState {
    data object Idle : VoiceMessageState
    data class Recording(val paused: Boolean) : VoiceMessageState
    data object Sending : VoiceMessageState
    data class Sent(val durationSeconds: Int) : VoiceMessageState
    data class Error(val message: String) : VoiceMessageState
}
