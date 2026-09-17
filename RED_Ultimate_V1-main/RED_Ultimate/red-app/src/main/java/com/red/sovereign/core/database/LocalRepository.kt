package com.red.sovereign.core.database

import android.content.Context
import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow

class LocalRepository(context: Context) {
    private val appCtx = context.applicationContext
    private val db = RedDatabase.getInstance(context)
    private val dao = db.redDao()
    private val outboxDao by lazy { db.outboxDao() }
    private val mediaUploadDao by lazy { db.mediaUploadDao() }
    private val channelDao by lazy { db.channelDao() }
    private val communityDao by lazy { db.communityDao() }
    private val communityMemberDao by lazy { db.communityMemberDao() }
    private val channelMemberDao by lazy { db.channelMemberDao() }
    private val liveStreamDao by lazy { db.liveStreamDao() }
    private val liveStreamViewerDao by lazy { db.liveStreamViewerDao() }
    private val liveStreamRecordingDao by lazy { db.liveStreamRecordingDao() }
    private val liveStreamChatDao by lazy { db.liveStreamChatDao() }
    private val liveStreamReactionDao by lazy { db.liveStreamReactionDao() }
    private val liveStreamGiftDao by lazy { db.liveStreamGiftDao() }
    private val mediaFileDao by lazy { db.mediaFileDao() }
    private val deviceDao by lazy { db.deviceDao() }
    private val userProfileDao by lazy { db.userProfileDao() }
    private val appSettingDao by lazy { db.appSettingDao() }
    private val liveStreamDraftDao by lazy { db.liveStreamDraftDao() }

    // --- Messages ---
    suspend fun saveMessage(message: MessageEntity) = dao.insertMessage(message)
    // P0-B: ملء أعمدة replyTo* عند الحفظ حتى يبقى الاقتباس بعد إعادة التشغيل.
    // RichMessage.replyTo يحمل حالياً معرف الرسالة المقتبسة فقط (String?)،
    // فننسخ المعرف ونستكمل النص/المرسل عبر lookup للرسالة الأصلية عند توفرها.
    suspend fun saveLocalHistory(history: LocalHistoryEntity) {
        val filled = runCatching {
            val rich = com.red.sovereign.core.RichMessage.decode(history.encryptedPlaintext)
            val replyId = history.replyToMessageId ?: rich?.replyTo
            if (replyId != null && (history.replyToMessageText == null || history.replyToSenderId == null)) {
                val quoted = runCatching { dao.getLocalHistoryEntry(replyId) }.getOrNull()
                val quotedText = history.replyToMessageText ?: quoted?.let {
                    runCatching {
                        com.red.sovereign.core.RichMessage.decode(it.encryptedPlaintext)?.text
                            ?.takeIf { t -> t.isNotBlank() }
                            ?: it.encryptedPlaintext.toString(Charsets.UTF_8)
                    }.getOrNull()?.take(500)
                }
                val quotedSender = history.replyToSenderId ?: quoted?.senderId
                history.copy(
                    replyToMessageId = replyId,
                    replyToMessageText = quotedText,
                    replyToSenderId = quotedSender
                )
            } else history
        }.getOrDefault(history)
        dao.insertLocalHistory(filled)
        // LEGENDARY FIX: فهرسة FTS فور الحفظ (كانت فجوة فيتسع الفارق ويبقى البحث مكسوراً)
        runCatching {
            val db = RedDatabase.getInstance(appCtx).openHelper.writableDatabase
            FtsSearchManager(db).indexLocalHistory(filled)
        }
    }
    fun getLocalHistory(convId: String): Flow<List<LocalHistoryEntity>> = dao.getLocalHistory(convId)
    /**
     * Paging3 لسجل المحادثة (2026-09-10): صفحات 30 عنصرًا بدل تحميل
     * `local_history` كاملًا في الذاكرة. يغذّي `ChatHistoryPagingColumn`
     * في `ChatThreadScreen.kt` عبر `collectAsLazyPagingItems()` مع `loadState`.
     * المسار القديم `getLocalHistory()` يبقى للتوافق (استعادة سريعة/بحث).
     */
    fun chatHistoryPager(conversationId: String, pageSize: Int = 30): Flow<PagingData<LocalHistoryEntity>> =
        com.red.sovereign.core.database.chatHistoryPager(dao, conversationId, pageSize)
    suspend fun updateMessageStatus(id: String, status: String) = dao.updateMessageStatus(id, status)
    /** Phase-2 reliability: rows awaiting (re-)send after process death. */
    suspend fun getUnsentOutgoing() = dao.getUnsentOutgoing()
    suspend fun updateLocalHistoryText(id: String, plaintext: ByteArray) = dao.updateLocalHistoryText(id, plaintext)

    suspend fun saveIncomingMessage(message: com.red.sovereign.proto.RedProtos.ChatMessage, outgoing: Boolean = false) {
        dao.insertMessage(message.toMessageEntity(outgoing))
    }

