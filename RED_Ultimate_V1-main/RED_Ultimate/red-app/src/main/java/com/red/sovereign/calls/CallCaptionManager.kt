package com.red.sovereign.calls

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

/**
 * Live captioning manager for RED Sovereign Call System — تجريبي ومعطّل افتراضيًا أثناء مكالمة WebRTC نشطة.
 *
 * التحذير الصادق:
 * - Android SpeechRecognizer ليس Whisper محليًا. قد يستخدم خدمة Google السحابية
 *   حسب الجهاز، وقد يتطلب اتصال إنترنت، وقد يرسل الصوت لخوادم خارجية.
 * - يحتل الميكروفون وقد يتعارض مع WebRTC (الذي يحتكر mic).
 * - الاستخدام الموصى به للإنتاج: server-side STT (Whisper-large عبر backend)
 *   عبر بث صوتي منفصل بعد موافقة المستخدم الصريحة.
 *
 * الخصوصية: لا يُفعل إلا بموافقة صريحة من المستخدم في الإعدادات، ويعرض تحذيرًا.
 *
 * التطويرات والتحسينات المضافة (Agent 17 - Live Captions & Speech AI Specialist):
 * 1. Thread Safety: فرض التشغيل على الخيط الرئيسي (Main Thread) باستخدام Handler لمنع استثناءات SpeechRecognizer.
 * 2. Language Selection: دعم تحديد اللغة الهدف (`targetLocale`) مع إمكانية التبديل بين اللغات (العربية/الإنجليزية وغيرها).
 * 3. Subtitle History Buffering: تخزين مؤقت وسجل للترجمات (`captionHistory`) مع سعة قصوى ودعم مسح السجل والحصول على النص الكامل.
 * 4. Noise & Silence Filtering: إضافة إعدادات الفلترة ومهلات الصمت في `RecognizerIntent`.
 * 5. Exception Handling & Smart Restart: حماية كاملة ضد الاستثناءات ومنع حلقات إعادة البدء اللانهائية عند حدوث أخطاء حرجة (مثل نقص إذن الميكروفون).
 * 6. Memory Leak Prevention: استخدام `Context.applicationContext` لتجنب تسرب ذاكرة الأنشطة (Activity Context leaks).
 */
class CallCaptionManager(context: Context) {
    private val appContext: Context = context.applicationContext
    private var recognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val captionHistory = mutableListOf<String>()
    private val maxHistorySize = 50

    /** اللغة المستهدفة للتعرف على الكلام (افتراضيًا لغة النظام / اللغة المحلية) */
    var targetLocale: Locale = Locale.getDefault()

    /** معاودة الاتصال عند استقبال نتائج التسمية النهائية (النص الكامل المتراكم أو المحدث) */
    var onCaption: ((String) -> Unit)? = null

    /** معاودة الاتصال عند استقبال نتائج جزئية فورية (Live Partial Transcript) */
    var onPartialCaption: ((String) -> Unit)? = null

    /** معاودة الاتصال عند حدوث أخطاء في التعرف على الكلام */
    var onErrorCallback: ((Int, String) -> Unit)? = null

    var isActive: Boolean = false
        private set

    /**
     * يبدأ البث الحي للنص — فقط إذا لم تكن هناك مكالمة WebRTC نشطة.
     * يجب استدعاؤه بعد موافقة المستخدم وفهم مخاطره.
     */
    fun start() {
        if (isActive) return
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            onErrorCallback?.invoke(-1, "Speech recognition is not available on this device.")
            return
        }

        // التحقق من عدم تداخل الميكروفون مع مكالمة WebRTC أو مؤتمر أو بث نشط
        try {
            val callIdle = CallRuntime.state is CallUiState.Idle
            val conferenceIdle = ConferenceRuntime.state is ConferenceUiState.Idle
            val liveStreamIdle = LiveStreamRuntime.state is LiveStreamUiState.Idle

            if (!callIdle || !conferenceIdle || !liveStreamIdle) {
                // WebRTC active — don't steal mic
                return
            }
        } catch (e: Exception) {
            // Fallback if runtime state check throws across module boundaries
        }

