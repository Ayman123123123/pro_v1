package com.red.sovereign.core.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RedDao {
    // --- Messages & History ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocalHistory(history: LocalHistoryEntity)

    // مرتبة بثبات: حسب الوقت ثم المعرف لمنع قفزات عند وصول رسائل متأخرة (out-of-order)
    @Query("SELECT * FROM local_history WHERE conversationId = :convId ORDER BY createdAt ASC, id ASC")
    fun getLocalHistory(convId: String): Flow<List<LocalHistoryEntity>>

    @Query("UPDATE local_history SET status = :status WHERE id = :id")
    suspend fun updateMessageStatus(id: String, status: String)

    @Query("UPDATE local_history SET encryptedPlaintext = :plaintext WHERE id = :id")
    suspend fun updateLocalHistoryText(id: String, plaintext: ByteArray)

    // --- Conversations ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conversation: ConversationEntity)

    @Query("SELECT * FROM conversations WHERE archived = 0 ORDER BY pinned DESC, lastMessageTimestamp DESC")
    fun getActiveConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE archived = 1 ORDER BY pinned DESC, lastMessageTimestamp DESC")
    fun getArchivedConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations ORDER BY pinned DESC, lastMessageTimestamp DESC")
    fun getAllConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun getConversation(id: String): ConversationEntity?

    @Query("UPDATE conversations SET unreadCount = 0 WHERE id = :id")
    suspend fun clearUnread(id: String)

    @Query("UPDATE conversations SET unreadCount = :count WHERE id = :id")
    suspend fun setUnreadCount(id: String, count: Int)

    @Query("UPDATE conversations SET pinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: String, pinned: Boolean)

    @Query("UPDATE conversations SET archived = :archived WHERE id = :id")
    suspend fun setArchived(id: String, archived: Boolean)

    @Query("UPDATE conversations SET mutedUntil = :until WHERE id = :id")
    suspend fun setMutedUntil(id: String, until: Long)

    @Query("UPDATE conversations SET lastMessageText = :preview, lastMessageTimestamp = :ts, unreadCount = CASE WHEN :isIncoming = 1 THEN unreadCount + 1 ELSE unreadCount END WHERE id = :id")
    suspend fun updateConversationLast(id: String, preview: String, ts: Long, isIncoming: Int)

    // --- Contacts ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContacts(contacts: List<ContactEntity>)

    @Query("DELETE FROM contacts")
    suspend fun clearContacts()

    @Query("SELECT * FROM contacts WHERE isFriend = 1")
    fun getFriends(): Flow<List<ContactEntity>>

    // --- Groups ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroups(groups: List<GroupEntity>)

    @Query("SELECT * FROM groups ORDER BY createdAt DESC")
    fun getGroups(): Flow<List<GroupEntity>>

    // --- Call History ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCallLog(log: CallLogEntity)

    // إدراج دفعة في معاملة واحدة — أسرع بكثير من حلقة insert منفردة عند مزامنة السجل
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCallLogs(logs: List<CallLogEntity>)

    @Query("SELECT * FROM call_logs ORDER BY timestamp DESC")
    fun getCallLogs(): Flow<List<CallLogEntity>>

    @Query("DELETE FROM call_logs WHERE id = :id")
    suspend fun deleteCallLog(id: String)

    @Query("DELETE FROM call_logs")
    suspend fun clearCallLogs()

    // --- Stories ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStories(stories: List<StoryEntity>)

    @Query("SELECT * FROM stories WHERE expiresAt > :now ORDER BY timestamp DESC")
    fun getActiveStories(now: Long): Flow<List<StoryEntity>>

    /**
     * حذف القصص المنتهية فعليًا.
     *
     * `getActiveStories` يُخفي المنتهية بشرط `expiresAt > now` لكنه لا
     * يحذفها، فكانت الصفوف تتراكم بلا حد ويكبر ملف القاعدة إلى ما لا
     * نهاية. يستدعيه [com.red.sovereign.core.workers.StoryCleanupWorker]
     * دوريًا.
     */
    @Query("DELETE FROM stories WHERE expiresAt <= :now")
    suspend fun cleanupExpiredStories(now: Long): Int

    // --- Drafts ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveDraft(draft: DraftEntity)

    @Query("SELECT * FROM drafts WHERE conversationId = :convId")
    suspend fun getDraft(convId: String): DraftEntity?

    @Query("DELETE FROM drafts WHERE conversationId = :convId")
    suspend fun deleteDraft(convId: String)

    // --- Search ---
    /**
     * بحث احتياطي داخل محادثة واحدة.
     *
     * **`CAST` ليس زخرفًا.** العمود `encryptedPlaintext` من نوع
     * `ByteArray` ⇒ `BLOB` في SQLite، و`LIKE` على BLOB **لا يطابق
     * شيئًا أبدًا** — لا يخطئ بل يرجع صفر صفوف صامتًا. فكان هذا
     * الاستعلام يرجع قائمة فارغة دائمًا مهما كان النص (مقيس: أربع
     * عبارات موجودة فعلًا في البيانات، صفر نتيجة).
     *
     * **والأفضل منه [FtsSearchManager]**: هذا مسحٌ كامل للجدول بلا
     * فهرس، ولا يطبّع الحركات فلا تطابق «السلام» كلمة «السَّلام»،
     * ويفشل مع حمولات `RICH_TEXT` لأنها protobuf لا نصّ خام. يبقى هنا
     * للاحتياط حين يتعذّر بناء فهرس FTS.
     */
    @Query(
        "SELECT * FROM local_history WHERE conversationId = :convId " +
            "AND CAST(encryptedPlaintext AS TEXT) LIKE :query ESCAPE '\\' " +
            "ORDER BY createdAt DESC"
    )
    suspend fun searchMessages(convId: String, query: String): List<LocalHistoryEntity>

    /** وسائط محادثة (صور/فيديو/ملفات/صوت) — مرتبة بالأحدث أولاً. أساس معرض الوسائط. */
    @Query("SELECT * FROM local_history WHERE conversationId = :convId AND messageType IN ('IMAGE','VIDEO','FILE','AUDIO') ORDER BY createdAt DESC")
    fun mediaForConversation(convId: String): Flow<List<LocalHistoryEntity>>

    // --- Reactions (E2EE: emoji مخزّن محلياً بعد فك التشفير) ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReaction(reaction: MessageReactionEntity)

    @Query("DELETE FROM message_reactions WHERE messageId = :messageId AND senderId = :senderId")
    suspend fun deleteReaction(messageId: String, senderId: String)

    @Query("DELETE FROM message_reactions WHERE messageId = :messageId AND senderId = :senderId AND emoji = :emoji")
    suspend fun deleteReactionIfEmoji(messageId: String, senderId: String, emoji: String)

    @Query("SELECT * FROM message_reactions WHERE conversationId = :convId ORDER BY timestamp ASC")
    fun reactionsForConversation(convId: String): Flow<List<MessageReactionEntity>>

    @Query("SELECT * FROM message_reactions WHERE messageId = :messageId ORDER BY timestamp ASC")
    suspend fun reactionsForMessage(messageId: String): List<MessageReactionEntity>

    @Query("DELETE FROM message_reactions WHERE conversationId = :convId")
    suspend fun deleteReactionsByConversation(convId: String)

    @Query("DELETE FROM message_reactions WHERE messageId = :messageId")
    suspend fun deleteReactionsForMessage(messageId: String)

    @Query("SELECT COUNT(*) FROM local_history WHERE conversationId = :convId AND outgoing = 0 AND createdAt > :since")
    suspend fun countIncomingSince(convId: String, since: Long): Int

    @Query("UPDATE conversations SET unreadCount = 0 WHERE id = :convId")
    suspend fun clearUnread(convId: String)

    @Query("SELECT * FROM local_history WHERE id = :id LIMIT 1")
    suspend fun getLocalHistoryEntry(id: String): LocalHistoryEntity?

    // --- Delete ---
    @Query("DELETE FROM local_history WHERE id = :messageId")
    suspend fun deleteLocalHistory(messageId: String)

    @Query("DELETE FROM messages WHERE id = :messageId")
    suspend fun deleteMessage(messageId: String)

    @Query("DELETE FROM local_history WHERE conversationId = :convId")
    suspend fun deleteLocalHistoryByConversation(convId: String)

    @Query("DELETE FROM messages WHERE conversationId = :convId")
    suspend fun deleteMessagesByConversation(convId: String)

    @Query("DELETE FROM conversations WHERE id = :convId")
    suspend fun deleteConversationRow(convId: String)

    @Query("SELECT * FROM local_history WHERE encryptedPlaintext LIKE :query ORDER BY createdAt DESC")
    suspend fun searchAllMessages(query: String): List<LocalHistoryEntity>

    /** كل الرسائل الغنية المخزنة — لفحص مؤقت الاختفاء (expiresAt داخل حمولة RichMessage). */
    @Query("SELECT * FROM local_history WHERE messageType = 'RICH_TEXT'")
    suspend fun allRichHistory(): List<LocalHistoryEntity>
}
