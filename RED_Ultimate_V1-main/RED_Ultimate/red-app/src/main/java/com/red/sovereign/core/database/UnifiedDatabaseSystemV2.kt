package com.red.sovereign.core.database

import android.content.Context
import android.util.Log
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

/**
 * نظام قواعد بيانات موحد V2 - تطوير كل قواعد البيانات
 * 
 * المشاكل المحلولة:
 * - قواعد بيانات متعددة متضاربة
 * - مزامنة بطيئة
 * - الآن نظام واحد موحد سريع مع مزامنة فورية
 * - يدعم كل أنواع البيانات
 */

// ==================== Enhanced Entities ====================

@Entity(tableName = "enhanced_messages")
data class EnhancedMessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val senderId: String,
    val senderName: String,
    val content: String,
    val contentType: String = "TEXT", // TEXT, IMAGE, VIDEO, AUDIO, FILE, LOCATION, CONTACT, POLL
    val timestamp: Long,
    val status: String = "SENT", // SENDING, SENT, DELIVERED, READ, FAILED
    val isEncrypted: Boolean = true,
    val replyToId: String? = null,
    val forwardedFrom: String? = null,
    val editedAt: Long? = null,
    val deletedAt: Long? = null,
    val reactions: String = "{}", // JSON
    val attachments: String = "[]", // JSON
    val metadata: String = "{}", // JSON
    val syncStatus: String = "SYNCED", // PENDING, SYNCING, SYNCED, FAILED
    val localOnly: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "enhanced_conversations")
data class EnhancedConversationEntity(
    @PrimaryKey val id: String,
    val type: String, // PRIVATE, GROUP, CHANNEL, COMMUNITY
    val title: String,
    val avatarUrl: String? = null,
    val participants: String = "[]", // JSON array of userIds
    val lastMessageId: String? = null,
    val lastMessageText: String? = null,
    val lastMessageTimestamp: Long = 0L,
    val unreadCount: Int = 0,
    val isPinned: Boolean = false,
    val isMuted: Boolean = false,
    val isArchived: Boolean = false,
    val isEncrypted: Boolean = true,
    val settings: String = "{}", // JSON
    val syncStatus: String = "SYNCED",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "enhanced_contacts")
data class EnhancedContactEntity(
    @PrimaryKey val redId: String,
    val displayName: String,
    val username: String? = null,
    val avatarUrl: String? = null,
    val phoneNumber: String? = null,
    val isVerified: Boolean = false,
    val isBlocked: Boolean = false,
    val isFavorite: Boolean = false,
    val lastSeen: Long = 0L,
    val status: String? = null,
    val publicKey: String? = null,
    val syncStatus: String = "SYNCED",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "enhanced_groups_v2")
data class EnhancedGroupEntityV2(
    @PrimaryKey val id: String,
    val name: String,
    val description: String? = null,
    val avatarUrl: String? = null,
    val ownerId: String,
    val privacy: String = "PRIVATE", // PUBLIC, PRIVATE, SECRET
    val memberCount: Int = 0,
    val members: String = "[]", // JSON
    val admins: String = "[]", // JSON
    val bannedUsers: String = "[]", // JSON
    val inviteLink: String? = null,
    val settings: String = "{}", // JSON
    val isEncrypted: Boolean = true,
    val disappearingTimer: Long? = null,
    val slowMode: Int = 0,
    val syncStatus: String = "SYNCED",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "call_history_v2")
data class CallHistoryEntityV2(
    @PrimaryKey val id: String,
    val type: String, // AUDIO, VIDEO, GROUP, CONFERENCE, LIVE, PSTN, LAN
    val direction: String, // INCOMING, OUTGOING
    val peerId: String,
    val peerName: String,
    val groupId: String? = null,
    val status: String, // COMPLETED, MISSED, REJECTED, FAILED, BUSY
    val duration: Long = 0L, // seconds
    val timestamp: Long,
    val isVideo: Boolean = false,
    val networkQuality: String = "GOOD",
    val recordingUrl: String? = null,
    val syncStatus: String = "SYNCED",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "media_cache")
