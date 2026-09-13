package com.red.sovereign.core

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class RecipientDeliveryInfo(
    val messageId: String,
    val recipientId: String,
    val recipientName: String,
    val status: String,
    val deliveredAt: Long? = null,
    val readAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

class DeliveryStatusStore(context: Context) : SQLiteOpenHelper(context, "red_delivery_status.db", null, 1) {
    
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE recipient_delivery_status (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                message_id TEXT NOT NULL,
                recipient_id TEXT NOT NULL,
                recipient_name TEXT NOT NULL,
                status TEXT NOT NULL DEFAULT 'SENT',
                delivered_at INTEGER,
                read_at INTEGER,
                created_at INTEGER NOT NULL,
                UNIQUE(message_id, recipient_id)
            )
        """)
        db.execSQL("CREATE INDEX idx_delivery_message ON recipient_delivery_status(message_id)")
        db.execSQL("CREATE INDEX idx_delivery_recipient ON recipient_delivery_status(recipient_id)")
    }
    
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Handle future upgrades here
    }
    
    fun saveDeliveryStatus(info: RecipientDeliveryInfo) {
        writableDatabase.insertWithOnConflict(
            "recipient_delivery_status",
            null,
            ContentValues().apply {
                put("message_id", info.messageId)
                put("recipient_id", info.recipientId)
                put("recipient_name", info.recipientName)
                put("status", info.status)
                put("delivered_at", info.deliveredAt)
                put("read_at", info.readAt)
                put("created_at", info.createdAt)
            },
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }
    
    fun updateStatus(messageId: String, recipientId: String, status: String) {
        val timestamp = System.currentTimeMillis()
        val values = ContentValues().apply {
            put("status", status)
            when (status) {
                "DELIVERED" -> put("delivered_at", timestamp)
                "READ" -> put("read_at", timestamp)
            }
        }
        writableDatabase.update(
            "recipient_delivery_status",
            values,
            "message_id = ? AND recipient_id = ?",
            arrayOf(messageId, recipientId)
        )
    }
    
    fun getMessageDeliveryStatus(messageId: String): List<RecipientDeliveryInfo> {
        val result = mutableListOf<RecipientDeliveryInfo>()
        readableDatabase.query(
            "recipient_delivery_status",
            null,
            "message_id = ?",
            arrayOf(messageId),
            null,
            null,
            "created_at ASC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += RecipientDeliveryInfo(
                    messageId = cursor.getString(cursor.getColumnIndexOrThrow("message_id")),
                    recipientId = cursor.getString(cursor.getColumnIndexOrThrow("recipient_id")),
                    recipientName = cursor.getString(cursor.getColumnIndexOrThrow("recipient_name")),
                    status = cursor.getString(cursor.getColumnIndexOrThrow("status")),
                    deliveredAt = cursor.getLong(cursor.getColumnIndexOrThrow("delivered_at")).takeIf { it > 0 },
                    readAt = cursor.getLong(cursor.getColumnIndexOrThrow("read_at")).takeIf { it > 0 },
                    createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at"))
                )
            }
        }
        return result
    }
    
    fun getDeliverySummary(messageId: String): DeliverySummary {
        val statuses = getMessageDeliveryStatus(messageId)
        return DeliverySummary(
            messageId = messageId,
            totalRecipients = statuses.size,
            deliveredCount = statuses.count { it.status == "DELIVERED" || it.status == "READ" },
            readCount = statuses.count { it.status == "READ" },
            sentCount = statuses.count { it.status == "SENT" },
            pendingCount = statuses.count { it.status == "PENDING" }
        )
    }
    
    fun deleteMessageDeliveryStatus(messageId: String) {
        writableDatabase.delete(
            "recipient_delivery_status",
            "message_id = ?",
            arrayOf(messageId)
        )
    }
}

data class DeliverySummary(
    val messageId: String,
    val totalRecipients: Int,
    val deliveredCount: Int,
    val readCount: Int,
    val sentCount: Int,
    val pendingCount: Int
)
