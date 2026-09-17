package com.red.sovereign.core.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaUploadDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(e: MediaUploadEntity)

    @Query("SELECT * FROM media_uploads WHERE status IN ('PENDING','FAILED') AND nextAttemptAt <= :now ORDER BY nextAttemptAt ASC LIMIT 10")
    suspend fun due(now: Long): List<MediaUploadEntity>

    @Query("SELECT * FROM media_uploads WHERE messageId = :id LIMIT 1")
    suspend fun byId(id: String): MediaUploadEntity?

    @Query("UPDATE media_uploads SET status=:s, retryCount=:r, nextAttemptAt=:n, objectKey=:k, url=:u WHERE messageId=:id")
    suspend fun mark(id: String, s: String, r: Int, n: Long, k: String?, u: String?)

    @Query("DELETE FROM media_uploads WHERE messageId=:id")
    suspend fun delete(id: String)

    /** حذف كل عمليات رفع محادثة — يُستدعى ضمن حذف المحادثة الذري. */
    @Query("DELETE FROM media_uploads WHERE conversationId = :convId")
    suspend fun deleteByConversation(convId: String): Int
}

@Dao
@TypeConverters(RedTypeConverters::class)
interface RedDao {
    // --- Messages & History ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocalHistory(history: LocalHistoryEntity)

    // مرتبة بثبات: حسب الوقت ثم المعرف لمنع قفزات عند وصول رسائل متأخرة (out-of-order)
    @Query("SELECT * FROM local_history WHERE conversationId = :convId ORDER BY createdAt ASC, id ASC")
    fun getLocalHistory(convId: String): Flow<List<LocalHistoryEntity>>

    // ── Paging3 لسجل المحادثة (2026-09-10): صفحات LIMIT/OFFSET بدل تحميل
    // السجل كاملًا في الذاكرة. يغذيها ChatHistoryPagingSource عبر chatHistoryPager.
    @Query("SELECT * FROM local_history WHERE conversationId = :convId ORDER BY createdAt DESC, id DESC LIMIT :limit OFFSET :offset")
    suspend fun getLocalHistoryPage(convId: String, limit: Int, offset: Int): List<LocalHistoryEntity>

    @Query("SELECT COUNT(*) FROM local_history WHERE conversationId = :convId")
    suspend fun countLocalHistory(convId: String): Int

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

    /** جلب محادثة عبر معرف الطرف — كان يستلزم تحميل كل المحادثات وفلترتها في الذاكرة. */
    @Query("SELECT * FROM conversations WHERE peerId = :peerId LIMIT 1")
    suspend fun getConversationByPeerId(peerId: String): ConversationEntity?

    /** مجموع غير المقروء (غير المؤرشف) لشارة التطبيق — تجميع SQL بدل جمع Flow في الذاكرة. */
    @Query("SELECT COALESCE(SUM(unreadCount), 0) FROM conversations WHERE archived = 0")
    suspend fun totalUnreadCount(): Int

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

    /**
     * لمس المحادثة ذريًا (قراءة-تعديل-كتابة في معاملة واحدة):
     * إنشاء أو تحديث آخر رسالة + عداد غير المقروء دون سباق بين خيطين.
     */
    @Transaction
    suspend fun touchConversation(conversationId: String, peerId: String, preview: String, ts: Long, isIncoming: Boolean) {
        val existing = getConversation(conversationId)
        if (existing != null) {
            updateConversationLast(conversationId, preview, ts, if (isIncoming) 1 else 0)
        } else {
            insertConversation(
                ConversationEntity(
                    id = conversationId, peerId = peerId,
                    lastMessageText = preview, lastMessageTimestamp = ts,
                    unreadCount = if (isIncoming) 1 else 0
                )
            )
        }
    }

    /**
     * حفظ رسالة واردة + لمس المحادثة ذريًا: الرسالة وصف المحادثة
     * في نفس المعاملة — لا رسالة بلا تحديث قائمة، ولا عداد مكسور عند القتل.
     */
    @Transaction
    suspend fun insertMessageAndTouchConversation(
        message: MessageEntity,
        peerId: String,
        preview: String,
        ts: Long,
        isIncoming: Boolean
    ) {
        insertMessage(message)
        touchConversation(message.conversationId, peerId, preview, ts, isIncoming)
    }

    /**
     * حذف محادثة كاملة ذريًا: السجل + الرسائل + التفاعلات + صف المحادثة —
     * فشل منتصف الطريق سابقًا كان يترك بيانات يتيمة.
     */
    @Transaction
    suspend fun deleteConversationFull(convId: String) {
        deleteLocalHistoryByConversation(convId)
        deleteMessagesByConversation(convId)
        deleteReactionsByConversation(convId)
        deleteConversationRow(convId)
    }

    // --- Contacts ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContacts(contacts: List<ContactEntity>)

    @Query("DELETE FROM contacts")
    suspend fun clearContacts()

    @Query("SELECT * FROM contacts WHERE isFriend = 1")
    fun getFriends(): Flow<List<ContactEntity>>

