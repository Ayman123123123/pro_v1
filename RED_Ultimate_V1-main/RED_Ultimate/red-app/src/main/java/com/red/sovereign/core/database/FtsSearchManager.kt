package com.red.sovereign.core.database

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.RoomDatabase

/**
 * 🔍 البحث المحلي المشفَّر — FTS5 على SQLCipher.
 *
 * الخادم لا يرى النص أبدًا؛ الفهرسة والبحث يجريان على الجهاز بعد فكّ
 * التشفير محليًّا فقط.
 *
 * ## عيبان مقيسان كانا يُبطلان البحث كلّه
 *
 * **الأول — الجدول لم يكن يُنشأ أصلًا.** كانت صيغة المُجزِّئ:
 *
 *     tokenize='unicode61 "remove_diacritics 1"'
 *
 * وهي خطأ نحوي: FTS5 لا يقبل اقتباسًا مزدوجًا داخل الاقتباس المفرد،
 * فيردّ `parse error in tokenize directive`. ولأن `FtsCallback.onOpen`
 * كان يبتلع الاستثناء بـ`catch (_: Exception) {}`، كان الجدول يفشل
 * صامتًا، فيرجع كل بحث بقائمة فارغة بلا أثر في السجلّ. الصواب:
 *
 *     tokenize='unicode61 remove_diacritics 1'
 *
 * **الثاني — `unicode61` يكسر الكلمة العربية المشكَّلة.** الحركات
 * (فتحة/ضمة/شدّة…) من فئة `Mn`، ويعاملها المُجزِّئ **فاصلًا بين
 * الكلمات** لا جزءًا منها. وخيار `remove_diacritics` لا يشملها لأنه
 * يعالج علامات اللاتينية المركَّبة. النتيجة المقيسة:
 *
 *     "السَّلامُ عليكم"  ⇒  ['الس', 'لام', 'عليكم']
 *
 * فكلمة واحدة تصير رمزين مبتورين، ولا يطابقها بحثٌ عن «السلام». وهذا
 * يقع عند القيم الثلاث `remove_diacritics 0|1|2` سواءً — جُرّبت كلّها.
 *
 * الحلّ: **تطبيع النص قبل الفهرسة وقبل البحث معًا** بـ[normalizeArabic]،
 * فتُحذف الحركات ويُوحَّد رسم الهمزات. وهو أيضًا ما يجعل «ابراهيم»
 * تطابق «إبراهيم»، و«مكتبه» تطابق «مكتبة» — وهي أخطاء إملائية شائعة
 * جدًّا في الكتابة اليوميّة.
 */
class FtsSearchManager(private val db: SupportSQLiteDatabase) {

