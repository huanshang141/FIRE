package app.fireplan.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong

/**
 * One lock for chat turns and the daily reminder, plus an epoch that clear() bumps
 * so a request that started earlier cannot write back into an emptied transcript.
 */
object Conversation {
    private val mutex = Mutex()
    private val epoch = AtomicLong(0)

    fun epoch(): Long = epoch.get()

    fun bumpEpoch(): Long = epoch.incrementAndGet()

    suspend fun <T> exclusive(block: suspend () -> T): T = mutex.withLock { block() }
}