    /** جهة واحدة بالمعرف — كان يستلزم فلترة getFriends() كاملة في الذاكرة. */
    @Query("SELECT * FROM contacts WHERE redId = :redId LIMIT 1")
    suspend fun getContactById(redId: String): ContactEntity?

    /**
     * بحث جهات بحد SQL صريح. النمط مُهرَّب مسبقًا عبر [likeContainsPattern]
     * (المكان الوحيد للتهريب) مع `ESCAPE '\'` هنا.
     */
    @Query(
        "SELECT * FROM contacts WHERE username LIKE :query ESCAPE '\\' " +
            "OR displayName LIKE :query ESCAPE '\\' ORDER BY displayName ASC LIMIT :limit"
    )
    suspend fun searchContacts(query: String, limit: Int): List<ContactEntity>

    @Query("UPDATE contacts SET isBlocked = :blocked WHERE redId = :redId")
    suspend fun setBlocked(redId: String, blocked: Boolean)

    /**
     * استبدال ذري لجهات الاتصال: مسح + إدراج في معاملة واحدة —
     * عطل بينهما سابقًا كان يترك جدولًا فارغًا.
     */
    @Transaction
    suspend fun replaceContactsAtomic(contacts: List<ContactEntity>) {
        clearContacts()
        if (contacts.isNotEmpty()) insertContacts(contacts)
    }

    // --- Groups ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroups(groups: List<GroupEntity>)

    /** مجموعة واحدة بالمعرف — كان يستلزم فلترة getGroups() كاملة. */
    @Query("SELECT * FROM groups WHERE id = :id LIMIT 1")
    suspend fun getGroupById(id: String): GroupEntity?

    @Query("SELECT * FROM groups ORDER BY createdAt DESC")
    fun getGroups(): Flow<List<GroupEntity>>

    // ── Paging للمجموعات: صفحات LIMIT/OFFSET بدل تحميل كل الصفوف في الذاكرة.
    // getGroups() القديمة تبقى للتوافق (كاش offline في GroupViewModel).
    // TODO: استهلاكها من GroupViewModel/UI عند نمو القوائم (تحميل تدريجي).
    @Query("SELECT * FROM groups ORDER BY createdAt DESC LIMIT :limit OFFSET :offset")
    suspend fun getGroupsPage(limit: Int, offset: Int): List<GroupEntity>

    @Query("SELECT COUNT(*) FROM groups")
    suspend fun countGroups(): Int

    // --- Call History ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCallLog(log: CallLogEntity)

    // إدراج دفعة في معاملة واحدة — أسرع بكثير من حلقة insert منفردة عند مزامنة السجل
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCallLogs(logs: List<CallLogEntity>)

    @Query("SELECT * FROM call_logs ORDER BY timestamp DESC")
    fun getCallLogs(): Flow<List<CallLogEntity>>

    // ── Paging لسجل المكالمات: صفحات LIMIT/OFFSET بدل تحميل آلاف السجلات
    // في الذاكرة دفعة واحدة (يتفوق على واتساب في القوائم الطويلة).
    @Query("SELECT * FROM call_logs ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    suspend fun getCallLogsPage(limit: Int, offset: Int): List<CallLogEntity>

    @Query("SELECT COUNT(*) FROM call_logs")
    suspend fun countCallLogs(): Int

    /** عدد المكالمات الفائتة لشارة/فلتر الفائت — كان يستلزم تحميل كل السجل. */
    @Query("SELECT COUNT(*) FROM call_logs WHERE status = 'MISSED'")
    suspend fun countMissedCalls(): Int

    @Query("DELETE FROM call_logs WHERE id = :id")
    suspend fun deleteCallLog(id: String)

    @Query("DELETE FROM call_logs WHERE id IN (:ids)")
    suspend fun deleteCallLogs(ids: List<String>)

    @Query("DELETE FROM call_logs")
    suspend fun clearCallLogs()

    /** حذف السجلات الأقدم من حد الاحتفاظ (CALL_HISTORY_RETENTION) — يُبقي الأحدث فقط. */
    @Query("DELETE FROM call_logs WHERE timestamp < :cutoff")
    suspend fun deleteCallLogsOlderThan(cutoff: Long): Int

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
            // الفهرس المركب (conversationId, createdAt) يقيّد المسح بالمحادثة،
            // وLIMIT صريح يمنع إغراق الذاكرة في المحادثات الطويلة.
            "ORDER BY createdAt DESC LIMIT :limit"
    )
    suspend fun searchMessages(convId: String, query: String, limit: Int): List<LocalHistoryEntity>

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

    /** Phase 0 (2026-09-16): شريط الردود — ThreadRepliesScreen يقرأ به. */
    @Query("SELECT * FROM local_history WHERE conversationId = :convId AND replyToMessageId = :rootId ORDER BY createdAt ASC LIMIT :limit OFFSET :offset")
    suspend fun getThreadReplies(convId: String, rootId: String, offset: Int, limit: Int): List<LocalHistoryEntity>

