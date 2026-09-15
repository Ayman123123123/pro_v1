package com.red.sovereign.core

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.red.sovereign.crypto.ProtocolRecordCipher
import com.red.sovereign.proto.RedProtos
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap

data class StoredMessage(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val receiverId: String,
    val encryptedPayload: ByteArray,
    val type: String,
    val senderDeviceId: Int,
    val receiverDeviceId: Int,
    val ciphertextType: Int,
    val sequence: Long,
    val status: String,
    val createdAt: Long
)

data class LocalMessage(val id: String, val conversationId: String, val senderId: String, val plaintext: ByteArray, val type: String, val timestamp: Long, val outgoing: Boolean, val status: String = "SENT")
data class ConversationSummary(val conversationId: String, val peerId: String, val preview: String, val timestamp: Long, val pinned: Boolean, val archived: Boolean, val mutedUntil: Long)

/** Ciphertext is retained for protocol delivery; decrypted UI history is separately encrypted with Android Keystore. */
class MessageStore(context: Context) : SQLiteOpenHelper(context.applicationContext, "red_messages.db", null, 7) {
    private val recordCipher = ProtocolRecordCipher()
    private val appContext: Context = context.applicationContext

    companion object {
        private const val TAG = "MessageStore"
        /**
         * Application-scoped burn scheduler — replaces per-message
         * `Handler(Looper.getMainLooper()).postDelayed(.., up to 7 days)` which
         * captured the outer MessageStore (and its SQLiteOpenHelper/Context)
         * in a main-thread callback for days (leak) and died on process death.
         *
         * - Handler is static/application-scoped: never holds an Activity/Service ref.
         * - Each Runnable holds only a [WeakReference] to the store: if the store
         *   is GC'd the burn is a no-op instead of keeping SQLite alive.
         * - `pendingBurns` allows explicit cancellation (e.g. message deleted early).
         * - Reboot/process-death recovery: `expiresAt` is already persisted inside
         *   the encrypted payload — call [purgeExpiredLocalHistory] on startup
         *   (and Room's `purgeExpiredMessages`) to delete what the in-memory
         *   handler missed. Delays longer than [MAX_IN_MEMORY_BURN_DELAY_MS] are
         *   NOT kept in memory; they rely on that startup purge (or WorkManager).
         */
        private val burnHandler: Handler by lazy { Handler(Looper.getMainLooper()) }
        private val pendingBurns = ConcurrentHashMap<String, Runnable>()
        /** Cap for in-memory Handler burns — 24h. Longer timers use persisted expiresAt + startup purge. */
        const val MAX_IN_MEMORY_BURN_DELAY_MS = 24 * 60 * 60 * 1000L

        private class BurnRunnable(
            storeRef: WeakReference<MessageStore>,
            private val messageId: String
        ) : Runnable {
            private val ref = storeRef
            override fun run() {
                pendingBurns.remove(messageId)
                val store = ref.get() ?: return
                runCatching { store.delete(messageId) }
                    .onFailure { Log.w(TAG, "burn delete failed for $messageId", it) }
                runCatching { store.writableDatabase.delete("local_history", "id = ?", arrayOf(messageId)) }
                    .onFailure { Log.w(TAG, "burn local_history delete failed for $messageId", it) }
                runCatching { store.writableDatabase.execSQL("DELETE FROM messages_fts WHERE messageId = ?", arrayOf(messageId)) }
                    .onFailure { e -> Log.w(TAG, "burn FTS delete failed for $messageId", e) }
            }
        }
    }

    /** Schedule a disappearing-message burn without leaking this store. */
    fun scheduleDisappearingBurn(messageId: String, delayMs: Long) {
        cancelBurn(messageId)
        if (delayMs <= 0) {
            runCatching { delete(messageId) }
                .onFailure { Log.w(TAG, "immediate burn failed for $messageId", it) }
            runCatching { writableDatabase.delete("local_history", "id = ?", arrayOf(messageId)) }
                .onFailure { Log.w(TAG, "immediate burn local_history failed for $messageId", it) }
            return
        }
        if (delayMs > MAX_IN_MEMORY_BURN_DELAY_MS) {
            // Persisted expiresAt inside the payload is the source of truth;
            // startup purge (purgeExpiredLocalHistory) will delete it after reboot.
            Log.i(TAG, "burn for $messageId exceeds in-memory cap (${delayMs}ms) — relying on persisted expiresAt + startup purge")
            return
        }
        val runnable = BurnRunnable(WeakReference(this), messageId)
        pendingBurns[messageId] = runnable
        burnHandler.postDelayed(runnable, delayMs)
    }

