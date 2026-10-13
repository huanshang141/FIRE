package app.fireplan.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        GoalEntity::class,
        NetWorthSnapshotEntity::class,
        ConversationEntity::class,
        ChatMessageEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class FireDatabase : RoomDatabase() {
    abstract fun goalDao(): GoalDao
    abstract fun snapshotDao(): SnapshotDao
    abstract fun conversationDao(): ConversationDao
    abstract fun chatDao(): ChatDao

    companion object {
        @Volatile
        private var instance: FireDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `conversations` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `title` TEXT NOT NULL,
                        `kind` TEXT NOT NULL,
                        `dayKey` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `titleLocked` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `chat_messages_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `conversationId` INTEGER NOT NULL,
                        `role` TEXT NOT NULL,
                        `content` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `error` TEXT,
                        `kind` TEXT NOT NULL
                    )
                    """.trimIndent(),
                )
                val count = db.query("SELECT COUNT(*) FROM chat_messages").use { cursor ->
                    if (cursor.moveToFirst()) cursor.getInt(0) else 0
                }
                val conversationId = if (count > 0) {
                    val now = System.currentTimeMillis()
                    db.execSQL(
                        "INSERT INTO `conversations` (`title`, `kind`, `dayKey`, `createdAt`, `updatedAt`, `titleLocked`) VALUES (?, 'chat', NULL, ?, ?, 1)",
                        arrayOf<Any>("以前的聊天", now, now),
                    )
                    db.query("SELECT last_insert_rowid()").use { cursor ->
                        if (cursor.moveToFirst()) cursor.getLong(0) else 0L
                    }
                } else {
                    0L
                }
                db.execSQL(
                    """
                    INSERT INTO `chat_messages_new` (`id`, `conversationId`, `role`, `content`, `createdAt`, `error`, `kind`)
                    SELECT `id`, $conversationId, `role`, `content`, `createdAt`, `error`, 'message' FROM `chat_messages`
                    """.trimIndent(),
                )
                db.execSQL("DROP TABLE `chat_messages`")
                db.execSQL("ALTER TABLE `chat_messages_new` RENAME TO `chat_messages`")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_chat_messages_conversationId` ON `chat_messages` (`conversationId`)",
                )
                db.execSQL("DELETE FROM sqlite_sequence WHERE name IN ('chat_messages', 'chat_messages_new')")
                db.execSQL(
                    "INSERT INTO sqlite_sequence(name, seq) SELECT 'chat_messages', IFNULL(MAX(id), 0) FROM chat_messages",
                )
            }
        }

        fun get(context: Context): FireDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    FireDatabase::class.java,
                    "fireplan.db",
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }
        }
    }
}