    @Query("SELECT COUNT(*) FROM local_history WHERE conversationId = :convId AND outgoing = 0 AND createdAt > :since")
    suspend fun countIncomingSince(convId: String, since: Long): Int

    @Query("SELECT * FROM local_history WHERE id = :id LIMIT 1")
    suspend fun getLocalHistoryEntry(id: String): LocalHistoryEntity?

    /**
     * Phase-2 reliability: outgoing 1:1 rows stuck in SENDING (process death /
     * offline kill before drain). Re-driven at service start. Capped so a huge
     * backlog can't stall startup; oldest first.
     */
    @Query("SELECT * FROM local_history WHERE outgoing = 1 AND status = 'SENDING' ORDER BY createdAt ASC LIMIT 100")
    suspend fun getUnsentOutgoing(): List<LocalHistoryEntity>

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

    // بحث شامل: CAST لازم لأن encryptedPlaintext BLOB وLIKE على BLOB
    // لا يطابق شيئًا أبدًا (نفس علة searchMessages). ESCAPE للتهريب.
    // LIMIT داخل SQL بدل take() في الذاكرة بعد جلب كل المطابقات.
    @Query("SELECT * FROM local_history WHERE CAST(encryptedPlaintext AS TEXT) LIKE :query ESCAPE '\\' ORDER BY createdAt DESC LIMIT :limit")
    suspend fun searchAllMessages(query: String, limit: Int): List<LocalHistoryEntity>

    /** كل الرسائل الغنية المخزنة — لفحص مؤقت الاختفاء (expiresAt داخل حمولة RichMessage). */
    @Query("SELECT * FROM local_history WHERE messageType = 'RICH_TEXT'")
    suspend fun allRichHistory(): List<LocalHistoryEntity>

    // --- Starred Messages ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStarredMessage(starred: StarredMessageEntity)

    @Query("DELETE FROM starred_messages WHERE messageId = :messageId")
    suspend fun unstarMessage(messageId: String)

    @Query("SELECT * FROM starred_messages WHERE messageId = :messageId LIMIT 1")
    suspend fun getStarredMessage(messageId: String): StarredMessageEntity?

    @Query("SELECT * FROM starred_messages ORDER BY starredAt DESC")
    fun getAllStarredMessages(): Flow<List<StarredMessageEntity>>

    @Query("SELECT * FROM starred_messages WHERE conversationId = :convId ORDER BY starredAt DESC")
    fun starredMessagesForConversation(convId: String): Flow<List<StarredMessageEntity>>

    // --- Reply (P0-B): الاقتباس المحفوظ replyTo* يبقى بعد إعادة التشغيل ---
    /** جلب الرسالة المقتبسة لعرض معاينتها من الأعمدة المحفوظة. */
    @Query("SELECT * FROM local_history WHERE id = :replyToId LIMIT 1")
    suspend fun getReplyTarget(replyToId: String): LocalHistoryEntity?

    /** تعبئة/تحديث معاينة الرد دون إعادة إدراج الصف كاملاً. */
    @Query("UPDATE local_history SET replyToMessageId = :replyId, replyToMessageText = :replyText, replyToSenderId = :replySender WHERE id = :id")
    suspend fun updateReplyPreview(id: String, replyId: String?, replyText: String?, replySender: String?)
}

/** DAO للقنوات (Channels) */
@Dao
@TypeConverters(RedTypeConverters::class)
interface ChannelDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(channel: ChannelEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(channels: List<ChannelEntity>)

    @Query("SELECT * FROM channels WHERE id = :id AND deletedAt = 0 LIMIT 1")
    suspend fun getById(id: String): ChannelEntity?

    @Query("SELECT * FROM channels WHERE communityId = :communityId AND deletedAt = 0 ORDER BY createdAt DESC")
    fun getByCommunity(communityId: String): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE communityId = :communityId AND deletedAt = 0 ORDER BY createdAt DESC LIMIT :limit OFFSET :offset")
    suspend fun getByCommunityPage(communityId: String, limit: Int, offset: Int): List<ChannelEntity>

    @Query("SELECT COUNT(*) FROM channels WHERE communityId = :communityId AND deletedAt = 0")
    suspend fun countByCommunity(communityId: String): Int

    @Query("SELECT * FROM channels WHERE ownerId = :ownerId AND deletedAt = 0 ORDER BY createdAt DESC")
    fun getByOwner(ownerId: String): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE type = :type AND deletedAt = 0 ORDER BY createdAt DESC LIMIT :limit OFFSET :offset")
    suspend fun getByType(type: String, limit: Int, offset: Int): List<ChannelEntity>

    @Query("UPDATE channels SET memberCount = memberCount + :delta WHERE id = :id")
    suspend fun incrementMemberCount(id: String, delta: Int)

    @Query("UPDATE channels SET messageCount = messageCount + 1, updatedAt = :ts WHERE id = :id")
    suspend fun updateLastMessage(id: String, ts: Long)

    @Query("UPDATE channels SET isArchived = 1, archivedAt = :ts, updatedAt = :ts WHERE id = :id")
    suspend fun archive(id: String, ts: Long)

    @Query("UPDATE channels SET isArchived = 0, archivedAt = 0, updatedAt = :ts WHERE id = :id")
    suspend fun unarchive(id: String, ts: Long)

    @Query("UPDATE channels SET deletedAt = :ts, updatedAt = :ts WHERE id = :id")
    suspend fun softDelete(id: String, ts: Long)

    @Query("DELETE FROM channels WHERE id = :id")
    suspend fun hardDelete(id: String): Int

    @Query("SELECT * FROM channels WHERE id IN (:ids) AND deletedAt = 0")
    suspend fun getByIds(ids: List<String>): List<ChannelEntity>
}

