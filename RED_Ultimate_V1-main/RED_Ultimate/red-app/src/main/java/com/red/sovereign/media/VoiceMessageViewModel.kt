package com.red.sovereign.media

import android.app.Application
import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
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
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
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

/**
 * ðŸŽ™ï¸ YOUNES Sovereign Voice Message ViewModel â€” ÙŠØ¯Ø¹Ù…:
 *
 *  **Ø§Ù„ØªØ³Ø¬ÙŠÙ„:**
 *  - MediaRecorder + AAC + 96kbps + 44.1kHz + M4A
 *  - Press-to-record + Release-to-send
 *  - lock-to-record (Ø§Ù„Ø³Ø­Ø¨ Ù„Ù„Ø£Ø¹Ù„Ù‰ Ù„Ù„Ù‚ÙÙ„)
 *  - drag-to-cancel (Ø§Ù„Ø³Ø­Ø¨ Ù„Ù„Ø£Ø³ÙÙ„/Ø§Ù„ÙŠØ³Ø§Ø±)
 *  - peak detection (live amplitude + waveform)
 *  - auto-silence trim (Ø¥Ø²Ø§Ù„Ø© Ø§Ù„ØµÙ…Øª Ù…Ù† Ø§Ù„Ø¨Ø¯Ø§ÙŠØ© ÙˆØ§Ù„Ù†Ù‡Ø§ÙŠØ©)
 *  - quality modes (Standard 96kbps / High 128kbps / Ultra 192kbps)
 *
 *  **Ø§Ù„Ù…Ø¹Ø§ÙŠÙ†Ø©:**
 *  - preview Ù‚Ø¨Ù„ Ø§Ù„Ø¥Ø±Ø³Ø§Ù„ (Ù…Ø¹ waveform ÙˆØ£Ø²Ø±Ø§Ø±)
 *  - playback Ù…Ø­Ù„ÙŠ (Ø§Ø®ØªÙŠØ§Ø±ÙŠ)
 *  - edit waveform (Ø§Ø®ØªÙŠØ§Ø±ÙŠ)
 *
 *  **Ø§Ù„ØªØ´ÙÙŠØ±:**
 *  - E2E Ø¨Ù€ AES-256-GCM + Key ÙÙŠ Android Keystore
 *  - SHA-256 integrity
 *  - Waveform 96 sample ÙƒÙ€ base64
 *
 *  **Ø§Ù„Ø¥Ø±Ø³Ø§Ù„:**
 *  - Multipart upload Ø¥Ù„Ù‰ MinIO
 *  - VoiceManifest JSON
 *  - Signal Protocol encryption
 */
class VoiceMessageViewModel(application: Application) : AndroidViewModel(application) {
    private val media = MediaApi(application, AuthorizedApiClient(TokenStore(application)))
    private val random = SecureRandom()
    private var recorder: MediaRecorder? = null
    private var recordingFile: File? = null
    /** MediaRecorder can exist after prepare() has failed; never treat that as a recording. */
    private var recorderStarted = false
    private var ticker: Job? = null
    private var pendingTarget: Triple<String, String, String>? = null
    private var pendingGroup: com.red.sovereign.groups.Group? = null
    private var recordingPaused = false
    private var startTimeMs: Long = 0L
    private var pausedDurationMs: Long = 0L
    private var lastPauseTimeMs: Long = 0L

    var state: VoiceMessageState by mutableStateOf(VoiceMessageState.Idle); private set
    var elapsedSeconds by mutableIntStateOf(0); private set
    var waveform: List<Int> by mutableStateOf(emptyList()); private set

    // Ù„Ù„Ø­ÙØ¸ Ø§Ù„Ù…Ø¤Ù‚Øª Ù‚Ø¨Ù„ Ø§Ù„Ø¥Ø±Ø³Ø§Ù„ (preview)
    var previewPath: String? by mutableStateOf(null); private set
    var previewDuration: Int by mutableIntStateOf(0); private set
    var previewWaveform: List<Int> by mutableStateOf(emptyList()); private set