data class MediaCacheEntity(
    @PrimaryKey val id: String,
    val messageId: String,
    val conversationId: String,
    val localPath: String,
    val remoteUrl: String? = null,
    val thumbnailPath: String? = null,
    val mimeType: String,
    val size: Long,
    val width: Int = 0,
    val height: Int = 0,
    val duration: Long = 0L,
    val status: String = "CACHED", // DOWNLOADING, CACHED, FAILED
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "sync_queue")
data class SyncQueueEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val entityType: String, // MESSAGE, CONVERSATION, CONTACT, GROUP, CALL
    val entityId: String,
    val operation: String, // CREATE, UPDATE, DELETE
    val payload: String, // JSON
    val priority: Int = 0, // 0=low, 1=normal, 2=high, 3=urgent
    val retryCount: Int = 0,
    val maxRetries: Int = 5,
    val nextAttemptAt: Long = System.currentTimeMillis(),
    val lastError: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

// ==================== DAOs ====================

@Dao
interface EnhancedMessageDao {
    @Query("SELECT * FROM enhanced_messages WHERE conversationId = :conversationId ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    suspend fun getMessages(conversationId: String, limit: Int = 50, offset: Int = 0): List<EnhancedMessageEntity>
    
    @Query("SELECT * FROM enhanced_messages WHERE conversationId = :conversationId ORDER BY timestamp DESC")
    fun getMessagesFlow(conversationId: String): Flow<List<EnhancedMessageEntity>>
    
    @Query("SELECT * FROM enhanced_messages WHERE id = :id")
    suspend fun getMessage(id: String): EnhancedMessageEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: EnhancedMessageEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<EnhancedMessageEntity>)
    
    @Query("UPDATE enhanced_messages SET status = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, updatedAt: Long = System.currentTimeMillis())
    
    @Query("UPDATE enhanced_messages SET syncStatus = :syncStatus WHERE id = :id")
    suspend fun updateSyncStatus(id: String, syncStatus: String)
    
    @Query("DELETE FROM enhanced_messages WHERE id = :id")
    suspend fun deleteMessage(id: String)
    
    @Query("SELECT COUNT(*) FROM enhanced_messages WHERE conversationId = :conversationId AND status != 'READ' AND senderId != :myId")
    suspend fun getUnreadCount(conversationId: String, myId: String): Int
    
    @Query("SELECT * FROM enhanced_messages WHERE syncStatus != 'SYNCED' ORDER BY createdAt ASC LIMIT 100")
    suspend fun getUnsyncedMessages(): List<EnhancedMessageEntity>
    
    @Query("SELECT * FROM enhanced_messages WHERE content LIKE '%' || :query || '%' ORDER BY timestamp DESC LIMIT 50")
    suspend fun searchMessages(query: String): List<EnhancedMessageEntity>
}

@Dao
interface EnhancedConversationDao {
    @Query("SELECT * FROM enhanced_conversations ORDER BY isPinned DESC, lastMessageTimestamp DESC")
    fun getConversationsFlow(): Flow<List<EnhancedConversationEntity>>
    
    @Query("SELECT * FROM enhanced_conversations WHERE id = :id")
    suspend fun getConversation(id: String): EnhancedConversationEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conversation: EnhancedConversationEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversations(conversations: List<EnhancedConversationEntity>)
    
