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
    fun getActiveConversations(): Flow<List<ConversationEntity>> = dao.getActiveConversations()
    suspend fun getConversation(id: String) = dao.getConversation(id)
    suspend fun setPinned(id: String, pinned: Boolean) = dao.setPinned(id, pinned)
    suspend fun setArchived(id: String, archived: Boolean) = dao.setArchived(id, archived)
    suspend fun setMutedUntil(id: String, until: Long) = dao.setMutedUntil(id, until)

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
    suspend fun search(convId: String, query: String): List<LocalHistoryEntity> {
        // Since we store encrypted data, we pull and decrypt for search
        // In a production app, we would use a separate FTS index for decrypted text
        return emptyList() // TODO: Implement properly with FTS
    }
}