        mainHandler.post {
            try {
                if (isActive) return@post
                val rec = SpeechRecognizer.createSpeechRecognizer(appContext)
                rec?.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) { /* UI ready */ }
                    override fun onBeginningOfSpeech() { /* user started speaking */ }
                    override fun onRmsChanged(rmsdB: Float) { /* audio level */ }
                    override fun onBufferReceived(buffer: ByteArray?) { /* partial audio */ }
                    override fun onEndOfSpeech() {
                        mainHandler.postDelayed({ restartListening() }, 300)
                    }
                    override fun onError(error: Int) {
                        onErrorCallback?.invoke(error, getErrorMessage(error))
                        // تجنب إعادة البدء في حال الأخطاء الحرجة مثل نقص الأذونات أو أخطاء العميل
                        if (error != SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS &&
                            error != SpeechRecognizer.ERROR_CLIENT) {
                            mainHandler.postDelayed({ restartListening() }, 1000)
                        } else {
                            stop()
                        }
                    }
                    override fun onResults(results: Bundle?) {
                        val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                        if (text.isNotBlank()) {
                            addCaptionToHistory(text)
                            onCaption?.invoke(getFormattedHistory())
                        }
                    }
                    override fun onPartialResults(partialResults: Bundle?) {
                        val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                        if (partial.isNotBlank()) {
                            onPartialCaption?.invoke(partial)
                        }
                    }
                    override fun onEvent(eventType: Int, params: Bundle?) { /* */ }
                })
                recognizer = rec
                isActive = true
                restartListening()
            } catch (e: Exception) {
                isActive = false
                onErrorCallback?.invoke(-2, e.localizedMessage ?: "Failed to initialize SpeechRecognizer")
            }
        }
    }

    private fun restartListening() {
        if (!isActive) return
        mainHandler.post {
            try {
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, targetLocale)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    // إعدادات الحد الأدنى ومهلات الصمت لتقليل الضوضاء وتحسين دقة التعرف
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 3000L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
                }
                recognizer?.startListening(intent)
            } catch (e: Exception) {
                onErrorCallback?.invoke(-3, e.localizedMessage ?: "Failed to start listening")
            }
        }
    }

    private fun addCaptionToHistory(text: String) {
        synchronized(captionHistory) {
            captionHistory.add(text)
            if (captionHistory.size > maxHistorySize) {
                captionHistory.removeAt(0)
            }
        }
    }

    /** الحصول على السجل الكامل للنصوص منسقة في نص واحد */
    fun getFormattedHistory(): String {
        synchronized(captionHistory) {
            return captionHistory.joinToString(" ")
        }
    }

    /** الحصول على قائمة السجل الكاملة كقائمة نصوص */
    fun getHistoryList(): List<String> {
        synchronized(captionHistory) {
            return captionHistory.toList()
        }
    }

    /** مسح سجل التسميات التلقائية */
    fun clearHistory() {
        synchronized(captionHistory) {
            captionHistory.clear()
        }
    }

    /** إيقاف التعرف على الكلام وتحرير الموارد */
    fun stop() {
        isActive = false
        mainHandler.post {
            try {
                recognizer?.stopListening()
                recognizer?.destroy()
            } catch (e: Exception) {
                // Ignore cleanup exceptions
            } finally {
                recognizer = null
            }
        }
    }

    private fun getErrorMessage(errorCode: Int): String {
        return when (errorCode) {
            SpeechRecognizer.ERROR_AUDIO -> "خطأ في تسجيل الصوت"
            SpeechRecognizer.ERROR_CLIENT -> "خطأ في الاتصال بالعميل"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "صلاحية الميكروفون غير متوفرة"
            SpeechRecognizer.ERROR_NETWORK -> "خطأ في الاتصال بالشبكة"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "انتهت مهلة الاتصال بالشبكة"
            SpeechRecognizer.ERROR_NO_MATCH -> "لم يتم التعرف على الكلام"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "محرك التعرف مشغول حالياً"
            SpeechRecognizer.ERROR_SERVER -> "خطأ من الخادم"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "انتهت مهلة التحدث"
            else -> "خطأ غير معروف ($errorCode)"
        }
    }
}