/** DAO للمجتمعات (Communities) */
@Dao
@TypeConverters(RedTypeConverters::class)
interface CommunityDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(community: CommunityEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(communities: List<CommunityEntity>)

    @Query("SELECT * FROM communities WHERE id = :id AND deletedAt = 0 LIMIT 1")
    suspend fun getById(id: String): CommunityEntity?

    @Query("SELECT * FROM communities WHERE ownerId = :ownerId AND deletedAt = 0 ORDER BY createdAt DESC")
    fun getByOwner(ownerId: String): Flow<List<CommunityEntity>>

    @Query("SELECT * FROM communities WHERE isPublic = 1 AND deletedAt = 0 ORDER BY memberCount DESC LIMIT :limit OFFSET :offset")
    suspend fun getPublicCommunities(limit: Int, offset: Int): List<CommunityEntity>

    @Query("SELECT COUNT(*) FROM communities WHERE isPublic = 1 AND deletedAt = 0")
    suspend fun countPublicCommunities(): Int

    @Query("SELECT * FROM communities WHERE id IN (:ids) AND deletedAt = 0")
    suspend fun getByIds(ids: List<String>): List<CommunityEntity>

    @Query("UPDATE communities SET memberCount = memberCount + :delta WHERE id = :id")
    suspend fun incrementMemberCount(id: String, delta: Int)

    @Query("UPDATE communities SET channelCount = channelCount + :delta WHERE id = :id")
    suspend fun incrementChannelCount(id: String, delta: Int)

    @Query("UPDATE communities SET deletedAt = :ts, updatedAt = :ts WHERE id = :id")
    suspend fun softDelete(id: String, ts: Long)

    @Query("DELETE FROM communities WHERE id = :id")
    suspend fun hardDelete(id: String): Int
}

/** DAO لأعضاء المجتمع */
@Dao
@TypeConverters(RedTypeConverters::class)
interface CommunityMemberDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(member: CommunityMemberEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(members: List<CommunityMemberEntity>)

    @Query("SELECT * FROM community_members WHERE communityId = :communityId AND userId = :userId LIMIT 1")
    suspend fun get(communityId: String, userId: String): CommunityMemberEntity?

    @Query("SELECT * FROM community_members WHERE communityId = :communityId ORDER BY joinedAt DESC")
    fun getByCommunity(communityId: String): Flow<List<CommunityMemberEntity>>

    @Query("SELECT * FROM community_members WHERE communityId = :communityId AND role IN ('OWNER','ADMIN','MODERATOR') ORDER BY joinedAt DESC")
    suspend fun getModerators(communityId: String): List<CommunityMemberEntity>

    @Query("SELECT * FROM community_members WHERE userId = :userId ORDER BY joinedAt DESC")
    fun getByUser(userId: String): Flow<List<CommunityMemberEntity>>

    @Query("SELECT COUNT(*) FROM community_members WHERE communityId = :communityId")
    suspend fun countByCommunity(communityId: String): Int

    @Query("DELETE FROM community_members WHERE communityId = :communityId AND userId = :userId")
    suspend fun remove(communityId: String, userId: String): Int

    @Query("DELETE FROM community_members WHERE communityId = :communityId")
    suspend fun removeAll(communityId: String): Int

    @Query("UPDATE community_members SET role = :role WHERE communityId = :communityId AND userId = :userId")
    suspend fun updateRole(communityId: String, userId: String, role: String): Int

    @Query("UPDATE community_members SET isMuted = :muted, mutedUntil = :until WHERE communityId = :communityId AND userId = :userId")
    suspend fun setMuted(communityId: String, userId: String, muted: Boolean, until: Long): Int
}

