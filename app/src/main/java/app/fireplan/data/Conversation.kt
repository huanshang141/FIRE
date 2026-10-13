package app.fireplan.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * One lock for chat turns and the daily reminder. Deleting a conversation bumps
 * only that conversation, so an in-flight request cannot write back into it.
 */
object Conversation {
    private val mutex = Mutex()
    private val generations = ConcurrentHashMap<Long, Long>()

    fun generation(conversationId: Long): Long = generations[conversationId] ?: 0L

    fun bump(conversationId: Long): Long {
        return generations.merge(conversationId, 1L) { current, _ -> current + 1L }!!
    }

    suspend fun <T> exclusive(block: suspend () -> T): T = mutex.withLock { block() }
}
