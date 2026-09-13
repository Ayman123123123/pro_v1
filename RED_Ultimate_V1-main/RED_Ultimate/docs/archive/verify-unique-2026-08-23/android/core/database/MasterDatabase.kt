package com.red.sovereign.core.database

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 🗄️ YOUNES Sovereign Room Database — الإصدار 5
 * 
 * الكيانات (Entities):
 * ┌──────────────────────┬─────────────┬────────────────────────────────┐
 * │ الجدول                │ النوع        │ الوصف                          │
 * ├──────────────────────┼─────────────┼────────────────────────────────┤
 * │ messages             │ Room Entity  │ رسائل المحادثات المخزنة محليًا   │
 * │ conversations        │ Room Entity  │ المحادثات والآخر رسالة          │
 * │ call_logs            │ Room Entity  │ سجل المكالمات الموحد            │
 * │ pstn_logs            │ Room Entity  │ سجل مكالمات Dinstar             │
 * │ stories              │ Room Entity  │ القصص المخزنة محليًا            │
 * │ story_views          │ Room Entity  │ مشاهدات القصص                  │
 * │ contacts             │ Room Entity  │ جهات الاتصال السيادية           │
 * │ groups               │ Room Entity  │ المجموعات المحلية               │
 * │ group_members        │ Room Entity  │ أعضاء المجموعات                 │
 * │ notifications        │ Room Entity  │ الإشعارات المحلية               │
 * │ media_attachments    │ Room Entity  │ مرفقات الوسائط المحلية          │
 * │ privacy_settings     │ Room Entity  │ إعدادات الخصوصية               │
 * │ user_profile         │ Room Entity  │ الملف الشخصي المحلي             │
 * │ draft_messages       │ Room Entity  │ رسائل المسودة                   │
 * │ reactions            │ Room Entity  │ تفاعلات الرسائل                 │
 * └──────────────────────┴─────────────┴────────────────────────────────┘
 */