/** DAO لأعضاء القناة */
@Dao
@TypeConverters(RedTypeConverters::class)
interface ChannelMemberDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(member: ChannelMemberEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(members: List<ChannelMemberEntity>)

    @Query("SELECT * FROM channel_members WHERE channelId = :channelId AND userId = :userId LIMIT 1")
    suspend fun get(channelId: String, userId: String): ChannelMemberEntity?

    @Query("SELECT * FROM channel_members WHERE channelId = :channelId ORDER BY joinedAt DESC")
    fun getByChannel(channelId: String): Flow<List<ChannelMemberEntity>>

    @Query("SELECT * FROM channel_members WHERE channelId = :channelId AND role IN ('OWNER','ADMIN','MODERATOR','SPEAKER') ORDER BY joinedAt DESC")
    suspend fun getModerators(channelId: String): List<ChannelMemberEntity>

    @Query("SELECT * FROM channel_members WHERE userId = :userId ORDER BY joinedAt DESC")
    fun getByUser(userId: String): Flow<List<ChannelMemberEntity>>

    @Query("SELECT COUNT(*) FROM channel_members WHERE channelId = :channelId")
    suspend fun countByChannel(channelId: String): Int

    @Query("DELETE FROM channel_members WHERE channelId = :channelId AND userId = :userId")
    suspend fun remove(channelId: String, userId: String): Int

    @Query("DELETE FROM channel_members WHERE channelId = :channelId")
    suspend fun removeAll(channelId: String): Int

    @Query("UPDATE channel_members SET role = :role WHERE channelId = :channelId AND userId = :userId")
    suspend fun updateRole(channelId: String, userId: String, role: String): Int

    @Query("UPDATE channel_members SET isMuted = :muted, mutedUntil = :until WHERE channelId = :channelId AND userId = :userId")
    suspend fun setMuted(channelId: String, userId: String, muted: Boolean, until: Long): Int

    @Query("UPDATE channel_members SET lastReadMessageId = :msgId WHERE channelId = :channelId AND userId = :userId")
    suspend fun updateLastRead(channelId: String, userId: String, msgId: String): Int
}

