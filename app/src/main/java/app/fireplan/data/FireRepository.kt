package app.fireplan.data

import android.content.Context
import androidx.room.withTransaction
import app.fireplan.data.db.ChatMessageEntity
import app.fireplan.data.db.ConversationEntity
import app.fireplan.data.db.FireDatabase
import app.fireplan.data.db.GoalEntity
import app.fireplan.data.db.NetWorthSnapshotEntity
import app.fireplan.domain.FireInput
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

class FireRepository private constructor(private val db: FireDatabase) {
    fun observeGoal(): Flow<GoalEntity?> = db.goalDao().observe()

    fun observeSnapshots(): Flow<List<NetWorthSnapshotEntity>> = db.snapshotDao().observeAll()

    fun observeConversations(): Flow<List<ConversationEntity>> = db.conversationDao().observeAll()

    fun observeMessages(conversationId: Long): Flow<List<ChatMessageEntity>> = db.chatDao().observe(conversationId)

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

    suspend fun listConversations(): List<ConversationEntity> = db.conversationDao().listAll()

    suspend fun conversationExists(id: Long): Boolean = db.conversationDao().get(id) != null

    suspend fun createChat(now: Long = System.currentTimeMillis()): Long {
        return db.conversationDao().insert(
            ConversationEntity(
                title = NEW_CHAT_TITLE,
                kind = CONVERSATION_CHAT,
                dayKey = null,
                createdAt = now,
                updatedAt = now,
                titleLocked = false,
            ),
        )
    }

    suspend fun ensureDailyConversation(date: LocalDate, now: Long = System.currentTimeMillis()): ConversationEntity {
        val key = dailyDayKey(date)
        db.conversationDao().findByKindAndDay(CONVERSATION_DAILY, key)?.let { return it }
        val id = db.conversationDao().insert(
            ConversationEntity(
                title = dailyConversationTitle(date),
                kind = CONVERSATION_DAILY,
                dayKey = key,
                createdAt = now,
                updatedAt = now,
                titleLocked = true,
            ),
        )
        return db.conversationDao().get(id) ?: error("开工会话没有写入")
    }

    suspend fun renameConversation(id: Long, title: String, now: Long = System.currentTimeMillis()) {
        db.conversationDao().updateTitle(id, title, titleLocked = true, updatedAt = now)
    }

    suspend fun maybeTitleFromFirstMessage(conversationId: Long, text: String, now: Long = System.currentTimeMillis()) {
        val conversation = db.conversationDao().get(conversationId) ?: return
        if (conversation.titleLocked || conversation.kind != CONVERSATION_CHAT) return
        if (conversation.title != NEW_CHAT_TITLE) return
        db.conversationDao().updateTitle(
            conversationId,
            titleFromFirstUtterance(text),
            titleLocked = true,
            updatedAt = now,
        )
    }

    suspend fun deleteConversation(id: Long) {
        db.withTransaction {
            db.chatDao().deleteForConversation(id)
            db.conversationDao().delete(id)
        }
    }

    suspend fun listMessages(conversationId: Long): List<ChatMessageEntity> = db.chatDao().list(conversationId)

    suspend fun insertMessage(
        conversationId: Long,
        role: String,
        content: String,
        error: String?,
        kind: String = MESSAGE_KIND,
        now: Long = System.currentTimeMillis(),
    ): Long {
        val id = db.chatDao().insert(
            ChatMessageEntity(
                conversationId = conversationId,
                role = role,
                content = content,
                createdAt = now,
                error = error,
                kind = kind,
            ),
        )
        db.conversationDao().touch(conversationId, now)
        return id
    }

    suspend fun deleteMessage(id: Long) = db.chatDao().deleteById(id)

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