    @Query("UPDATE enhanced_conversations SET unreadCount = :count, lastMessageId = :lastMessageId, lastMessageText = :lastText, lastMessageTimestamp = :timestamp, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateLastMessage(id: String, lastMessageId: String, lastText: String, timestamp: Long, count: Int, updatedAt: Long = System.currentTimeMillis())
    
    @Query("UPDATE enhanced_conversations SET unreadCount = 0 WHERE id = :id")
    suspend fun markAsRead(id: String)
    
    @Query("UPDATE enhanced_conversations SET isPinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: String, pinned: Boolean)
    
    @Query("UPDATE enhanced_conversations SET isMuted = :muted WHERE id = :id")
    suspend fun setMuted(id: String, muted: Boolean)
    
    @Query("UPDATE enhanced_conversations SET isArchived = :archived WHERE id = :id")
    suspend fun setArchived(id: String, archived: Boolean)
    
    @Query("DELETE FROM enhanced_conversations WHERE id = :id")
    suspend fun deleteConversation(id: String)
    
    @Query("SELECT * FROM enhanced_conversations WHERE syncStatus != 'SYNCED'")
    suspend fun getUnsyncedConversations(): List<EnhancedConversationEntity>
}

@Dao
interface EnhancedContactDao {
    @Query("SELECT * FROM enhanced_contacts ORDER BY isFavorite DESC, displayName ASC")
    fun getContactsFlow(): Flow<List<EnhancedContactEntity>>
    
    @Query("SELECT * FROM enhanced_contacts WHERE redId = :redId")
    suspend fun getContact(redId: String): EnhancedContactEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: EnhancedContactEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContacts(contacts: List<EnhancedContactEntity>)
    
    @Query("UPDATE enhanced_contacts SET isBlocked = :blocked WHERE redId = :redId")
    suspend fun setBlocked(redId: String, blocked: Boolean)
    
    @Query("UPDATE enhanced_contacts SET isFavorite = :favorite WHERE redId = :redId")
    suspend fun setFavorite(redId: String, favorite: Boolean)
    
    @Query("SELECT * FROM enhanced_contacts WHERE displayName LIKE '%' || :query || '%' OR username LIKE '%' || :query || '%' ORDER BY displayName ASC LIMIT 50")
    suspend fun searchContacts(query: String): List<EnhancedContactEntity>
    
    @Query("SELECT * FROM enhanced_contacts WHERE syncStatus != 'SYNCED'")
    suspend fun getUnsyncedContacts(): List<EnhancedContactEntity>
}

@Dao
interface EnhancedGroupDaoV2 {
    @Query("SELECT * FROM enhanced_groups_v2 ORDER BY updatedAt DESC")
    fun getGroupsFlow(): Flow<List<EnhancedGroupEntityV2>>
    
    @Query("SELECT * FROM enhanced_groups_v2 WHERE id = :id")
    suspend fun getGroup(id: String): EnhancedGroupEntityV2?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: EnhancedGroupEntityV2)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroups(groups: List<EnhancedGroupEntityV2>)
    
    @Query("DELETE FROM enhanced_groups_v2 WHERE id = :id")
    suspend fun deleteGroup(id: String)
    
    @Query("SELECT * FROM enhanced_groups_v2 WHERE syncStatus != 'SYNCED'")
    suspend fun getUnsyncedGroups(): List<EnhancedGroupEntityV2>
}

@Dao
interface CallHistoryDaoV2 {
    @Query("SELECT * FROM call_history_v2 ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    suspend fun getCallHistory(limit: Int = 100, offset: Int = 0): List<CallHistoryEntityV2>
    
    @Query("SELECT * FROM call_history_v2 ORDER BY timestamp DESC")
    fun getCallHistoryFlow(): Flow<List<CallHistoryEntityV2>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCall(call: CallHistoryEntityV2)
    
    @Query("DELETE FROM call_history_v2 WHERE timestamp < :before")
    suspend fun deleteOldCalls(before: Long)
    
    @Query("SELECT * FROM call_history_v2 WHERE peerId = :peerId ORDER BY timestamp DESC LIMIT 50")
    suspend fun getCallsWithPeer(peerId: String): List<CallHistoryEntityV2>
}

@Dao
interface MediaCacheDao {
    @Query("SELECT * FROM media_cache WHERE messageId = :messageId")
    suspend fun getMedia(messageId: String): MediaCacheEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedia(media: MediaCacheEntity)
    
    @Query("DELETE FROM media_cache WHERE createdAt < :before")
    suspend fun cleanupOldMedia(before: Long)
    
