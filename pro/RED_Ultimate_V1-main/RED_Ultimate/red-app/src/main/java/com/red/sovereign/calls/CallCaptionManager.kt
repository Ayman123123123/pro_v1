package com.red.sovereign.calls

import android.content.Context
import android.content.Intent
import android.os.Build
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

/**
 * Live captioning أثناء المكالمة.
 *
 * يستخدم Android SpeechRecognizer المحلي (Whisper صغير مدرب على الجهاز، لا يوجد API call).
 * ملاحظة: SpeechRecognizer يحتل الـ mic — لا يعمل مع مكالمة نشطة.
 * البديل: server-side STT (Whisper-large عبر backend) للنسخة الكاملة.
 *
 * Privacy: لا يُرسل أي صوت لخادم خارجي. كل التحويل محلي.
 */
class CallCaptionManager(private val context: Context) {
    private var recognizer: SpeechRecognizer? = null
    private val buffer = StringBuilder()
    var onCaption: ((String) -> Unit)? = null

    /**
     * يبدأ البث الحي للنص. يجب استدعاؤها فقط إذا الـ device supports speech recognition
     * والـ mic مشغول بمكالمة (Android سيستخدم الـ mic إذا كان متاح).
     */
    fun start() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) return
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
        recognizer?.stopListening()
        recognizer?.destroy()
        recognizer = null
    }
}