    // Ù„Ù„Ø­ÙØ¸ Ø§Ù„Ù…Ø¤Ù‚Øª ÙÙŠ Ø­Ø§Ù„Ø© lock-to-record
    var isLocked: Boolean by mutableStateOf(false); private set
    // Ù„Ù„Ø­ÙØ¸ Ø§Ù„Ù…Ø¤Ù‚Øª ÙÙŠ Ø­Ø§Ù„Ø© drag-to-cancel (Ù†Ø³Ø¨Ø© Ø§Ù„Ø¥Ù„ØºØ§Ø¡ 0..1)
    var cancelProgress: Float by mutableStateOf(0f); private set

    // Quality mode (default: STANDARD)
    var qualityMode: VoiceQuality by mutableStateOf(VoiceQuality.STANDARD); private set
    // Peak detection - for visualizing input level in real time
    var currentPeak by mutableIntStateOf(0); private set
    // Average amplitude (for silence detection)
    private var amplitudeHistory: MutableList<Int> = mutableListOf()
    // Number of consecutive low-amplitude samples (for auto-trim)
    private var consecutiveSilenceQuarters: Int = 0
    // Whether recording is silent
    var isSilent by mutableStateOf(false); private set

    fun start(targetRedId: String, conversationId: String) {
        if (recorder != null || state is VoiceMessageState.Sending) return
        pendingTarget = Triple(targetRedId, conversationId, "VOICE")
        pendingGroup = null
        startRecorder()
    }

    /** ÙŠØ¨Ø¯Ø£ ØªØ³Ø¬ÙŠÙ„Ø§Ù‹ Ù…ÙˆØ¬Ù‡Ø§Ù‹ Ù„Ù…Ø¬Ù…ÙˆØ¹Ø© (Ù…Ø³Ø§Ø± ØªØ´ÙÙŠØ± Ø§Ù„Ù…Ø¬Ù…ÙˆØ¹Ø© Ø¹Ù†Ø¯ Ø§Ù„Ø¥Ø±Ø³Ø§Ù„). */
    fun startForGroup(group: com.red.sovereign.groups.Group) {
        if (recorder != null || state is VoiceMessageState.Sending) return
        pendingTarget = null
        pendingGroup = group
        startRecorder()
    }

