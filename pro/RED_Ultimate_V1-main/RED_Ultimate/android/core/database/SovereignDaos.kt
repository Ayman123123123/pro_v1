package com.red.sovereign.core.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * 🗄️ YOUNES Sovereign DAOs — كل واجهات الوصول للبيانات
 */

// ════════════════════════════════════════════════════
// 💬 Master DAO — الرسائل والمحادثات
// ════════════════════════════════════════════════════

@Dao
interface MasterDao {
    // ─── المحادثات ───
    @Query("SELECT * FROM conversations ORDER BY isPinned DESC, lastMessageTimestamp DESC")
    fun getAllConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE isGroup = 0 ORDER BY isPinned DESC, lastMessageTimestamp DESC")
    fun getPrivateConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE isGroup = 1 ORDER BY isPinned DESC, lastMessageTimestamp DESC")
    fun getGroupConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun getConversation(id: String): ConversationEntity?

    @Query("SELECT * FROM conversations WHERE unreadCount > 0")
    fun getUnreadConversations(): Flow<List<ConversationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conv: ConversationEntity)

    @Update
    suspend fun updateConversation(conv: ConversationEntity)

    @Query("UPDATE conversations SET unreadCount = 0 WHERE id = :id")
    suspend fun markConversationRead(id: String)

    @Query("UPDATE conversations SET isPinned = :pinned WHERE id = :id")
    suspend fun togglePin(id: String, pinned: Boolean)

    @Query("UPDATE conversations SET isMuted = :muted WHERE id = :id")
    suspend fun toggleMute(id: String, muted: Boolean)

    @Query("UPDATE conversations SET draftText = :draft WHERE id = :id")
    suspend fun updateDraft(id: String, draft: String?)

    @Delete
    suspend fun deleteConversation(conv: ConversationEntity)

    // ─── الرسائل ───
    @Query("SELECT * FROM messages WHERE conversationId = :convId ORDER BY timestamp ASC")
    fun getMessages(convId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :convId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentMessages(convId: String, limit: Int = 50): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun getMessage(id: String): MessageEntity?

    @Query("SELECT COUNT(*) FROM messages WHERE conversationId = :convId AND status = 'SENT'")
    suspend fun getUnsentCount(convId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(msg: MessageEntity)

    @Update
    suspend fun updateMessage(msg: MessageEntity)

    @Query("UPDATE messages SET status = :status WHERE id = :id")
    suspend fun updateMessageStatus(id: String, status: String)

    @Query("UPDATE messages SET isDeletedForMe = 1 WHERE id = :id")
    suspend fun deleteMessageForMe(id: String)

    @Query("UPDATE messages SET isDeletedForEveryone = 1 WHERE id = :id")
    suspend fun deleteMessageForEveryone(id: String)

    @Query("DELETE FROM messages WHERE conversationId = :convId")
    suspend fun deleteAllMessages(convId: String)

    // ─── مرفقات الوسائط ───
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedia(media: MediaAttachmentEntity)

    @Query("SELECT * FROM media_attachments WHERE messageId = :messageId")
    suspend fun getMediaForMessage(messageId: String): List<MediaAttachmentEntity>

    @Query("UPDATE media_attachments SET localPath = :path, isDownloaded = 1 WHERE id = :id")
    suspend fun markMediaDownloaded(id: String, path: String)

    @Query("UPDATE media_attachments SET uploadProgress = :progress WHERE id = :id")
    suspend fun updateUploadProgress(id: String, progress: Float)

    // ─── تفاعلات الرسائل ───
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReaction(reaction: MessageReactionEntity)

    @Query("SELECT * FROM reactions WHERE messageId = :messageId")
    suspend fun getReactions(messageId: String): List<MessageReactionEntity>

    @Query("DELETE FROM reactions WHERE messageId = :messageId AND userId = :userId")
    suspend fun removeReaction(messageId: String, userId: String)

    // ─── رسائل المسودة ───
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveDraft(draft: DraftMessageEntity)

    @Query("SELECT * FROM draft_messages WHERE conversationId = :convId")
    suspend fun getDraft(convId: String): DraftMessageEntity?

    @Query("DELETE FROM draft_messages WHERE conversationId = :convId")
    suspend fun clearDraft(convId: String)

    // ─── البحث ───
    @Query("SELECT * FROM conversations WHERE remoteName LIKE '%' || :query || '%' ORDER BY lastMessageTimestamp DESC")
    fun searchConversations(query: String): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM messages WHERE text LIKE '%' || :query || '%' AND isDeletedForMe = 0 ORDER BY timestamp DESC LIMIT 50")
    suspend fun searchMessages(query: String): List<MessageEntity>

    // ─── الملف الشخصي ───
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProfile(profile: UserProfileEntity)

    @Query("SELECT * FROM user_profile LIMIT 1")
    suspend fun getProfile(): UserProfileEntity?

    @Query("SELECT * FROM user_profile LIMIT 1")
    fun getProfileFlow(): Flow<UserProfileEntity?>
}

// ════════════════════════════════════════════════════
// 📖 القصص DAO
// ════════════════════════════════════════════════════

@Dao
interface StoryDao {
    @Query("SELECT * FROM stories WHERE expiresAt > :now ORDER BY timestamp DESC")
    fun getActiveStories(now: Long = System.currentTimeMillis()): Flow<List<StoryEntity>>

    @Query("SELECT * FROM stories WHERE userId = :userId AND expiresAt > :now ORDER BY timestamp ASC")
    suspend fun getUserStories(userId: String, now: Long = System.currentTimeMillis()): List<StoryEntity>

    @Query("SELECT * FROM stories WHERE isMyStory = 1 AND expiresAt > :now ORDER BY timestamp DESC")
    fun getMyStories(now: Long = System.currentTimeMillis()): Flow<List<StoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStory(story: StoryEntity)

    @Query("UPDATE stories SET viewCount = viewCount + 1 WHERE id = :id")
    suspend fun incrementViewCount(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertView(view: StoryViewEntity)

    @Query("SELECT COUNT(*) FROM story_views WHERE storyId = :storyId")
    suspend fun getViewCount(storyId: String): Int

    @Query("DELETE FROM stories WHERE expiresAt < :now")
    suspend fun deleteExpiredStories(now: Long = System.currentTimeMillis())

    @Delete
    suspend fun deleteStory(story: StoryEntity)
}

// ════════════════════════════════════════════════════
// 📞 سجل المكالمات DAO
// ════════════════════════════════════════════════════

@Dao
interface CallLogDao {
    @Query("SELECT * FROM call_logs ORDER BY timestamp DESC")
    fun getAllCalls(): Flow<List<CallLogEntity>>

    @Query("SELECT * FROM call_logs WHERE callType = :type ORDER BY timestamp DESC")
    fun getCallsByType(type: String): Flow<List<CallLogEntity>>

    @Query("SELECT * FROM call_logs WHERE callType IN ('VOIP_AUDIO', 'VOIP_VIDEO') ORDER BY timestamp DESC")
    fun getVoipCalls(): Flow<List<CallLogEntity>>

    @Query("SELECT * FROM call_logs WHERE callType = 'PSTN_DINSTAR' ORDER BY timestamp DESC")
    fun getPstnCalls(): Flow<List<CallLogEntity>>

    @Query("SELECT * FROM call_logs WHERE callType = 'CONFERENCE' ORDER BY timestamp DESC")
    fun getConferenceCalls(): Flow<List<CallLogEntity>>

    @Query("SELECT * FROM call_logs WHERE direction = 'INCOMING' AND status = 'MISSED' ORDER BY timestamp DESC")
    fun getMissedCalls(): Flow<List<CallLogEntity>>

    @Query("SELECT COUNT(*) FROM call_logs WHERE direction = 'INCOMING' AND status = 'MISSED'")
    suspend fun getMissedCallCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCall(call: CallLogEntity)

    @Update
    suspend fun updateCall(call: CallLogEntity)

    @Query("DELETE FROM call_logs WHERE timestamp < :beforeTimestamp")
    suspend fun deleteOldCalls(beforeTimestamp: Long)

    @Query("DELETE FROM call_logs")
    suspend fun clearAllCalls()
}

// ════════════════════════════════════════════════════
// 👥 جهات الاتصال DAO
// ════════════════════════════════════════════════════

@Dao
interface ContactDao {
    @Query("SELECT * FROM contacts WHERE isBlocked = 0 ORDER BY isPinned DESC, name ASC")
    fun getAllContacts(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts WHERE isOnline = 1 ORDER BY name ASC")
    fun getOnlineContacts(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts WHERE name LIKE '%' || :query || '%' OR username LIKE '%' || :query || '%'")
    fun searchContacts(query: String): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts WHERE userId = :userId")
    suspend fun getContact(userId: String): ContactEntity?

    @Query("SELECT * FROM contacts WHERE redId = :redId")
    suspend fun getContactByRedId(redId: String): ContactEntity?

    @Query("SELECT * FROM contacts WHERE yemeniPhoneNumber = :phone")
    suspend fun getContactByPhone(phone: String): ContactEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: ContactEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContacts(contacts: List<ContactEntity>)

    @Query("UPDATE contacts SET isBlocked = :blocked WHERE userId = :userId")
    suspend fun toggleBlock(userId: String, blocked: Boolean)

    @Query("UPDATE contacts SET isOnline = 1, statusType = :statusType, statusText = :statusText WHERE userId = :userId")
    suspend fun updateOnlineStatus(userId: String, statusType: String, statusText: String?)

    @Query("UPDATE contacts SET isOnline = 0, statusType = 'OFFLINE' WHERE userId = :userId")
    suspend fun setOffline(userId: String)

    @Delete
    suspend fun deleteContact(contact: ContactEntity)

    @Query("SELECT COUNT(*) FROM contacts WHERE isBlocked = 0")
    suspend fun getContactCount(): Int
}

// ════════════════════════════════════════════════════
// 👥 المجموعات DAO
// ════════════════════════════════════════════════════

@Dao
interface GroupDao {
    @Query("SELECT * FROM groups ORDER BY isPinned DESC, createdAt DESC")
    fun getAllGroups(): Flow<List<GroupEntity>>

    @Query("SELECT * FROM groups WHERE id = :groupId")
    suspend fun getGroup(groupId: String): GroupEntity?

    @Query("SELECT * FROM group_members WHERE groupId = :groupId ORDER BY role ASC, name ASC")
    fun getGroupMembers(groupId: String): Flow<List<GroupMemberEntity>>

    @Query("SELECT * FROM group_members WHERE groupId = :groupId AND role = 'OWNER'")
    suspend fun getGroupOwner(groupId: String): GroupMemberEntity?

    @Query("SELECT COUNT(*) FROM group_members WHERE groupId = :groupId")
    suspend fun getMemberCount(groupId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: GroupEntity)

    @Update
    suspend fun updateGroup(group: GroupEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMember(member: GroupMemberEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMembers(members: List<GroupMemberEntity>)

    @Query("UPDATE group_members SET role = :role WHERE groupId = :groupId AND userId = :userId")
    suspend fun updateMemberRole(groupId: String, userId: String, role: String)

    @Query("DELETE FROM group_members WHERE groupId = :groupId AND userId = :userId")
    suspend fun removeMember(groupId: String, userId: String)

    @Delete
    suspend fun deleteGroup(group: GroupEntity)
}

// ════════════════════════════════════════════════════
// 🔔 الإشعارات DAO
// ════════════════════════════════════════════════════

@Dao
interface NotificationDao {
    @Query("SELECT * FROM notifications ORDER BY timestamp DESC")
    fun getAllNotifications(): Flow<List<NotificationEntity>>

    @Query("SELECT * FROM notifications WHERE isRead = 0 ORDER BY timestamp DESC")
    fun getUnreadNotifications(): Flow<List<NotificationEntity>>

    @Query("SELECT * FROM notifications WHERE type = :type ORDER BY timestamp DESC")
    fun getNotificationsByType(type: String): Flow<List<NotificationEntity>>

    @Query("SELECT COUNT(*) FROM notifications WHERE isRead = 0")
    suspend fun getUnreadCount(): Int

    @Query("SELECT COUNT(*) FROM notifications WHERE isRead = 0")
    fun getUnreadCountFlow(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: NotificationEntity)

    @Query("UPDATE notifications SET isRead = 1 WHERE id = :id")
    suspend fun markAsRead(id: String)

    @Query("UPDATE notifications SET isRead = 1")
    suspend fun markAllAsRead()

    @Delete
    suspend fun deleteNotification(notification: NotificationEntity)

    @Query("DELETE FROM notifications WHERE timestamp < :beforeTimestamp")
    suspend fun deleteOldNotifications(beforeTimestamp: Long)

    @Query("DELETE FROM notifications")
    suspend fun clearAll()
}

// ════════════════════════════════════════════════════
// 🔒 الخصوصية DAO
// ════════════════════════════════════════════════════

@Dao
interface PrivacyDao {
    @Query("SELECT * FROM privacy_settings LIMIT 1")
    suspend fun getSettings(): PrivacySettingsEntity?

    @Query("SELECT * FROM privacy_settings LIMIT 1")
    fun getSettingsFlow(): Flow<PrivacySettingsEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSettings(settings: PrivacySettingsEntity)

    @Query("UPDATE privacy_settings SET lastSeen = :value WHERE id = 1")
    suspend fun updateLastSeen(value: String)

    @Query("UPDATE privacy_settings SET profilePhoto = :value WHERE id = 1")
    suspend fun updateProfilePhoto(value: String)

    @Query("UPDATE privacy_settings SET status = :value WHERE id = 1")
    suspend fun updateStatus(value: String)

    @Query("UPDATE privacy_settings SET readReceipts = :value WHERE id = 1")
    suspend fun updateReadReceipts(value: String)

    @Query("UPDATE privacy_settings SET calls = :value WHERE id = 1")
    suspend fun updateCalls(value: String)

    @Query("UPDATE privacy_settings SET liveLocation = :value WHERE id = 1")
    suspend fun updateLiveLocation(value: String)
}

// ════════════════════════════════════════════════════
// 📡 Dinstar Gateway DAO — لقطات المنافذ
// ════════════════════════════════════════════════════

@Dao
interface DinstarDao {
    @Query("SELECT * FROM dinstar_port_snapshots ORDER BY portIndex ASC")
    fun getAllPorts(): Flow<List<DinstarPortSnapshotEntity>>

    @Query("SELECT * FROM dinstar_port_snapshots WHERE registrationState = 'REGISTERED' AND callState = 'IDLE' ORDER BY signalPercent DESC")
    fun getAvailablePorts(): Flow<List<DinstarPortSnapshotEntity>>

    @Query("SELECT * FROM dinstar_port_snapshots WHERE isHealthy = 1 ORDER BY signalPercent DESC")
    fun getHealthyPorts(): Flow<List<DinstarPortSnapshotEntity>>

    @Query("SELECT * FROM dinstar_port_snapshots WHERE portIndex = :port")
    suspend fun getPort(port: Int): DinstarPortSnapshotEntity?

    @Query("SELECT AVG(signalPercent) FROM dinstar_port_snapshots WHERE registrationState = 'REGISTERED'")
    suspend fun getAverageSignal(): Float?

    @Query("SELECT COUNT(*) FROM dinstar_port_snapshots WHERE registrationState = 'REGISTERED'")
    suspend fun getRegisteredCount(): Int

    @Query("SELECT COUNT(*) FROM dinstar_port_snapshots WHERE callState = 'ACTIVE'")
    suspend fun getActiveCallCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPort(port: DinstarPortSnapshotEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPorts(ports: List<DinstarPortSnapshotEntity>)

    @Query("DELETE FROM dinstar_port_snapshots")
    suspend fun clearAll()
}
