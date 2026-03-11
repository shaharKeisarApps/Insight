package com.keisardev.insight.core.database

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.keisardev.insight.core.common.di.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Entity representing a persisted chat message.
 */
data class ChatMessageEntity(
    val id: String,
    val content: String,
    val role: String,
    val timestamp: Long,
)

/**
 * Data source for persisting chat messages in SQLDelight.
 */
interface ChatMessageLocalDataSource {
    fun observeAll(): Flow<List<ChatMessageEntity>>
    suspend fun insert(message: ChatMessageEntity)
    suspend fun updateContent(id: String, content: String)
    suspend fun deleteAll()
    suspend fun deleteOldest(keepCount: Long)
    suspend fun count(): Long
}

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class ChatMessageLocalDataSourceImpl(
    private val database: ExpenseDatabase,
) : ChatMessageLocalDataSource {

    private val queries get() = database.chatMessageQueries

    override fun observeAll(): Flow<List<ChatMessageEntity>> =
        queries.selectAll { id, content, role, timestamp ->
            ChatMessageEntity(id, content, role, timestamp)
        }.asFlow().mapToList(Dispatchers.Default)

    override suspend fun insert(message: ChatMessageEntity) {
        withContext(Dispatchers.Default) {
            queries.insert(message.id, message.content, message.role, message.timestamp)
        }
    }

    override suspend fun updateContent(id: String, content: String) {
        withContext(Dispatchers.Default) {
            queries.updateContent(content, id)
        }
    }

    override suspend fun deleteAll() {
        withContext(Dispatchers.Default) {
            queries.deleteAll()
        }
    }

    override suspend fun deleteOldest(keepCount: Long) {
        withContext(Dispatchers.Default) {
            queries.deleteOldest(keepCount)
        }
    }

    override suspend fun count(): Long = withContext(Dispatchers.Default) {
        queries.selectCount().executeAsOne()
    }
}
