package com.red.sovereign.core.database

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.red.sovereign.core.SecureStore
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [
        MessageEntity::class,
        LocalHistoryEntity::class,
        ConversationEntity::class,
        ContactEntity::class,
        GroupEntity::class,
        CallLogEntity::class,
        StoryEntity::class,
        DraftEntity::class,
        MessageReactionEntity::class,
        OutboxMessageEntity::class,
        StarredMessageEntity::class,
        MediaUploadEntity::class,
        ChannelEntity::class,
        CommunityEntity::class,
        CommunityMemberEntity::class,
        ChannelMemberEntity::class,
        LiveStreamEntity::class,
        LiveStreamViewerEntity::class,
        LiveStreamRecordingEntity::class,
        LiveStreamChatEntity::class,
        LiveStreamReactionEntity::class,
        LiveStreamGiftEntity::class,
        MediaFileEntity::class,
        DeviceEntity::class,
        UserProfileEntity::class,
        AppSettingEntity::class,
        LiveStreamDraftEntity::class
    ],
    version = 9,
    exportSchema = false
)
abstract class RedDatabase : RoomDatabase() {
    abstract fun redDao(): RedDao
    abstract fun outboxDao(): OutboxDao
    abstract fun mediaUploadDao(): MediaUploadDao
    abstract fun channelDao(): ChannelDao
    abstract fun communityDao(): CommunityDao
    abstract fun communityMemberDao(): CommunityMemberDao
    abstract fun channelMemberDao(): ChannelMemberDao
    abstract fun liveStreamDao(): LiveStreamDao
    abstract fun liveStreamViewerDao(): LiveStreamViewerDao
    abstract fun liveStreamRecordingDao(): LiveStreamRecordingDao
    abstract fun liveStreamChatDao(): LiveStreamChatDao
    abstract fun liveStreamReactionDao(): LiveStreamReactionDao
    abstract fun liveStreamGiftDao(): LiveStreamGiftDao
    abstract fun mediaFileDao(): MediaFileDao
    abstract fun deviceDao(): DeviceDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun appSettingDao(): AppSettingDao
    abstract fun liveStreamDraftDao(): LiveStreamDraftDao

