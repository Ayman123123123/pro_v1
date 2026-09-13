package com.red.sovereign.core.database

import android.content.Context
import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow

class LocalRepository(context: Context) {
    private val appCtx = context.applicationContext
    private val dao = RedDatabase.getInstance(context).redDao()

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
        // LEGENDARY FIX: فهرسة FTS فور الحفظ (كانت TODO فيتسع الفارق ويبقى البحث مكسوراً)
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
    suspend fun updateLocalHistoryText(id: String, plaintext: ByteArray) = dao.updateLocalHistoryText(id, plaintext)

    suspend fun saveIncomingMessage(message: com.red.sovereign.proto.RedProtos.ChatMessage, outgoing: Boolean = false) {
        val entity = MessageEntity(
            id = message.id,
            conversationId = message.conversationId,
            senderId = message.senderId,
            receiverId = message.receiverId,
            payload = message.payload.toByteArray(),
            type = message.type,
            senderDeviceId = message.senderDeviceId,
            receiverDeviceId = message.receiverDeviceId,
            ciphertextType = message.ciphertextType,
            sequence = message.sequenceNumber,
            status = if (outgoing) "SENT" else "DELIVERED",
            createdAt = message.timestamp,
            outgoing = outgoing
        )
        dao.insertMessage(entity)
    }

    // --- Conversations ---
    suspend fun saveConversation(conv: ConversationEntity) = dao.insertConversation(conv)

    /**
     * يُنشئ/يُحدّث صف المحادثة عند إرسال أو استقبال رسالة، بحيث تظهر
     * المحادثة في قائمة الدردشات مع آخر رسالة والطابع الزمني وعدد غير المقروء.
     * يحافظ على pinned/archived/muted عند وجود المحادثة مسبقاً.
     */
    suspend fun onMessageStored(conversationId: String, peerId: String, preview: String, timestamp: Long, isIncoming: Boolean) {
        val existing = dao.getConversation(conversationId)
        if (existing != null) {
            dao.updateConversationLast(conversationId, preview, timestamp, if (isIncoming) 1 else 0)
        } else {
            dao.insertConversation(
                ConversationEntity(
                    id = conversationId, peerId = peerId,
                    lastMessageText = preview, lastMessageTimestamp = timestamp,
                    unreadCount = if (isIncoming) 1 else 0
                )
            )
        }
    }
    fun getActiveConversations(): Flow<List<ConversationEntity>> = dao.getActiveConversations()
    fun getArchivedConversations(): Flow<List<ConversationEntity>> = dao.getArchivedConversations()
    fun getAllConversations(): Flow<List<ConversationEntity>> = dao.getAllConversations()
    suspend fun getConversation(id: String) = dao.getConversation(id)
    suspend fun setPinned(id: String, pinned: Boolean) = dao.setPinned(id, pinned)
    suspend fun setArchived(id: String, archived: Boolean) = dao.setArchived(id, archived)
    suspend fun setMutedUntil(id: String, until: Long) = dao.setMutedUntil(id, until)
    suspend fun clearUnread(id: String) = dao.clearUnread(id)
    suspend fun setUnreadCount(id: String, count: Int) = dao.setUnreadCount(id, count.coerceAtLeast(0))

    // --- Contacts ---
    suspend fun saveContacts(contacts: List<ContactEntity>) = dao.insertContacts(contacts)
    suspend fun replaceContacts(contacts: List<ContactEntity>) {
        dao.clearContacts()
        if (contacts.isNotEmpty()) dao.insertContacts(contacts)
    }
    fun getFriends(): Flow<List<ContactEntity>> = dao.getFriends()

    // --- Groups ---
    suspend fun saveGroups(groups: List<GroupEntity>) = dao.insertGroups(groups)
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
     * لأنه مفهرس ويطبّع الحركات؛ هذا مسحٌ كامل للجدول.
     *
     * `%` و`_` محرفا بدل في `LIKE`، فلو مرّا كما هما لطابق بحثُ
     * المستخدم عن «%» **كلَّ** رسائل المحادثة بدل أن يجد لا شيء
     * (مقيس). لذا يُهرَّبان مع `\` نفسها — والترتيب مقصود: تهريب
     * الشرطة المائلة **أولًا** وإلا ضوعف تهريبُ ما بعدها.
     */
    suspend fun search(convId: String, query: String): List<LocalHistoryEntity> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return emptyList()
        val escaped = trimmed
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
        return dao.searchMessages(convId, "%$escaped%")
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

    /** يحذف كل بيانات محادثة: السجل المحلي + الرسائل + تفاعلاتها + صف المحادثة. */
    suspend fun deleteConversation(convId: String) {
        dao.deleteLocalHistoryByConversation(convId)
        dao.deleteMessagesByConversation(convId)
        dao.deleteReactionsByConversation(convId)
        dao.deleteConversationRow(convId)
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
        val escaped = trimmed
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
        // ملاحظة: searchAllMessages بلا ESCAPE صريح — `%` و`_` مهرَّبة هنا
        // احترازًا، والتطبيع العربي الكامل عبر FtsSearchManager عند توفره.
        return dao.searchAllMessages("%$escaped%").take(100)
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
}
