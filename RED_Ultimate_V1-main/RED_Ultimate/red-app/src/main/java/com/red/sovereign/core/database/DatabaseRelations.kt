package com.red.sovereign.core.database

import androidx.room.Embedded
import androidx.room.Relation

/**
 * علاقات Room المعلنة (لا تغيّر السكيما — @Relation للقراءة فقط).
 * تمنع صفوفًا يتيمة ظاهريًا: كل قراءة للسجل تُجلب مع سياقها الذري.
 */
data class ConversationWithMessages(
    @Embedded val conversation: ConversationEntity,
    @Relation(parentColumn = "id", entityColumn = "conversationId")
    val history: List<LocalHistoryEntity>,
    @Relation(parentColumn = "id", entityColumn = "conversationId")
    val reactions: List<MessageReactionEntity>
)

data class ConversationWithOutbox(
    @Embedded val conversation: ConversationEntity,
    @Relation(parentColumn = "id", entityColumn = "conversationId")
    val outbox: List<OutboxMessageEntity>,
    @Relation(parentColumn = "id", entityColumn = "conversationId")
    val uploads: List<MediaUploadEntity>
)

data class MessageWithReactions(
    @Embedded val message: LocalHistoryEntity,
    @Relation(parentColumn = "id", entityColumn = "messageId")
    val reactions: List<MessageReactionEntity>
)

data class PollWithVotes(
    @Embedded val poll: MightyPollEntity,
    @Relation(parentColumn = "id", entityColumn = "pollId")
    val votes: List<PollVoteEntity>
)

data class GroupWithPins(
    @Embedded val group: GroupEntity,
    @Relation(parentColumn = "id", entityColumn = "groupId")
    val pins: List<PinEntity>
)
