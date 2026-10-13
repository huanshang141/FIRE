package app.fireplan.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [GoalEntity::class, NetWorthSnapshotEntity::class, ChatMessageEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class FireDatabase : RoomDatabase() {
    abstract fun goalDao(): GoalDao
    abstract fun snapshotDao(): SnapshotDao
    abstract fun chatDao(): ChatDao

    companion object {
        @Volatile
        private var instance: FireDatabase? = null

        fun get(context: Context): FireDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    FireDatabase::class.java,
                    "fireplan.db",
                ).build().also { instance = it }
            }
        }
    }
}
