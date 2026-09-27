package com.red.server.sync

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "device_sync_events")
class DeviceSyncEvent(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @Column(name = "user_id", nullable = false)
    var userId: UUID,

    @Column(name = "device_id", nullable = false)
    var deviceId: Int,

    @Column(name = "event_type", nullable = false, length = 50)
    var eventType: String,

    @Column(name = "reference_id", length = 100)
    var referenceId: String? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    var payload: String,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "delivered_at")
    var deliveredAt: Instant? = null
)

@Entity
@Table(name = "device_sync_states")
class DeviceSyncState(
    @EmbeddedId
    var id: DeviceSyncStateId,

    @Column(name = "last_sync_seq", nullable = false)
    var lastSyncSeq: Long = 0,

    @Column(name = "last_sync_time", nullable = false)
    var lastSyncTime: Instant = Instant.now()
)

@Embeddable
data class DeviceSyncStateId(
    @Column(name = "user_id", nullable = false)
    var userId: UUID,

    @Column(name = "device_id", nullable = false)
    var deviceId: Int
) : java.io.Serializable

@Entity
@Table(
    name = "group_message_receipts",
    uniqueConstraints = [UniqueConstraint(columnNames = ["message_uuid", "user_id"])]
)
class GroupMessageReceipt(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @Column(name = "group_id", nullable = false)
    var groupId: UUID,

    @Column(name = "message_uuid", nullable = false, length = 64)
    var messageUuid: String,

    @Column(name = "user_id", nullable = false)
    var userId: UUID,

    @Column(name = "status", nullable = false, length = 20)
    var status: String,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),

    // V67: طوابع دورة حياة الإيصال — تُملأ تراكميًا (لا تُصفَّر عند الترقية).
    @Column(name = "delivered_at")
    var deliveredAt: Instant? = null,

    @Column(name = "read_at")
    var readAt: Instant? = null,

    @Column(name = "played_at")
    var playedAt: Instant? = null
)

@Entity
@Table(name = "group_mutations_log")
class GroupMutationLog(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @Column(name = "group_id", nullable = false)
    var groupId: UUID,

    @Column(name = "actor_id", nullable = false)
    var actorId: UUID,

    @Column(name = "mutation_type", nullable = false, length = 50)
    var mutationType: String,

    @Column(name = "target_user_id")
    var targetUserId: UUID? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "previous_state", columnDefinition = "jsonb")
    var previousState: String? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "new_state", columnDefinition = "jsonb")
    var newState: String? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now()
)

@Entity
@Table(name = "user_chat_states")
class UserChatState(
    @EmbeddedId
    var id: UserChatStateId,

    @Column(name = "is_muted", nullable = false)
    var isMuted: Boolean = false,

    @Column(name = "mute_until")
    var muteUntil: Instant? = null,

    @Column(name = "is_archived", nullable = false)
    var isArchived: Boolean = false,

    @Column(name = "is_pinned", nullable = false)
    var isPinned: Boolean = false,

    @Column(name = "pin_order")
    var pinOrder: Int = 0,

    @Column(name = "unread_count", nullable = false)
    var unreadCount: Int = 0,

    @Column(name = "last_activity_time", nullable = false)
    var lastActivityTime: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
)

@Embeddable
data class UserChatStateId(
    @Column(name = "user_id", nullable = false)
    var userId: UUID,

    @Column(name = "chat_id", nullable = false, length = 100)
    var chatId: String
) : java.io.Serializable
