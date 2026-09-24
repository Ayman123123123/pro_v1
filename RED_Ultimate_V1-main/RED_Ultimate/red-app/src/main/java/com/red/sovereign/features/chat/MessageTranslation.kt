package com.red.sovereign.features.chat

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.Default.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CoroutineScope
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * 🌐 On-device Message Translation using ML Kit
 * 
 * يدعم 100+ لغة مع نماذج On-device (لا حاجة للإنترنت بعد التحميل)
 * النماذج تُحمَّل عند الطلب وتُخزَّن محلياً (~30-50MB لكل لغة)
 * 
 * Privacy: كل الترجمة تتم على الجهاز — النص لا يغادر الجهاز أبداً
 * E2EE Compatible: يُفك تشفير الرسالة محلياً → يترجم → يعرض (لا يُخزن المترجم)
 */

data class TranslationResult(
    val originalText: String,
    val translatedText: String,
    val sourceLanguage: String,
    val targetLanguage: String,
    val confidence: Float = 1.0f
)

data class LanguageModelInfo(
    val languageTag: String,
    val displayName: String,
    val isDownloaded: Boolean,
    val sizeBytes: Long
)

class MessageTranslator(private val context: Context) {

    private val translators = mutableMapOf<String, Translator>()
    private val modelManager = com.google.mlkit.common.model.DownloadConditions.Builder()
        .requireWifi()
        .build()

