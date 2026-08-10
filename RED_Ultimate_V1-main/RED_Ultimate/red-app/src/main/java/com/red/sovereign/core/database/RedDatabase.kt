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
        DraftEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class RedDatabase : RoomDatabase() {
    abstract fun redDao(): RedDao

    companion object {
        @Volatile
        private var INSTANCE: RedDatabase? = null

        fun getInstance(context: Context): RedDatabase {
            return INSTANCE ?: synchronized(this) {
                val secureStore = SecureStore(context, "red_database_security")
                var passphrase = secureStore.get("passphrase")
                if (passphrase == null) {
                    passphrase = java.util.UUID.randomUUID().toString()
                    secureStore.put("passphrase", passphrase)
                }

                val factory = SupportOpenHelperFactory(passphrase.toByteArray())
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    RedDatabase::class.java,
                    "red_sovereign.db"
                ).openHelperFactory(factory).addCallback(FtsCallback()).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