/** DAO للبث المباشر */
@Dao
@TypeConverters(RedTypeConverters::class)
interface LiveStreamDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(stream: LiveStreamEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(streams: List<LiveStreamEntity>)

    @Query("SELECT * FROM live_streams WHERE id = :id AND deletedAt = 0 LIMIT 1")
    suspend fun getById(id: String): LiveStreamEntity?

    @Query("SELECT * FROM live_streams WHERE channelId = :channelId AND deletedAt = 0 ORDER BY startedAt DESC")
    fun getByChannel(channelId: String): Flow<List<LiveStreamEntity>>

    @Query("SELECT * FROM live_streams WHERE channelId = :channelId AND status = 'LIVE' AND deletedAt = 0 LIMIT 1")
    suspend fun getActiveByChannel(channelId: String): LiveStreamEntity?

    @Query("SELECT * FROM live_streams WHERE hostId = :hostId AND deletedAt = 0 ORDER BY startedAt DESC LIMIT :limit OFFSET :offset")
    suspend fun getByHost(hostId: String, limit: Int, offset: Int): List<LiveStreamEntity>

    @Query("SELECT * FROM live_streams WHERE communityId = :communityId AND deletedAt = 0 ORDER BY startedAt DESC LIMIT :limit OFFSET :offset")
    suspend fun getByCommunity(communityId: String, limit: Int, offset: Int): List<LiveStreamEntity>

    @Query("SELECT * FROM live_streams WHERE status = 'LIVE' AND deletedAt = 0 ORDER BY startedAt DESC LIMIT :limit")
    suspend fun getActiveStreams(limit: Int): List<LiveStreamEntity>

    @Query("SELECT * FROM live_streams WHERE status = 'SCHEDULED' AND scheduledAt > :now AND deletedAt = 0 ORDER BY scheduledAt ASC LIMIT :limit")
    suspend fun getUpcomingStreams(now: Long, limit: Int): List<LiveStreamEntity>

    @Query("SELECT COUNT(*) FROM live_streams WHERE channelId = :channelId AND deletedAt = 0")
    suspend fun countByChannel(channelId: String): Int

    @Query("UPDATE live_streams SET status = :status, startedAt = :startedAt, updatedAt = :ts WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, startedAt: Long, ts: Long): Int

    @Query("UPDATE live_streams SET endedAt = :endedAt, durationSeconds = :duration, peakViewers = :peak, totalUniqueViewers = :unique, totalWatchTimeSeconds = :watchTime, recordingStatus = :recStatus, recordingUrl = :recUrl, status = 'ENDED', updatedAt = :ts WHERE id = :id")
    suspend fun endStream(id: String, endedAt: Long, duration: Long, peak: Int, unique: Long, watchTime: Long, recStatus: String, recUrl: String?, ts: Long): Int

    @Query("UPDATE live_streams SET peakViewers = :peak, updatedAt = :ts WHERE id = :id AND peakViewers < :peak")
    suspend fun updatePeakViewers(id: String, peak: Int, ts: Long): Int

    @Query("UPDATE live_streams SET deletedAt = :ts, updatedAt = :ts WHERE id = :id")
    suspend fun softDelete(id: String, ts: Long): Int

    @Query("DELETE FROM live_streams WHERE id = :id")
    suspend fun hardDelete(id: String): Int
}

/** DAO لمشاهدي البث المباشر */
@Dao
@TypeConverters(RedTypeConverters::class)
interface LiveStreamViewerDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(viewer: LiveStreamViewerEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(viewers: List<LiveStreamViewerEntity>)

    @Query("SELECT * FROM live_stream_viewers WHERE streamId = :streamId AND userId = :userId LIMIT 1")
    suspend fun get(streamId: String, userId: String): LiveStreamViewerEntity?

    @Query("SELECT * FROM live_stream_viewers WHERE streamId = :streamId AND leftAt = 0 ORDER BY joinedAt ASC")
    fun getActiveViewers(streamId: String): Flow<List<LiveStreamViewerEntity>>

    @Query("SELECT COUNT(*) FROM live_stream_viewers WHERE streamId = :streamId AND leftAt = 0")
    suspend fun countActiveViewers(streamId: String): Int

    @Query("SELECT * FROM live_stream_viewers WHERE streamId = :streamId ORDER BY joinedAt DESC LIMIT :limit OFFSET :offset")
    suspend fun getByStream(streamId: String, limit: Int, offset: Int): List<LiveStreamViewerEntity>

    @Query("SELECT * FROM live_stream_viewers WHERE userId = :userId ORDER BY joinedAt DESC LIMIT :limit OFFSET :offset")
    suspend fun getByUser(userId: String, limit: Int, offset: Int): List<LiveStreamViewerEntity>

    @Query("UPDATE live_stream_viewers SET leftAt = :leftAt, watchDurationSeconds = :duration WHERE streamId = :streamId AND userId = :userId")
    suspend fun leave(streamId: String, userId: String, leftAt: Long, duration: Long): Int

    @Query("UPDATE live_stream_viewers SET watchDurationSeconds = watchDurationSeconds + :delta WHERE streamId = :streamId AND userId = :userId")
    suspend fun incrementWatchTime(streamId: String, userId: String, delta: Long): Int

    @Query("DELETE FROM live_stream_viewers WHERE streamId = :streamId")
    suspend fun clearByStream(streamId: String): Int
}

/** DAO لتسجيلات البث المباشر */
@Dao
@TypeConverters(RedTypeConverters::class)
interface LiveStreamRecordingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(recording: LiveStreamRecordingEntity)

    @Query("SELECT * FROM live_stream_recordings WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): LiveStreamRecordingEntity?

    @Query("SELECT * FROM live_stream_recordings WHERE streamId = :streamId ORDER BY createdAt DESC")
    suspend fun getByStream(streamId: String): List<LiveStreamRecordingEntity>

    @Query("SELECT * FROM live_stream_recordings WHERE hostId = :hostId ORDER BY createdAt DESC LIMIT :limit OFFSET :offset")
    suspend fun getByHost(hostId: String, limit: Int, offset: Int): List<LiveStreamRecordingEntity>

    @Query("SELECT * FROM live_stream_recordings WHERE status = :status ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getByStatus(status: String, limit: Int): List<LiveStreamRecordingEntity>

    @Query("UPDATE live_stream_recordings SET status = :status, processingStartedAt = :started, updatedAt = :ts WHERE id = :id")
    suspend fun updateProcessingStatus(id: String, status: String, started: Long, ts: Long): Int

    @Query("UPDATE live_stream_recordings SET status = 'READY', hlsManifestUrl = :hls, thumbnailUrl = :thumb, fileSizeBytes = :size, processingCompletedAt = :completed, updatedAt = :ts WHERE id = :id")
    suspend fun markReady(id: String, hls: String, thumb: String?, size: Long, completed: Long, ts: Long): Int

    @Query("UPDATE live_stream_recordings SET status = 'FAILED', errorMessage = :error, updatedAt = :ts WHERE id = :id")
    suspend fun markFailed(id: String, error: String, ts: Long): Int
}

/** DAO لدردشة البث المباشر */
@Dao
@TypeConverters(RedTypeConverters::class)
interface LiveStreamChatDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: LiveStreamChatEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<LiveStreamChatEntity>)

    @Query("SELECT * FROM live_stream_chat WHERE streamId = :streamId AND isDeleted = 0 ORDER BY sentAt DESC LIMIT :limit OFFSET :offset")
    suspend fun getByStream(streamId: String, limit: Int, offset: Int): List<LiveStreamChatEntity>

    @Query("SELECT * FROM live_stream_chat WHERE streamId = :streamId AND messageType = :type AND isDeleted = 0 ORDER BY sentAt DESC LIMIT :limit")
    suspend fun getByStreamAndType(streamId: String, type: String, limit: Int): List<LiveStreamChatEntity>

    @Query("SELECT COUNT(*) FROM live_stream_chat WHERE streamId = :streamId AND isDeleted = 0")
    suspend fun countByStream(streamId: String): Int

    @Query("UPDATE live_stream_chat SET isDeleted = 1, deletedAt = :ts, deletedBy = :by WHERE id = :id")
    suspend fun softDelete(id: String, ts: Long, by: String): Int

    @Query("DELETE FROM live_stream_chat WHERE streamId = :streamId AND sentAt < :cutoff")
    suspend fun cleanupOldMessages(streamId: String, cutoff: Long): Int
}

