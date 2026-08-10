package com.red.sovereign.core.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * 1. جدول الرسائل (System C)
 * يدعم: التوصيل المضمون، الردود، التعديل، الحذف للجميع
 */
@Entity(tableName = "messages", indices = [Index("uuid", unique = true), Index("conversationId")])
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String,            // UUID v7 (Time-ordered)
    val conversationId: String,
    val senderId: String,
    val type: String,            // TEXT, IMAGE, VIDEO, VOICE, FILE, CALL_LOG
    val content: String,         // النص أو رابط MinIO
    val status: String,          // SENDING, SENT, DELIVERED, READ, FAILED
    val timestamp: Long,
    val sequenceNumber: Long,    // حرج لمزامنة الفجوات
    val replyToId: String? = null,
    val isEdited: Boolean = false,
    val metadata: String? = null // حجم الملف، مدة الصوت، إلخ
)

/**
 * 2. جدول المجموعات والأعضاء
 */
@Entity(tableName = "groups")
data class GroupEntity(
    @PrimaryKey val groupId: String,
    val name: String,
    val avatarUrl: String?,
    val ownerId: String,
    val myRole: String           // OWNER, ADMIN, MEMBER
)

/**
 * 3. جدول سجل المكالمات الموحد (System A & B)
 */
@Entity(tableName = "call_logs")
data class CallLogEntity(
    @PrimaryKey val id: String,
    val remoteId: String,        // المعرف السيادي أو رقم GSM
    val type: String,            // VOIP_AUDIO, VOIP_VIDEO, CONFERENCE, LIVE, PSTN
    val direction: String,       // INCOMING, OUTGOING, MISSED
    val timestamp: Long,
    val duration: Long,
    val dinstarSlot: Int? = null // إذا كانت مكالمة عبر DINSTAR
)

@Dao
interface RedDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(msg: MessageEntity)

    @Query("SELECT * FROM messages WHERE conversationId = :cId ORDER BY sequenceNumber ASC")
    fun getMessages(cId: String): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: GroupEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCall(call: CallLogEntity)
}

@Database(entities = [MessageEntity::class, GroupEntity::class, CallLogEntity::class], version = 1)
abstract class RedMasterDatabase : RoomDatabase() {
    abstract fun dao(): RedDao
}
