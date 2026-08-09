package com.red.sovereign.core

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.red.sovereign.crypto.ProtocolRecordCipher
import com.red.sovereign.proto.RedProtos

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
class MessageStore(context: Context) : SQLiteOpenHelper(context, "red_messages.db", null, 4) {
    private val recordCipher = ProtocolRecordCipher()
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
    }

    private fun createLocalHistoryTables(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS local_history (
            id TEXT PRIMARY KEY, conversation_id TEXT NOT NULL, sender_id TEXT NOT NULL,
            encrypted_plaintext BLOB NOT NULL, message_type TEXT NOT NULL, created_at INTEGER NOT NULL,
            outgoing INTEGER NOT NULL DEFAULT 0, status TEXT NOT NULL DEFAULT 'SENT')""")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_local_history_conversation ON local_history(conversation_id, created_at DESC)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS conversation_preferences (
            conversation_id TEXT PRIMARY KEY, pinned INTEGER NOT NULL DEFAULT 0,
            archived INTEGER NOT NULL DEFAULT 0, muted_until INTEGER NOT NULL DEFAULT 0)""")
    }

    fun save(message: RedProtos.ChatMessage, status: String = "DELIVERED") {
        require(message.payload.size() > 0) { "Ciphertext is empty" }
        writableDatabase.insertWithOnConflict("messages", null, ContentValues().apply {
            put("id", message.id); put("conversation_id", message.conversationId); put("sender_id", message.senderId)
            put("receiver_id", message.receiverId); put("encrypted_payload", message.payload.toByteArray())
            put("message_type", message.type); put("sender_device_id", message.senderDeviceId)
            put("receiver_device_id", message.receiverDeviceId); put("ciphertext_type", message.ciphertextType)
            put("sequence_number", message.sequenceNumber); put("status", status); put("created_at", message.timestamp)
        }, SQLiteDatabase.CONFLICT_IGNORE)
    }

    fun updateStatus(messageId: String, status: String) {
        require(status in setOf("SENT", "DELIVERED", "READ"))
        writableDatabase.update("messages", ContentValues().apply { put("status", status) }, "id = ?", arrayOf(messageId))
        writableDatabase.update("local_history", ContentValues().apply { put("status", status) }, "id = ?", arrayOf(messageId))
    }

    fun delete(messageId: String) { writableDatabase.delete("messages", "id = ?", arrayOf(messageId)) }

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
        require(message.plaintext.isNotEmpty() && message.plaintext.size <= 256 * 1024)
        writableDatabase.insertWithOnConflict("local_history", null, ContentValues().apply {
            put("id", message.id); put("conversation_id", message.conversationId); put("sender_id", message.senderId)
            put("encrypted_plaintext", recordCipher.encrypt(message.plaintext)); put("message_type", message.type)
            put("created_at", message.timestamp); put("outgoing", if (message.outgoing) 1 else 0)
            put("status", message.status)
        }, SQLiteDatabase.CONFLICT_REPLACE)
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
        require(field in setOf("pinned", "archived", "muted_until"))
        writableDatabase.execSQL("INSERT OR IGNORE INTO conversation_preferences(conversation_id) VALUES (?)", arrayOf(conversationId))
        writableDatabase.update("conversation_preferences", ContentValues().apply { put(field, value) }, "conversation_id=?", arrayOf(conversationId))
    }

    fun conversationPreference(conversationId: String): Triple<Boolean, Boolean, Long> = readableDatabase.query(
        "conversation_preferences", arrayOf("pinned", "archived", "muted_until"), "conversation_id=?", arrayOf(conversationId), null, null, null
    ).use { if (it.moveToFirst()) Triple(it.getInt(0) == 1, it.getInt(1) == 1, it.getLong(2)) else Triple(false, false, 0L) }
}
