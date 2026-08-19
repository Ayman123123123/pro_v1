package com.red.sovereign.core.database

import android.content.Context
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
        OutboxEntity::class,
        OutboxEnvelopeEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class RedDatabase : RoomDatabase() {
    abstract fun redDao(): RedDao

    companion object {
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
            // delete and recreate to avoid permanent crash. Corrupted file is backed up.
            fun create(): RedDatabase = Room.databaseBuilder(
                context.applicationContext,
                RedDatabase::class.java,
                "red_sovereign.db"
            )
                .openHelperFactory(factory)
                .addMigrations(REACTION_MIGRATION_1_2, OUTBOX_MIGRATION_2_3)
                .addCallback(FtsCallback())
                .build()

            return try {
                create().also { db ->
                    // Test open
                    db.openHelper.writableDatabase
                }
            } catch (e: Exception) {
                // Backup corrupted DB
                try {
                    val dbFile = context.getDatabasePath("red_sovereign.db")
                    if (dbFile.exists()) {
                        val backup = java.io.File(dbFile.parent, "red_sovereign.db.corrupted.${System.currentTimeMillis()}.bak")
                        dbFile.renameTo(backup)
                    }
                } catch (_: Exception) {}
                // Try with same passphrase after deleting -wal -shm
                try {
                    val dbFile = context.getDatabasePath("red_sovereign.db")
                    java.io.File("${dbFile.absolutePath}-wal").delete()
                    java.io.File("${dbFile.absolutePath}-shm").delete()
                    dbFile.delete()
                } catch (_: Exception) {}
                try {
                    create()
                } catch (e2: Exception) {
                    // Last resort: regenerate passphrase and recreate
                    secureStore.remove("passphrase")
                    val newPass = java.util.UUID.randomUUID().toString()
                    secureStore.put("passphrase", newPass)
                    val newFactory = SupportOpenHelperFactory(newPass.toByteArray())
                    Room.databaseBuilder(
                        context.applicationContext,
                        RedDatabase::class.java,
                        "red_sovereign.db"
                    )
                        .openHelperFactory(newFactory)
                        .addMigrations(REACTION_MIGRATION_1_2)
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

/** رسائل صادرة دائمة: نية الإرسال والحوامل المشفرة الثابتة لإعادة المحاولة الآمنة. */
private val OUTBOX_MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
    override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
        database.execSQL(
            """CREATE TABLE IF NOT EXISTS `outbox` (
                `id` TEXT NOT NULL,
                `kind` TEXT NOT NULL,
                `clientId` TEXT,
                `targetRedId` TEXT,
                `conversationId` TEXT NOT NULL,
                `messageType` TEXT,
                `payload` BLOB NOT NULL,
                `groupJson` TEXT,
                `isRich` INTEGER NOT NULL,
                `localMessageId` TEXT,
                `createdAt` INTEGER NOT NULL,
                `lastAttemptAt` INTEGER NOT NULL,
                `attemptCount` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )"""
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_outbox_createdAt` ON `outbox` (`createdAt`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_outbox_conversationId` ON `outbox` (`conversationId`)")
        database.execSQL(
            """CREATE TABLE IF NOT EXISTS `outbox_envelopes` (
                `messageId` TEXT NOT NULL,
                `outboxId` TEXT NOT NULL,
                `receiverRedId` TEXT NOT NULL,
                `conversationId` TEXT NOT NULL,
                `messageType` TEXT NOT NULL,
                `encryptedPayload` BLOB NOT NULL,
                `receiverDeviceId` INTEGER NOT NULL,
                `ciphertextType` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
                PRIMARY KEY(`messageId`)
            )"""
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_outbox_envelopes_outboxId` ON `outbox_envelopes` (`outboxId`)")
    }
}