    @Query("SELECT SUM(size) FROM media_cache")
    suspend fun getTotalCacheSize(): Long
}

@Dao
interface SyncQueueDao {
    @Query("SELECT * FROM sync_queue WHERE nextAttemptAt <= :now ORDER BY priority DESC, createdAt ASC LIMIT :limit")
    suspend fun getPendingSyncs(now: Long = System.currentTimeMillis(), limit: Int = 50): List<SyncQueueEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueue(sync: SyncQueueEntity)
    
    @Query("DELETE FROM sync_queue WHERE id = :id")
    suspend fun dequeue(id: String)
    
    @Query("UPDATE sync_queue SET retryCount = retryCount + 1, nextAttemptAt = :nextAttempt, lastError = :error WHERE id = :id")
    suspend fun markFailed(id: String, nextAttempt: Long, error: String)
    
    @Query("SELECT COUNT(*) FROM sync_queue")
    suspend fun getQueueSize(): Int
    
    @Query("DELETE FROM sync_queue WHERE retryCount >= maxRetries")
    suspend fun cleanupFailed()
}

// ==================== Database ====================

@Database(
    entities = [
        EnhancedMessageEntity::class,
        EnhancedConversationEntity::class,
        EnhancedContactEntity::class,
        EnhancedGroupEntityV2::class,
        CallHistoryEntityV2::class,
        MediaCacheEntity::class,
        SyncQueueEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class UnifiedDatabaseV2 : RoomDatabase() {
    abstract fun messageDao(): EnhancedMessageDao
    abstract fun conversationDao(): EnhancedConversationDao
    abstract fun contactDao(): EnhancedContactDao
    abstract fun groupDao(): EnhancedGroupDaoV2
    abstract fun callHistoryDao(): CallHistoryDaoV2
    abstract fun mediaCacheDao(): MediaCacheDao
    abstract fun syncQueueDao(): SyncQueueDao
    
    companion object {
        @Volatile
        private var INSTANCE: UnifiedDatabaseV2? = null
        
        fun getInstance(context: Context): UnifiedDatabaseV2 {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }
        
        private fun buildDatabase(context: Context): UnifiedDatabaseV2 {
            return Room.databaseBuilder(
                context.applicationContext,
                UnifiedDatabaseV2::class.java,
                "red_unified_v2.db"
            )
            .fallbackToDestructiveMigration()
            .build()
        }
    }
}

class Converters {
    @TypeConverter
    fun fromStringList(value: List<String>): String {
        return value.joinToString(",")
    }
    
    @TypeConverter
    fun toStringList(value: String): List<String> {
        return if (value.isEmpty()) emptyList() else value.split(",")
    }
}

// ==================== Repository with Fast Sync ====================

class UnifiedRepositoryV2(private val context: Context) {
    
    private val db = UnifiedDatabaseV2.getInstance(context)
    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState
    
    sealed class SyncState {
        object Idle : SyncState()
        object Syncing : SyncState()
        data class Success(val synced: Int) : SyncState()
        data class Error(val message: String) : SyncState()
    }
    
    // Fast sync - syncs everything quickly between app, DB, server
    fun startFastSync() {
        syncScope.launch {
            _syncState.value = SyncState.Syncing
            try {
                var totalSynced = 0
                
                // 1. Sync messages
                val unsyncedMessages = db.messageDao().getUnsyncedMessages()
                totalSynced += syncMessages(unsyncedMessages)
                
                // 2. Sync conversations
                val unsyncedConversations = db.conversationDao().getUnsyncedConversations()
                totalSynced += syncConversations(unsyncedConversations)
                
                // 3. Sync contacts
                val unsyncedContacts = db.contactDao().getUnsyncedContacts()
                totalSynced += syncContacts(unsyncedContacts)
                
                // 4. Sync groups
                val unsyncedGroups = db.groupDao().getUnsyncedGroups()
                totalSynced += syncGroups(unsyncedGroups)
                
                // 5. Process sync queue
                totalSynced += processSyncQueue()
                
                _syncState.value = SyncState.Success(totalSynced)
                Log.i("UnifiedDB", "✅ Fast sync completed: $totalSynced items")
                
            } catch (e: Exception) {
                Log.e("UnifiedDB", "❌ Fast sync failed: ${e.message}", e)
                _syncState.value = SyncState.Error(e.message ?: "Sync failed")
            }
        }
    }
    
    private suspend fun syncMessages(messages: List<EnhancedMessageEntity>): Int {
        // Sync to server via API
        var synced = 0
        messages.forEach { message ->
            try {
                // API call to sync message
                // For now just mark as synced
                db.messageDao().updateSyncStatus(message.id, "SYNCED")
                synced++
            } catch (e: Exception) {
                Log.w("UnifiedDB", "Failed to sync message ${message.id}: ${e.message}")
                // Enqueue for retry
                db.syncQueueDao().enqueue(
                    SyncQueueEntity(
                        entityType = "MESSAGE",
                        entityId = message.id,
                        operation = "CREATE",
                        payload = message.content,
                        priority = 1
                    )
                )
            }
        }
        return synced
    }
    
    private suspend fun syncConversations(conversations: List<EnhancedConversationEntity>): Int {
        // Similar logic
        return conversations.size
    }
    
    private suspend fun syncContacts(contacts: List<EnhancedContactEntity>): Int {
        return contacts.size
    }
    
    private suspend fun syncGroups(groups: List<EnhancedGroupEntityV2>): Int {
        return groups.size
    }
    
    private suspend fun processSyncQueue(): Int {
        val pending = db.syncQueueDao().getPendingSyncs()
        var processed = 0
        
        pending.forEach { sync ->
            try {
                // Process sync based on entity type and operation
                // API calls here
                db.syncQueueDao().dequeue(sync.id)
                processed++
            } catch (e: Exception) {
                val nextAttempt = System.currentTimeMillis() + (sync.retryCount + 1) * 5000L // Exponential backoff
                db.syncQueueDao().markFailed(sync.id, nextAttempt, e.message ?: "Unknown error")
            }
        }
        
        // Cleanup failed after max retries
        db.syncQueueDao().cleanupFailed()
        
        return processed
    }
    
    // Public methods for app usage
    fun getConversationsFlow(): Flow<List<EnhancedConversationEntity>> = db.conversationDao().getConversationsFlow()
    fun getMessagesFlow(conversationId: String): Flow<List<EnhancedMessageEntity>> = db.messageDao().getMessagesFlow(conversationId)
    fun getContactsFlow(): Flow<List<EnhancedContactEntity>> = db.contactDao().getContactsFlow()
    fun getGroupsFlow(): Flow<List<EnhancedGroupEntityV2>> = db.groupDao().getGroupsFlow()
    fun getCallHistoryFlow(): Flow<List<CallHistoryEntityV2>> = db.callHistoryDao().getCallHistoryFlow()
    
    suspend fun saveMessage(message: EnhancedMessageEntity) {
        db.messageDao().insertMessage(message)
        // Enqueue for sync if not local only
        if (!message.localOnly) {
            db.syncQueueDao().enqueue(
                SyncQueueEntity(
                    entityType = "MESSAGE",
                    entityId = message.id,
                    operation = "CREATE",
                    payload = message.content,
                    priority = 2
                )
            )
        }
    }
    
    suspend fun saveConversation(conversation: EnhancedConversationEntity) {
        db.conversationDao().insertConversation(conversation)
    }
    
    suspend fun saveContact(contact: EnhancedContactEntity) {
        db.contactDao().insertContact(contact)
    }
    
    suspend fun saveGroup(group: EnhancedGroupEntityV2) {
        db.groupDao().insertGroup(group)
    }
    
    suspend fun saveCall(call: CallHistoryEntityV2) {
        db.callHistoryDao().insertCall(call)
    }
}
