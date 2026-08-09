package com.red.sovereign.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.red.sovereign.features.stories.StoryEntity
import com.red.sovereign.features.stories.StoryViewEntity
import com.red.sovereign.features.stories.StoryDao

@Database(
    entities = [
        MessageEntity::class,
        ConversationEntity::class,
        PstnLogEntity::class,
        StoryEntity::class,
        StoryViewEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class MasterDatabase : RoomDatabase() {
    abstract fun masterDao(): MasterDao
    abstract fun storyDao(): StoryDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. إنشاء جدول القصص
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `stories` (
                        `id` TEXT NOT NULL, 
                        `userId` TEXT NOT NULL, 
                        `mediaUrl` TEXT NOT NULL, 
                        `type` TEXT NOT NULL, 
                        `caption` TEXT, 
                        `backgroundColor` TEXT, 
                        `timestamp` INTEGER NOT NULL, 
                        `expiresAt` INTEGER NOT NULL, 
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
                
                // 2. إنشاء جدول المشاهدات مع الربط (Foreign Key)
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `story_views` (
                        `storyId` TEXT NOT NULL, 
                        `viewerId` TEXT NOT NULL, 
                        `timestamp` INTEGER NOT NULL, 
                        PRIMARY KEY(`storyId`, `viewerId`), 
                        FOREIGN KEY(`storyId`) REFERENCES `stories`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE 
                    )
                """.trimIndent())

                // 3. إنشاء الفهارس للسرعة
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_stories_userId` ON `stories` (`userId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_stories_expiresAt` ON `stories` (`expiresAt`)")
            }
        }
    }
}
