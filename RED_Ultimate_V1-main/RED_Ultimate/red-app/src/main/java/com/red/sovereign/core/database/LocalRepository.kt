package com.red.sovereign.core.database

import android.content.Context
import kotlinx.coroutines.flow.Flow

class LocalRepository(context: Context) {
    private val dao = RedDatabase.getInstance(context).redDao()

    // --- Messages ---
    suspend fun saveMessage(message: MessageEntity) = dao.insertMessage(message)
    suspend fun saveLocalHistory(history: LocalHistoryEntity) = dao.insertLocalHistory(history)
    fun getLocalHistory(convId: String): Flow<List<LocalHistoryEntity>> = dao.getLocalHistory(convId)
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
    fun getFriends(): Flow<List<ContactEntity>> = dao.getFriends()

    // --- Groups ---
    suspend fun saveGroups(groups: List<GroupEntity>) = dao.insertGroups(groups)
    fun getGroups(): Flow<List<GroupEntity>> = dao.getGroups()

    // --- Call Logs ---
    suspend fun saveCallLog(log: CallLogEntity) = dao.insertCallLog(log)
    suspend fun saveCallLogs(logs: List<CallLogEntity>) = logs.forEach { dao.insertCallLog(it) } // Simplify batch for now
    fun getCallLogs(): Flow<List<CallLogEntity>> = dao.getCallLogs()

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

    /** يحذف رسالة من السجل المحلي (المفكوك) فقط — يُستخدم لحذف رسالة واحدة. */
    suspend fun deleteLocalMessage(messageId: String) = dao.deleteLocalHistory(messageId)

    /** يحذف كل بيانات محادثة: السجل المحلي + الرسائل + تفاعلاتها + صف المحادثة. */
    suspend fun deleteConversation(convId: String) {
        dao.deleteLocalHistoryByConversation(convId)
        dao.deleteMessagesByConversation(convId)
        dao.deleteReactionsByConversation(convId)
        dao.deleteConversationRow(convId)
    }

    // --- Global Search ---
    suspend fun searchAll(query: String): List<LocalHistoryEntity> {
        if (query.isBlank()) return emptyList()
        return dao.searchAllMessages("%${query.trim()}%")
    }

    /** وسائط محادثة (Flow) — لمعرض الوسائط. الصور/الفيديو/الملفات/الصوت. */
    fun mediaForConversation(convId: String) = dao.mediaForConversation(convId)
}
