package app.fireplan.data

import android.content.Context
import androidx.room.withTransaction
import app.fireplan.data.db.ChatMessageEntity
import app.fireplan.data.db.FireDatabase
import app.fireplan.data.db.GoalEntity
import app.fireplan.data.db.NetWorthSnapshotEntity
import app.fireplan.domain.FireInput
import kotlinx.coroutines.flow.Flow

class FireRepository private constructor(private val db: FireDatabase) {
    fun observeGoal(): Flow<GoalEntity?> = db.goalDao().observe()

    fun observeSnapshots(): Flow<List<NetWorthSnapshotEntity>> = db.snapshotDao().observeAll()

    fun observeMessages(): Flow<List<ChatMessageEntity>> = db.chatDao().observeAll()

    suspend fun getGoal(): GoalEntity? = db.goalDao().get()

    suspend fun saveGoal(input: FireInput) {
        val now = System.currentTimeMillis()
        db.withTransaction {
            db.goalDao().upsert(
                GoalEntity(
                    netWorth = input.netWorth,
                    monthlyIncome = input.monthlyIncome,
                    monthlyExpenses = input.monthlyExpenses,
                    withdrawalRate = input.withdrawalRate,
                    expectedReturn = input.annualReturn,
                    updatedAt = now,
                ),
            )
            db.snapshotDao().insert(
                NetWorthSnapshotEntity(netWorth = input.netWorth, recordedAt = now),
            )
        }
    }

    suspend fun listMessages(): List<ChatMessageEntity> = db.chatDao().listAll()

    suspend fun insertMessage(role: String, content: String, error: String?): Long {
        return db.chatDao().insert(
            ChatMessageEntity(
                role = role,
                content = content,
                createdAt = System.currentTimeMillis(),
                error = error,
            ),
        )
    }

    suspend fun deleteMessage(id: Long) = db.chatDao().deleteById(id)

    suspend fun clearMessages() = db.chatDao().deleteAll()

    companion object {
        @Volatile
        private var instance: FireRepository? = null

        fun get(context: Context): FireRepository {
            return instance ?: synchronized(this) {
                instance ?: FireRepository(FireDatabase.get(context)).also { instance = it }
            }
        }
    }
}

fun GoalEntity.toInput(): FireInput = FireInput(
    netWorth = netWorth,
    monthlyIncome = monthlyIncome,
    monthlyExpenses = monthlyExpenses,
    withdrawalRate = withdrawalRate,
    annualReturn = expectedReturn,
)
