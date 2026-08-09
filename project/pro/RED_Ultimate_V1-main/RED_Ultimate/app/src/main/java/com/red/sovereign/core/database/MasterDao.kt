package com.red.sovereign.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * RED Master DAO — Database access for messages, groups, calls, and status tracking.
 */
@Dao
interface MasterDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertMessage(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertGroup(group: GroupEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertCall(call: CallLogEntity)

    @Query("SELECT * FROM messages ORDER BY timestamp DESC")
    fun getMessages(): List<MessageEntity>

    @Query("SELECT status FROM messages WHERE id = :msgId")
    fun getMessageStatus(msgId: String): String?

    @Query("UPDATE messages SET status = :status WHERE id = :msgId")
    fun updateMessageStatus(msgId: String, status: String)

    @Query("SELECT * FROM groups ORDER BY createdAt DESC")
    fun getGroups(): List<GroupEntity>

    @Query("SELECT * FROM call_logs ORDER BY timestamp DESC")
    fun getCallLogs(): List<CallLogEntity>
}