    /**
     * مسار ذري (معاملة Room واحدة عبر [RedDao.insertMessageAndTouchConversation]):
     * إدراج الرسالة + إنشاء/تحديث صف المحادثة معًا — لا رسالة بلا ظهور
     * في القائمة، ولا عداد غير مقروء مكسور عند القتل بين العمليتين.
     * (المساران المنفصلان أعلاه/أدناه يبقيان للتوافق مع RedConnectionService.)
     */
    suspend fun saveIncomingMessageAndTouchConversation(
        message: com.red.sovereign.proto.RedProtos.ChatMessage,
        peerId: String,
        preview: String,
        timestamp: Long = message.timestamp,
        outgoing: Boolean = false,
        isIncoming: Boolean = !outgoing
    ) {
        dao.insertMessageAndTouchConversation(
            message.toMessageEntity(outgoing), peerId, preview, timestamp, isIncoming
        )
    }

    private fun com.red.sovereign.proto.RedProtos.ChatMessage.toMessageEntity(outgoing: Boolean) = MessageEntity(
        id = id,
        conversationId = conversationId,
        senderId = senderId,
        receiverId = receiverId,
        payload = payload.toByteArray(),
        type = type,
        senderDeviceId = senderDeviceId,
        receiverDeviceId = receiverDeviceId,
        ciphertextType = ciphertextType,
        sequence = sequenceNumber,
        status = if (outgoing) MessageStatus.SENT.name else MessageStatus.DELIVERED.name,
        createdAt = timestamp,
        outgoing = outgoing
    )

    // --- Conversations ---
    suspend fun saveConversation(conv: ConversationEntity) = dao.insertConversation(conv)

    /**
     * يُنشئ/يُحدّث صف المحادثة عند إرسال أو استقبال رسالة، بحيث تظهر
     * المحادثة في قائمة الدردشات مع آخر رسالة والطابع الزمني وعدد غير المقروء.
     * يحافظ على pinned/archived/muted عند وجود المحادثة مسبقاً.
     * ذري عبر [RedDao.touchConversation] (قراءة-تعديل-كتابة في معاملة واحدة).
     */
    suspend fun onMessageStored(conversationId: String, peerId: String, preview: String, timestamp: Long, isIncoming: Boolean) {
        dao.touchConversation(conversationId, peerId, preview, timestamp, isIncoming)
    }
    fun getActiveConversations(): Flow<List<ConversationEntity>> = dao.getActiveConversations()
    fun getArchivedConversations(): Flow<List<ConversationEntity>> = dao.getArchivedConversations()
    fun getAllConversations(): Flow<List<ConversationEntity>> = dao.getAllConversations()
    suspend fun getConversation(id: String) = dao.getConversation(id)
    /** جلب محادثة عبر معرف الطرف — استعلام مباشر بدل فلترة كل المحادثات. */
    suspend fun getConversationByPeerId(peerId: String) = dao.getConversationByPeerId(peerId)
    /** مجموع غير المقروء (غير المؤرشف) لشارة التطبيق — تجميع SQL. */
    suspend fun totalUnreadCount(): Int = runCatching { dao.totalUnreadCount() }.getOrDefault(0)
    suspend fun setPinned(id: String, pinned: Boolean) = dao.setPinned(id, pinned)
    suspend fun setArchived(id: String, archived: Boolean) = dao.setArchived(id, archived)
    suspend fun setMutedUntil(id: String, until: Long) = dao.setMutedUntil(id, until)
    suspend fun clearUnread(id: String) = dao.clearUnread(id)
    suspend fun setUnreadCount(id: String, count: Int) = dao.setUnreadCount(id, count.coerceAtLeast(0))

