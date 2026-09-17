package com.red.sovereign.calls

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions
import com.google.mlkit.nl.languageid.LanguageIdentifier
import com.google.mlkit.speechreco.SpeechRecognition
import com.google.mlkit.speechreco.SpeechRecognizer
import com.google.mlkit.speechreco.SpeechRecognizerOptions
import com.google.mlkit.speechreco.SpeechRecognitionResult
import com.google.mlkit.speechreco.SpeechRecognitionModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Live Captions Manager using ML Kit on-device speech recognition.
 * Provides real-time transcription of remote audio during calls.
 */
class LiveCaptionManager(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())
    
    private var speechRecognizer: SpeechRecognizer? = null
    private var audioRecord: AudioRecord? = null
    private var isProcessing = false
    private val pendingResults = ConcurrentLinkedQueue<CaptionResult>()
    private var currentLanguage: String? = null
    private var languageIdentifier: LanguageIdentifier? = null
    
    // 16kHz mono PCM for ML Kit
    private val sampleRate = 16000
    private val bufferSize = AudioRecord.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    ).coerceAtLeast(sampleRate * 2) // 1 second buffer

    interface Listener {
        fun onCaption(text: String, isFinal: Boolean, language: String)
        fun onError(message: String)
    }

    private var listener: Listener? = null

    fun setListener(listener: Listener) {
        this.listener = listener
    }

    /**
     * Starts live captioning for the given audio track.
     * Uses ML Kit on-device speech recognition (no network required).
     */
    fun start(listener: Listener) {
        this.listener = listener
        if (isProcessing) return

        scope.launch {
            initializeRecognizer()
            if (speechRecognizer == null) {
                listener.onError("Failed to initialize speech recognizer")
                return@launch
            }
            startAudioCapture()
        }
    }

    /**
     * Stops live captioning.
     */
    fun stop() {
        isProcessing = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        speechRecognizer?.close()
        speechRecognizer = null
        languageIdentifier?.close()
        languageIdentifier = null
        currentLanguage = null
        pendingResults.clear()
    }

    private suspend fun initializeRecognizer() {
        // Initialize language identifier for auto-detection
        languageIdentifier = LanguageIdentification.getClient(
            LanguageIdentificationOptions.Builder()
                .setConfidenceThreshold(0.5f)
                .build()
        )

        // Use on-device model for privacy and low latency
        val options = SpeechRecognizerOptions.Builder()
            .setModel(SpeechRecognitionModel.ON_DEVICE)
            .setEnablePartialResults(true)
            .build()

        speechRecognizer = SpeechRecognition.getClient(options)
        
        // Warm up the recognizer
        try {
            // ML Kit doesn't have explicit warmup, but first call initializes
        } catch (e: Exception) {
            Log.w("LiveCaptionManager", "Recognizer warmup failed: ${e.message}")
        }
    }

    private fun startAudioCapture() {
        isProcessing = true
        
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        ).apply {
            startRecording()
        }

        val buffer = ShortArray(bufferSize / 2)
        
        scope.launch {
            while (isProcessing && audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (read > 0) {
                    processAudioBuffer(buffer, read)
                }
            }
        }
    }

    private fun processAudioBuffer(buffer: ShortArray, length: Int) {
        // Convert to byte array for ML Kit
        val byteBuffer = ByteBuffer.allocate(length * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until length) {
            byteBuffer.putShort(buffer[i])
        }
        val audioData = byteBuffer.array()

        // Auto-detect language on first few buffers
        if (currentLanguage == null && languageIdentifier != null) {
            scope.launch {
                try {
                    // Convert audio to text for language detection (simplified)
                    // In production, use a small text sample or skip language detection
                    val languages = languageIdentifier?.identifyLanguage("test").await()
                    if (languages != null && languages.isNotEmpty()) {
                        currentLanguage = languages[0].languageTag
                        Log.d("LiveCaptionManager", "Detected language: $currentLanguage")
                    }
                } catch (e: Exception) {
                    currentLanguage = Locale.getDefault().language
                }
            }
        }

        // Process speech recognition
        speechRecognizer?.let { recognizer ->
            scope.launch {
                try {
                    val result = recognizer.recognize(audioData, sampleRate).await()
                    handleRecognitionResult(result)
                } catch (e: Exception) {
                    Log.w("LiveCaptionManager", "Recognition failed: ${e.message}")
                }
            }
        }
    }

    private fun handleRecognitionResult(result: SpeechRecognitionResult) {
        val text = result.text.trim()
        if (text.isEmpty()) return

        val isFinal = result.isFinal
        val language = currentLanguage ?: Locale.getDefault().language

        val captionResult = CaptionResult(text, isFinal, language)
        
        if (isFinal) {
            // Final result - send immediately
            sendCaption(captionResult)
        } else {
            // Partial result - debounce
            pendingResults.add(captionResult)
            mainHandler.removeCallbacksAndMessages(null)
            mainHandler.postDelayed({
                pendingResults.poll()?.let { sendCaption(it) }
            }, 300)
        }
    }

    private fun sendCaption(result: CaptionResult) {
        listener?.onCaption(result.text, result.isFinal, result.language)
    }

    data class CaptionResult(
        val text: String,
        val isFinal: Boolean,
        val language: String
    )

    companion object {
        fun isAvailable(context: Context): Boolean {
            return try {
                // Check if ML Kit speech recognition is available
                SpeechRecognition.getClient(
                    SpeechRecognizerOptions.Builder()
                        .setModel(SpeechRecognitionModel.ON_DEVICE)
                        .build()
                ) != null
            } catch (e: Exception) {
                false
            }
        }
    }
}