/** DAO لتفاعلات البث المباشر */
@Dao
@TypeConverters(RedTypeConverters::class)
interface LiveStreamReactionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(reaction: LiveStreamReactionEntity)

    @Query("SELECT * FROM live_stream_reactions WHERE streamId = :streamId ORDER BY createdAt DESC")
    fun getByStream(streamId: String): Flow<List<LiveStreamReactionEntity>>

    @Query("SELECT * FROM live_stream_reactions WHERE streamId = :streamId AND userId = :userId")
    suspend fun getByUser(streamId: String, userId: String): List<LiveStreamReactionEntity>

    @Query("SELECT emoji, SUM(count) as total FROM live_stream_reactions WHERE streamId = :streamId GROUP BY emoji ORDER BY total DESC")
    suspend fun getReactionSummary(streamId: String): List<ReactionSummary>

    @Query("UPDATE live_stream_reactions SET count = count + 1, updatedAt = :ts WHERE streamId = :streamId AND userId = :userId AND emoji = :emoji")
    suspend fun increment(streamId: String, userId: String, emoji: String, ts: Long): Int

    @Query("DELETE FROM live_stream_reactions WHERE streamId = :streamId AND userId = :userId AND emoji = :emoji")
    suspend fun remove(streamId: String, userId: String, emoji: String): Int
}

data class ReactionSummary(val emoji: String, val total: Long)

/** DAO لهدايا البث المباشر */
@Dao
@TypeConverters(RedTypeConverters::class)
interface LiveStreamGiftDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(gift: LiveStreamGiftEntity)

    @Query("SELECT * FROM live_stream_gifts WHERE streamId = :streamId ORDER BY createdAt DESC LIMIT :limit OFFSET :offset")
    suspend fun getByStream(streamId: String, limit: Int, offset: Int): List<LiveStreamGiftEntity>

    @Query("SELECT * FROM live_stream_gifts WHERE senderId = :senderId ORDER BY createdAt DESC LIMIT :limit OFFSET :offset")
    suspend fun getBySender(senderId: String, limit: Int, offset: Int): List<LiveStreamGiftEntity>

    @Query("SELECT SUM(amount) FROM live_stream_gifts WHERE streamId = :streamId")
    suspend fun getTotalAmount(streamId: String): Double?

    @Query("SELECT senderId, SUM(amount) as total FROM live_stream_gifts WHERE streamId = :streamId GROUP BY senderId ORDER BY total DESC LIMIT :limit")
    suspend fun getTopGifters(streamId: String, limit: Int): List<GifterSummary>
}

data class GifterSummary(val senderId: String, val total: Double)

