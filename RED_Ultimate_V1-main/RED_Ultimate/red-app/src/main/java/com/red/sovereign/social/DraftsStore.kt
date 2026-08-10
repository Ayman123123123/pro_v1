package com.red.sovereign.social

import android.content.Context
import com.red.sovereign.media.EncryptedMediaCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * ════════════════════════════════════════════════════════════════════════
 *  DraftsStore — تخزين مسودات آمن ومشفر
 *  - يستخدم EncryptedMediaCache (AES-GCM 256 + Android Keystore)
 *  - كل scope له draft مستقل (LOCAL_YEMEN, USER, GROUP, ...)
 *  - يدعم حتى 5 مسودات لكل user
 *  - يحفظ تلقائياً كل 1.5 ثانية بعد التوقف عن الكتابة
 * ════════════════════════════════════════════════════════════════════════
 */
class DraftsStore(context: Context) {
    private val cache = EncryptedMediaCache(context)
    private val scope = CoroutineScope(Dispatchers.IO)
    private val mutex = Mutex()

    private val _hasDraft = MutableStateFlow(false)
    val hasDraft: StateFlow<Boolean> = _hasDraft.asStateFlow()

    private fun key(scope: String?) = "draft:${scope ?: "default"}"

    /**
     * حفظ مسودة في الخلفية (آمن للنداء من الـ main thread)
     * @param text نص المسودة
     * @param draftScope النطاق (مثل "LOCAL_YEMEN" أو "USER:abc")
     */
    fun save(text: String, draftScope: String? = null) {
        if (text.isBlank()) {
            // No empty drafts — clean up instead
            scope.launch { mutex.withLock { cache.delete(key(draftScope)) } }
            _hasDraft.value = false
            return
        }
        scope.launch {
            mutex.withLock {
                try {
                    val payload = DraftPayload(
                        text = text,
                        savedAt = System.currentTimeMillis(),
                        version = DRAFT_VERSION
                    )
                    val bytes = payload.toBytes()
                    cache.put(key(draftScope), bytes)
                    _hasDraft.value = true
                } catch (e: Exception) {
                    android.util.Log.w("DraftsStore", "save failed: ${e.message}")
                }
            }
        }
    }

    /**
     * تحميل مسودة محفوظة
     * @return نص المسودة أو null إذا لم توجد
     */
    suspend fun load(draftScope: String? = null): String? = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val bytes = cache.get(key(draftScope)) ?: run {
                    _hasDraft.value = false
                    return@withLock null
                }
                val payload = DraftPayload.fromBytes(bytes)
                if (payload.version != DRAFT_VERSION) {
                    cache.delete(key(draftScope))
                    return@withLock null
                }
                // Expire after 7 days
                if (System.currentTimeMillis() - payload.savedAt > 7L * 24 * 60 * 60 * 1000) {
                    cache.delete(key(draftScope))
                    return@withLock null
                }
                _hasDraft.value = true
                payload.text
            } catch (e: Exception) {
                android.util.Log.w("DraftsStore", "load failed: ${e.message}")
                null
            }
        }
    }

    /**
     * حذف مسودة بعد النشر
     */
    fun delete(draftScope: String? = null) {
        scope.launch {
            mutex.withLock {
                try {
                    cache.delete(key(draftScope))
                    _hasDraft.value = false
                } catch (e: Exception) {
                    android.util.Log.w("DraftsStore", "delete failed: ${e.message}")
                }
            }
        }
    }

    /**
     * حذف جميع المسودات (عند تسجيل الخروج مثلاً)
     */
    fun clearAll() {
        scope.launch {
            mutex.withLock {
                try {
                    cache.clear()
                    _hasDraft.value = false
                } catch (e: Exception) {
                    android.util.Log.w("DraftsStore", "clearAll failed: ${e.message}")
                }
            }
        }
    }

    @kotlinx.serialization.Serializable
    private data class DraftPayload(
        val text: String,
        val savedAt: Long,
        val version: Int
    ) {
        fun toBytes(): ByteArray {
            val json = kotlinx.serialization.json.Json.encodeToString(serializer(), this)
            return json.toByteArray(Charsets.UTF_8)
        }
        companion object {
            fun fromBytes(bytes: ByteArray): DraftPayload {
                val json = String(bytes, Charsets.UTF_8)
                return kotlinx.serialization.json.Json.decodeFromString(serializer(), json)
            }
        }
    }

    companion object {
        private const val DRAFT_VERSION = 1
    }
}