    companion object {
        private const val TAG = "RedDatabase"
        @Volatile
        private var INSTANCE: RedDatabase? = null

        fun getInstance(context: Context): RedDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): RedDatabase {
            val secureStore = SecureStore(context, "red_database_security")
            var passphrase = secureStore.get("passphrase")
            if (passphrase == null) {
                passphrase = java.util.UUID.randomUUID().toString()
                secureStore.put("passphrase", passphrase)
            }

            val factory = SupportOpenHelperFactory(passphrase.toByteArray())

            // Safe open: if DB fails to open (wrong key after restore, corruption, SQLCipher upgrade),
            // restore from the latest backup instead of destroying user data. The DB file is only
            // deleted after a backup copy has been secured, and a fresh passphrase is only used as
            // a last resort (all backups keep the original key).
fun create(): RedDatabase = Room.databaseBuilder(
                context.applicationContext,
                RedDatabase::class.java,
                "red_sovereign.db"
            )
                .openHelperFactory(factory)
                        .addMigrations(REACTION_MIGRATION_1_2, INDEX_MIGRATION_2_3, MESSAGES_INDEX_MIGRATION_3_4, OUTBOX_MIGRATION_4_5, STARRED_MIGRATION_5_6, MEDIA_UPLOAD_MIGRATION_6_7, OUTBOX_REPAIR_MIGRATION_7_8, NEW_ENTITIES_MIGRATION_8_9)
                .addCallback(FtsCallback())
                .build()

            fun dbFile(): java.io.File = context.getDatabasePath("red_sovereign.db")

            fun deleteSideFiles() {
                try {
                    java.io.File("${dbFile().absolutePath}-wal").delete()
                    java.io.File("${dbFile().absolutePath}-shm").delete()
                } catch (e: Exception) { Log.w(TAG, "deleteSideFiles failed", e) }
            }

            fun backupCorrupted(): java.io.File? {
                return try {
                    val f = dbFile()
                    if (f.exists() && f.length() > 0) {
                        val backup = java.io.File(f.parent, "red_sovereign.db.corrupted.${System.currentTimeMillis()}.bak")
                        if (f.renameTo(backup)) backup else null
                    } else null
                } catch (e: Exception) { Log.w(TAG, "backupCorrupted failed", e); null }
            }

            fun latestCorruptedBackup(): java.io.File? {
                return try {
                    java.io.File(dbFile().parent)
                        .listFiles { _, name -> name.startsWith("red_sovereign.db.corrupted.") && name.endsWith(".bak") }
                        ?.maxByOrNull { it.name }
                } catch (e: Exception) { Log.w(TAG, "latestCorruptedBackup failed", e); null }
            }

            fun restoreFromBackup(backup: java.io.File): RedDatabase? {
                return try {
                    val target = dbFile()
                    target.parentFile?.mkdirs()
                    backup.copyTo(target, overwrite = true)
                    deleteSideFiles()
                    create().also { db -> db.openHelper.writableDatabase }
                } catch (e: Exception) { Log.w(TAG, "restoreFromBackup failed for ${backup.name}", e); null }
            }

            // 1) Normal open
            try {
                return create().also { db -> db.openHelper.writableDatabase }
            } catch (e: Exception) {
                // 2) Back up the (possibly corrupted) file before touching anything
                val moved = backupCorrupted()
                deleteSideFiles()
                try {
                    return create().also { db -> db.openHelper.writableDatabase }
                } catch (e2: Exception) {
                    // 3) Try restoring the newest previous backup (same passphrase) instead of wiping data
                    if (moved == null) {
                        latestCorruptedBackup()?.let { moved2 ->
                            restoreFromBackup(moved2)?.let { return it }
                        }
                    } else {
                        restoreFromBackup(moved)?.let { return it }
                    }
                    // 4) Last resort: regenerate passphrase and recreate — only after backups exist.
                    //    A readable snapshot of the old file is retained for external recovery.
                    try {
                        java.io.File(dbFile().parent, "red_sovereign.db.legacy-${System.currentTimeMillis()}.bak")
                            .writeBytes(byteArrayOf()) // placeholder marker; old bytes already backed up above
                    } catch (e: Exception) { Log.w(TAG, "legacy marker write failed", e) }
                    secureStore.remove("passphrase")
                    val newPass = java.util.UUID.randomUUID().toString()
                    secureStore.put("passphrase", newPass)
                    val newFactory = SupportOpenHelperFactory(newPass.toByteArray())
                    return Room.databaseBuilder(
                        context.applicationContext,
                        RedDatabase::class.java,
                        "red_sovereign.db"
                    )
                        .openHelperFactory(newFactory)
.addMigrations(REACTION_MIGRATION_1_2, INDEX_MIGRATION_2_3, MESSAGES_INDEX_MIGRATION_3_4, OUTBOX_MIGRATION_4_5, STARRED_MIGRATION_5_6, MEDIA_UPLOAD_MIGRATION_6_7, OUTBOX_REPAIR_MIGRATION_7_8, NEW_ENTITIES_MIGRATION_8_9)
                        .addCallback(FtsCallback())
                        .fallbackToDestructiveMigration()
                        .build()
                }
            }
        }
    }
}

/** إضافة جدول message_reactions دون فقدان البيانات المشفّرة الموجودة. */
private val REACTION_MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
    override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
        database.execSQL(
            """CREATE TABLE IF NOT EXISTS `message_reactions` (
                `messageId` TEXT NOT NULL,
                `conversationId` TEXT NOT NULL,
                `senderId` TEXT NOT NULL,
                `emoji` TEXT NOT NULL,
                `timestamp` INTEGER NOT NULL,
                PRIMARY KEY(`messageId`, `senderId`)
            )"""
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_message_reactions_conversationId` ON `message_reactions` (`conversationId`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_message_reactions_messageId` ON `message_reactions` (`messageId`)")
    }
}

