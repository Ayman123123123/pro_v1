package com.red.sovereign.core.database

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper

/**
 * 🔍 البحث المحلي المشفر — FTS5 على SQLCipher
 * الخادم لا يرى النص أبداً، البحث كله على الجهاز
 * يفهرس النص بعد فك التشفير محلياً فقط
 */
class FtsSearchManager(private val db: SupportSQLiteDatabase) {

    fun createFtsTable() {
        // FTS5 virtual table for decrypted message previews (local only, never synced)
        db.execSQL("""
            CREATE VIRTUAL TABLE IF NOT EXISTS messages_fts 
            USING fts5(
                messageId UNINDEXED,
                conversationId UNINDEXED,
                senderId UNINDEXED,
                content,
                tokenize='unicode61 remove_diacritics 1'
            )
        """.trimIndent())
        db.execSQL("CREATE TRIGGER IF NOT EXISTS messages_fts_delete AFTER DELETE ON messages BEGIN DELETE FROM messages_fts WHERE messageId = old.id; END")
    }

    fun indexMessage(messageId: String, conversationId: String, senderId: String, plaintext: String) {
        if (plaintext.length < 2 || plaintext.length > 5000) return
        db.execSQL(
            "INSERT OR REPLACE INTO messages_fts(messageId, conversationId, senderId, content) VALUES (?, ?, ?, ?)",
            arrayOf(messageId, conversationId, senderId, plaintext)
        )
    }

    fun search(query: String, limit: Int = 50): List<FtsResult> {
        if (query.trim().length < 2) return emptyList()
        // Sanitize FTS query: escape quotes and wrap in quotes for phrase search
        val sanitized = query.trim().replace("\"", "\"\"").take(100)
        val cursor = db.query("SELECT messageId, conversationId, senderId, content, rank FROM messages_fts WHERE messages_fts MATCH ? ORDER BY rank LIMIT ?", arrayOf<Any>("\"$sanitized\"", limit))
        val results = mutableListOf<FtsResult>()
        while (cursor.moveToNext()) {
            results += FtsResult(
                messageId = cursor.getString(0),
                conversationId = cursor.getString(1),
                senderId = cursor.getString(2),
                snippet = cursor.getString(3).take(120),
                rank = cursor.getDouble(4)
            )
        }
        cursor.close()
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
 * Callback to create FTS table on DB creation/migration
 */
class FtsCallback : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        FtsSearchManager(db).createFtsTable()
    }
    override fun onOpen(db: SupportSQLiteDatabase) {
        // Ensure FTS exists on open (for existing DBs)
        try { FtsSearchManager(db).createFtsTable() } catch (_: Exception) {}
    }
}