/** DAO لملفات الوسائط */
@Dao
@TypeConverters(RedTypeConverters::class)
interface MediaFileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(file: MediaFileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(files: List<MediaFileEntity>)

    @Query("SELECT * FROM media_files WHERE id = :id AND deletedAt = 0 LIMIT 1")
    suspend fun getById(id: String): MediaFileEntity?

    @Query("SELECT * FROM media_files WHERE conversationId = :conversationId AND deletedAt = 0 ORDER BY createdAt DESC")
    fun getByConversation(conversationId: String): Flow<List<MediaFileEntity>>

    @Query("SELECT * FROM media_files WHERE conversationId = :conversationId AND type = :type AND deletedAt = 0 ORDER BY createdAt DESC")
    suspend fun getByConversationAndType(conversationId: String, type: String): List<MediaFileEntity>

    @Query("SELECT * FROM media_files WHERE ownerId = :ownerId AND deletedAt = 0 ORDER BY createdAt DESC LIMIT :limit OFFSET :offset")
    suspend fun getByOwner(ownerId: String, limit: Int, offset: Int): List<MediaFileEntity>

    @Query("SELECT * FROM media_files WHERE messageId = :messageId AND deletedAt = 0 LIMIT 1")
    suspend fun getByMessageId(messageId: String): MediaFileEntity?

    @Query("SELECT * FROM media_files WHERE status = :status ORDER BY createdAt ASC LIMIT :limit")
    suspend fun getByStatus(status: String, limit: Int): List<MediaFileEntity>

    @Query("UPDATE media_files SET status = :status, cdnUrl = :url, updatedAt = :ts WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, url: String?, ts: Long): Int

    @Query("UPDATE media_files SET deletedAt = :ts, updatedAt = :ts WHERE id = :id")
    suspend fun softDelete(id: String, ts: Long): Int

    @Query("DELETE FROM media_files WHERE id = :id")
    suspend fun hardDelete(id: String): Int
}

/** DAO للأجهزة */
@Dao
@TypeConverters(RedTypeConverters::class)
interface DeviceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(device: DeviceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(devices: List<DeviceEntity>)

    @Query("SELECT * FROM devices WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): DeviceEntity?

    @Query("SELECT * FROM devices WHERE userId = :userId ORDER BY lastActiveAt DESC")
    fun getByUser(userId: String): Flow<List<DeviceEntity>>

    @Query("SELECT * FROM devices WHERE deviceToken = :token LIMIT 1")
    suspend fun getByToken(token: String): DeviceEntity?

    @Query("SELECT * FROM devices WHERE userId = :userId AND isActive = 1 ORDER BY lastActiveAt DESC")
    suspend fun getActiveByUser(userId: String): List<DeviceEntity>

    @Query("UPDATE devices SET lastActiveAt = :ts, lastSeenAt = :ts, isActive = 1, updatedAt = :ts WHERE id = :id")
    suspend fun updateLastActive(id: String, ts: Long): Int

    @Query("UPDATE devices SET deviceToken = :token, voipToken = :voip, pushEnabled = :push, voipEnabled = :voipEnabled, updatedAt = :ts WHERE id = :id")
    suspend fun updateTokens(id: String, token: String?, voip: String?, push: Boolean, voipEnabled: Boolean, ts: Long): Int

    @Query("UPDATE devices SET isTrusted = :trusted, updatedAt = :ts WHERE id = :id")
    suspend fun setTrusted(id: String, trusted: Boolean, ts: Long): Int

    @Query("UPDATE devices SET isActive = 0, updatedAt = :ts WHERE id = :id")
    suspend fun deactivate(id: String, ts: Long): Int

    @Query("DELETE FROM devices WHERE id = :id")
    suspend fun delete(id: String): Int

    @Query("DELETE FROM devices WHERE userId = :userId AND lastActiveAt < :cutoff")
    suspend fun cleanupOldDevices(userId: String, cutoff: Long): Int
}

/** DAO لملفات تعريف المستخدمين */
@Dao
@TypeConverters(RedTypeConverters::class)
interface UserProfileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: UserProfileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(profiles: List<UserProfileEntity>)

    @Query("SELECT * FROM user_profiles WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): UserProfileEntity?

    @Query("SELECT * FROM user_profiles WHERE username = :username LIMIT 1")
    suspend fun getByUsername(username: String): UserProfileEntity?

    @Query("SELECT * FROM user_profiles WHERE displayName LIKE :query ESCAPE '\\' OR username LIKE :query ESCAPE '\\' ORDER BY displayName ASC LIMIT :limit")
    suspend fun search(query: String, limit: Int): List<UserProfileEntity>

    @Query("SELECT * FROM user_profiles WHERE status = 'ONLINE' ORDER BY lastSeen DESC LIMIT :limit")
    suspend fun getOnlineUsers(limit: Int): List<UserProfileEntity>

    @Query("UPDATE user_profiles SET status = :status, lastSeen = :ts, updatedAt = :ts WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, ts: Long): Int

    @Query("UPDATE user_profiles SET customStatus = :status, customStatusEmoji = :emoji, customStatusExpiresAt = :expires, updatedAt = :ts WHERE id = :id")
    suspend fun updateCustomStatus(id: String, status: String?, emoji: String?, expires: Long, ts: Long): Int

    @Query("UPDATE user_profiles SET avatarUrl = :url, updatedAt = :ts WHERE id = :id")
    suspend fun updateAvatar(id: String, url: String, ts: Long): Int

    @Query("DELETE FROM user_profiles WHERE id = :id")
    suspend fun delete(id: String): Int
}

/** DAO لإعدادات التطبيق */
@Dao
@TypeConverters(RedTypeConverters::class)
interface AppSettingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun set(setting: AppSettingEntity)

    @Query("SELECT * FROM app_settings WHERE key = :key LIMIT 1")
    suspend fun get(key: String): AppSettingEntity?

    @Query("SELECT value FROM app_settings WHERE key = :key LIMIT 1")
    suspend fun getString(key: String): String?

    @Query("SELECT * FROM app_settings")
    fun getAll(): Flow<List<AppSettingEntity>>

    @Query("DELETE FROM app_settings WHERE key = :key")
    suspend fun delete(key: String): Int
}

/** DAO لمسودات البث المباشر */
@Dao
@TypeConverters(RedTypeConverters::class)
interface LiveStreamDraftDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(draft: LiveStreamDraftEntity)

    @Query("SELECT * FROM live_stream_drafts WHERE channelId = :channelId LIMIT 1")
    suspend fun getByChannel(channelId: String): LiveStreamDraftEntity?

    @Query("SELECT * FROM live_stream_drafts WHERE hostId = :hostId ORDER BY updatedAt DESC")
    suspend fun getByHost(hostId: String): List<LiveStreamDraftEntity>

    @Query("DELETE FROM live_stream_drafts WHERE channelId = :channelId")
    suspend fun deleteByChannel(channelId: String): Int

    @Query("DELETE FROM live_stream_drafts WHERE hostId = :hostId")
    suspend fun deleteByHost(hostId: String): Int
}