    companion object {
        /**
         * الحركات وعلامة التطويل. تُحذف قبل الفهرسة لأن `unicode61`
         * يعدّها فواصل كلمات فتنكسر الكلمة إلى رموز مبتورة.
         */
        private val ARABIC_DIACRITICS = Regex("[\u064B-\u0652\u0670\u0640]")

        /** صور الألف: آ أ إ ٱ ⇒ ا */
        private val ALEF_FORMS = Regex("[\u0622\u0623\u0625\u0671]")

        /**
         * يوحّد النص العربي حتى يطابق البحثُ الكتابةَ الفعلية للناس.
         *
         * يجب استدعاؤه على **طرفَي** العملية — الفهرسة والاستعلام —
         * وإلا اختلف تمثيل المخزون عن تمثيل المطلوب فلا يتطابقان.
         */
        fun normalizeArabic(text: String): String = text
            .replace(ARABIC_DIACRITICS, "")
            .replace(ALEF_FORMS, "\u0627")
            .replace('\u0649', '\u064A')   // ى ⇒ ي
            .replace('\u0629', '\u0647')   // ة ⇒ ه

        /**
         * يستخرج النص القابل للفهرسة من حمولة مخزّنة (P0-C 2026-09-12).
         *
         * - RICH_TEXT: يُفك عبر RichMessage ويُؤخذ `text` فقط (تُتجاهل
         *   REACTION/DELETE/EDIT/POLL_VOTE لأنها نظام لا محتوى بحثي).
         * - وسائط (IMAGE/VIDEO/FILE/AUDIO/VOICE/STICKER/GROUP_MESSAGE):
         *   الحمولة JSON لمانيفست — تُفهرس كما هي (اسم الملف داخلها
         *   قابل للبحث) ما لم تكن ثنائية خالصة.
         * - TEXT/غيره: الخام UTF-8 مباشرة.
         *
         * @return النص الخام (قبل تطبيع الفهرسة) أو null للتخطّي.
         */
        fun extractIndexableText(plaintext: ByteArray, messageType: String): String? {
            val raw = (if (messageType == "RICH_TEXT") {
                val rich = runCatching {
                    com.red.sovereign.core.RichMessage.decode(plaintext)
                }.getOrNull() ?: return null
                if (rich.action in setOf("POLL_VOTE", "REACTION", "REACTION_REMOVE", "DELETE", "EDIT")) return null
                val t = rich.text.trim()
                if (t.isEmpty()) {
                    // RICH_TEXT بلا نص (موقع/جهة اتصال/استطلاع): لا شيء يُفهرس.
                    if (rich.location != null) "موقع ${rich.location.name}" else null
                } else t
            } else {
                runCatching { plaintext.toString(Charsets.UTF_8).trim() }.getOrNull()
            }) ?: return null
            if (raw.length < MIN_QUERY_LENGTH || raw.length > MAX_INDEXED_LENGTH) return null
            return raw
        }

        private const val MIN_QUERY_LENGTH = 2
        private const val MAX_INDEXED_LENGTH = 5000
        private const val MAX_QUERY_LENGTH = 100
        private const val SNIPPET_LENGTH = 120
    }