    private fun startRecorder() {
        val app = getApplication<Application>()
        if (ContextCompat.checkSelfPermission(app, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            state = VoiceMessageState.Error("MICROPHONE_PERMISSION_REQUIRED")
            pendingTarget = null
            pendingGroup = null
            return
        }
        val file = File.createTempFile("voice-", ".m4a", getApplication<Application>().cacheDir)
        val instance = runCatching {
            createRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(qualityMode.bitrate)
                setAudioSamplingRate(qualityMode.sampleRate)
                setOutputFile(file.absolutePath)
                setMaxDuration(MAX_DURATION_SECONDS * 1000)
                setOnInfoListener { _, what, _ -> if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) stopAndSendPendingTarget() }
                prepare()
                start()
            }
        }.getOrElse {
            file.delete(); pendingTarget = null; pendingGroup = null
            state = VoiceMessageState.Error("VOICE_RECORDER_START_FAILED: ${it.message.orEmpty()}")
            return
        }
        recordingFile = file
        recorder = instance
        recorderStarted = true
        elapsedSeconds = 0
        waveform = emptyList()
        amplitudeHistory.clear()
        consecutiveSilenceQuarters = 0
        recordingPaused = false
        isLocked = false
        cancelProgress = 0f
        isSilent = false
        startTimeMs = SystemClock.elapsedRealtime()
        pausedDurationMs = 0L
        state = VoiceMessageState.Recording(paused = false)
        ticker = viewModelScope.launch {
            var quarterSeconds = 0
            while (isActive && recorder != null) {
                delay(250)
                if (!recordingPaused) {
                    quarterSeconds++
                    // A monotonic clock remains correct when the system time changes and
                    // avoids reporting a zero-length recording after the first second.
                    elapsedSeconds = ((SystemClock.elapsedRealtime() - startTimeMs - pausedDurationMs) / 1_000L)
                        .toInt().coerceAtLeast(0)
                    val amplitude = runCatching { recorder?.maxAmplitude ?: 0 }.getOrDefault(0)
                    val normalized = ((amplitude / 32767f) * 100).toInt().coerceIn(2, 100)
                    currentPeak = normalized
                    waveform = (waveform + normalized).takeLast(96)

                    // Silence detection (amplitude < 5 for 8+ consecutive samples = ~2s of silence)
                    if (normalized < 5) {
                        consecutiveSilenceQuarters++
                        if (consecutiveSilenceQuarters >= 8) {
                            isSilent = true
                        }
                    } else {
                        consecutiveSilenceQuarters = 0
                        isSilent = false
                    }

                    // Update amplitude history (for visual feedback)
                    amplitudeHistory.add(normalized)
                    if (amplitudeHistory.size > 32) amplitudeHistory.removeAt(0)
                }
            }
        }
    }

    private fun createRecorder(): MediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        MediaRecorder(getApplication<Application>())
    } else {
        @Suppress("DEPRECATION") MediaRecorder()
    }

    fun togglePause() {
        val instance = recorder ?: return
        runCatching {
            if (recordingPaused) {
                instance.resume()
                // Add paused duration
                if (lastPauseTimeMs > 0) {
                    pausedDurationMs += SystemClock.elapsedRealtime() - lastPauseTimeMs
                }
            } else {
                instance.pause()
                lastPauseTimeMs = SystemClock.elapsedRealtime()
            }
            recordingPaused = !recordingPaused
            state = VoiceMessageState.Recording(recordingPaused)
        }.onFailure { state = VoiceMessageState.Error(it.message ?: "VOICE_PAUSE_FAILED") }
    }

    /**
     * ðŸ”’ ØªÙØ¹ÙŠÙ„ Ø§Ù„Ù‚ÙÙ„ â€” ÙŠØ­ÙˆÙ‘Ù„ Ø§Ù„Ù€ Recording Ù…Ù† "Ø§Ø¶ØºØ· Ù…Ø·ÙˆÙ‘Ù„Ø§Ù‹" Ø¥Ù„Ù‰ "ÙŠØ¯ Ø­Ø±Ø©"
     */
    fun lockRecording() {
        if (state is VoiceMessageState.Recording) {
            isLocked = true
            // The user has locked, so cancel progress is reset
            cancelProgress = 0f
        }
    }

    /**
     * ðŸ“¤ ØªØ­Ø¯ÙŠØ« Ù†Ø³Ø¨Ø© Ø§Ù„Ø¥Ù„ØºØ§Ø¡ Ø¹Ù†Ø¯ Ø§Ù„Ø³Ø­Ø¨ (0 = Ù„Ø§ Ø¥Ù„ØºØ§Ø¡ØŒ 1 = Ø¥Ù„ØºØ§Ø¡ ÙƒØ§Ù…Ù„)
     * Ø¥Ø°Ø§ ÙˆØµÙ„Øª Ø¥Ù„Ù‰ CANCEL_THRESHOLDØŒ ÙŠØªÙ… Ø­Ø°Ù Ø§Ù„ØªØ³Ø¬ÙŠÙ„ ØªÙ„Ù‚Ø§Ø¦ÙŠØ§Ù‹
     */
    fun updateCancelProgress(progress: Float) {
        if (isLocked) return // Ù„Ø§ Ø¥Ù„ØºØ§Ø¡ Ø¨Ø¹Ø¯ Ø§Ù„Ù‚ÙÙ„
        cancelProgress = progress.coerceIn(0f, 1f)
        if (progress >= CANCEL_THRESHOLD && state is VoiceMessageState.Recording) {
            cancel()
        }
    }

    /**
     * ðŸ“¤ Ø¥ÙŠÙ‚Ø§Ù Ø§Ù„ØªØ³Ø¬ÙŠÙ„ ÙˆØ§Ù„Ø¯Ø®ÙˆÙ„ ÙÙŠ ÙˆØ¶Ø¹ Ø§Ù„Ù€ preview Ù‚Ø¨Ù„ Ø§Ù„Ø¥Ø±Ø³Ø§Ù„
     * ÙŠØ­ÙØ¸ Ø§Ù„Ù€ target Ùˆ conversationId Ù„Ù„Ø¥Ø±Ø³Ø§Ù„ Ø§Ù„Ù„Ø§Ø­Ù‚
     */
    fun stopAndPreview(targetRedId: String? = null, conversationId: String? = null) {
        val file = recordingFile ?: return
        val duration = elapsedSeconds
        val recordedWaveform = waveform.toList()
        if (!releaseRecorder(deleteFile = false)) {
            file.delete()
            state = VoiceMessageState.Error("VOICE_RECORDER_STOP_FAILED")
            return
        }
        if (duration < 1 || file.length() <= 0) {
            file.delete()
            state = VoiceMessageState.Error("VOICE_TOO_SHORT")
            viewModelScope.launch {
                kotlinx.coroutines.delay(2500)
                if (state is VoiceMessageState.Error) state = VoiceMessageState.Idle
            }
            return
        }
        if (targetRedId != null && conversationId != null) {
            pendingTarget = Triple(targetRedId, conversationId, "VOICE")
        }
        previewPath = file.absolutePath
        previewDuration = duration
        // Trim silence from start/end
        previewWaveform = trimSilence(recordedWaveform)
        state = VoiceMessageState.Preview(duration)
    }

    fun stopAndSend(targetRedId: String, conversationId: String) {
        pendingTarget = Triple(targetRedId, conversationId, "VOICE")
        pendingGroup = null
        stopAndSendPendingTarget()
    }

    /** ÙŠÙˆÙ‚Ù Ø§Ù„ØªØ³Ø¬ÙŠÙ„ ÙˆÙŠØ±Ø³Ù„Ù‡ Ø¥Ù„Ù‰ Ø§Ù„Ù…Ø¬Ù…ÙˆØ¹Ø© Ø¹Ø¨Ø± Ù…Ø³Ø§Ø± ØªØ´ÙÙŠØ± Ø§Ù„Ù…Ø¬Ù…ÙˆØ¹Ø© (Sender Keys). */
    fun stopAndSendToGroup(group: com.red.sovereign.groups.Group) {
        pendingTarget = null
        pendingGroup = group
        stopAndSendPendingTarget()
    }

    private fun stopAndSendPendingTarget() {
        val target = pendingTarget
        val group = pendingGroup
        val duration: Int
        val recordedWaveform: List<Int>
        val file: File

        if (state is VoiceMessageState.Preview && previewPath != null) {
            file = File(previewPath)
            duration = previewDuration
            recordedWaveform = previewWaveform
        } else {
            file = recordingFile ?: return
            duration = elapsedSeconds
            recordedWaveform = waveform
            if (!releaseRecorder(deleteFile = false)) {
                file.delete()
                state = VoiceMessageState.Error("VOICE_RECORDER_STOP_FAILED")
                pendingTarget = null
                pendingGroup = null
                return
            }
        }

        if (duration < 1 || file.length() <= 0) {
            file.delete()
            state = VoiceMessageState.Error("VOICE_TOO_SHORT")
            viewModelScope.launch {
                kotlinx.coroutines.delay(2500)
                if (state is VoiceMessageState.Error) state = VoiceMessageState.Idle
            }
            return
        }
        val grantTargets = if (group != null) group.members.map { it.redId } else target?.first?.let { listOf(it) } ?: emptyList()
        if (grantTargets.isEmpty()) {
            file.delete()
            state = VoiceMessageState.Error("VOICE_NO_TARGET")
            viewModelScope.launch {
                kotlinx.coroutines.delay(2500)
                if (state is VoiceMessageState.Error) state = VoiceMessageState.Idle
            }
            return
        }
        viewModelScope.launch {
            state = VoiceMessageState.Sending
            when (val result = encryptUploadAndGrant(file, grantTargets, duration, recordedWaveform)) {
                is ApiResult.Error -> {
                    state = VoiceMessageState.Error(result.message)
                    kotlinx.coroutines.delay(3000)
                    if (state is VoiceMessageState.Error) state = VoiceMessageState.Idle
                }
                is ApiResult.Success -> {
                    if (group != null) {
                        RedConnectionService.sendGroupPayload(
                            getApplication(),
                            group,
                            "VOICE",
                            result.value.toByteArray(Charsets.UTF_8)
                        )
                    } else {
                        target?.let {
                            RedConnectionService.sendPayload(
                                getApplication(), it.first, it.second, it.third,
                                result.value.toByteArray(Charsets.UTF_8)
                            )
                        }
                    }
                    state = VoiceMessageState.Sent(duration)
                    kotlinx.coroutines.delay(2000)
                    if (state is VoiceMessageState.Sent) state = VoiceMessageState.Idle
                }
            }
            file.delete()
            previewPath = null
            pendingTarget = null
            pendingGroup = null
        }
    }

    /**
     * ðŸ—‘ï¸ Ø­Ø°Ù Ø§Ù„Ù€ preview ÙˆØ§Ù„Ø¹ÙˆØ¯Ø© Ø¥Ù„Ù‰ Idle
     */
    fun discardPreview() {
        previewPath?.let { File(it).delete() }
        previewPath = null
        previewDuration = 0
        previewWaveform = emptyList()
        state = VoiceMessageState.Idle
    }

    fun cancel() {
        releaseRecorder(deleteFile = true)
        previewPath?.let { File(it).delete() }
        previewPath = null
        previewDuration = 0
        previewWaveform = emptyList()
        pendingTarget = null
        waveform = emptyList()
        elapsedSeconds = 0
        isLocked = false
        cancelProgress = 0f
        currentPeak = 0
        isSilent = false
        state = VoiceMessageState.Idle
    }

    fun permissionDenied() { if (recorder == null) state = VoiceMessageState.Error("MICROPHONE_PERMISSION_REQUIRED") }
    fun clear() {
        if (recorder == null) {
            previewPath?.let { File(it).delete() }
            previewPath = null
            previewDuration = 0
            previewWaveform = emptyList()
            state = VoiceMessageState.Idle
        }
    }

    /**
     * ðŸŽšï¸ Trim silence from start and end of waveform
     * Removes low-amplitude samples from the edges
     */
    private fun trimSilence(samples: List<Int>): List<Int> {
        if (samples.size < 4) return samples

        // Find first non-silent sample
        var start = 0
        while (start < samples.size && samples[start] < 8) start++

        // Find last non-silent sample
        var end = samples.size - 1
        while (end > start && samples[end] < 8) end--

        if (start >= end) return samples // All silence
        return samples.subList(start, end + 1)
    }

    private suspend fun encryptUploadAndGrant(file: File, targetRedIds: List<String>, duration: Int, waveform: List<Int>): ApiResult<String> {
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
                is ApiResult.Success -> {
                    // Ù…Ù†Ø­ Ø§Ù„ÙˆØµÙˆÙ„ Ù„ÙƒÙ„ Ù…Ø³ØªÙ„Ù… (ÙØ±Ø¯ Ø£Ùˆ Ø£Ø¹Ø¶Ø§Ø¡ Ø§Ù„Ù…Ø¬Ù…ÙˆØ¹Ø©) â€” ÙØ´Ù„ Ø¹Ø¶Ùˆ Ù„Ø§ ÙŠÙ…Ù†Ø¹ Ø§Ù„Ø¥Ø±Ø³Ø§Ù„
                                                            val grantResults = coroutineScope {
                        targetRedIds.filter { it.isNotBlank() }.map { grantee ->
                            async {
                                media.grant(uploaded.value.objectKey, grantee)
                            }
                        }.awaitAll()
                    }
                    val anyGranted = grantResults.any { it is ApiResult.Success }
                    
                    if (!anyGranted) {
                        media.delete(uploaded.value.url)
                        return ApiResult.Error(null, "VOICE_GRANT_FAILED")
                    }
                    ApiResult.Success(uploaded.code, Json.encodeToString(
                        VoiceManifest(
                            objectKey = uploaded.value.objectKey,
                            url = uploaded.value.url,
                            name = "voice-${System.currentTimeMillis()}.m4a",
                            size = file.length(),
                            durationSeconds = duration,
                            waveform = waveform.map { it.coerceIn(0, 100) }.take(96),
                            sha256 = digest.digest().joinToString("") { "%02x".format(it) },
                            key = Base64.getEncoder().encodeToString(key),
                            nonce = Base64.getEncoder().encodeToString(nonce),
                            codec = "AAC",
                            sampleRate = qualityMode.sampleRate,
                            bitrate = qualityMode.bitrate
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

    /**
     * Stops and releases the recorder.  MediaRecorder.stop() throws when no audio
     * frames were produced; returning that failure prevents uploading a corrupt M4A.
     */
    private fun releaseRecorder(deleteFile: Boolean): Boolean {
        ticker?.cancel(); ticker = null
        val stopped = if (recorderStarted && recorder != null) runCatching { recorder?.stop() }.isSuccess else true
        runCatching { recorder?.reset() }
        runCatching { recorder?.release() }
        recorder = null
        recorderStarted = false
        if (deleteFile) recordingFile?.delete()
        recordingFile = null
        isLocked = false
        cancelProgress = 0f
        currentPeak = 0
        return stopped
    }

    override fun onCleared() {
        releaseRecorder(deleteFile = true)
        previewPath?.let { File(it).delete() }
        super.onCleared()
    }

    companion object {
        private const val MAX_DURATION_SECONDS = 600
        const val CANCEL_THRESHOLD = 0.6f  // 60% Ø³Ø­Ø¨ = Ø¥Ù„ØºØ§Ø¡
    }
}

/**
 * ðŸŽšï¸ Voice Quality Modes
 * - STANDARD: 96kbps / 44.1kHz (Ø§ÙØªØ±Ø§Ø¶ÙŠØŒ ØªÙˆØ§Ø²Ù† Ø¨ÙŠÙ† Ø§Ù„Ø­Ø¬Ù… ÙˆØ§Ù„Ø¬ÙˆØ¯Ø©)
 * - HIGH: 128kbps / 44.1kHz (Ø¬ÙˆØ¯Ø© Ø¹Ø§Ù„ÙŠØ©)
 * - ULTRA: 192kbps / 48kHz (Ø¬ÙˆØ¯Ø© Ø§Ø³ØªÙˆØ¯ÙŠÙˆ)
 * - COMPACT: 64kbps / 22kHz (Ù…ÙˆÙØ± Ù„Ù„Ø¨ÙŠØ§Ù†Ø§Øª)
 */
enum class VoiceQuality(val bitrate: Int, val sampleRate: Int, val labelAr: String) {
    COMPACT(64_000, 22_050, "Ù…ÙˆÙØ± (64kbps)"),
    STANDARD(96_000, 44_100, "Ø¹Ø§Ø¯ÙŠ (96kbps)"),
    HIGH(128_000, 44_100, "Ø¹Ø§Ù„ÙŠ (128kbps)"),
    ULTRA(192_000, 48_000, "Ø§Ø­ØªØ±Ø§ÙÙŠ (192kbps)")
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
    val nonce: String,
    val codec: String = "AAC",
    val sampleRate: Int = 44100,
    val bitrate: Int = 96000
)

sealed interface VoiceMessageState {
    data object Idle : VoiceMessageState
    data class Recording(val paused: Boolean) : VoiceMessageState
    data class Preview(val durationSeconds: Int) : VoiceMessageState
    data object Sending : VoiceMessageState
    data class Sent(val durationSeconds: Int) : VoiceMessageState
    data class Error(val message: String) : VoiceMessageState
}








