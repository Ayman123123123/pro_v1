package com.red.sovereign.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(tableName = "messages", indices = [Index("conversationId"), Index("status")])
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val senderId: String,
    val receiverId: String,
    val payload: ByteArray,
    val type: String,
    val senderDeviceId: Int,
    val receiverDeviceId: Int,
    val ciphertextType: Int,
    val sequence: Long,
    val status: String,
    val createdAt: Long,
    val outgoing: Boolean
)

@Entity(tableName = "local_history", indices = [Index("conversationId")])
data class LocalHistoryEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val senderId: String,
    val encryptedPlaintext: ByteArray,
    val messageType: String,
    val createdAt: Long,
    val outgoing: Boolean,
    val status: String = "SENT"
)

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String,
    val peerId: String,
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val mutedUntil: Long = 0,
    val lastMessageText: String? = null,
    val lastMessageTimestamp: Long = 0,
    val unreadCount: Int = 0
)

@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey val redId: String,
    val username: String,
    val displayName: String,
    val avatarUrl: String? = null,
    val isFriend: Boolean = true,
    val isBlocked: Boolean = false,
    val lastSeen: Long = 0
)

@Entity(tableName = "groups")
data class GroupEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String? = null,
    val avatarUrl: String? = null,
    val myRole: String = "MEMBER",
    val memberCount: Int = 0,
    val createdAt: Long = 0
)

@Entity(tableName = "call_logs")
data class CallLogEntity(
    @PrimaryKey val id: String,
    val peerId: String,
    val type: String, // VOICE, VIDEO, DINSTAR
    val direction: String, // INCOMING, OUTGOING
    val status: String, // COMPLETED, MISSED, REJECTED
    val timestamp: Long,
    val durationMs: Long = 0
)

@Entity(tableName = "stories")
data class StoryEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val mediaUrl: String,
    val mediaType: String,
    val caption: String? = null,
    val timestamp: Long,
    val expiresAt: Long,
    val isMyStory: Boolean = false
)

@Entity(tableName = "drafts")
data class DraftEntity(
    @PrimaryKey val conversationId: String,
    val text: String,
    val timestamp: Long
)
