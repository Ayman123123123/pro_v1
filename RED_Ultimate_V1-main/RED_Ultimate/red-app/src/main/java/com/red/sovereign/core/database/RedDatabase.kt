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
        // V8 Ultimate - أقوى وأنسب وأحدث قواعد بيانات 2026 - كل الأنواع
        MightyPollEntity::class,
        PollVoteEntity::class,
        SovereignStoryEntity::class,
        PrivateNoteEntity::class,
        ProfileCustomizationEntity::class,
        SovereignGiftEntity::class,
        LiveCommentEntity::class,
        AISummaryEntity::class,
        ScamAlertEntity::class,
        LivePhotoEntity::class,
        ScannedDocumentEntity::class,
        KeyTransparencyEntity::class,
        SafetyNumberEntity::class,
        LinkedDeviceEntity::class,
        CallQualityEntity::class,
        NetworkStatsEntity::class,
        FolderEntity::class,
        PinEntity::class,
        PersonalChatFolderEntity::class,
        UserSettingsEntity::class,
        NotificationEntity::class,
        AppStatsEntity::class
    ],
    version = 8,
    exportSchema = false
)
abstract class RedDatabase : RoomDatabase() {
    abstract fun redDao(): RedDao
    abstract fun outboxDao(): OutboxDao
    abstract fun mediaUploadDao(): MediaUploadDao
    abstract fun ultimateDao(): UltimateDao

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
                .addMigrations(REACTION_MIGRATION_1_2, INDEX_MIGRATION_2_3, MESSAGES_INDEX_MIGRATION_3_4, OUTBOX_MIGRATION_4_5, STARRED_MIGRATION_5_6, MEDIA_UPLOAD_MIGRATION_6_7, ULTIMATE_MIGRATION_7_8)
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
                        .addMigrations(REACTION_MIGRATION_1_2, INDEX_MIGRATION_2_3, MESSAGES_INDEX_MIGRATION_3_4, OUTBOX_MIGRATION_4_5, STARRED_MIGRATION_5_6, MEDIA_UPLOAD_MIGRATION_6_7, ULTIMATE_MIGRATION_7_8)
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

/** ULTIMATE V8: كل أنواع قواعد البيانات - أقوى وأنسب وأحدث 2026 - 21 نوع جديد - كل شيء يريده */
private val ULTIMATE_MIGRATION_7_8 = object : androidx.room.migration.Migration(7, 8) {
    override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
        // 1. استطلاعات قوية Mighty Polls
        database.execSQL(
            """CREATE TABLE IF NOT EXISTS `mighty_polls` (
                `id` TEXT NOT NULL,
                `question` TEXT NOT NULL,
                `questionMediaUrl` TEXT,
                `questionLocation` TEXT,
                `description` TEXT,
                `optionsJson` TEXT NOT NULL,
                `allowSuggestOptions` INTEGER NOT NULL,
                `showVoters` INTEGER NOT NULL,
                `timeLimitSeconds` INTEGER,
                `shuffledOptions` INTEGER NOT NULL,
                `disableRevoting` INTEGER NOT NULL,
                `hiddenResults` INTEGER NOT NULL,
                `isClosed` INTEGER NOT NULL,
                `createdBy` TEXT NOT NULL,
                `groupId` TEXT,
                `channelId` TEXT,
                `timestamp` INTEGER NOT NULL,
                `expiresAt` INTEGER,
                `votersJson` TEXT NOT NULL,
                `suggestedOptionsJson` TEXT NOT NULL,
                PRIMARY KEY(`id`)
            )"""
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_mighty_polls_groupId` ON `mighty_polls` (`groupId`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_mighty_polls_channelId` ON `mighty_polls` (`channelId`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_mighty_polls_createdBy` ON `mighty_polls` (`createdBy`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_mighty_polls_isClosed_expiresAt` ON `mighty_polls` (`isClosed`, `expiresAt`)")

        database.execSQL(
            """CREATE TABLE IF NOT EXISTS `poll_votes` (
                `pollId` TEXT NOT NULL,
                `userId` TEXT NOT NULL,
                `optionIndex` INTEGER NOT NULL,
                `timestamp` INTEGER NOT NULL,
                PRIMARY KEY(`pollId`, `userId`)
            )"""
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_poll_votes_pollId` ON `poll_votes` (`pollId`)")

        // 2. قصص محسنة
        database.execSQL(
            """CREATE TABLE IF NOT EXISTS `sovereign_stories` (
                `id` TEXT NOT NULL,
                `userId` TEXT NOT NULL,
                `username` TEXT NOT NULL,
                `displayName` TEXT NOT NULL,
                `mediaUrl` TEXT,
                `mediaType` TEXT NOT NULL,
                `text` TEXT,
                `backgroundColor` TEXT,
                `caption` TEXT,
                `timestamp` INTEGER NOT NULL,
                `expiresAt` INTEGER NOT NULL,
                `views` INTEGER NOT NULL,
                `viewersJson` TEXT NOT NULL,
                `isMyStory` INTEGER NOT NULL,
                `isPremium` INTEGER NOT NULL,
                `playbackStyle` TEXT NOT NULL,
                `musicUrl` TEXT,
                `musicTitle` TEXT,
                `linkUrl` TEXT,
                `location` TEXT,
                PRIMARY KEY(`id`)
            )"""
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_sovereign_stories_userId` ON `sovereign_stories` (`userId`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_sovereign_stories_expiresAt` ON `sovereign_stories` (`expiresAt`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_sovereign_stories_isMyStory_timestamp` ON `sovereign_stories` (`isMyStory`, `timestamp`)")

        // 3. ملاحظات خاصة
        database.execSQL(
            """CREATE TABLE IF NOT EXISTS `private_notes` (
                `contactId` TEXT NOT NULL,
                `note` TEXT NOT NULL,
                `howMet` TEXT,
                `birthday` TEXT,
                `customAvatar` TEXT,
                `customName` TEXT,
                `work` TEXT,
                `favorite` TEXT,
                `timestamp` INTEGER NOT NULL,
                PRIMARY KEY(`contactId`)
            )"""
        )

        // 4. تخصيص ملف شخصي
        database.execSQL(
            """CREATE TABLE IF NOT EXISTS `profile_customizations` (
                `userId` TEXT NOT NULL,
                `color` TEXT NOT NULL,
                `background` TEXT,
                `giftBackdrop` TEXT,
                `giftSymbol` TEXT,
                `animatedReplyStyle` TEXT,
                `linkStyle` TEXT,
                `isPremium` INTEGER NOT NULL,
                `timestamp` INTEGER NOT NULL,
                PRIMARY KEY(`userId`)
            )"""
        )

        // 5. هدايا سيادية
        database.execSQL(
            """CREATE TABLE IF NOT EXISTS `sovereign_gifts` (
                `id` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `description` TEXT NOT NULL,
                `fromUserId` TEXT NOT NULL,
                `toUserId` TEXT NOT NULL,
                `backdrop` TEXT NOT NULL,
                `symbol` TEXT NOT NULL,
                `isBlockchain` INTEGER NOT NULL,
                `fragmentVerified` INTEGER NOT NULL,
                `price` INTEGER NOT NULL,
                `signature` TEXT,
                `customMessage` TEXT,
                `timestamp` INTEGER NOT NULL,
                `canRemoveSignature` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )"""
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_sovereign_gifts_toUserId` ON `sovereign_gifts` (`toUserId`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_sovereign_gifts_fromUserId` ON `sovereign_gifts` (`fromUserId`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_sovereign_gifts_isBlockchain` ON `sovereign_gifts` (`isBlockchain`)")

        // 6. تعليقات مباشرة
        database.execSQL(
            """CREATE TABLE IF NOT EXISTS `live_comments` (
                `id` TEXT NOT NULL,
                `callId` TEXT NOT NULL,
                `userId` TEXT NOT NULL,
                `username` TEXT NOT NULL,
                `text` TEXT,
                `emoji` TEXT,
                `isAnimatedReaction` INTEGER NOT NULL,
                `timestamp` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )"""
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_live_comments_callId` ON `live_comments` (`callId`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_live_comments_callId_timestamp` ON `live_comments` (`callId`, `timestamp`)")

        // 7. ملخصات AI
        database.execSQL(
            """CREATE TABLE IF NOT EXISTS `ai_summaries` (
                `id` TEXT NOT NULL,
                `originalText` TEXT NOT NULL,
                `summary` TEXT NOT NULL,
                `sourceType` TEXT NOT NULL,
                `sourceId` TEXT NOT NULL,
                `timestamp` INTEGER NOT NULL,
                `isEncrypted` INTEGER NOT NULL,
                `cocoonVerified` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )"""
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_summaries_sourceId` ON `ai_summaries` (`sourceId`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_summaries_sourceType` ON `ai_summaries` (`sourceType`)")

        // 8. كشف احتيال
        database.execSQL(
            """CREATE TABLE IF NOT EXISTS `scam_alerts` (
                `messageId` TEXT NOT NULL,
                `isScam` INTEGER NOT NULL,
                `reason` TEXT,
                `domain` TEXT,
                `riskLevel` TEXT NOT NULL,
                `timestamp` INTEGER NOT NULL,
                PRIMARY KEY(`messageId`)
            )"""
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_scam_alerts_isScam_riskLevel` ON `scam_alerts` (`isScam`, `riskLevel`)")

        // 9. صور مباشرة
        database.execSQL(
            """CREATE TABLE IF NOT EXISTS `live_photos` (
                `id` TEXT NOT NULL,
                `imageUrl` TEXT NOT NULL,
                `videoUrl` TEXT NOT NULL,
                `playbackStyle` TEXT NOT NULL,
                `durationMs` INTEGER NOT NULL,
                `timestamp` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )"""
        )

        // 10. مستندات ممسوحة
        database.execSQL(
            """CREATE TABLE IF NOT EXISTS `scanned_documents` (
                `id` TEXT NOT NULL,
                `imagesJson` TEXT NOT NULL,
                `pdfUrl` TEXT,
                `text` TEXT,
                `timestamp` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )"""
        )

        // 11. شفافية مفاتيح
        database.execSQL(
            """CREATE TABLE IF NOT EXISTS `key_transparency` (
                `userId` TEXT NOT NULL,
                `publicKey` TEXT NOT NULL,
                `verified` INTEGER NOT NULL,
                `verificationMethod` TEXT NOT NULL,
                `cloudflareVerified` INTEGER NOT NULL,
                `trailOfBitsVerified` INTEGER NOT NULL,
                `timestamp` INTEGER NOT NULL,
                `lastVerifiedAt` INTEGER,
                PRIMARY KEY(`userId`)
            )"""
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_key_transparency_verified` ON `key_transparency` (`verified`)")

        // 12. أرقام أمان
        database.execSQL(
            """CREATE TABLE IF NOT EXISTS `safety_numbers` (
                `userId` TEXT NOT NULL,
                `safetyNumber` TEXT NOT NULL,
                `qrCode` TEXT,
                `verified` INTEGER NOT NULL,
                `verifiedAt` INTEGER,
                `fingerprint` TEXT NOT NULL,
                `timestamp` INTEGER NOT NULL,
                PRIMARY KEY(`userId`)
            )"""
        )

        // 13. أجهزة مرتبطة
        database.execSQL(
            """CREATE TABLE IF NOT EXISTS `linked_devices` (
                `deviceId` TEXT NOT NULL,
                `userId` TEXT NOT NULL,
                `deviceName` TEXT NOT NULL,
                `deviceType` TEXT NOT NULL,
                `lastSeen` INTEGER NOT NULL,
                `isCurrent` INTEGER NOT NULL,
                `isTrusted` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
                PRIMARY KEY(`deviceId`)
            )"""
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_linked_devices_userId` ON `linked_devices` (`userId`)")

        // 14. جودة مكالمات
        database.execSQL(
            """CREATE TABLE IF NOT EXISTS `call_quality` (
                `id` TEXT NOT NULL,
                `callId` TEXT NOT NULL,
                `rttMs` INTEGER NOT NULL,
                `packetLossPercent` REAL NOT NULL,
                `availableBitrateKbps` INTEGER NOT NULL,
                `bandwidthKbps` INTEGER NOT NULL,
                `framesPerSecond` INTEGER NOT NULL,
                `codec` TEXT NOT NULL,
                `resolution` TEXT NOT NULL,
                `timestamp` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )"""
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_call_quality_callId` ON `call_quality` (`callId`)")

        // 15. إحصائيات شبكة
        database.execSQL(
            """CREATE TABLE IF NOT EXISTS `network_stats` (
                `id` TEXT NOT NULL,
                `type` TEXT NOT NULL,
                `quality` TEXT NOT NULL,
                `rttMs` INTEGER NOT NULL,
                `packetLoss` REAL NOT NULL,
                `bandwidthKbps` INTEGER NOT NULL,
                `isLan` INTEGER NOT NULL,
                `timestamp` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )"""
        )

        // 16. مجلدات
        database.execSQL(
            """CREATE TABLE IF NOT EXISTS `folders` (
                `id` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `peerIdsJson` TEXT NOT NULL,
                `locked` INTEGER NOT NULL,
                `color` TEXT,
                `icon` TEXT,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )"""
        )

        // 17. رسائل مثبتة
        database.execSQL(
            """CREATE TABLE IF NOT EXISTS `pins` (
                `messageId` TEXT NOT NULL,
                `groupId` TEXT NOT NULL,
                `pinnedBy` TEXT NOT NULL,
                `expiresAt` INTEGER,
                `timestamp` INTEGER NOT NULL,
                PRIMARY KEY(`messageId`)
            )"""
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_pins_groupId` ON `pins` (`groupId`)")

        // 18. مجلدات دردشات شخصية
        database.execSQL(
            """CREATE TABLE IF NOT EXISTS `personal_chat_folders` (
                `id` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `peerIdsJson` TEXT NOT NULL,
                `locked` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )"""
        )

        // 19. إعدادات مستخدم
        database.execSQL(
            """CREATE TABLE IF NOT EXISTS `user_settings` (
                `userId` TEXT NOT NULL,
                `themePreset` TEXT NOT NULL,
                `themeMode` TEXT NOT NULL,
                `highContrast` INTEGER NOT NULL,
                `liquidGlassEnabled` INTEGER NOT NULL,
                `reduceMotion` INTEGER NOT NULL,
                `fontScale` REAL NOT NULL,
                `customPrimary` TEXT,
                `typingIndicators` INTEGER NOT NULL,
                `readReceipts` INTEGER NOT NULL,
                `callNotifications` INTEGER NOT NULL,
                `language` TEXT NOT NULL,
                `timestamp` INTEGER NOT NULL,
                PRIMARY KEY(`userId`)
            )"""
        )

        // 20. إشعارات
        database.execSQL(
            """CREATE TABLE IF NOT EXISTS `notifications` (
                `id` TEXT NOT NULL,
                `userId` TEXT NOT NULL,
                `type` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `body` TEXT NOT NULL,
                `dataJson` TEXT,
                `isRead` INTEGER NOT NULL,
                `timestamp` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )"""
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_notifications_userId` ON `notifications` (`userId`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_notifications_isRead_timestamp` ON `notifications` (`isRead`, `timestamp`)")

        // 21. إحصائيات تطبيق
        database.execSQL(
            """CREATE TABLE IF NOT EXISTS `app_stats` (
                `id` TEXT NOT NULL,
                `messagesCount` INTEGER NOT NULL,
                `groupsCount` INTEGER NOT NULL,
                `callsCount` INTEGER NOT NULL,
                `storiesCount` INTEGER NOT NULL,
                `pollsCount` INTEGER NOT NULL,
                `giftsCount` INTEGER NOT NULL,
                `timestamp` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )"""
        )
    }
}
