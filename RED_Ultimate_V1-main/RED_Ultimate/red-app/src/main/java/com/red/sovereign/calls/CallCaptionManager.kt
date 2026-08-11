package com.red.sovereign.calls

import android.content.Context
import android.content.Intent
import android.os.Build
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

/**
 * Live captioning — تجريبي ومعطّل افتراضيًا أثناء مكالمة WebRTC نشطة.
 *
 * التحذير الصادق:
 * - Android SpeechRecognizer ليس Whisper محليًا. قد يستخدم خدمة Google السحابية
 *   حسب الجهاز، وقد يتطلب اتصال إنترنت، وقد يرسل الصوت لخوادم خارجية.
 * - يحتل الميكروفون وقد يتعارض مع WebRTC (الذي يحتكر mic).
 * - لذلك هذا الكلاس غير متصل افتراضيًا ولا يُستدعى في SovereignActiveCallScreen.
 *   الاستخدام الموصى به للإنتاج: server-side STT (Whisper-large عبر backend)
 *   عبر بث صوتي منفصل بعد موافقة المستخدم الصريحة.
 *
 * الخصوصية: لا يُفعل إلا بموافقة صريحة من المستخدم في الإعدادات، ويعرض تحذيرًا.
 */
class CallCaptionManager(private val context: Context) {
    private var recognizer: SpeechRecognizer? = null
    private val buffer = StringBuilder()
    var onCaption: ((String) -> Unit)? = null
    var isActive: Boolean = false
        private set

    /**
     * يبدأ البث الحي للنص — فقط إذا لم تكن هناك مكالمة WebRTC نشطة.
     * يجب استدعاؤه بعد موافقة المستخدم وفهم مخاطره.
     */
    fun start() {
        if (isActive) return
        if (!SpeechRecognizer.isRecognitionAvailable(context)) return
        // لا تبدأ إذا كانت هناك مكالمة WebRTC نشطة (يتعارض mic)
        if (com.red.sovereign.calls.CallRuntime.state !is com.red.sovereign.calls.CallUiState.Idle &&
            com.red.sovereign.calls.ConferenceRuntime.state !is com.red.sovereign.calls.ConferenceUiState.Idle &&
            com.red.sovereign.calls.LiveStreamRuntime.state !is com.red.sovereign.calls.LiveStreamUiState.Idle) {
            // WebRTC active — don't steal mic
            return
        }
        val rec = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: android.os.Bundle?) { /* UI ready */ }
                override fun onBeginningOfSpeech() { /* user started speaking */ }
                override fun onRmsChanged(rmsdB: Float) { /* audio level */ }
                override fun onBufferReceived(buffer: ByteArray?) { /* partial audio */ }
                override fun onEndOfSpeech() { restart() }
                override fun onError(error: Int) { restart() }
                override fun onResults(results: android.os.Bundle?) {
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    if (text.isNotBlank()) {
                        buffer.append(text).append(' ')
                        onCaption?.invoke(buffer.toString())
                    }
                }
                override fun onPartialResults(partialResults: android.os.Bundle?) {
                    val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    if (partial.isNotBlank()) onCaption?.invoke(partial)
                }
                override fun onEvent(eventType: Int, params: android.os.Bundle?) { /* */ }
            })
        }
        recognizer = rec
        isActive = true
        restart()
    }

    private fun restart() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        recognizer?.startListening(intent)
    }

    fun stop() {
        isActive = false
        recognizer?.stopListening()
        recognizer?.destroy()
        recognizer = null
    }
}