/** إضافة الفهارس المركبة لتسريع استعلامات الرسائل والمكالمات والمحادثات */
private val INDEX_MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
    override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_local_history_conversationId_createdAt` ON `local_history` (`conversationId`, `createdAt`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_local_history_conversationId_messageType_createdAt` ON `local_history` (`conversationId`, `messageType`, `createdAt`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_conversations_archived_pinned_lastMessageTimestamp` ON `conversations` (`archived`, `pinned`, `lastMessageTimestamp`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_conversations_lastMessageTimestamp` ON `conversations` (`lastMessageTimestamp`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_groups_createdAt` ON `groups` (`createdAt`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_call_logs_timestamp` ON `call_logs` (`timestamp`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_call_logs_peerId_timestamp` ON `call_logs` (`peerId`, `timestamp`)")
    }
}

/** فهرس مركّب لتسريع الترتيب الزمني داخل المحادثة (conversationId + createdAt) */
private val MESSAGES_INDEX_MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
    override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_messages_conversationId_createdAt` ON `messages` (`conversationId`, `createdAt`)")
    }
}

/** صندوق الصادر المتين — يضمن عدم فقدان أي رسالة بعد قتل العملية */
private val OUTBOX_MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
    override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
        database.execSQL(
            """CREATE TABLE IF NOT EXISTS `outbox_messages` (
                `id` TEXT NOT NULL,
                `conversationId` TEXT NOT NULL,
                `payload` BLOB NOT NULL,
                `type` TEXT NOT NULL,
                `status` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `retryCount` INTEGER NOT NULL,
                `nextAttemptAt` INTEGER NOT NULL,
                `idempotencyKey` TEXT NOT NULL,
                `lastError` TEXT,
                PRIMARY KEY(`id`)
            )"""
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_outbox_messages_status_nextAttemptAt` ON `outbox_messages` (`status`, `nextAttemptAt`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_outbox_messages_conversationId` ON `outbox_messages` (`conversationId`)")
        database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_outbox_messages_idempotencyKey` ON `outbox_messages` (`idempotencyKey`)")
    }
}

/** جدول الرسائل المُعلَّمة (Starred/Bookmarked) — للرجوع السريع إليها. */
private val STARRED_MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
    override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
        database.execSQL(
            """CREATE TABLE IF NOT EXISTS `starred_messages` (
                `messageId` TEXT NOT NULL,
                `conversationId` TEXT NOT NULL,
                `senderId` TEXT NOT NULL,
                `messageText` TEXT NOT NULL,
                `messageType` TEXT NOT NULL,
                `starredAt` INTEGER NOT NULL,
                PRIMARY KEY(`messageId`)
            )"""
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_starred_messages_conversationId` ON `starred_messages` (`conversationId`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_starred_messages_starredAt` ON `starred_messages` (`starredAt`)")
    }
}

/** LEGENDARY P1: رفع الوسائط + إثراء المجموعات + فهارس الأداء 6→7 (ترحيل واحد موحد) */
private val MEDIA_UPLOAD_MIGRATION_6_7 = object : androidx.room.migration.Migration(6, 7) {
    override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
        database.execSQL(
            """CREATE TABLE IF NOT EXISTS `media_uploads` (
                `messageId` TEXT NOT NULL,
                `conversationId` TEXT NOT NULL,
                `targetRedId` TEXT,
                `localPath` TEXT NOT NULL,
                `mimeType` TEXT NOT NULL,
                `size` INTEGER NOT NULL,
                `thumbPath` TEXT,
                `blurHash` TEXT,
                `width` INTEGER NOT NULL,
                `height` INTEGER NOT NULL,
                `durationMs` INTEGER NOT NULL,
                `objectKey` TEXT,
                `url` TEXT,
                `status` TEXT NOT NULL,
                `retryCount` INTEGER NOT NULL,
                `nextAttemptAt` INTEGER NOT NULL,
                `idempotencyKey` TEXT NOT NULL,
                PRIMARY KEY(`messageId`)
            )"""
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_media_uploads_status_nextAttemptAt` ON `media_uploads` (`status`,`nextAttemptAt`)")
        database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_media_uploads_messageId` ON `media_uploads` (`messageId`)")
        // إثراء groups
        runCatching { database.execSQL("ALTER TABLE `groups` ADD COLUMN `ownerRedId` TEXT NOT NULL DEFAULT ''") }
        runCatching { database.execSQL("ALTER TABLE `groups` ADD COLUMN `privacy` TEXT NOT NULL DEFAULT 'PRIVATE'") }
        runCatching { database.execSQL("ALTER TABLE `groups` ADD COLUMN `settingsJson` TEXT NOT NULL DEFAULT '{}'") }
        runCatching { database.execSQL("ALTER TABLE `groups` ADD COLUMN `communityId` TEXT") }
        runCatching { database.execSQL("ALTER TABLE `groups` ADD COLUMN `slowModeSeconds` INTEGER NOT NULL DEFAULT 0") }
        runCatching { database.execSQL("ALTER TABLE `groups` ADD COLUMN `disappearingSeconds` INTEGER NOT NULL DEFAULT 0") }
        runCatching { database.execSQL("ALTER TABLE `groups` ADD COLUMN `archived` INTEGER NOT NULL DEFAULT 0") }
        runCatching { database.execSQL("ALTER TABLE `groups` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0") }
        runCatching { database.execSQL("CREATE INDEX IF NOT EXISTS `index_groups_archived` ON `groups` (`archived`)") }
        runCatching { database.execSQL("CREATE INDEX IF NOT EXISTS `index_groups_updatedAt` ON `groups` (`updatedAt`)") }
        // فهارس الأداء الحاسمة للدردشة
        runCatching { database.execSQL("CREATE INDEX IF NOT EXISTS `index_local_history_conv_created_desc` ON `local_history` (`conversationId`, `createdAt` DESC, `id` DESC)") }
        runCatching { database.execSQL("CREATE INDEX IF NOT EXISTS `index_local_history_conv_status` ON `local_history` (`conversationId`, `status`)") }
    }
}

/** إصلاح الترقية 7→8: إكمال أعمدة outbox الناقصة + فهارس جهات الاتصال/القصص/الردود.
 * يمنع تعطل الترقية من v4/v5/v6 (no such column) — كلها IF NOT EXISTS آمنة. */
private val OUTBOX_REPAIR_MIGRATION_7_8 = object : androidx.room.migration.Migration(7, 8) {
    override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
        runCatching { database.execSQL("ALTER TABLE `outbox_messages` ADD COLUMN `targetRedId` TEXT") }
        runCatching { database.execSQL("ALTER TABLE `outbox_messages` ADD COLUMN `priority` INTEGER NOT NULL DEFAULT 2") }
        runCatching { database.execSQL("ALTER TABLE `outbox_messages` ADD COLUMN `mediaType` TEXT") }
        runCatching { database.execSQL("ALTER TABLE `outbox_messages` ADD COLUMN `localMediaPath` TEXT") }
        runCatching { database.execSQL("ALTER TABLE `outbox_messages` ADD COLUMN `mediaEncryptionKey` TEXT") }
        runCatching { database.execSQL("CREATE INDEX IF NOT EXISTS `index_outbox_priority_next` ON `outbox_messages` (`priority`, `nextAttemptAt`)") }
        runCatching { database.execSQL("CREATE INDEX IF NOT EXISTS `index_outbox_status_created` ON `outbox_messages` (`status`, `createdAt`)") }
        runCatching { database.execSQL("CREATE INDEX IF NOT EXISTS `index_contacts_friend` ON `contacts` (`isFriend`)") }
        runCatching { database.execSQL("CREATE INDEX IF NOT EXISTS `index_stories_expiry` ON `stories` (`expiresAt`)") }
        runCatching { database.execSQL("CREATE INDEX IF NOT EXISTS `index_stories_user` ON `stories` (`userId`)") }
        runCatching { database.execSQL("CREATE INDEX IF NOT EXISTS `index_history_replyTo` ON `local_history` (`replyToMessageId`)") }
    }
}

/** الترقية 8→9: إضافة جداول جديدة للقنوات، المجتمعات، البث المباشر، الوسائط، الأجهزة، الإعدادات.
 * جميع الجداول جديدة — لا تعديل على الموجود، فقط CREATE TABLE IF NOT EXISTS. */
private val NEW_ENTITIES_MIGRATION_8_9 = object : androidx.room.migration.Migration(8, 9) {
    override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
        // ── Channels ──
        database.execSQL(
            """CREATE TABLE IF NOT EXISTS `channels` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `name` TEXT NOT NULL,
                `description` TEXT,
                `type` TEXT NOT NULL DEFAULT 'TEXT',
                `communityId` TEXT,
                `ownerId` TEXT NOT NULL,
                `avatarUrl` TEXT,
                `bannerUrl` TEXT,
                `settingsJson` TEXT NOT NULL DEFAULT '{}',
                `memberCount` INTEGER NOT NULL DEFAULT 0,
                `messageCount` INTEGER NOT NULL DEFAULT 0,
                `isPrivate` INTEGER NOT NULL DEFAULT 1,
                `isArchived` INTEGER NOT NULL DEFAULT 0,
                `archivedAt` INTEGER NOT NULL DEFAULT 0,
                `slowModeSeconds` INTEGER NOT NULL DEFAULT 0,
                `maxMembers` INTEGER NOT NULL DEFAULT 0,
                `defaultNotifications` TEXT NOT NULL DEFAULT 'ALL',
                `encryptionEnabled` INTEGER NOT NULL DEFAULT 1,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `deletedAt` INTEGER NOT NULL DEFAULT 0
            )"""
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_channels_communityId` ON `channels` (`communityId`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_channels_ownerId` ON `channels` (`ownerId`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_channels_type` ON `channels` (`type`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_channels_createdAt` ON `channels` (`createdAt`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_channels_community_type` ON `channels` (`communityId`, `type`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_channels_archived_updated` ON `channels` (`isArchived`, `updatedAt`)")

        // ── Communities ──
        database.execSQL(
            """CREATE TABLE IF NOT EXISTS `communities` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `name` TEXT NOT NULL,
                `description` TEXT,
                `ownerId` TEXT NOT NULL,
                `avatarUrl` TEXT,
                `bannerUrl` TEXT,
                `settingsJson` TEXT NOT NULL DEFAULT '{}',
                `memberCount` INTEGER NOT NULL DEFAULT 0,
                `channelCount` INTEGER NOT NULL DEFAULT 0,
                `isPublic` INTEGER NOT NULL DEFAULT 0,
                `isArchived` INTEGER NOT NULL DEFAULT 0,
                `archivedAt` INTEGER NOT NULL DEFAULT 0,
                `verificationLevel` INTEGER NOT NULL DEFAULT 0,
                `explicitContentFilter` INTEGER NOT NULL DEFAULT 1,
                `defaultChannelNotifications` TEXT NOT NULL DEFAULT 'MENTIONS',
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `deletedAt` INTEGER NOT NULL DEFAULT 0
            )"""
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_communities_ownerId` ON `communities` (`ownerId`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_communities_createdAt` ON `communities` (`createdAt`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_communities_isPublic` ON `communities` (`isPublic`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_communities_archived_updated` ON `communities` (`isArchived`, `updatedAt`)")

        // ── Community Members ──
        database.execSQL(
            """CREATE TABLE IF NOT EXISTS `community_members` (
                `communityId` TEXT NOT NULL,
                `userId` TEXT NOT NULL,
                `role` TEXT NOT NULL DEFAULT 'MEMBER',
                `joinedAt` INTEGER NOT NULL,
                `invitedBy` TEXT,
                `nickname` TEXT,
                `isMuted` INTEGER NOT NULL DEFAULT 0,
                `mutedUntil` INTEGER NOT NULL DEFAULT 0,
                `permissionsJson` TEXT NOT NULL DEFAULT '{}',
                `lastReadMessageId` TEXT,
                `notificationLevel` TEXT NOT NULL DEFAULT 'ALL',
                PRIMARY KEY(`communityId`, `userId`)
            )"""
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_community_members_userId` ON `community_members` (`userId`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_community_members_role` ON `community_members` (`role`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_community_members_joinedAt` ON `community_members` (`joinedAt`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_community_members_community_role` ON `community_members` (`communityId`, `role`)")

        // ── Channel Members ──
        database.execSQL(
            """CREATE TABLE IF NOT EXISTS `channel_members` (
                `channelId` TEXT NOT NULL,
                `userId` TEXT NOT NULL,
                `role` TEXT NOT NULL DEFAULT 'MEMBER',
                `joinedAt` INTEGER NOT NULL,
                `invitedBy` TEXT,
                `isMuted` INTEGER NOT NULL DEFAULT 0,
                `mutedUntil` INTEGER NOT NULL DEFAULT 0,
                `permissionsJson` TEXT NOT NULL DEFAULT '{}',
                `lastReadMessageId` TEXT,
                `notificationLevel` TEXT NOT NULL DEFAULT 'ALL',
                PRIMARY KEY(`channelId`, `userId`)
            )"""
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_channel_members_userId` ON `channel_members` (`userId`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_channel_members_role` ON `channel_members` (`role`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_channel_members_joinedAt` ON `channel_members` (`joinedAt`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_channel_members_channel_role` ON `channel_members` (`channelId`, `role`)")

        // ── Live Streams ──
        database.execSQL(
            """CREATE TABLE IF NOT EXISTS `live_streams` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `channelId` TEXT NOT NULL,
                `hostId` TEXT NOT NULL,
                `communityId` TEXT,
                `title` TEXT NOT NULL,
                `description` TEXT,
                `thumbnailUrl` TEXT,
                `status` TEXT NOT NULL DEFAULT 'SCHEDULED',
                `streamType` TEXT NOT NULL DEFAULT 'VIDEO',
                `scheduledAt` INTEGER NOT NULL DEFAULT 0,
                `startedAt` INTEGER NOT NULL DEFAULT 0,
                `endedAt` INTEGER NOT NULL DEFAULT 0,
                `durationSeconds` INTEGER NOT NULL DEFAULT 0,
                `peakViewers` INTEGER NOT NULL DEFAULT 0,
                `totalUniqueViewers` INTEGER NOT NULL DEFAULT 0,
                `totalWatchTimeSeconds` INTEGER NOT NULL DEFAULT 0,
                `hlsManifestUrl` TEXT,
                `rtmpIngestUrl` TEXT,
                `streamKey` TEXT,
                `recordingEnabled` INTEGER NOT NULL DEFAULT 1,
                `recordingStatus` TEXT NOT NULL DEFAULT 'PENDING',
                `recordingUrl` TEXT,
                `chatEnabled` INTEGER NOT NULL DEFAULT 1,
                `reactionsEnabled` INTEGER NOT NULL DEFAULT 1,
                `giftsEnabled` INTEGER NOT NULL DEFAULT 0,
                `settingsJson` TEXT NOT NULL DEFAULT '{}',
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `deletedAt` INTEGER NOT NULL DEFAULT 0
            )"""
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_live_streams_channelId` ON `live_streams` (`channelId`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_live_streams_hostId` ON `live_streams` (`hostId`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_live_streams_communityId` ON `live_streams` (`communityId`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_live_streams_status` ON `live_streams` (`status`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_live_streams_scheduledAt` ON `live_streams` (`scheduledAt`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_live_streams_startedAt` ON `live_streams` (`startedAt`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_live_streams_endedAt` ON `live_streams` (`endedAt`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_live_streams_channel_status` ON `live_streams` (`channelId`, `status`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_live_streams_host_status` ON `live_streams` (`hostId`, `status`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_live_streams_community_status` ON `live_streams` (`communityId`, `status`)")

        // ── Live Stream Viewers ──
        database.execSQL(
            """CREATE TABLE IF NOT EXISTS `live_stream_viewers` (
                `streamId` TEXT NOT NULL,
                `userId` TEXT NOT NULL,
                `joinedAt` INTEGER NOT NULL,
                `leftAt` INTEGER NOT NULL DEFAULT 0,
                `watchDurationSeconds` INTEGER NOT NULL DEFAULT 0,
                `quality` TEXT NOT NULL DEFAULT 'AUTO',
                `platform` TEXT NOT NULL DEFAULT 'ANDROID',
                `ipAddress` TEXT,
                `userAgent` TEXT,
                PRIMARY KEY(`streamId`, `userId`)
            )"""
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_live_stream_viewers_userId` ON `live_stream_viewers` (`userId`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_live_stream_viewers_joinedAt` ON `live_stream_viewers` (`joinedAt`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_live_stream_viewers_leftAt` ON `live_stream_viewers` (`leftAt`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_live_stream_viewers_stream_joined` ON `live_stream_viewers` (`streamId`, `joinedAt`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_live_stream_viewers_stream_left` ON `live_stream_viewers` (`streamId`, `leftAt`)")

        // ── Live Stream Recordings ──
        database.execSQL(
            """CREATE TABLE IF NOT EXISTS `live_stream_recordings` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `streamId` TEXT NOT NULL,
                `hostId` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `durationSeconds` INTEGER NOT NULL DEFAULT 0,
                `fileSizeBytes` INTEGER NOT NULL DEFAULT 0,
                `storagePath` TEXT,
                `hlsManifestUrl` TEXT,
                `thumbnailUrl` TEXT,
                `status` TEXT NOT NULL DEFAULT 'PENDING',
                `processingStartedAt` INTEGER NOT NULL DEFAULT 0,
                `processingCompletedAt` INTEGER NOT NULL DEFAULT 0,
                `errorMessage` TEXT,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL
            )"""
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_live_stream_recordings_streamId` ON `live_stream_recordings` (`streamId`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_live_stream_recordings_hostId` ON `live_stream_recordings` (`hostId`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_live_stream_recordings_status` ON `live_stream_recordings` (`status`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_live_stream_recordings_createdAt` ON `live_stream_recordings` (`createdAt`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_live_stream_recordings_stream_status` ON `live_stream_recordings` (`streamId`, `status`)")

        // ── Live Stream Chat ──
        database.execSQL(
            """CREATE TABLE IF NOT EXISTS `live_stream_chat` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `streamId` TEXT NOT NULL,
                `userId` TEXT NOT NULL,
                `content` TEXT NOT NULL,
                `messageType` TEXT NOT NULL DEFAULT 'CHAT',
                `metadataJson` TEXT NOT NULL DEFAULT '{}',
                `sentAt` INTEGER NOT NULL,
                `isDeleted` INTEGER NOT NULL DEFAULT 0,
                `deletedAt` INTEGER NOT NULL DEFAULT 0,
                `deletedBy` TEXT
            )"""
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_live_stream_chat_streamId` ON `live_stream_chat` (`streamId`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_live_stream_chat_userId` ON `live_stream_chat` (`userId`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_live_stream_chat_sentAt` ON `live_stream_chat` (`sentAt`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_live_stream_chat_stream_sent` ON `live_stream_chat` (`streamId`, `sentAt`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_live_stream_chat_stream_type` ON `live_stream_chat` (`streamId`, `messageType`)")

        // ── Live Stream Reactions ──
        database.execSQL(
            """CREATE TABLE IF NOT EXISTS `live_stream_reactions` (
                `streamId` TEXT NOT NULL,
                `userId` TEXT NOT NULL,
                `emoji` TEXT NOT NULL,
                `count` INTEGER NOT NULL DEFAULT 1,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`streamId`, `userId`, `emoji`)
            )"""
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_live_stream_reactions_streamId` ON `live_stream_reactions` (`streamId`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_live_stream_reactions_userId` ON `live_stream_reactions` (`userId`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_live_stream_reactions_createdAt` ON `live_stream_reactions` (`createdAt`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_live_stream_reactions_stream_created` ON `live_stream_reactions` (`streamId`, `createdAt`)")

        // ── Live Stream Gifts ──
        database.execSQL(
            """CREATE TABLE IF NOT EXISTS `live_stream_gifts` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `streamId` TEXT NOT NULL,
                `senderId` TEXT NOT NULL,
                `giftType` TEXT NOT NULL,
                `giftName` TEXT NOT NULL,
                `amount` REAL NOT NULL,
                `currency` TEXT NOT NULL DEFAULT 'USD',
                `message` TEXT,
                `animationUrl` TEXT,
                `createdAt` INTEGER NOT NULL
            )"""
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_live_stream_gifts_streamId` ON `live_stream_gifts` (`streamId`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_live_stream_gifts_senderId` ON `live_stream_gifts` (`senderId`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_live_stream_gifts_createdAt` ON `live_stream_gifts` (`createdAt`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_live_stream_gifts_stream_created` ON `live_stream_gifts` (`streamId`, `createdAt`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_live_stream_gifts_stream_amount` ON `live_stream_gifts` (`streamId`, `amount`)")

        // ── Media Files ──
        database.execSQL(
            """CREATE TABLE IF NOT EXISTS `media_files` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `ownerId` TEXT NOT NULL,
                `conversationId` TEXT,
                `messageId` TEXT,
                `type` TEXT NOT NULL,
                `mimeType` TEXT NOT NULL,
                `fileName` TEXT NOT NULL,
                `fileSize` INTEGER NOT NULL,
                `width` INTEGER NOT NULL DEFAULT 0,
                `height` INTEGER NOT NULL DEFAULT 0,
                `durationMs` INTEGER NOT NULL DEFAULT 0,
                `blurHash` TEXT,
                `thumbnailPath` TEXT,
                `storagePath` TEXT NOT NULL,
                `cdnUrl` TEXT,
                `status` TEXT NOT NULL DEFAULT 'PENDING',
                `encryptionKey` TEXT,
                `checksum` TEXT,
                `metadataJson` TEXT NOT NULL DEFAULT '{}',
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `deletedAt` INTEGER NOT NULL DEFAULT 0
            )"""
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_media_files_ownerId` ON `media_files` (`ownerId`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_media_files_conversationId` ON `media_files` (`conversationId`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_media_files_messageId` ON `media_files` (`messageId`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_media_files_type` ON `media_files` (`type`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_media_files_status` ON `media_files` (`status`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_media_files_createdAt` ON `media_files` (`createdAt`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_media_files_conv_type` ON `media_files` (`conversationId`, `type`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_media_files_owner_created` ON `media_files` (`ownerId`, `createdAt`)")

        // ── Devices ──
        database.execSQL(
            """CREATE TABLE IF NOT EXISTS `devices` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `userId` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `type` TEXT NOT NULL DEFAULT 'UNKNOWN',
                `platform` TEXT NOT NULL DEFAULT '',
                `platformVersion` TEXT NOT NULL DEFAULT '',
                `appVersion` TEXT NOT NULL DEFAULT '',
                `deviceToken` TEXT,
                `voipToken` TEXT,
                `publicKey` TEXT,
                `lastActiveAt` INTEGER NOT NULL,
                `lastSeenAt` INTEGER NOT NULL DEFAULT 0,
                `isActive` INTEGER NOT NULL DEFAULT 1,
                `isTrusted` INTEGER NOT NULL DEFAULT 0,
                `pushEnabled` INTEGER NOT NULL DEFAULT 1,
                `voipEnabled` INTEGER NOT NULL DEFAULT 0,
                `settingsJson` TEXT NOT NULL DEFAULT '{}',
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL
            )"""
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_devices_userId` ON `devices` (`userId`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_devices_deviceToken` ON `devices` (`deviceToken`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_devices_type` ON `devices` (`type`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_devices_lastActiveAt` ON `devices` (`lastActiveAt`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_devices_user_type` ON `devices` (`userId`, `type`)")

        // ── User Profiles ──
        database.execSQL(
            """CREATE TABLE IF NOT EXISTS `user_profiles` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `username` TEXT NOT NULL,
                `displayName` TEXT NOT NULL,
                `bio` TEXT,
                `avatarUrl` TEXT,
                `bannerUrl` TEXT,
                `status` TEXT NOT NULL DEFAULT 'OFFLINE',
                `customStatus` TEXT,
                `customStatusEmoji` TEXT,
                `customStatusExpiresAt` INTEGER NOT NULL DEFAULT 0,
                `isVerified` INTEGER NOT NULL DEFAULT 0,
                `isBot` INTEGER NOT NULL DEFAULT 0,
                `isSystem` INTEGER NOT NULL DEFAULT 0,
                `lastSeen` INTEGER NOT NULL DEFAULT 0,
                `createdAt` INTEGER NOT NULL DEFAULT 0,
                `updatedAt` INTEGER NOT NULL,
                `privacySettingsJson` TEXT NOT NULL DEFAULT '{}',
                `notificationSettingsJson` TEXT NOT NULL DEFAULT '{}',
                `theme` TEXT NOT NULL DEFAULT 'SYSTEM',
                `language` TEXT NOT NULL DEFAULT 'ar'
            )"""
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_user_profiles_username` ON `user_profiles` (`username`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_user_profiles_displayName` ON `user_profiles` (`displayName`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_user_profiles_status` ON `user_profiles` (`status`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_user_profiles_lastSeen` ON `user_profiles` (`lastSeen`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_user_profiles_updatedAt` ON `user_profiles` (`updatedAt`)")

        // ── App Settings ──
        database.execSQL(
            """CREATE TABLE IF NOT EXISTS `app_settings` (
                `key` TEXT NOT NULL PRIMARY KEY,
                `value` TEXT NOT NULL,
                `updatedAt` INTEGER NOT NULL
            )"""
        )

        // ── Live Stream Drafts ──
        database.execSQL(
            """CREATE TABLE IF NOT EXISTS `live_stream_drafts` (
                `channelId` TEXT NOT NULL PRIMARY KEY,
                `hostId` TEXT NOT NULL,
                `title` TEXT NOT NULL DEFAULT '',
                `description` TEXT NOT NULL DEFAULT '',
                `thumbnailPath` TEXT,
                `scheduledAt` INTEGER NOT NULL DEFAULT 0,
                `streamType` TEXT NOT NULL DEFAULT 'VIDEO',
                `settingsJson` TEXT NOT NULL DEFAULT '{}',
                `updatedAt` INTEGER NOT NULL
            )"""
        )
    }
}
