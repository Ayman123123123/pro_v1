package com.red.sovereign.core.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
@TypeConverters(RedTypeConverters::class)
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

    /** تنظيف رفع الوسائط عند حذف المحادثة — يُستدعى داخل معاملة deleteConversation. */
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

    /** محادثة عبر معرف النظير — لمنع تكرار صفوف 1:1 لنفس الجهة. */
    @Query("SELECT * FROM conversations WHERE peerId = :peerId LIMIT 1")
    suspend fun getConversationByPeerId(peerId: String): ConversationEntity?

    /** مجموع غير المقروء عبر كل المحادثات — لشارة التطبيق. */
    @Query("SELECT COALESCE(SUM(unreadCount), 0) FROM conversations")
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
     * إنشاء/تحديث ذري لصف المحادثة عند تخزين رسالة (يحفظ pinned/
     * archived/muted). كان get+insert/update متفرقين في Repository
     * فيتسابق خيطان ويضيع unread أو يتكرر الصف.
     */
    @Transaction
    suspend fun upsertConversationOnMessage(
        conversationId: String,
        peerId: String,
        preview: String,
        timestamp: Long,
        isIncoming: Boolean
    ) {
        val existing = getConversation(conversationId)
        if (existing != null) {
            updateConversationLast(conversationId, preview, timestamp, if (isIncoming) 1 else 0)
        } else {
            insertConversation(
                ConversationEntity(
                    id = conversationId, peerId = peerId,
                    lastMessageText = preview, lastMessageTimestamp = timestamp,
                    unreadCount = if (isIncoming) 1 else 0
                )
            )
        }
    }

    /**
     * حفظ وارد + لمس المحادثة في معاملة واحدة: لا رسالة بلا صف
     * محادثة ولا عداد ضائع عند قتل العملية بين العمليتين.
     */
    @Transaction
    suspend fun insertMessageAndTouchConversation(
        message: MessageEntity,
        peerId: String,
        preview: String,
        isIncoming: Boolean
    ) {
        insertMessage(message)
        upsertConversationOnMessage(message.conversationId, peerId, preview, message.createdAt, isIncoming)
    }

    // --- Contacts ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContacts(contacts: List<ContactEntity>)

    @Query("DELETE FROM contacts")
    suspend fun clearContacts()

    @Query("SELECT * FROM contacts WHERE isFriend = 1")
    fun getFriends(): Flow<List<ContactEntity>>

    /** جهة واحدة بالمعرف — للتحقق قبل الحظر/العرض. */
    @Query("SELECT * FROM contacts WHERE redId = :redId LIMIT 1")
    suspend fun getContactById(redId: String): ContactEntity?

    /**
     * بحث جهات بالاسم/المعرف — يستقبل نمط LIKE جاهزًا من
     * [SearchGuards.likePattern] (التهريب في Repository، وESCAPE هنا).
     */
    @Query(
        "SELECT * FROM contacts WHERE username LIKE :pattern ESCAPE '\\' " +
            "OR displayName LIKE :pattern ESCAPE '\\' " +
            "ORDER BY displayName ASC LIMIT :limit"
    )
    suspend fun searchContacts(pattern: String, limit: Int = 50): List<ContactEntity>

    /** حظر/فك حظر — يعيد عدد الصفوف المعدلة (0 = لا جهة بهذا المعرف). */
    @Query("UPDATE contacts SET isBlocked = :blocked WHERE redId = :redId")
    suspend fun setBlocked(redId: String, blocked: Boolean): Int

    /** استبدال ذري لقائمة الجهات: مسح + إدراج في معاملة واحدة. */
    @Transaction
    suspend fun replaceContactsFull(contacts: List<ContactEntity>) {
        clearContacts()
        if (contacts.isNotEmpty()) insertContacts(contacts)
    }

    // --- Groups ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroups(groups: List<GroupEntity>)

    @Query("SELECT * FROM groups ORDER BY createdAt DESC")
    fun getGroups(): Flow<List<GroupEntity>>

    // ── Paging للمجموعات: صفحات LIMIT/OFFSET بدل تحميل كل الصفوف في الذاكرة.
    // getGroups() القديمة تبقى للتوافق (كاش offline في GroupViewModel).
    // TODO: استهلاكها من GroupViewModel/UI عند نمو القوائم (تحميل تدريجي).
    @Query("SELECT * FROM groups ORDER BY createdAt DESC LIMIT :limit OFFSET :offset")
    suspend fun getGroupsPage(limit: Int, offset: Int): List<GroupEntity>

    @Query("SELECT COUNT(*) FROM groups")
    suspend fun countGroups(): Int

    /** مجموعة واحدة بالمعرف — لشاشة التفاصيل قبل الاشتراك في Flow. */
    @Query("SELECT * FROM groups WHERE id = :id LIMIT 1")
    suspend fun getGroupById(id: String): GroupEntity?

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

    @Query("DELETE FROM call_logs WHERE id = :id")
    suspend fun deleteCallLog(id: String)

    @Query("DELETE FROM call_logs WHERE id IN (:ids)")
    suspend fun deleteCallLogs(ids: List<String>)

    @Query("DELETE FROM call_logs")
    suspend fun clearCallLogs()

    /** عدد المكالمات الفائتة — لشارة "فاتك N". */
    @Query("SELECT COUNT(*) FROM call_logs WHERE status = 'MISSED'")
    suspend fun countMissedCalls(): Int

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
            "ORDER BY createdAt DESC LIMIT :limit"
    )
    suspend fun searchMessages(convId: String, query: String, limit: Int = 100): List<LocalHistoryEntity>

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

    /**
     * حذف محادثة ذري: السجل + الرسائل + التفاعلات + الصف في معاملة
     * واحدة — لا صف يتيم ولا سجل بلا محادثة عند قتل العملية وسط الحذف.
     * (تنظيف outbox/media/FTS في LocalRepository بنفس المعاملة عبر DB).
     */
    @Transaction
    suspend fun deleteConversationFull(convId: String) {
        deleteLocalHistoryByConversation(convId)
        deleteMessagesByConversation(convId)
        deleteReactionsByConversation(convId)
        deleteConversationRow(convId)
    }

    // بحث شامل: CAST لازم لأن encryptedPlaintext BLOB وLIKE على BLOB
    // لا يطابق شيئًا أبدًا (نفس علة searchMessages). ESCAPE للتهريب.
    @Query("SELECT * FROM local_history WHERE CAST(encryptedPlaintext AS TEXT) LIKE :query ESCAPE '\\' ORDER BY createdAt DESC LIMIT :limit")
    suspend fun searchAllMessages(query: String, limit: Int = 100): List<LocalHistoryEntity>

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
