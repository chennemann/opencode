package de.chennemann.opencode.mobile.domain.session

import kotlinx.coroutines.flow.Flow

data class RecentSessionCache(
    val server: String,
    val project: String?,
    val session: SessionState,
)

interface SessionCacheGateway {
    suspend fun upsertSession(server: String, project: String?, session: SessionState)

    fun recentSession(): RecentSessionCache?

    suspend fun listMessages(server: String, sessionId: String): List<MessageState>

    fun observeMessages(server: String, sessionId: String): Flow<List<MessageState>>

    suspend fun upsertMessage(server: String, sessionId: String, message: MessageState, updatedAt: Long)

    suspend fun deleteMessage(server: String, sessionId: String, messageId: String)

    suspend fun deleteSessionMessages(server: String, sessionId: String)
}
