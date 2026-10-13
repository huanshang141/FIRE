package app.fireplan.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface GoalDao {
    @Query("SELECT * FROM goal WHERE id = 1")
    fun observe(): Flow<GoalEntity?>

    @Query("SELECT * FROM goal WHERE id = 1")
    suspend fun get(): GoalEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(goal: GoalEntity)
}

@Dao
interface SnapshotDao {
    @Query("SELECT * FROM net_worth_snapshots ORDER BY recordedAt ASC, id ASC")
    fun observeAll(): Flow<List<NetWorthSnapshotEntity>>

    @Insert
    suspend fun insert(snapshot: NetWorthSnapshotEntity)
}

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_messages ORDER BY id ASC")
    fun observeAll(): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages ORDER BY id ASC")
    suspend fun listAll(): List<ChatMessageEntity>

    @Insert
    suspend fun insert(message: ChatMessageEntity): Long

    @Query("DELETE FROM chat_messages WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM chat_messages")
    suspend fun deleteAll()
}