    fun createFtsTable() {
        // prefix='2 3 4' يبني فهرس بادئات فيصير البحث أثناء الكتابة
        // فوريًّا بدل مسح الفهرس كاملًا عند كل حرف.
        db.execSQL(
            """
            CREATE VIRTUAL TABLE IF NOT EXISTS messages_fts
            USING fts5(
                messageId UNINDEXED,
                conversationId UNINDEXED,
                senderId UNINDEXED,
                content,
                prefix='2 3 4',
                tokenize='unicode61 remove_diacritics 1'
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE TRIGGER IF NOT EXISTS messages_fts_delete " +
                "AFTER DELETE ON local_history BEGIN " +
                "DELETE FROM messages_fts WHERE messageId = old.id; END"
        )
        // تنظيف مكرر: احذف القديم قبل الإدراج (لا UNIQUE في FTS — REPLACE لا تعمل بلا قيد).
        db.execSQL(
            "CREATE TRIGGER IF NOT EXISTS messages_fts_cleanup_insert " +
                "AFTER DELETE ON messages BEGIN " +
                "DELETE FROM messages_fts WHERE messageId = old.id; END"
        )
    }

    fun indexMessage(messageId: String, conversationId: String, senderId: String, plaintext: String) {
        if (plaintext.length < MIN_QUERY_LENGTH || plaintext.length > MAX_INDEXED_LENGTH) return
        // FTS بلا UNIQUE: احذف القديم أولًا لمنع التكرار (LocalRepository + Paging كانا يفهرسان مرتين).
        runCatching { db.execSQL("DELETE FROM messages_fts WHERE messageId = ?", arrayOf(messageId)) }
        db.execSQL(
            "INSERT INTO messages_fts(messageId, conversationId, senderId, content) " +
                "VALUES (?, ?, ?, ?)",
            arrayOf(messageId, conversationId, senderId, normalizeArabic(plaintext))
        )
    }

    /**
     * Hook P0-C: فهرسة كيان سجل محلي كامل (2026-09-12).
     *
     * يستخرج النص القابل للفهرسة عبر [extractIndexableText] (يفك RichMessage
     * وإلا الخام UTF-8) ثم ينادي [indexMessage] — فيُضمن أن كل حفظ يُفهرس.
     *
     * DONE(P0-C): نُقل النداء إلى `LocalRepository.saveLocalHistory`
     * (خارج نطاق P0-C — ممنوع لمسه هنا):
     * ```
     * suspend fun saveLocalHistory(h: LocalHistoryEntity) {
     *     dao.insertLocalHistory(h)
     *     runCatching {
     *         FtsSearchManager(RedDatabase.getInstance(ctx).openHelper.writableDatabase)
     *             .indexLocalHistory(h)
     *     }
     * }
     * ```
     * وحتى يتم ذلك، ينادي `ChatHistoryPagingViewModel.saveAndIndex()`
     * (داخل النطاق المسموح) نفس الـ hook — انظر `ChatHistoryPaging.kt`.
     *
     * @return true إذا فُهرس فعلًا، false إذا تُخطّي (نظام/تفاعل/فارغ).
     */
    fun indexLocalHistory(entity: LocalHistoryEntity): Boolean {
        val text = extractIndexableText(entity.encryptedPlaintext, entity.messageType)
            ?: return false
        return runCatching {
            indexMessage(entity.id, entity.conversationId, entity.senderId, text)
            true
        }.getOrDefault(false)
    }

    /**
     * @param prefixMatch مطابقة البادئة — تُستعمل أثناء الكتابة، فيجد
     *   المستخدمُ النتيجةَ قبل إتمام الكلمة.
     */
    fun search(query: String, limit: Int = 50, prefixMatch: Boolean = true): List<FtsResult> {
        val trimmed = query.trim()
        if (trimmed.length < MIN_QUERY_LENGTH) return emptyList()

        // الاقتباس المزدوج يُهرَّب بتكراره — صيغة FTS5 — حتى لا يستطيع
        // نصُّ المستخدم كسر عبارة البحث أو حقن عوامل استعلام.
        val sanitized = normalizeArabic(trimmed)
            .replace("\"", "\"\"")
            .take(MAX_QUERY_LENGTH)
        val match = if (prefixMatch) "\"$sanitized\"*" else "\"$sanitized\""

        // bm25 هو ترتيب FTS5 الافتراضي عبر `rank`: الأصغر أفضل.
        val cursor = db.query(
            "SELECT messageId, conversationId, senderId, content, rank FROM messages_fts " +
                "WHERE messages_fts MATCH ? ORDER BY rank LIMIT ?",
            // arrayOf<Any?> صراحةً: الوسيطان String وInt، فيستنتج arrayOf
            // النوع Array<Any> ولا يقبله توقيع query(String, Array<out Any?>).
            // أصلحها main في 8c36908 وهي لازمة للترجمة.
            arrayOf<Any?>(match, limit)
        )
        val results = mutableListOf<FtsResult>()
        cursor.use {
            while (it.moveToNext()) {
                results += FtsResult(
                    messageId = it.getString(0),
                    conversationId = it.getString(1),
                    senderId = it.getString(2),
                    snippet = it.getString(3).take(SNIPPET_LENGTH),
                    rank = it.getDouble(4)
                )
            }
        }
        return results
    }

    fun deleteConversation(conversationId: String) {
        db.execSQL("DELETE FROM messages_fts WHERE conversationId = ?", arrayOf(conversationId))
    }

    fun clear() {
        db.execSQL("DELETE FROM messages_fts")
    }
}

data class FtsResult(
    val messageId: String,
    val conversationId: String,
    val senderId: String,
    val snippet: String,
    val rank: Double
)

/**
 * ينشئ جدول FTS عند إنشاء قاعدة البيانات وعند فتحها.
 *
 * `onOpen` لازم للنسخ القائمة التي أُنشئت قبل وجود الجدول — ومنها كل
 * نسخة أُنشئت أيام صيغة المُجزِّئ المعطوبة.
 */
class FtsCallback : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        FtsSearchManager(db).createFtsTable()
    }

    override fun onOpen(db: SupportSQLiteDatabase) {
        // لا يُبتلع الخطأ صامتًا: ابتلاعُه هو ما أخفى عطب الصيغة السابق
        // حتى صار البحث يرجع فارغًا بلا أيّ أثر يدلّ على السبب.
        try {
            FtsSearchManager(db).createFtsTable()
        } catch (e: Exception) {
            android.util.Log.e("FtsSearchManager", "تعذّر إنشاء فهرس البحث messages_fts", e)
        }
    }
}
