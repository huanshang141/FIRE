package app.fireplan.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "goal")
data class GoalEntity(
    @PrimaryKey val id: Int = SINGLE_ID,
    val netWorth: Double,
    val monthlyIncome: Double,
    val monthlyExpenses: Double,
    val withdrawalRate: Double,
    val expectedReturn: Double,
    val updatedAt: Long,
) {
    companion object {
        const val SINGLE_ID = 1
    }
}

@Entity(tableName = "net_worth_snapshots")
data class NetWorthSnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val netWorth: Double,
    val recordedAt: Long,
)

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val kind: String,
    val dayKey: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val titleLocked: Boolean,
)

@Entity(
    tableName = "chat_messages",
    indices = [Index("conversationId")],
)
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: Long = 0,
    val role: String,
    val content: String,
    val createdAt: Long,
    val error: String? = null,
    val kind: String = "message",
)