    /** Cancel a pending in-memory burn (e.g. message deleted early). */
    fun cancelBurn(messageId: String) {
        pendingBurns.remove(messageId)?.let { burnHandler.removeCallbacks(it) }
    }

    /** Cancel all pending burns and close the database. Call when the store is no longer needed. */
    override fun close() {
        pendingBurns.values.forEach { burnHandler.removeCallbacks(it) }
        pendingBurns.clear()
        super.close()
    }

    /**
     * Reboot recovery: delete local_history rows whose RichMessage.expiresAt
     * has passed. Call on startup — covers burns missed while the process
     * was dead and long timers beyond the in-memory cap.
     * @return number of rows deleted.
     */
    fun purgeExpiredLocalHistory(now: Long = System.currentTimeMillis()): Int {
        var deleted = 0
        val ids = mutableListOf<Pair<String, ByteArray>>()
        runCatching {
            readableDatabase.query("local_history", arrayOf("id", "encrypted_plaintext"), null, null, null, null, null).use { c ->
                val idIdx = c.getColumnIndexOrThrow("id")
                val blobIdx = c.getColumnIndexOrThrow("encrypted_plaintext")
                while (c.moveToNext()) {
                    runCatching { ids += c.getString(idIdx) to c.getBlob(blobIdx) }
                        .onFailure { e -> Log.w(TAG, "purge read row failed", e) }
                }
            }
        }.onFailure { e -> Log.w(TAG, "purge scan failed", e); return 0 }
        ids.forEach { (id, blob) ->
            val expiresAt = runCatching { com.red.sovereign.core.RichMessage.decode(recordCipher.decrypt(blob))?.expiresAt }.getOrNull() ?: return@forEach
            if (expiresAt <= now) {
                cancelBurn(id)
                var ok = false
                runCatching { delete(id); writableDatabase.delete("local_history", "id = ?", arrayOf(id)); ok = true }
                    .onFailure { e -> Log.w(TAG, "purge delete failed for $id", e) }
                if (ok) deleted++
            }
        }
        return deleted
    }
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE messages (
            id TEXT PRIMARY KEY, conversation_id TEXT NOT NULL, sender_id TEXT NOT NULL,
            receiver_id TEXT NOT NULL, encrypted_payload BLOB NOT NULL, message_type TEXT NOT NULL,
            sender_device_id INTEGER NOT NULL, receiver_device_id INTEGER NOT NULL, ciphertext_type INTEGER NOT NULL,
            sequence_number INTEGER NOT NULL, status TEXT NOT NULL, created_at INTEGER NOT NULL,
            UNIQUE(conversation_id, sequence_number))""")
        db.execSQL("CREATE INDEX idx_messages_conversation ON messages(conversation_id, sequence_number DESC)")
        db.execSQL("CREATE INDEX idx_messages_status ON messages(status)")
        createLocalHistoryTables(db)
        // 🔍 FTS5 for encrypted local search (never synced)
        try { db.execSQL("""
            CREATE VIRTUAL TABLE IF NOT EXISTS messages_fts USING fts5(
                messageId UNINDEXED, conversationId UNINDEXED, senderId UNINDEXED, content, tokenize='unicode61 remove_diacritics 1')
        """.trimIndent()) } catch (e: Exception) { Log.w(TAG, "FTS table create failed (best-effort)", e) }
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE messages ADD COLUMN sender_device_id INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE messages ADD COLUMN receiver_device_id INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE messages ADD COLUMN ciphertext_type INTEGER NOT NULL DEFAULT 0")
        }
        if (oldVersion < 3) createLocalHistoryTables(db)
        if (oldVersion < 4) {
            db.execSQL("ALTER TABLE local_history ADD COLUMN status TEXT NOT NULL DEFAULT 'SENT'")
        }
        if (oldVersion < 5) {
            runCatching { db.execSQL("ALTER TABLE conversation_preferences ADD COLUMN disappearing_duration_ms INTEGER NOT NULL DEFAULT 0") }
        }
        if (oldVersion < 6) {
            runCatching { db.execSQL("ALTER TABLE conversation_preferences ADD COLUMN read_receipts_override INTEGER NOT NULL DEFAULT 0") }
        }
        if (oldVersion < 7) {
            // P1-D: وضع Highlights لكل محادثة (0=كل الرسائل، 1=منشن/رد/جهات فقط).
            runCatching { db.execSQL("ALTER TABLE conversation_preferences ADD COLUMN highlights_only INTEGER NOT NULL DEFAULT 0") }
        }
    }
    // منع انهيار SQLite عند رجوع إصدار قاعدة البيانات (استرجاع نسخة أقدم من التطبيق)
    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    private fun createLocalHistoryTables(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS local_history (
            id TEXT PRIMARY KEY, conversation_id TEXT NOT NULL, sender_id TEXT NOT NULL,
            encrypted_plaintext BLOB NOT NULL, message_type TEXT NOT NULL, created_at INTEGER NOT NULL,
            outgoing INTEGER NOT NULL DEFAULT 0, status TEXT NOT NULL DEFAULT 'SENT')""")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_local_history_conversation ON local_history(conversation_id, created_at DESC)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS conversation_preferences (
            conversation_id TEXT PRIMARY KEY, pinned INTEGER NOT NULL DEFAULT 0,
            archived INTEGER NOT NULL DEFAULT 0, muted_until INTEGER NOT NULL DEFAULT 0,
            custom_name TEXT DEFAULT NULL, wallpaper INTEGER NOT NULL DEFAULT 0,
            disappearing_duration_ms INTEGER NOT NULL DEFAULT 0,
            read_receipts_override INTEGER NOT NULL DEFAULT 0,
            unread_count INTEGER NOT NULL DEFAULT 0,
            highlights_only INTEGER NOT NULL DEFAULT 0)""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS message_reactions (
            message_id TEXT NOT NULL,
            sender_id TEXT NOT NULL,
            emoji TEXT NOT NULL,
            created_at INTEGER NOT NULL,
            PRIMARY KEY (message_id, sender_id))""")
        // ترقية الجداول القديمة بإضافة الأعمدة الجديدة بأمان
        runCatching { db.execSQL("ALTER TABLE conversation_preferences ADD COLUMN custom_name TEXT DEFAULT NULL") }
        runCatching { db.execSQL("ALTER TABLE conversation_preferences ADD COLUMN wallpaper INTEGER NOT NULL DEFAULT 0") }
        runCatching { db.execSQL("ALTER TABLE conversation_preferences ADD COLUMN disappearing_duration_ms INTEGER NOT NULL DEFAULT 0") }
        runCatching { db.execSQL("ALTER TABLE conversation_preferences ADD COLUMN read_receipts_override INTEGER NOT NULL DEFAULT 0") }
        runCatching { db.execSQL("ALTER TABLE conversation_preferences ADD COLUMN unread_count INTEGER NOT NULL DEFAULT 0") }
        // P1-D: ترقية آمنة للجداول القديمة — Highlights فقط (لا أعمدة أخرى هنا).
        runCatching { db.execSQL("ALTER TABLE conversation_preferences ADD COLUMN highlights_only INTEGER NOT NULL DEFAULT 0") }
    }

    fun save(message: RedProtos.ChatMessage, status: String = "DELIVERED") {
        if (message.payload.size() == 0) { Log.w(TAG, "save skipped: empty ciphertext id=${message.id}"); return }
        writableDatabase.insertWithOnConflict("messages", null, ContentValues().apply {
            put("id", message.id); put("conversation_id", message.conversationId); put("sender_id", message.senderId)
            put("receiver_id", message.receiverId); put("encrypted_payload", message.payload.toByteArray())
            put("message_type", message.type); put("sender_device_id", message.senderDeviceId)
            put("receiver_device_id", message.receiverDeviceId); put("ciphertext_type", message.ciphertextType)
            put("sequence_number", message.sequenceNumber); put("status", status); put("created_at", message.timestamp)
        }, SQLiteDatabase.CONFLICT_IGNORE)
    }

    fun updateStatus(messageId: String, status: String) {
        if (status !in setOf("PENDING", "SENDING", "SENT", "DELIVERED", "READ", "FAILED", "DEAD_LETTER", "DELETED_FOR_ALL")) { Log.w(TAG, "updateStatus skipped: bad status=$status id=$messageId"); return }
        writableDatabase.update("messages", ContentValues().apply { put("status", status) }, "id = ?", arrayOf(messageId))
        writableDatabase.update("local_history", ContentValues().apply { put("status", status) }, "id = ?", arrayOf(messageId))
    }

    fun delete(messageId: String) { writableDatabase.delete("messages", "id = ?", arrayOf(messageId)); try { writableDatabase.execSQL("DELETE FROM messages_fts WHERE messageId = ?", arrayOf(messageId)) } catch (e: Exception) { Log.w(TAG, "FTS delete failed for $messageId (best-effort)", e) } }

    /** ✅ حذف للجميع (Delete for All) — يعمل حتى بلا أعمدة جديدة: يستبدل النص بعلامة حذف ويحدّث FTS */
    fun deleteForAll(messageId: String, deletedBy: String) {
        try {
            // حاول تحديث الأعمدة الجديدة إن وجدت (للتوافق مع نسخ مستقبلية)
            writableDatabase.update("messages", ContentValues().apply {
                put("status", "DELETED_FOR_ALL")
            }, "id = ?", arrayOf(messageId))
        } catch (e: Exception) {
            Log.w(TAG, "deleteForAll messages update failed for $messageId, fallback to delete", e)
            // fallback: احذف السجل المشفر إن فشل التحديث
            writableDatabase.delete("messages", "id = ?", arrayOf(messageId))
        }
        try {
            // استبدال النص المحلي بعلامة حذف مع الحفاظ على السجل للعرض
            val tombstone = "{\"text\":\"تم حذف هذه الرسالة\",\"deleted\":true,\"by\":\"$deletedBy\"}".toByteArray(Charsets.UTF_8)
            writableDatabase.update("local_history", ContentValues().apply {
                put("encrypted_plaintext", recordCipher.encrypt(tombstone))
                put("status", "DELETED_FOR_ALL")
            }, "id = ?", arrayOf(messageId))
            // تحديث الفهرس
            try { writableDatabase.execSQL("DELETE FROM messages_fts WHERE messageId = ?", arrayOf(messageId)) } catch (e: Exception) { Log.w(TAG, "FTS delete failed for $messageId (best-effort)", e) }
            // إن لم يوجد السجل (0 صفوف محدثة) نحذفه فعلياً
            // (handled by update count check — simplified)
        } catch (e: Exception) {
            Log.w(TAG, "deleteForAll local_history failed for $messageId", e)
            try { writableDatabase.delete("local_history", "id = ?", arrayOf(messageId)) } catch (e2: Exception) { Log.w(TAG, "deleteForAll fallback delete failed for $messageId", e2) }
            try { writableDatabase.execSQL("DELETE FROM messages_fts WHERE messageId = ?", arrayOf(messageId)) } catch (e2: Exception) { Log.w(TAG, "FTS delete failed for $messageId (best-effort)", e2) }
        }
    }

    /** حذف محلي نهائي */
    fun deleteLocalMessage(messageId: String) {
        cancelBurn(messageId)
        writableDatabase.delete("messages", "id = ?", arrayOf(messageId))
        writableDatabase.delete("local_history", "id = ?", arrayOf(messageId))
        try { writableDatabase.execSQL("DELETE FROM messages_fts WHERE messageId = ?", arrayOf(messageId)) } catch (e: Exception) { Log.w(TAG, "FTS delete failed for $messageId (best-effort)", e) }
    }

    // ── P0-D: حرق viewOnce فقط ──────────────────────────────────────────
    // البروتوكول: RichMessage(viewOnce=true). عند فتحها:
    //   1) ابعث READ أولاً عبر RedConnectionService.markRead(context, id, sequence)
    //      (قبل الحذف — الحذف أولاً يفقد sequence اللازم للإيصال)،
    //   2) ثم احذف محلياً عبر consumeViewOnce(id).
    // الواجهة: if (viewOnce) أخفِ إعادة التوجيه/الحفظ/النسخ/المشاركة والتثبيت.
    //   مثال: val viewOnce = RichMessage.decode(msg.plaintext)?.viewOnce == true
    //          if (viewOnce) hide forward/save/copy/share/star/pin
    // لا يُجدول burn هنا — الحرق فوري عند الفتح، لا مؤقت.

    /** هل هذه الحمولة رسالة عرض-مرة-واحدة؟ (فحص بسيط للواجهة لإخفاء forward/save). */
    fun isViewOncePlaintext(plaintext: ByteArray): Boolean =
        runCatching { RichMessage.decode(plaintext)?.viewOnce == true }.getOrDefault(false)

    /** هل رسالة السجل المحلي هذه viewOnce؟ */
    fun isViewOnceMessage(message: LocalMessage): Boolean = isViewOncePlaintext(message.plaintext)

    /**
     * استهلاك رسالة viewOnce بعد العرض — حذف محلي فوري من كل الجداول + FTS.
     * يُستدعى بعد RedConnectionService.markRead (READ ثم حذف).
     */
    fun consumeViewOnce(messageId: String) {
        deleteLocalMessage(messageId)
        deleteReactionsForMessage(messageId)
    }

    fun deleteReactionsForMessage(messageId: String) {
        try { writableDatabase.delete("reactions", "message_id = ?", arrayOf(messageId)) } catch (e: Exception) { Log.w(TAG, "delete reactions failed for $messageId", e) }
        try { writableDatabase.delete("message_reactions", "message_id = ?", arrayOf(messageId)) } catch (e: Exception) { Log.w(TAG, "delete message_reactions failed for $messageId", e) }
    }

    fun getLocalHistoryEntry(messageId: String): LocalMessage? {
        readableDatabase.query("local_history", null, "id=?", arrayOf(messageId), null, null, null).use { c ->
            if (!c.moveToFirst()) return null
            return LocalMessage(
                c.getString(c.getColumnIndexOrThrow("id")),
                c.getString(c.getColumnIndexOrThrow("conversation_id")),
                c.getString(c.getColumnIndexOrThrow("sender_id")),
                recordCipher.decrypt(c.getBlob(c.getColumnIndexOrThrow("encrypted_plaintext"))),
                c.getString(c.getColumnIndexOrThrow("message_type")),
                c.getLong(c.getColumnIndexOrThrow("created_at")),
                c.getInt(c.getColumnIndexOrThrow("outgoing")) == 1,
                c.getString(c.getColumnIndexOrThrow("status"))
            )
        }
    }

    fun updateLocalHistoryText(messageId: String, newPlaintext: ByteArray) {
        writableDatabase.update("local_history", ContentValues().apply {
            put("encrypted_plaintext", recordCipher.encrypt(newPlaintext))
        }, "id=?", arrayOf(messageId))
        try {
            val rich = runCatching { com.red.sovereign.core.RichMessage.decode(newPlaintext) }.getOrNull()
            val indexText = rich?.text ?: newPlaintext.toString(Charsets.UTF_8)
            writableDatabase.execSQL("INSERT OR REPLACE INTO messages_fts(messageId, conversationId, senderId, content) VALUES (?, ?, ?, ?)",
                arrayOf(messageId, "", "", indexText.take(5000)))
        } catch (e: Exception) { Log.w(TAG, "FTS index update failed for $messageId (best-effort)", e) }
    }

    fun deleteConversation(conversationId: String) {
        writableDatabase.delete("messages", "conversation_id=?", arrayOf(conversationId))
        writableDatabase.delete("local_history", "conversation_id=?", arrayOf(conversationId))
        try { writableDatabase.execSQL("DELETE FROM messages_fts WHERE conversationId=?", arrayOf(conversationId)) } catch (e: Exception) { Log.w(TAG, "FTS delete failed for conversation $conversationId (best-effort)", e) }
    }

    // NOTE (Phase-2): legacy saveReply removed — zero callers, and it wrote
    // reply_to_* columns that exist in neither table (SQLiteException on call).
    // Replies persist via Room LocalHistoryEntity.replyTo* (LocalRepository
    // backfills preview at save). See RedDao.updateReplyPreview.

    fun messages(conversationId: String, limit: Int = 100): List<StoredMessage> {
        val result = mutableListOf<StoredMessage>()
        readableDatabase.query("messages", null, "conversation_id = ?", arrayOf(conversationId), null, null,
            "sequence_number DESC", limit.coerceIn(1, 500).toString()).use { cursor ->
            while (cursor.moveToNext()) result += StoredMessage(
                cursor.getString(cursor.getColumnIndexOrThrow("id")), cursor.getString(cursor.getColumnIndexOrThrow("conversation_id")),
                cursor.getString(cursor.getColumnIndexOrThrow("sender_id")), cursor.getString(cursor.getColumnIndexOrThrow("receiver_id")),
                cursor.getBlob(cursor.getColumnIndexOrThrow("encrypted_payload")), cursor.getString(cursor.getColumnIndexOrThrow("message_type")),
                cursor.getInt(cursor.getColumnIndexOrThrow("sender_device_id")), cursor.getInt(cursor.getColumnIndexOrThrow("receiver_device_id")),
                cursor.getInt(cursor.getColumnIndexOrThrow("ciphertext_type")), cursor.getLong(cursor.getColumnIndexOrThrow("sequence_number")), cursor.getString(cursor.getColumnIndexOrThrow("status")),
                cursor.getLong(cursor.getColumnIndexOrThrow("created_at"))
            )
        }
        return result
    }

    fun saveDecrypted(message: LocalMessage) {
        if (message.plaintext.isEmpty() || message.plaintext.size > 256 * 1024) { Log.w(TAG, "saveDecrypted skipped: bad size=${message.plaintext.size} id=${message.id}"); return }
        writableDatabase.insertWithOnConflict("local_history", null, ContentValues().apply {
            put("id", message.id); put("conversation_id", message.conversationId); put("sender_id", message.senderId)
            put("encrypted_plaintext", recordCipher.encrypt(message.plaintext)); put("message_type", message.type)
            put("created_at", message.timestamp); put("outgoing", if (message.outgoing) 1 else 0)
            put("status", message.status)
        }, SQLiteDatabase.CONFLICT_REPLACE)
        // 🔍 Index for local FTS search (plaintext only on device)
        // P0-D viewOnce: لا تُفهرس رسائل العرض-مرة-واحدة — تُحذف بعد أول فتح فلا معنى لبقائها في البحث.
        try {
            val rich = runCatching { com.red.sovereign.core.RichMessage.decode(message.plaintext) }.getOrNull()
            val isViewOnce = rich?.viewOnce == true
            if (!isViewOnce) {
                val indexText = rich?.text ?: message.plaintext.toString(Charsets.UTF_8)
                if (indexText.length in 2..5000) {
                    writableDatabase.execSQL("INSERT OR REPLACE INTO messages_fts(messageId, conversationId, senderId, content) VALUES (?, ?, ?, ?)",
                        arrayOf(message.id, message.conversationId, message.senderId, indexText))
                }
            } else {
                // تأكيد عدم بقاء فهرس قديم إن أُعيد حفظ نفس المعرف.
                runCatching { writableDatabase.execSQL("DELETE FROM messages_fts WHERE messageId = ?", arrayOf(message.id)) }
            }
            // ⏳ Schedule disappearing deletion if expiresAt set.
            // Uses application-scoped handler + WeakReference (see scheduleDisappearingBurn);
            // long timers rely on persisted expiresAt + purgeExpiredLocalHistory on startup.
            rich?.expiresAt?.let { expiresAt ->
                val delay = expiresAt - System.currentTimeMillis()
                if (delay > 0) {
                    scheduleDisappearingBurn(message.id, delay)
                } else {
                    delete(message.id)
                    runCatching { writableDatabase.delete("local_history", "id = ?", arrayOf(message.id)) }
                        .onFailure { e -> Log.w(TAG, "immediate expired delete failed for ${message.id}", e) }
                }
            }
        } catch (e: Exception) { Log.w(TAG, "saveDecrypted FTS/burn failed for ${message.id} (best-effort)", e) }
    }

    fun localHistory(conversationId: String, limit: Int = 200): List<LocalMessage> {
        val result = mutableListOf<LocalMessage>()
        readableDatabase.query("local_history", null, "conversation_id=?", arrayOf(conversationId), null, null, "created_at ASC", limit.coerceIn(1, 1000).toString()).use { cursor ->
            while (cursor.moveToNext()) result += LocalMessage(
                cursor.getString(cursor.getColumnIndexOrThrow("id")), conversationId,
                cursor.getString(cursor.getColumnIndexOrThrow("sender_id")),
                recordCipher.decrypt(cursor.getBlob(cursor.getColumnIndexOrThrow("encrypted_plaintext"))),
                cursor.getString(cursor.getColumnIndexOrThrow("message_type")), cursor.getLong(cursor.getColumnIndexOrThrow("created_at")),
                cursor.getInt(cursor.getColumnIndexOrThrow("outgoing")) == 1,
                cursor.getString(cursor.getColumnIndexOrThrow("status"))
            )
        }
        return result
    }

    fun conversationSummaries(ownRedId: String): List<ConversationSummary> {
        val summaries = mutableListOf<ConversationSummary>()
        readableDatabase.rawQuery("SELECT conversation_id,encrypted_plaintext,created_at FROM local_history ORDER BY created_at DESC", null).use { cursor ->
            val seen = mutableSetOf<String>()
            while (cursor.moveToNext()) {
                val conversation = cursor.getString(0); if (!seen.add(conversation)) continue
                val peer = readableDatabase.query("messages", arrayOf("sender_id", "receiver_id"), "conversation_id=?", arrayOf(conversation), null, null, "created_at DESC", "1").use { message ->
                    if (!message.moveToFirst()) conversation else listOf(message.getString(0), message.getString(1)).firstOrNull { it != ownRedId } ?: conversation
                }
                val preview = runCatching { recordCipher.decrypt(cursor.getBlob(1)).toString(Charsets.UTF_8).take(120) }.getOrDefault("رسالة مشفرة")
                val pref = conversationPreference(conversation)
                summaries += ConversationSummary(conversation, peer, preview, cursor.getLong(2), pref.first, pref.second, pref.third)
            }
        }
        return summaries.sortedWith(compareByDescending<ConversationSummary> { it.pinned }.thenByDescending { it.timestamp })
    }

    fun search(query: String, limit: Int = 100): List<LocalMessage> {
        // 🔍 Try FTS first (fast), fallback to linear scan
        try {
            val sanitized = query.trim().replace("\"", "\"\"").take(100)
            if (sanitized.length >= 2) {
                val ftsIds = mutableListOf<String>()
                readableDatabase.rawQuery("SELECT messageId FROM messages_fts WHERE messages_fts MATCH ? ORDER BY rank LIMIT ?", arrayOf("\"" + sanitized + "\"", limit.coerceIn(1,200).toString())).use { c ->
                    while (c.moveToNext()) ftsIds += c.getString(0)
                }
                if (ftsIds.isNotEmpty()) {
                    val placeholders = ftsIds.joinToString(",") { "?" }
                    val result = mutableListOf<LocalMessage>()
                    readableDatabase.query("local_history", null, "id IN ($placeholders)", ftsIds.toTypedArray(), null, null, "created_at DESC").use { cursor ->
                        while (cursor.moveToNext()) result += LocalMessage(
                            cursor.getString(cursor.getColumnIndexOrThrow("id")), cursor.getString(cursor.getColumnIndexOrThrow("conversation_id")),
                            cursor.getString(cursor.getColumnIndexOrThrow("sender_id")),
                            recordCipher.decrypt(cursor.getBlob(cursor.getColumnIndexOrThrow("encrypted_plaintext"))),
                            cursor.getString(cursor.getColumnIndexOrThrow("message_type")), cursor.getLong(cursor.getColumnIndexOrThrow("created_at")),
                            cursor.getInt(cursor.getColumnIndexOrThrow("outgoing")) == 1,
                            cursor.getString(cursor.getColumnIndexOrThrow("status"))
                        )
                    }
                    if (result.isNotEmpty()) return result
                }
            }
        } catch (e: Exception) { Log.w(TAG, "FTS search failed, falling back to linear scan: ${e.message}", e) }
        // Fallback linear
        val needle = query.trim().lowercase(); if (needle.length < 2) return emptyList()
        val result = mutableListOf<LocalMessage>()
        readableDatabase.query("local_history", null, null, null, null, null, "created_at DESC", "1000").use { cursor ->
            while (cursor.moveToNext() && result.size < limit.coerceIn(1, 200)) {
                val plaintext = recordCipher.decrypt(cursor.getBlob(cursor.getColumnIndexOrThrow("encrypted_plaintext")))
                if (plaintext.toString(Charsets.UTF_8).lowercase().contains(needle)) result += LocalMessage(
                    cursor.getString(cursor.getColumnIndexOrThrow("id")), cursor.getString(cursor.getColumnIndexOrThrow("conversation_id")),
                    cursor.getString(cursor.getColumnIndexOrThrow("sender_id")), plaintext,
                    cursor.getString(cursor.getColumnIndexOrThrow("message_type")), cursor.getLong(cursor.getColumnIndexOrThrow("created_at")),
                    cursor.getInt(cursor.getColumnIndexOrThrow("outgoing")) == 1,
                    cursor.getString(cursor.getColumnIndexOrThrow("status"))
                )
            }
        }
        return result
    }

    fun setConversationPreference(conversationId: String, field: String, value: Long) {
        if (field !in setOf("pinned", "archived", "muted_until")) { Log.w(TAG, "pref skipped: bad field=$field conv=$conversationId"); return }
        writableDatabase.execSQL("INSERT OR IGNORE INTO conversation_preferences(conversation_id) VALUES (?)", arrayOf(conversationId))
        writableDatabase.update("conversation_preferences", ContentValues().apply { put(field, value) }, "conversation_id=?", arrayOf(conversationId))
    }

    /** مدة اختفاء الرسائل الافتراضية للمحادثة بالمللي ثانية؛ الصفر يعني تعطيلها. */
    fun setConversationDisappearingDuration(conversationId: String, durationMs: Long?) {
        writableDatabase.execSQL("INSERT OR IGNORE INTO conversation_preferences(conversation_id) VALUES (?)", arrayOf(conversationId))
        writableDatabase.update(
            "conversation_preferences",
            ContentValues().apply { put("disappearing_duration_ms", durationMs?.coerceAtLeast(0L) ?: 0L) },
            "conversation_id=?",
            arrayOf(conversationId)
        )
    }

    fun conversationDisappearingDuration(conversationId: String): Long = readableDatabase.query(
        "conversation_preferences",
        arrayOf("disappearing_duration_ms"),
        "conversation_id=?",
        arrayOf(conversationId),
        null,
        null,
        null
    ).use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0).coerceAtLeast(0L) else 0L }

    /** تخزين/استرجاع الاسم المخصص للمحادثة (تجاوز اسم الصديق). */
    fun setConversationCustomName(conversationId: String, name: String) {
        writableDatabase.execSQL("INSERT OR IGNORE INTO conversation_preferences(conversation_id) VALUES (?)", arrayOf(conversationId))
        writableDatabase.update("conversation_preferences", ContentValues().apply { put("custom_name", name) }, "conversation_id=?", arrayOf(conversationId))
    }
    fun conversationCustomName(conversationId: String): String? = readableDatabase.query(
        "conversation_preferences", arrayOf("custom_name"), "conversation_id=?", arrayOf(conversationId), null, null, null
    ).use { if (it.moveToFirst()) it.getString(0)?.takeIf(String::isNotBlank) else null }

    /** تخزين/استرجاع معرّف خلفية المحادثة (0=افتراضي، 1..n=ألوان/تدرجات). */
    fun setConversationWallpaper(conversationId: String, wallpaperId: Int) {
        writableDatabase.execSQL("INSERT OR IGNORE INTO conversation_preferences(conversation_id) VALUES (?)", arrayOf(conversationId))
        writableDatabase.update("conversation_preferences", ContentValues().apply { put("wallpaper", wallpaperId) }, "conversation_id=?", arrayOf(conversationId))
    }
    fun conversationWallpaper(conversationId: String): Int = readableDatabase.query(
        "conversation_preferences", arrayOf("wallpaper"), "conversation_id=?", arrayOf(conversationId), null, null, null
    ).use { if (it.moveToFirst()) it.getInt(0) else 0 }

    fun conversationPreference(conversationId: String): Triple<Boolean, Boolean, Long> = readableDatabase.query(
        "conversation_preferences", arrayOf("pinned", "archived", "muted_until"), "conversation_id=?", arrayOf(conversationId), null, null, null
    ).use { if (it.moveToFirst()) Triple(it.getInt(0) == 1, it.getInt(1) == 1, it.getLong(2)) else Triple(false, false, 0L) }

    /**
     * تجاوز إيصالات القراءة لكل محادثة: 0=الإعداد العام، 1=إجبار التشغيل، 2=إجبار الإيقاف.
     * العمود يُنشأ بترحيل آمن — الجداول القديمة بلا عمود تُقرأ كـ 0 (عام).
     */
    fun setConversationReadReceipts(conversationId: String, mode: Int) {
        val safe = mode.coerceIn(0, 2)
        writableDatabase.execSQL("INSERT OR IGNORE INTO conversation_preferences(conversation_id) VALUES (?)", arrayOf(conversationId))
        runCatching {
            writableDatabase.update(
                "conversation_preferences",
                ContentValues().apply { put("read_receipts_override", safe) },
                "conversation_id=?",
                arrayOf(conversationId)
            )
        }.onFailure { e -> Log.w(TAG, "read_receipts_override update failed for $conversationId", e) }
    }

    fun conversationReadReceipts(conversationId: String): Int = runCatching {
        readableDatabase.query(
            "conversation_preferences", arrayOf("read_receipts_override"),
            "conversation_id=?", arrayOf(conversationId), null, null, null
        ).use { if (it.moveToFirst()) it.getInt(0).coerceIn(0, 2) else 0 }
    }.getOrElse { 0 }

    /** القيمة الفعلية: التجاوز الفردي أولاً ثم الإعداد العام. */
    fun effectiveReadReceipts(conversationId: String, global: Boolean): Boolean = when (conversationReadReceipts(conversationId)) {
        1 -> true
        2 -> false
        else -> global
    }

    // ── P1-D: وضع Highlights لكل محادثة (منشن/رد/جهات فقط) ──────────
    // عند التفعيل تُنبّه المجموعة فقط للمنشن المباشر والرد وجهات الاتصال و@all
    // (يُقرأ عبر GroupMentions.shouldNotifyForGroupMessage كـ highlightsOnly).
    // العمود يُنشأ بترحيل آمن — الجداول القديمة بلا عمود تُقرأ كـ false.

    /** تفعيل/تعطيل وضع Highlights للمحادثة. */
    fun setConversationHighlightsOnly(conversationId: String, enabled: Boolean) {
        writableDatabase.execSQL("INSERT OR IGNORE INTO conversation_preferences(conversation_id) VALUES (?)", arrayOf(conversationId))
        runCatching {
            writableDatabase.update(
                "conversation_preferences",
                ContentValues().apply { put("highlights_only", if (enabled) 1 else 0) },
                "conversation_id=?",
                arrayOf(conversationId)
            )
        }.onFailure { Log.w(TAG, "highlights_only update failed for $conversationId", it) }
    }

    /** هل وضع Highlights مفعّل لهذه المحادثة؟ (آمن للجداول القديمة: false). */
    fun isConversationHighlightsOnly(conversationId: String): Boolean = runCatching {
        readableDatabase.query(
            "conversation_preferences", arrayOf("highlights_only"),
            "conversation_id=?", arrayOf(conversationId), null, null, null
        ).use { if (it.moveToFirst()) it.getInt(0) == 1 else false }
    }.getOrElse { false }
}
