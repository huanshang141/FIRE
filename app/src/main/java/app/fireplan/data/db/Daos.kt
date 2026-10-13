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
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC, id DESC")
    fun observeAll(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC, id DESC")
    suspend fun listAll(): List<ConversationEntity>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun get(id: Long): ConversationEntity?

    @Query("SELECT * FROM conversations WHERE kind = :kind AND dayKey = :dayKey LIMIT 1")
    suspend fun findByKindAndDay(kind: String, dayKey: String): ConversationEntity?

    @Insert
    suspend fun insert(conversation: ConversationEntity): Long

    @Query("UPDATE conversations SET title = :title, titleLocked = :titleLocked, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateTitle(id: Long, title: String, titleLocked: Boolean, updatedAt: Long)

    @Query("UPDATE conversations SET updatedAt = :updatedAt WHERE id = :id")
    suspend fun touch(id: Long, updatedAt: Long)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_messages WHERE conversationId = :conversationId ORDER BY id ASC")
    fun observe(conversationId: Long): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE conversationId = :conversationId ORDER BY id ASC")
    suspend fun list(conversationId: Long): List<ChatMessageEntity>

    @Insert
    suspend fun insert(message: ChatMessageEntity): Long

    @Query("DELETE FROM chat_messages WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM chat_messages WHERE conversationId = :conversationId")
    suspend fun deleteForConversation(conversationId: Long)
}