    /**
     * يترجم نص من لغة مصدر إلى لغة هدف
     * يحمل النموذج تلقائياً إن لم يكن موجوداً
     */
    suspend fun translate(
        text: String,
        sourceLang: String,
        targetLang: String
    ): Result<TranslationResult> = withContext(Dispatchers.IO) {
        try {
            val translator = getOrCreateTranslator(sourceLang, targetLang)
            
            // تأكد من تحميل النموذج
            if (!awaitModelDownloaded(translator)) {
                return@withContext Result.failure(Exception("فشل تحميل نموذج الترجمة"))
            }
            
            val translated = translator.translate(text).await()
            
            Result.success(TranslationResult(
                originalText = text,
                translatedText = translated,
                sourceLanguage = sourceLang,
                targetLanguage = targetLang
            ))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * يكتشف لغة النص تلقائياً (يستخدم ML Kit Language ID)
     */
    suspend fun detectLanguage(text: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val languageId = com.google.mlkit.nl.languageid.LanguageIdentification.getClient()
            val langCode = languageId.identifyLanguage(text).await()
            if (langCode == "und") {
                Result.failure(Exception("تعذر تحديد اللغة"))
            } else {
                Result.success(langCode)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * يحصل على قائمة اللغات المتاحة مع حالة التحميل
     */
    suspend fun getAvailableLanguages(): List<LanguageModelInfo> = withContext(Dispatchers.IO) {
        val allLanguages = TranslateLanguage.getAllLanguages()
        val translatorClient = com.google.mlkit.nl.translate.Translation.getClient()
        
        allLanguages.map { lang ->
            val tag = lang.bcp47Tag
            val name = getLanguageDisplayName(tag)
            val isDownloaded = translatorClient.isModelDownloaded(tag)
            val size = estimatorModelSize(tag)
            LanguageModelInfo(tag, name, isDownloaded, size)
        }.sortedBy { it.displayName }
    }

    /**
     * يحمل نموذج لغة محدد
     */
    suspend fun downloadModel(languageTag: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val translator = getOrCreateTranslator("auto", languageTag)
            translator.downloadModelIfNeeded(modelManager).await()
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * يحذف نموذج لغة لتوفير المساحة
     */
    suspend fun deleteModel(languageTag: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val translatorClient = com.google.mlkit.nl.translate.Translation.getClient()
            translatorClient.deleteDownloadedModel(languageTag).await()
            translators.remove(languageTag)?.close()
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun getOrCreateTranslator(sourceLang: String, targetLang: String): Translator {
        val key = "${sourceLang}_$targetLang"
        return translators.getOrPut(key) {
            val options = TranslatorOptions.Builder()
                .setSourceLanguage(getMlKitLanguage(sourceLang))
                .setTargetLanguage(getMlKitLanguage(targetLang))
                .build()
            Translation.getClient(options)
        }
    }

    private suspend fun awaitModelDownloaded(translator: Translator): Boolean = withContext(Dispatchers.IO) {
        try {
            translator.downloadModelIfNeeded(modelManager).await()
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun getMlKitLanguage(langCode: String): String {
        return when (langCode.lowercase()) {
            "auto", "ar" -> TranslateLanguage.ARABIC
            "en" -> TranslateLanguage.ENGLISH
            "fr" -> TranslateLanguage.FRENCH
            "es" -> TranslateLanguage.SPANISH
            "de" -> TranslateLanguage.GERMAN
            "it" -> TranslateLanguage.ITALIAN
            "pt" -> TranslateLanguage.PORTUGUESE
            "ru" -> TranslateLanguage.RUSSIAN
            "zh", "zh-cn", "zh-hans" -> TranslateLanguage.CHINESE
            "zh-tw", "zh-hant" -> TranslateLanguage.CHINESE_TRADITIONAL
            "ja" -> TranslateLanguage.JAPANESE
            "ko" -> TranslateLanguage.KOREAN
            "tr" -> TranslateLanguage.TURKISH
            "hi" -> TranslateLanguage.HINDI
            "bn" -> TranslateLanguage.BENGALI
            "ur" -> TranslateLanguage.URDU
            "fa" -> TranslateLanguage.PERSIAN
            "he" -> TranslateLanguage.HEBREW
            "th" -> TranslateLanguage.THAI
            "vi" -> TranslateLanguage.VIETNAMESE
            "id" -> TranslateLanguage.INDONESIAN
            "ms" -> TranslateLanguage.MALAY
            "tl" -> TranslateLanguage.TAGALOG
            "sw" -> TranslateLanguage.SWAHILI
            "am" -> TranslateLanguage.AMHARIC
            else -> TranslateLanguage.ENGLISH
        }
    }

    private fun getLanguageDisplayName(tag: String): String {
        return try {
            Locale.forLanguageTag(tag).displayName
        } catch (_: Exception) {
            tag.uppercase()
        }
    }

    private fun estimatorModelSize(tag: String): Long {
        // تقدير تقريبي لحجم النموذج (30-50MB)
        return 40 * 1024 * 1024L
    }

    fun close() {
        translators.values.forEach { it.close() }
        translators.clear()
    }
}

/**
 * واجهة UI لاختيار اللغة والترجمة الفورية
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslationBottomSheet(
    messageText: String,
    currentLang: String,
    onTranslate: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val translator = remember { MessageTranslator(context) }
    var targetLang by remember { mutableStateOf("en") }
    var translatedText by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val languages = remember { listOf(
        "ar" to "العربية", "en" to "English", "fr" to "Français", 
        "es" to "Español", "de" to "Deutsch", "it" to "Italiano",
        "pt" to "Português", "ru" to "Русский", "zh" to "中文",
        "ja" to "日本語", "ko" to "한국어", "tr" to "Türkçe",
        "hi" to "हिन्दी", "fa" to "فارسی", "ur" to "اردو"
    ) }

    Column(Modifier.padding(16.dp)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween) {
            Text("ترجمة الرسالة", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null) }
        }
        
        Spacer(Modifier.height(8.dp))
        
        // النص الأصلي
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = messageText,
                modifier = Modifier.padding(12.dp),
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        Spacer(Modifier.height(16.dp))
        
        // اختيار اللغة
        Text("اختر اللغة", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(8.dp))
        
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(languages) { (code, name) ->
                val selected = code == targetLang
                FilterChip(
                    selected = selected,
                    onClick = { targetLang = code },
                    label = { Text(name, fontSize = 12.sp) },
                    colors = FilterChipDefaults.colors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        unselectedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            }
        }
        
        Spacer(Modifier.height(16.dp))
        
        // زر الترجمة
        Button(
            onClick = {
                loading = true
                error = null
                scope.launch {
                    translator.translate(messageText, "auto", targetLang)
                        .onSuccess { result ->
                            translatedText = result.translatedText
                            loading = false
                            onTranslate(targetLang, result.translatedText)
                        }
                        .onFailure { e ->
                            error = e.message
                            loading = false
                        }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !loading
        ) {
            if (loading) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            else Text("ترجمة", fontWeight = FontWeight.Bold)
        }
        
        error?.let { err ->
            Spacer(Modifier.height(8.dp))
            Text(err, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        }
        
        translatedText?.let { text ->
            Spacer(Modifier.height(16.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(text, modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}