package com.red.sovereign.core

import android.content.Context
import com.red.sovereign.core.database.LocalRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * مصدر واحد لتثبيت/أرشفة/كتم/حذف المحادثة.
 * يحدّث Room وMessageStore معاً حتى لا تنفصل قائمة الدردشات عن ورقة الجهة.
 */
class ConversationActions(context: Context) {
    private val app = context.applicationContext
    private val repository = LocalRepository(app)
    private val messages = MessageStore(app)
    val organization = ChatOrganizationStore(app)

    suspend fun pin(conversationId: String, pinned: Boolean) = withContext(Dispatchers.IO) {
        repository.setPinned(conversationId, pinned)
        messages.setConversationPreference(conversationId, "pinned", if (pinned) 1 else 0)
    }

    suspend fun archive(conversationId: String, archived: Boolean) = withContext(Dispatchers.IO) {
        repository.setArchived(conversationId, archived)
        messages.setConversationPreference(conversationId, "archived", if (archived) 1 else 0)
        if (archived) organization.markUnread(conversationId, false)
    }

    suspend fun mute(conversationId: String, untilMs: Long) = withContext(Dispatchers.IO) {
        repository.setMutedUntil(conversationId, untilMs)
        messages.setConversationPreference(conversationId, "muted_until", untilMs)
    }

    suspend fun unmute(conversationId: String) = mute(conversationId, 0L)

    suspend fun markRead(conversationId: String) = withContext(Dispatchers.IO) {
        repository.clearUnread(conversationId)
        organization.markUnread(conversationId, false)
    }

    suspend fun markUnread(conversationId: String) = withContext(Dispatchers.IO) {
        repository.setUnreadCount(conversationId, 1)
        organization.markUnread(conversationId, true)
    }

    suspend fun delete(conversationId: String) = withContext(Dispatchers.IO) {
        repository.deleteConversation(conversationId)
        organization.markUnread(conversationId, false)
        organization.setPinnedMessages(conversationId, emptyList())
    }

    fun toggleFavorite(conversationId: String) = organization.toggleFavorite(conversationId)

    fun setLocked(conversationId: String, locked: Boolean) = organization.setLocked(conversationId, locked)

    companion object {
        const val MUTE_8H = 8L * 60 * 60 * 1000
        const val MUTE_1W = 7L * 24 * 60 * 60 * 1000
        const val MUTE_FOREVER = Long.MAX_VALUE / 2
    }
}