    // --- Contacts ---
    suspend fun saveContacts(contacts: List<ContactEntity>) = dao.insertContacts(contacts)
    /** استبدال ذري (مسح + إدراج في معاملة واحدة) — عطل بينهما كان يُفرغ الجدول. */
    suspend fun replaceContacts(contacts: List<ContactEntity>) = dao.replaceContactsAtomic(contacts)
    fun getFriends(): Flow<List<ContactEntity>> = dao.getFriends()
    suspend fun getContactById(redId: String) = dao.getContactById(redId)
    suspend fun searchContacts(query: String, limit: Int = CONTACT_SEARCH_DEFAULT_LIMIT): List<ContactEntity> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        return dao.searchContacts(likeContainsPattern(trimmed), limit.coerceIn(1, CONTACT_SEARCH_DEFAULT_LIMIT * 2))
    }
    suspend fun setBlocked(redId: String, blocked: Boolean) = dao.setBlocked(redId, blocked)

    // --- Groups ---
    suspend fun saveGroups(groups: List<GroupEntity>) = dao.insertGroups(groups)
    /** مجموعة واحدة بالمعرف — بدل فلترة getGroups() كاملة في الذاكرة. */
    suspend fun getGroupById(id: String) = dao.getGroupById(id)
    fun getGroups(): Flow<List<GroupEntity>> = dao.getGroups()
    // Paging للمجموعات: صفحة LIMIT/OFFSET — للاستهلاك التدريجي عند نمو القوائم.
    suspend fun getGroupsPage(limit: Int, offset: Int): List<GroupEntity> = dao.getGroupsPage(limit, offset)
    suspend fun countGroups(): Int = runCatching { dao.countGroups() }.getOrDefault(0)

    // --- Call Logs ---
    suspend fun saveCallLog(log: CallLogEntity) = dao.insertCallLog(log)
    suspend fun saveCallLogs(logs: List<CallLogEntity>) = dao.insertCallLogs(logs)
    fun getCallLogs(): Flow<List<CallLogEntity>> = dao.getCallLogs()
    // Paging لسجل المكالمات: صفحة LIMIT/OFFSET — يغذيها CallHistoryViewModel.loadMore().
    suspend fun getCallLogsPage(limit: Int, offset: Int): List<CallLogEntity> = dao.getCallLogsPage(limit, offset)
    suspend fun countCallLogs(): Int = runCatching { dao.countCallLogs() }.getOrDefault(0)
    /** عدد المكالمات الفائتة لشارة/فلتر الفائت — COUNT(*) بدل تحميل السجل. */
    suspend fun countMissedCalls(): Int = runCatching { dao.countMissedCalls() }.getOrDefault(0)
    suspend fun deleteCallLog(id: String) = dao.deleteCallLog(id)
    suspend fun deleteCallLogs(ids: List<String>) = dao.deleteCallLogs(ids)
    suspend fun clearCallLogs() = dao.clearCallLogs()
    /** حذف السجلات الأقدم من cutoff — يُعيد عدد المحذوف (سياسة CALL_HISTORY_RETENTION). */
    suspend fun deleteCallLogsOlderThan(cutoff: Long): Int = dao.deleteCallLogsOlderThan(cutoff)

    // --- Stories ---
    suspend fun saveStories(stories: List<StoryEntity>) = dao.insertStories(stories)
    fun getActiveStories(): Flow<List<StoryEntity>> = dao.getActiveStories(System.currentTimeMillis())

    // --- Drafts ---
    suspend fun saveDraft(convId: String, text: String) = dao.saveDraft(DraftEntity(convId, text, System.currentTimeMillis()))
    suspend fun getDraft(convId: String) = dao.getDraft(convId)
    suspend fun deleteDraft(convId: String) = dao.deleteDraft(convId)

    // --- Search ---
    /**
     * بحث احتياطي داخل محادثة. المسار المفضَّل هو [FtsSearchManager]
     * لأنه مفهرس ويطبّع الحركات؛ هذا مسحٌ مقيَّد بالمحادثة (فهرس
     * conversationId) مع `LIMIT` داخل SQL.
     * التهريب موحَّد في [likeContainsPattern] — لا تهريب مبعثر هنا.
     */
    suspend fun search(convId: String, query: String, limit: Int = MESSAGE_SEARCH_DEFAULT_LIMIT): List<LocalHistoryEntity> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return emptyList()
        return dao.searchMessages(convId, likeContainsPattern(trimmed), limit.coerceIn(1, MESSAGE_SEARCH_MAX_LIMIT))
    }

    // --- Delete ---
    suspend fun deleteMessage(messageId: String) {
        dao.deleteLocalHistory(messageId)
        dao.deleteMessage(messageId)
    }

    // --- Reactions (تُخزّن محلياً بعد فك التشفير؛ toggle عند الاستقبال) ---
    /** يُطبّق تفاعلاً وارداً: إضافة (REACTION) أو إزالة (REACTION_REMOVE). يُعيد الإيموجي المُطبّق أو null. */
    suspend fun applyReaction(messageId: String, conversationId: String, senderId: String, emoji: String?, remove: Boolean, timestamp: Long): String? {
        return if (remove) {
            dao.deleteReaction(messageId, senderId)
            null
        } else if (emoji != null) {
            dao.upsertReaction(MessageReactionEntity(messageId, conversationId, senderId, emoji, timestamp))
            emoji
        } else null
    }

    /** تفاعلات محادثة كاملة (Flow) لعرض الـ chips تحت الرسائل. */
    fun reactionsForConversation(convId: String): Flow<List<MessageReactionEntity>> = dao.reactionsForConversation(convId)

    suspend fun reactionsForMessage(messageId: String): List<MessageReactionEntity> = dao.reactionsForMessage(messageId)

    suspend fun deleteReactionsByConversation(convId: String) = dao.deleteReactionsByConversation(convId)

    /** يحذف تفاعلات رسالة واحدة (يُستخدم مع الحذف لدى الجميع وحذف لديّ). */
    suspend fun deleteReactionsForMessage(messageId: String) = dao.deleteReactionsForMessage(messageId)

    // ─── Phase 0 green-build shims (2026-09-16) ───
    // ReactionsScreen.kt و ThreadRepliesScreen.kt يستخدمان أسماءً مختلفة عن
    // الموجود في DAO؛ نسوّي الواجهة هنا دون لمس الشاشات لتفادي كسر أكبر.
    suspend fun getReactionsForMessage(messageId: String): List<MessageReactionEntity> = dao.reactionsForMessage(messageId)
    suspend fun deleteReaction(messageId: String, senderId: String) = dao.deleteReaction(messageId, senderId)
    suspend fun upsertReaction(reaction: MessageReactionEntity) = dao.upsertReaction(reaction)
    suspend fun getThreadReplies(conversationId: String, rootMessageId: String, offset: Int, limit: Int): List<LocalHistoryEntity> =
        dao.getThreadReplies(conversationId, rootMessageId, offset, limit)

    /** عدد الرسائل الواردة بعد لحظة معينة — لاستعادة عدادات غير المقروء بعد إعادة التشغيل. */
    suspend fun countIncomingSince(convId: String, since: Long): Int = dao.countIncomingSince(convId, since)

    /** تصفير عداد غير المقروء عند فتح المحادثة. */

    // --- Starred Messages ---
    suspend fun starMessage(messageId: String, conversationId: String, senderId: String, messageText: String, messageType: String) {
        dao.upsertStarredMessage(StarredMessageEntity(messageId, conversationId, senderId, messageText, messageType))
    }

    suspend fun unstarMessage(messageId: String) = dao.unstarMessage(messageId)

    suspend fun isMessageStarred(messageId: String): Boolean = dao.getStarredMessage(messageId) != null

    fun getAllStarredMessages(): Flow<List<StarredMessageEntity>> = dao.getAllStarredMessages()

    fun starredMessagesForConversation(convId: String): Flow<List<StarredMessageEntity>> = dao.starredMessagesForConversation(convId)

    /** جلب سجل واحد — للتحقق من ملكية التعديل/الحذف قبل تطبيقهما. */
    suspend fun getLocalHistoryEntry(id: String) = dao.getLocalHistoryEntry(id)

    /** يحذف رسالة من السجل المحلي (المفكوك) فقط — يُستخدم لحذف رسالة واحدة. */
    suspend fun deleteLocalMessage(messageId: String) = dao.deleteLocalHistory(messageId)

    /** LEGENDARY: حذف للجميع بـ tombstone موحد (كان حذفاً فعلياً يتناقض مع MessageStore) + شارة "معدّلة" */
    suspend fun deleteForEveryoneTombstone(messageId: String) {
        // tombstone داخل النص المشفر: يحتفظ بالصف + الوقت + المرسل، يستبدل النص فقط
        val tomb = "{\"deleted\":true,\"text\":\"تم حذف هذه الرسالة\"}".toByteArray(Charsets.UTF_8)
        runCatching { dao.updateLocalHistoryText(messageId, tomb) }
        runCatching {
            val db = RedDatabase.getInstance(appCtx).openHelper.writableDatabase
            db.execSQL("DELETE FROM messages_fts WHERE messageId = ?", arrayOf(messageId))
        }
    }

    /** LEGENDARY: تعديل مع شارة + إعادة فهرسة (كان يعيد الفهرسة بـ conversationId فارغ) */
    suspend fun editLocalMessage(messageId: String, newPlaintext: ByteArray) {
        dao.updateLocalHistoryText(messageId, newPlaintext)
        runCatching {
            val entry = dao.getLocalHistoryEntry(messageId) ?: return
            val db = RedDatabase.getInstance(appCtx).openHelper.writableDatabase
            FtsSearchManager(db).indexLocalHistory(entry)
        }
    }

    /** يحذف كل بيانات محادثة: السجل المحلي + الرسائل + تفاعلاتها + الصادر + الرفوعات + صف المحادثة. */
    suspend fun deleteConversation(convId: String) {
        // النواة الأربع ذرية في معاملة Room واحدة؛ الصادر/الرفوعات تنظيف
        // best-effort (ذرّيتها الكاملة عبر DAO تتطلب withTransaction على مستوى القاعدة).
        dao.deleteConversationFull(convId)
        runCatching { outboxDao.deleteByConversation(convId) }
        runCatching { mediaUploadDao.deleteByConversation(convId) }
    }

    // --- Global Search ---
    /**
     * بحث شامل موحد (2026-09-10): نقطة الدخول الوحيدة للبحث النصي.
     * `RedGlobalSearch` وحوار «البحث داخل المحادثة» يمران هنا بنفس
     * السياسة: حد أدنى حرفين + تهريب محارف LIKE + حد 100 نتيجة.
     * المسار المفضَّل طويل المدى هو `FtsSearchManager` (مفهرس + تطبيع
     * عربي)؛ هذا LIKE يبقى fallback حين يتعذّر FTS.
     */
    suspend fun searchAll(query: String): List<LocalHistoryEntity> {
        val trimmed = query.trim()
        if (trimmed.length < 2) return emptyList()
        // التهريب موحّد في likeContainsPattern (Entities.kt) — لا نسخ مبعثرة هنا.
        // LIMIT داخل SQL (MESSAGE_SEARCH_MAX_LIMIT) بدل take() بعد جلب الكل.
        return dao.searchAllMessages(likeContainsPattern(trimmed), MESSAGE_SEARCH_MAX_LIMIT)
    }

    /**
     * بحث FTS أولًا ثم LIKE احتياطيًا (2026-09-10): يُستدعى من
     * `RedGlobalSearch` عبر `RedDatabase.openHelper` — التطبيع العربي
     * على الطرفين (فهرسة + استعلام) عبر `FtsSearchManager.normalizeArabic`.
     */
    suspend fun searchUnified(context: android.content.Context, query: String, limit: Int = 50): List<FtsResult> {
        val trimmed = query.trim()
        if (trimmed.length < 2) return emptyList()
        return runCatching {
            val db = RedDatabase.getInstance(context).openHelper.readableDatabase
            FtsSearchManager(db).search(trimmed, limit)
        }.getOrDefault(emptyList())
    }

    /** وسائط محادثة (Flow) — لمعرض الوسائط. الصور/الفيديو/الملفات/الصوت. */
    fun mediaForConversation(convId: String) = dao.mediaForConversation(convId)

    /**
     * ⏳ حذف الرسائل المنتهية (disappearing) فعلياً من قاعدة البيانات.
     * `expiresAt` يعيش داخل حمولة RichMessage — نفك ترميز كل رسالة غنية
     * ونحذف ما انقضت مهلته. يُستدعى عند تشغيل الخدمة ثم دورياً.
     * @return عدد الرسائل المحذوفة
     */
    suspend fun purgeExpiredMessages(now: Long = System.currentTimeMillis()): Int {
        var deleted = 0
        dao.allRichHistory().forEach { stored ->
            val rich = runCatching { com.red.sovereign.core.RichMessage.decode(stored.encryptedPlaintext) }.getOrNull() ?: return@forEach
            val expiresAt = rich.expiresAt ?: return@forEach
            if (expiresAt <= now) {
                dao.deleteLocalHistory(stored.id)
                deleted++
            }
        }
        return deleted
    }

    // ============ CHANNELS ============
    suspend fun saveChannel(channel: ChannelEntity) = channelDao.upsert(channel)
    suspend fun saveChannels(channels: List<ChannelEntity>) = channelDao.upsertAll(channels)
    suspend fun getChannel(id: String) = channelDao.getById(id)
    fun getChannelsByCommunity(communityId: String): Flow<List<ChannelEntity>> = channelDao.getByCommunity(communityId)
    suspend fun getChannelsByCommunityPage(communityId: String, limit: Int, offset: Int) = channelDao.getByCommunityPage(communityId, limit, offset)
    suspend fun countChannelsByCommunity(communityId: String) = channelDao.countByCommunity(communityId)
    fun getChannelsByOwner(ownerId: String): Flow<List<ChannelEntity>> = channelDao.getByOwner(ownerId)
    suspend fun incrementChannelMemberCount(id: String, delta: Int) = channelDao.incrementMemberCount(id, delta)
    suspend fun updateChannelLastMessage(id: String, ts: Long) = channelDao.updateLastMessage(id, ts)
    suspend fun archiveChannel(id: String, ts: Long) = channelDao.archive(id, ts)
    suspend fun unarchiveChannel(id: String, ts: Long) = channelDao.unarchive(id, ts)
    suspend fun softDeleteChannel(id: String, ts: Long) = channelDao.softDelete(id, ts)
    suspend fun hardDeleteChannel(id: String) = channelDao.hardDelete(id)

    // ============ COMMUNITIES ============
    suspend fun saveCommunity(community: CommunityEntity) = communityDao.upsert(community)
    suspend fun saveCommunities(communities: List<CommunityEntity>) = communityDao.upsertAll(communities)
    suspend fun getCommunity(id: String) = communityDao.getById(id)
    fun getCommunitiesByOwner(ownerId: String): Flow<List<CommunityEntity>> = communityDao.getByOwner(ownerId)
    suspend fun getPublicCommunities(limit: Int, offset: Int) = communityDao.getPublicCommunities(limit, offset)
    suspend fun countPublicCommunities() = communityDao.countPublicCommunities()
    suspend fun incrementCommunityMemberCount(id: String, delta: Int) = communityDao.incrementMemberCount(id, delta)
    suspend fun incrementCommunityChannelCount(id: String, delta: Int) = communityDao.incrementChannelCount(id, delta)
    suspend fun softDeleteCommunity(id: String, ts: Long) = communityDao.softDelete(id, ts)
    suspend fun hardDeleteCommunity(id: String) = communityDao.hardDelete(id)

    // ============ COMMUNITY MEMBERS ============
    suspend fun saveCommunityMember(member: CommunityMemberEntity) = communityMemberDao.upsert(member)
    suspend fun saveCommunityMembers(members: List<CommunityMemberEntity>) = communityMemberDao.upsertAll(members)
    suspend fun getCommunityMember(communityId: String, userId: String) = communityMemberDao.get(communityId, userId)
    fun getCommunityMembers(communityId: String): Flow<List<CommunityMemberEntity>> = communityMemberDao.getByCommunity(communityId)
    suspend fun getCommunityModerators(communityId: String) = communityMemberDao.getModerators(communityId)
    fun getUserCommunities(userId: String): Flow<List<CommunityMemberEntity>> = communityMemberDao.getByUser(userId)
    suspend fun countCommunityMembers(communityId: String) = communityMemberDao.countByCommunity(communityId)
    suspend fun removeCommunityMember(communityId: String, userId: String) = communityMemberDao.remove(communityId, userId)
    suspend fun removeAllCommunityMembers(communityId: String) = communityMemberDao.removeAll(communityId)
    suspend fun updateCommunityMemberRole(communityId: String, userId: String, role: String) = communityMemberDao.updateRole(communityId, userId, role)
    suspend fun setCommunityMemberMuted(communityId: String, userId: String, muted: Boolean, until: Long) = communityMemberDao.setMuted(communityId, userId, muted, until)

    // ============ CHANNEL MEMBERS ============
    suspend fun saveChannelMember(member: ChannelMemberEntity) = channelMemberDao.upsert(member)
    suspend fun saveChannelMembers(members: List<ChannelMemberEntity>) = channelMemberDao.upsertAll(members)
    suspend fun getChannelMember(channelId: String, userId: String) = channelMemberDao.get(channelId, userId)
    fun getChannelMembers(channelId: String): Flow<List<ChannelMemberEntity>> = channelMemberDao.getByChannel(channelId)
    suspend fun getChannelModerators(channelId: String) = channelMemberDao.getModerators(channelId)
    fun getUserChannels(userId: String): Flow<List<ChannelMemberEntity>> = channelMemberDao.getByUser(userId)
    suspend fun countChannelMembers(channelId: String) = channelMemberDao.countByChannel(channelId)
    suspend fun removeChannelMember(channelId: String, userId: String) = channelMemberDao.remove(channelId, userId)
    suspend fun removeAllChannelMembers(channelId: String) = channelMemberDao.removeAll(channelId)
    suspend fun updateChannelMemberRole(channelId: String, userId: String, role: String) = channelMemberDao.updateRole(channelId, userId, role)
    suspend fun setChannelMemberMuted(channelId: String, userId: String, muted: Boolean, until: Long) = channelMemberDao.setMuted(channelId, userId, muted, until)
    suspend fun updateChannelMemberLastRead(channelId: String, userId: String, msgId: String) = channelMemberDao.updateLastRead(channelId, userId, msgId)

    // ============ LIVE STREAMS ============
    suspend fun saveLiveStream(stream: LiveStreamEntity) = liveStreamDao.upsert(stream)
    suspend fun saveLiveStreams(streams: List<LiveStreamEntity>) = liveStreamDao.upsertAll(streams)
    suspend fun getLiveStream(id: String) = liveStreamDao.getById(id)
    fun getLiveStreamsByChannel(channelId: String): Flow<List<LiveStreamEntity>> = liveStreamDao.getByChannel(channelId)
    suspend fun getActiveLiveStreamByChannel(channelId: String) = liveStreamDao.getActiveByChannel(channelId)
    suspend fun getLiveStreamsByHost(hostId: String, limit: Int, offset: Int) = liveStreamDao.getByHost(hostId, limit, offset)
    suspend fun getLiveStreamsByCommunity(communityId: String, limit: Int, offset: Int) = liveStreamDao.getByCommunity(communityId, limit, offset)
    suspend fun getActiveLiveStreams(limit: Int) = liveStreamDao.getActiveStreams(limit)
    suspend fun getUpcomingLiveStreams(now: Long, limit: Int) = liveStreamDao.getUpcomingStreams(now, limit)
    suspend fun countLiveStreamsByChannel(channelId: String) = liveStreamDao.countByChannel(channelId)
    suspend fun updateLiveStreamStatus(id: String, status: String, startedAt: Long, ts: Long) = liveStreamDao.updateStatus(id, status, startedAt, ts)
    suspend fun endLiveStream(id: String, endedAt: Long, duration: Long, peak: Int, unique: Long, watchTime: Long, recStatus: String, recUrl: String?, ts: Long) = liveStreamDao.endStream(id, endedAt, duration, peak, unique, watchTime, recStatus, recUrl, ts)
    suspend fun updateLiveStreamPeakViewers(id: String, peak: Int, ts: Long) = liveStreamDao.updatePeakViewers(id, peak, ts)
    suspend fun softDeleteLiveStream(id: String, ts: Long) = liveStreamDao.softDelete(id, ts)
    suspend fun hardDeleteLiveStream(id: String) = liveStreamDao.hardDelete(id)

    // ============ LIVE STREAM VIEWERS ============
    suspend fun saveLiveStreamViewer(viewer: LiveStreamViewerEntity) = liveStreamViewerDao.upsert(viewer)
    suspend fun saveLiveStreamViewers(viewers: List<LiveStreamViewerEntity>) = liveStreamViewerDao.upsertAll(viewers)
    suspend fun getLiveStreamViewer(streamId: String, userId: String) = liveStreamViewerDao.get(streamId, userId)
    fun getActiveLiveStreamViewers(streamId: String): Flow<List<LiveStreamViewerEntity>> = liveStreamViewerDao.getActiveViewers(streamId)
    suspend fun countActiveLiveStreamViewers(streamId: String) = liveStreamViewerDao.countActiveViewers(streamId)
    suspend fun getLiveStreamViewers(streamId: String, limit: Int, offset: Int) = liveStreamViewerDao.getByStream(streamId, limit, offset)
    suspend fun getUserLiveStreamViewers(userId: String, limit: Int, offset: Int) = liveStreamViewerDao.getByUser(userId, limit, offset)
    suspend fun leaveLiveStream(streamId: String, userId: String, leftAt: Long, duration: Long) = liveStreamViewerDao.leave(streamId, userId, leftAt, duration)
    suspend fun incrementLiveStreamWatchTime(streamId: String, userId: String, delta: Long) = liveStreamViewerDao.incrementWatchTime(streamId, userId, delta)
    suspend fun clearLiveStreamViewers(streamId: String) = liveStreamViewerDao.clearByStream(streamId)

    // ============ LIVE STREAM RECORDINGS ============
    suspend fun saveLiveStreamRecording(recording: LiveStreamRecordingEntity) = liveStreamRecordingDao.upsert(recording)
    suspend fun getLiveStreamRecording(id: String) = liveStreamRecordingDao.getById(id)
    suspend fun getLiveStreamRecordingsByStream(streamId: String) = liveStreamRecordingDao.getByStream(streamId)
    suspend fun getLiveStreamRecordingsByHost(hostId: String, limit: Int, offset: Int) = liveStreamRecordingDao.getByHost(hostId, limit, offset)
    suspend fun getLiveStreamRecordingsByStatus(status: String, limit: Int) = liveStreamRecordingDao.getByStatus(status, limit)
    suspend fun updateRecordingProcessingStatus(id: String, status: String, started: Long, ts: Long) = liveStreamRecordingDao.updateProcessingStatus(id, status, started, ts)
    suspend fun markRecordingReady(id: String, hls: String, thumb: String?, size: Long, completed: Long, ts: Long) = liveStreamRecordingDao.markReady(id, hls, thumb, size, completed, ts)
    suspend fun markRecordingFailed(id: String, error: String, ts: Long) = liveStreamRecordingDao.markFailed(id, error, ts)

    // ============ LIVE STREAM CHAT ============
    suspend fun saveLiveStreamChatMessage(message: LiveStreamChatEntity) = liveStreamChatDao.insert(message)
    suspend fun saveLiveStreamChatMessages(messages: List<LiveStreamChatEntity>) = liveStreamChatDao.insertAll(messages)
    suspend fun getLiveStreamChatMessages(streamId: String, limit: Int, offset: Int) = liveStreamChatDao.getByStream(streamId, limit, offset)
    suspend fun getLiveStreamChatMessagesByType(streamId: String, type: String, limit: Int) = liveStreamChatDao.getByStreamAndType(streamId, type, limit)
    suspend fun countLiveStreamChatMessages(streamId: String) = liveStreamChatDao.countByStream(streamId)
    suspend fun softDeleteLiveStreamChatMessage(id: String, ts: Long, by: String) = liveStreamChatDao.softDelete(id, ts, by)
    suspend fun cleanupOldLiveStreamChatMessages(streamId: String, cutoff: Long) = liveStreamChatDao.cleanupOldMessages(streamId, cutoff)

    // ============ LIVE STREAM REACTIONS ============
    suspend fun saveLiveStreamReaction(reaction: LiveStreamReactionEntity) = liveStreamReactionDao.upsert(reaction)
    fun getLiveStreamReactions(streamId: String): Flow<List<LiveStreamReactionEntity>> = liveStreamReactionDao.getByStream(streamId)
    suspend fun getLiveStreamUserReactions(streamId: String, userId: String) = liveStreamReactionDao.getByUser(streamId, userId)
    suspend fun getLiveStreamReactionSummary(streamId: String) = liveStreamReactionDao.getReactionSummary(streamId)
    suspend fun incrementLiveStreamReaction(streamId: String, userId: String, emoji: String, ts: Long) = liveStreamReactionDao.increment(streamId, userId, emoji, ts)
    suspend fun removeLiveStreamReaction(streamId: String, userId: String, emoji: String) = liveStreamReactionDao.remove(streamId, userId, emoji)

    // ============ LIVE STREAM GIFTS ============
    suspend fun saveLiveStreamGift(gift: LiveStreamGiftEntity) = liveStreamGiftDao.insert(gift)
    suspend fun getLiveStreamGifts(streamId: String, limit: Int, offset: Int) = liveStreamGiftDao.getByStream(streamId, limit, offset)
    suspend fun getUserLiveStreamGifts(senderId: String, limit: Int, offset: Int) = liveStreamGiftDao.getBySender(senderId, limit, offset)
    suspend fun getLiveStreamTotalGiftsAmount(streamId: String) = liveStreamGiftDao.getTotalAmount(streamId)
    suspend fun getLiveStreamTopGifters(streamId: String, limit: Int) = liveStreamGiftDao.getTopGifters(streamId, limit)

    // ============ MEDIA FILES ============
    suspend fun saveMediaFile(file: MediaFileEntity) = mediaFileDao.upsert(file)
    suspend fun saveMediaFiles(files: List<MediaFileEntity>) = mediaFileDao.upsertAll(files)
    suspend fun getMediaFile(id: String) = mediaFileDao.getById(id)
    fun getMediaFilesByConversation(conversationId: String): Flow<List<MediaFileEntity>> = mediaFileDao.getByConversation(conversationId)
    suspend fun getMediaFilesByConversationAndType(conversationId: String, type: String) = mediaFileDao.getByConversationAndType(conversationId, type)
    suspend fun getMediaFilesByOwner(ownerId: String, limit: Int, offset: Int) = mediaFileDao.getByOwner(ownerId, limit, offset)
    suspend fun getMediaFileByMessageId(messageId: String) = mediaFileDao.getByMessageId(messageId)
    suspend fun getMediaFilesByStatus(status: String, limit: Int) = mediaFileDao.getByStatus(status, limit)
    suspend fun updateMediaFileStatus(id: String, status: String, url: String?, ts: Long) = mediaFileDao.updateStatus(id, status, url, ts)
    suspend fun softDeleteMediaFile(id: String, ts: Long) = mediaFileDao.softDelete(id, ts)
    suspend fun hardDeleteMediaFile(id: String) = mediaFileDao.hardDelete(id)

    // ============ DEVICES ============
    suspend fun saveDevice(device: DeviceEntity) = deviceDao.upsert(device)
    suspend fun saveDevices(devices: List<DeviceEntity>) = deviceDao.upsertAll(devices)
    suspend fun getDevice(id: String) = deviceDao.getById(id)
    fun getDevicesByUser(userId: String): Flow<List<DeviceEntity>> = deviceDao.getByUser(userId)
    suspend fun getDeviceByToken(token: String) = deviceDao.getByToken(token)
    suspend fun getActiveDevicesByUser(userId: String) = deviceDao.getActiveByUser(userId)
    suspend fun updateDeviceLastActive(id: String, ts: Long) = deviceDao.updateLastActive(id, ts)
    suspend fun updateDeviceTokens(id: String, token: String?, voip: String?, push: Boolean, voipEnabled: Boolean, ts: Long) = deviceDao.updateTokens(id, token, voip, push, voipEnabled, ts)
    suspend fun setDeviceTrusted(id: String, trusted: Boolean, ts: Long) = deviceDao.setTrusted(id, trusted, ts)
    suspend fun deactivateDevice(id: String, ts: Long) = deviceDao.deactivate(id, ts)
    suspend fun deleteDevice(id: String) = deviceDao.delete(id)
    suspend fun cleanupOldDevices(userId: String, cutoff: Long) = deviceDao.cleanupOldDevices(userId, cutoff)

    // ============ USER PROFILES ============
    suspend fun saveUserProfile(profile: UserProfileEntity) = userProfileDao.upsert(profile)
    suspend fun saveUserProfiles(profiles: List<UserProfileEntity>) = userProfileDao.upsertAll(profiles)
    suspend fun getUserProfile(id: String) = userProfileDao.getById(id)
    suspend fun getUserProfileByUsername(username: String) = userProfileDao.getByUsername(username)
    suspend fun searchUserProfiles(query: String, limit: Int) = userProfileDao.search(query, limit)
    suspend fun getOnlineUsers(limit: Int) = userProfileDao.getOnlineUsers(limit)
    suspend fun updateUserProfileStatus(id: String, status: String, ts: Long) = userProfileDao.updateStatus(id, status, ts)
    suspend fun updateUserProfileCustomStatus(id: String, status: String?, emoji: String?, expires: Long, ts: Long) = userProfileDao.updateCustomStatus(id, status, emoji, expires, ts)
    suspend fun updateUserProfileAvatar(id: String, url: String, ts: Long) = userProfileDao.updateAvatar(id, url, ts)
    suspend fun deleteUserProfile(id: String) = userProfileDao.delete(id)

    // ============ APP SETTINGS ============
    suspend fun setAppSetting(key: String, value: String) = appSettingDao.set(AppSettingEntity(key, value, System.currentTimeMillis()))
    suspend fun getAppSetting(key: String) = appSettingDao.getString(key)
    suspend fun getAppSettingEntity(key: String) = appSettingDao.get(key)
    fun getAllAppSettings(): Flow<List<AppSettingEntity>> = appSettingDao.getAll()
    suspend fun deleteAppSetting(key: String) = appSettingDao.delete(key)

    // ============ LIVE STREAM DRAFTS ============
    suspend fun saveLiveStreamDraft(draft: LiveStreamDraftEntity) = liveStreamDraftDao.upsert(draft)
    suspend fun getLiveStreamDraft(channelId: String) = liveStreamDraftDao.getByChannel(channelId)
    suspend fun getLiveStreamDraftsByHost(hostId: String) = liveStreamDraftDao.getByHost(hostId)
    suspend fun deleteLiveStreamDraftByChannel(channelId: String) = liveStreamDraftDao.deleteByChannel(channelId)
    suspend fun deleteLiveStreamDraftsByHost(hostId: String) = liveStreamDraftDao.deleteByHost(hostId)
}
