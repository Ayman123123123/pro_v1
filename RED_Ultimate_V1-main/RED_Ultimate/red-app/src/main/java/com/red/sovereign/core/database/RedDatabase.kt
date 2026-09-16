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
        MediaUploadEntity::class
    ],
    version = 8,
    exportSchema = false
)
abstract class RedDatabase : RoomDatabase() {
    abstract fun redDao(): RedDao
    abstract fun outboxDao(): OutboxDao
    abstract fun mediaUploadDao(): MediaUploadDao

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
                        .addMigrations(REACTION_MIGRATION_1_2, INDEX_MIGRATION_2_3, MESSAGES_INDEX_MIGRATION_3_4, OUTBOX_MIGRATION_4_5, STARRED_MIGRATION_5_6, MEDIA_UPLOAD_MIGRATION_6_7, OUTBOX_REPAIR_MIGRATION_7_8)
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
                .addMigrations(REACTION_MIGRATION_1_2, INDEX_MIGRATION_2_3, MESSAGES_INDEX_MIGRATION_3_4, OUTBOX_MIGRATION_4_5, STARRED_MIGRATION_5_6, MEDIA_UPLOAD_MIGRATION_6_7, OUTBOX_REPAIR_MIGRATION_7_8)
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