@Database(
    entities = [
        MessageEntity::class,
        ConversationEntity::class,
        CallLogEntity::class,
        PstnLogEntity::class,
        StoryEntity::class,
        StoryViewEntity::class,
        ContactEntity::class,
        GroupEntity::class,
        GroupMemberEntity::class,
        NotificationEntity::class,
        MediaAttachmentEntity::class,
        PrivacySettingsEntity::class,
        UserProfileEntity::class,
        DraftMessageEntity::class,
        MessageReactionEntity::class,
        DinstarPortSnapshotEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class MasterDatabase : RoomDatabase() {
    abstract fun masterDao(): MasterDao
    abstract fun storyDao(): StoryDao
    abstract fun callLogDao(): CallLogDao
    abstract fun contactDao(): ContactDao
    abstract fun groupDao(): GroupDao
    abstract fun notificationDao(): NotificationDao
    abstract fun privacyDao(): PrivacyDao
    abstract fun dinstarDao(): DinstarDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `stories` (
                        `id` TEXT NOT NULL, `userId` TEXT NOT NULL, `mediaUrl` TEXT NOT NULL,
                        `type` TEXT NOT NULL, `caption` TEXT, `backgroundColor` TEXT,
                        `timestamp` INTEGER NOT NULL, `expiresAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `story_views` (
                        `storyId` TEXT NOT NULL, `viewerId` TEXT NOT NULL, `timestamp` INTEGER NOT NULL,
                        PRIMARY KEY(`storyId`, `viewerId`),
                        FOREIGN KEY(`storyId`) REFERENCES `stories`(`id`) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_stories_userId` ON `stories` (`userId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_stories_expiresAt` ON `stories` (`expiresAt`)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // سجل المكالمات الموحد
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `call_logs` (
                        `id` TEXT NOT NULL, `callType` TEXT NOT NULL, `direction` TEXT NOT NULL,
                        `remoteUserId` TEXT, `remoteName` TEXT NOT NULL, `remoteAvatar` TEXT,
                        `phoneNumber` TEXT, `status` TEXT NOT NULL, `durationMs` INTEGER NOT NULL DEFAULT 0,
                        `dinstarPort` INTEGER, `signalStrength` INTEGER, `viewerCount` INTEGER NOT NULL DEFAULT 0,
                        `isRecorded` INTEGER NOT NULL DEFAULT 0, `timestamp` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `idx_call_logs_type` ON `call_logs` (`callType`, `timestamp`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `idx_call_logs_user` ON `call_logs` (`remoteUserId`, `timestamp`)")

                // جهات الاتصال
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `contacts` (
                        `userId` TEXT NOT NULL, `redId` TEXT NOT NULL, `name` TEXT NOT NULL,
                        `username` TEXT, `avatarUrl` TEXT, `about` TEXT, `isOnline` INTEGER NOT NULL DEFAULT 0,
                        `statusType` TEXT NOT NULL DEFAULT 'OFFLINE', `statusText` TEXT,
                        `yemeniPhoneNumber` TEXT, `isBlocked` INTEGER NOT NULL DEFAULT 0,
                        `isMuted` INTEGER NOT NULL DEFAULT 0, `isPinned` INTEGER NOT NULL DEFAULT 0,
                        `lastSeenTimestamp` INTEGER, `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`userId`)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `idx_contacts_redId` ON `contacts` (`redId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `idx_contacts_name` ON `contacts` (`name`)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // المجموعات
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `groups` (
                        `id` TEXT NOT NULL, `name` TEXT NOT NULL, `description` TEXT,
                        `avatarUrl` TEXT, `privacy` TEXT NOT NULL DEFAULT 'PRIVATE',
                        `memberCount` INTEGER NOT NULL DEFAULT 0, `onlineCount` INTEGER NOT NULL DEFAULT 0,
                        `myRole` TEXT NOT NULL DEFAULT 'MEMBER', `isMuted` INTEGER NOT NULL DEFAULT 0,
                        `isPinned` INTEGER NOT NULL DEFAULT 0, `isAnnouncement` INTEGER NOT NULL DEFAULT 0,
                        `createdBy` TEXT, `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `group_members` (
                        `groupId` TEXT NOT NULL, `userId` TEXT NOT NULL, `name` TEXT NOT NULL,
                        `avatarUrl` TEXT, `role` TEXT NOT NULL DEFAULT 'MEMBER',
                        `customTitle` TEXT, `isOnline` INTEGER NOT NULL DEFAULT 0,
                        `joinedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`groupId`, `userId`),
                        FOREIGN KEY(`groupId`) REFERENCES `groups`(`id`) ON DELETE CASCADE
                    )
                """.trimIndent())

                // الإشعارات
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `notifications` (
                        `id` TEXT NOT NULL, `type` TEXT NOT NULL, `title` TEXT NOT NULL,
                        `body` TEXT NOT NULL, `senderId` TEXT, `senderName` TEXT,
                        `threadId` TEXT, `isRead` INTEGER NOT NULL DEFAULT 0,
                        `priority` TEXT NOT NULL DEFAULT 'NORMAL',
                        `actionLabel` TEXT, `timestamp` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `idx_notifs_type` ON `notifications` (`type`, `timestamp`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `idx_notifs_unread` ON `notifications` (`isRead`, `timestamp`)")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // مرفقات الوسائط
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `media_attachments` (
                        `id` TEXT NOT NULL, `messageId` TEXT NOT NULL, `mediaKey` TEXT NOT NULL,
                        `mimeType` TEXT NOT NULL, `localPath` TEXT, `remoteUrl` TEXT,
                        `sizeBytes` INTEGER NOT NULL DEFAULT 0, `width` INTEGER, `height` INTEGER,
                        `durationMs` INTEGER, `caption` TEXT, `fileName` TEXT,
                        `isDownloaded` INTEGER NOT NULL DEFAULT 0, `isUploading` INTEGER NOT NULL DEFAULT 0,
                        `uploadProgress` REAL NOT NULL DEFAULT 0.0,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `idx_media_message` ON `media_attachments` (`messageId`)")

                // إعدادات الخصوصية
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `privacy_settings` (
                        `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        `lastSeen` TEXT NOT NULL DEFAULT 'EVERYONE',
                        `onlineStatus` TEXT NOT NULL DEFAULT 'EVERYONE',
                        `profilePhoto` TEXT NOT NULL DEFAULT 'EVERYONE',
                        `about` TEXT NOT NULL DEFAULT 'EVERYONE',
                        `status` TEXT NOT NULL DEFAULT 'CONTACTS',
                        `readReceipts` TEXT NOT NULL DEFAULT 'EVERYONE',
                        `calls` TEXT NOT NULL DEFAULT 'CONTACTS',
                        `groupsAdd` TEXT NOT NULL DEFAULT 'EVERYONE',
                        `liveLocation` TEXT NOT NULL DEFAULT 'NOBODY'
                    )
                """.trimIndent())

                // الملف الشخصي
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `user_profile` (
                        `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        `userId` TEXT NOT NULL, `redId` TEXT NOT NULL, `name` TEXT NOT NULL,
                        `username` TEXT NOT NULL, `avatarUrl` TEXT, `about` TEXT,
                        `statusType` TEXT NOT NULL DEFAULT 'ONLINE', `statusCustomText` TEXT,
                        `statusVisibleTo` TEXT NOT NULL DEFAULT 'EVERYONE',
                        `themePreference` TEXT NOT NULL DEFAULT 'SOVEREIGN_DARK',
                        `accentColor` TEXT NOT NULL DEFAULT 'CYAN',
                        `fontScale` REAL NOT NULL DEFAULT 1.0,
                        `chatBubbleStyle` TEXT NOT NULL DEFAULT 'ROUNDED',
                        `language` TEXT NOT NULL DEFAULT 'ar', `isRtl` INTEGER NOT NULL DEFAULT 1
                    )
                """.trimIndent())

                // رسائل المسودة
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `draft_messages` (
                        `conversationId` TEXT NOT NULL, `text` TEXT NOT NULL,
                        `mediaUri` TEXT, `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`conversationId`)
                    )
                """.trimIndent())

                // تفاعلات الرسائل
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `reactions` (
                        `messageId` TEXT NOT NULL, `userId` TEXT NOT NULL, `emoji` TEXT NOT NULL,
                        `addedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`messageId`, `userId`)
                    )
                """.trimIndent())

                // أعمدة إضافية للمحادثات
                try {
                    db.execSQL("ALTER TABLE `conversations` ADD COLUMN `isPinned` INTEGER NOT NULL DEFAULT 0")
                } catch (_: Exception) {}
                try {
                    db.execSQL("ALTER TABLE `conversations` ADD COLUMN `isMuted` INTEGER NOT NULL DEFAULT 0")
                } catch (_: Exception) {}
                try {
                    db.execSQL("ALTER TABLE `conversations` ADD COLUMN `draftText` TEXT")
                } catch (_: Exception) {}

                // أعمدة إضافية للقصص
                try {
                    db.execSQL("ALTER TABLE `stories` ADD COLUMN `visibleTo` TEXT NOT NULL DEFAULT 'EVERYONE'")
                } catch (_: Exception) {}
                try {
                    db.execSQL("ALTER TABLE `stories` ADD COLUMN `isMyStory` INTEGER NOT NULL DEFAULT 0")
                } catch (_: Exception) {}
                try {
                    db.execSQL("ALTER TABLE `stories` ADD COLUMN `viewCount` INTEGER NOT NULL DEFAULT 0")
                } catch (_: Exception) {}
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // لقطات منافذ Dinstar المحلية
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `dinstar_port_snapshots` (
                        `portIndex` INTEGER NOT NULL,
                        `radioType` TEXT NOT NULL DEFAULT 'GSM',
                        `registrationState` TEXT NOT NULL DEFAULT 'UNREGISTERED',
                        `callState` TEXT NOT NULL DEFAULT 'IDLE',
                        `signalPercent` INTEGER NOT NULL DEFAULT 0,
                        `signalRaw` INTEGER NOT NULL DEFAULT 0,
                        `gprsState` TEXT NOT NULL DEFAULT 'DETACH',
                        `operatorName` TEXT NOT NULL DEFAULT '',
                        `numberMasked` TEXT,
                        `simType` TEXT NOT NULL DEFAULT 'UNKNOWN',
                        `isHealthy` INTEGER NOT NULL DEFAULT 0,
                        `observedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`portIndex`)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `idx_dinstar_signal` ON `dinstar_port_snapshots` (`signalPercent`, `registrationState`)")
            }
        }

        val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
    }
}

// ════════════════════════════════════════════════════
// 💬 الرسائل
// ════════════════════════════════════════════════════

@Entity(tableName = "messages", indices = [
    Index(value = ["conversationId"], name = "idx_messages_conv"),
    Index(value = ["senderId"], name = "idx_messages_sender"),
    Index(value = ["timestamp"], name = "idx_messages_time")
])
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val senderId: String,
    val receiverId: String,
    val text: String,
    val messageType: String = "TEXT", // TEXT, IMAGE, VIDEO, VOICE, FILE, LOCATION, CONTACT, POLL
    val status: String = "SENT", // SENT, DELIVERED, READ, FAILED
    val replyToMessageId: String? = null,
    val forwardedFromId: String? = null,
    val isDeletedForMe: Boolean = false,
    val isDeletedForEveryone: Boolean = false,
    val isEdited: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

// ════════════════════════════════════════════════════
// 💬 المحادثات
// ════════════════════════════════════════════════════

@Entity(tableName = "conversations", indices = [
    Index(value = ["remoteUserId"], name = "idx_conversations_user"),
    Index(value = ["lastMessageTimestamp"], name = "idx_conversations_last")
])
data class ConversationEntity(
    @PrimaryKey val id: String,
    val remoteUserId: String,
    val remoteName: String,
    val remoteAvatar: String? = null,
    val isGroup: Boolean = false,
    val lastMessageText: String? = null,
    val lastMessageTimestamp: Long = 0,
    val unreadCount: Int = 0,
    val isPinned: Boolean = false,
    val isMuted: Boolean = false,
    val draftText: String? = null,
    val yemeniPhoneNumber: String? = null
)

// ════════════════════════════════════════════════════
// 📞 سجل المكالمات الموحد
// ════════════════════════════════════════════════════

@Entity(tableName = "call_logs", indices = [
    Index(value = ["callType", "timestamp"], name = "idx_call_logs_type"),
    Index(value = ["remoteUserId", "timestamp"], name = "idx_call_logs_user")
])
data class CallLogEntity(
    @PrimaryKey val id: String,
    val callType: String, // VOIP_AUDIO, VOIP_VIDEO, CONFERENCE, LIVE_BROADCAST, PSTN_DINSTAR, AUDIO_SPACE
    val direction: String, // INCOMING, OUTGOING
    val remoteUserId: String? = null,
    val remoteName: String,
    val remoteAvatar: String? = null,
    val phoneNumber: String? = null,
    val status: String = "ENDED",
    val durationMs: Long = 0,
    val dinstarPort: Int? = null,
    val signalStrength: Int? = null,
    val viewerCount: Int = 0,
    val isRecorded: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

// ════════════════════════════════════════════════════
// 📞 سجل PSTN
// ════════════════════════════════════════════════════

@Entity(tableName = "pstn_logs")
data class PstnLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val port: Int,
    val number: String,
    val direction: String,
    val duration: Long,
    val timestamp: Long = System.currentTimeMillis()
)

// ════════════════════════════════════════════════════
// 📖 القصص
// ════════════════════════════════════════════════════

@Entity(tableName = "stories", indices = [
    Index(value = ["userId"], name = "index_stories_userId"),
    Index(value = ["expiresAt"], name = "index_stories_expiresAt")
])
data class StoryEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val mediaUrl: String,
    val type: String,
    val caption: String? = null,
    val backgroundColor: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val expiresAt: Long = timestamp + 86400000,
    val visibleTo: String = "EVERYONE",
    val isMyStory: Boolean = false,
    val viewCount: Int = 0
)

@Entity(tableName = "story_views", primaryKeys = ["storyId", "viewerId"],
    foreignKeys = [ForeignKey(entity = StoryEntity::class, parentColumns = ["id"], childColumns = ["storyId"], onDelete = ForeignKey.CASCADE)])
data class StoryViewEntity(
    val storyId: String,
    val viewerId: String,
    val timestamp: Long = System.currentTimeMillis()
)

// ════════════════════════════════════════════════════
// 👥 جهات الاتصال
// ════════════════════════════════════════════════════

@Entity(tableName = "contacts", indices = [
    Index(value = ["redId"], name = "idx_contacts_redId"),
    Index(value = ["name"], name = "idx_contacts_name")
])
data class ContactEntity(
    @PrimaryKey val userId: String,
    val redId: String,
    val name: String,
    val username: String? = null,
    val avatarUrl: String? = null,
    val about: String? = null,
    val isOnline: Boolean = false,
    val statusType: String = "OFFLINE",
    val statusText: String? = null,
    val yemeniPhoneNumber: String? = null,
    val isBlocked: Boolean = false,
    val isMuted: Boolean = false,
    val isPinned: Boolean = false,
    val lastSeenTimestamp: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

// ════════════════════════════════════════════════════
// 👥 المجموعات
// ════════════════════════════════════════════════════

@Entity(tableName = "groups")
data class GroupEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String? = null,
    val avatarUrl: String? = null,
    val privacy: String = "PRIVATE",
    val memberCount: Int = 0,
    val onlineCount: Int = 0,
    val myRole: String = "MEMBER",
    val isMuted: Boolean = false,
    val isPinned: Boolean = false,
    val isAnnouncement: Boolean = false,
    val createdBy: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "group_members", primaryKeys = ["groupId", "userId"],
    foreignKeys = [ForeignKey(entity = GroupEntity::class, parentColumns = ["id"], childColumns = ["groupId"], onDelete = ForeignKey.CASCADE)])
data class GroupMemberEntity(
    val groupId: String,
    val userId: String,
    val name: String,
    val avatarUrl: String? = null,
    val role: String = "MEMBER",
    val customTitle: String? = null,
    val isOnline: Boolean = false,
    val joinedAt: Long = System.currentTimeMillis()
)

// ════════════════════════════════════════════════════
// 🔔 الإشعارات
// ════════════════════════════════════════════════════

@Entity(tableName = "notifications", indices = [
    Index(value = ["type", "timestamp"], name = "idx_notifs_type"),
    Index(value = ["isRead", "timestamp"], name = "idx_notifs_unread")
])
data class NotificationEntity(
    @PrimaryKey val id: String,
    val type: String,
    val title: String,
    val body: String,
    val senderId: String? = null,
    val senderName: String? = null,
    val threadId: String? = null,
    val isRead: Boolean = false,
    val priority: String = "NORMAL",
    val actionLabel: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

// ════════════════════════════════════════════════════
// 🖼️ مرفقات الوسائط
// ════════════════════════════════════════════════════

@Entity(tableName = "media_attachments", indices = [
    Index(value = ["messageId"], name = "idx_media_message")
])
data class MediaAttachmentEntity(
    @PrimaryKey val id: String,
    val messageId: String,
    val mediaKey: String,
    val mimeType: String,
    val localPath: String? = null,
    val remoteUrl: String? = null,
    val sizeBytes: Long = 0,
    val width: Int? = null,
    val height: Int? = null,
    val durationMs: Long? = null,
    val caption: String? = null,
    val fileName: String? = null,
    val isDownloaded: Boolean = false,
    val isUploading: Boolean = false,
    val uploadProgress: Float = 0f,
    val createdAt: Long = System.currentTimeMillis()
)

// ════════════════════════════════════════════════════
// 🔒 إعدادات الخصوصية
// ════════════════════════════════════════════════════

@Entity(tableName = "privacy_settings")
data class PrivacySettingsEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 1,
    val lastSeen: String = "EVERYONE",
    val onlineStatus: String = "EVERYONE",
    val profilePhoto: String = "EVERYONE",
    val about: String = "EVERYONE",
    val status: String = "CONTACTS",
    val readReceipts: String = "EVERYONE",
    val calls: String = "CONTACTS",
    val groupsAdd: String = "EVERYONE",
    val liveLocation: String = "NOBODY"
)

// ════════════════════════════════════════════════════
// 👤 الملف الشخصي المحلي
// ════════════════════════════════════════════════════

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 1,
    val userId: String = "",
    val redId: String = "",
    val name: String = "",
    val username: String = "",
    val avatarUrl: String? = null,
    val about: String? = null,
    val statusType: String = "ONLINE",
    val statusCustomText: String? = null,
    val statusVisibleTo: String = "EVERYONE",
    val themePreference: String = "SOVEREIGN_DARK",
    val accentColor: String = "CYAN",
    val fontScale: Float = 1.0f,
    val chatBubbleStyle: String = "ROUNDED",
    val language: String = "ar",
    val isRtl: Boolean = true
)

// ════════════════════════════════════════════════════
// ✏️ رسائل المسودة
// ════════════════════════════════════════════════════

@Entity(tableName = "draft_messages")
data class DraftMessageEntity(
    @PrimaryKey val conversationId: String,
    val text: String,
    val mediaUri: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)

// ════════════════════════════════════════════════════
// ❤️ تفاعلات الرسائل
// ════════════════════════════════════════════════════

@Entity(tableName = "reactions", primaryKeys = ["messageId", "userId"])
data class MessageReactionEntity(
    val messageId: String,
    val userId: String,
    val emoji: String,
    val addedAt: Long = System.currentTimeMillis()
)

// ════════════════════════════════════════════════════
// 📡 لقطات منافذ Dinstar
// ════════════════════════════════════════════════════

@Entity(tableName = "dinstar_port_snapshots", indices = [
    Index(value = ["signalPercent", "registrationState"], name = "idx_dinstar_signal")
])
data class DinstarPortSnapshotEntity(
    @PrimaryKey val portIndex: Int,
    val radioType: String = "GSM",
    val registrationState: String = "UNREGISTERED",
    val callState: String = "IDLE",
    val signalPercent: Int = 0,
    val signalRaw: Int = 0,
    val gprsState: String = "DETACH",
    val operatorName: String = "",
    val numberMasked: String? = null,
    val simType: String = "UNKNOWN",
    val isHealthy: Boolean = false,
    val observedAt: Long = System.currentTimeMillis()
)
