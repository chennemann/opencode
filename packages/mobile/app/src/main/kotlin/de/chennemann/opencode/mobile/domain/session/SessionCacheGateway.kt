package de.chennemann.opencode.mobile.domain.session

import kotlinx.coroutines.flow.Flow

data class RecentSessionCache(
    val server: String,
    val project: String?,
    val session: SessionState,
)

data class SessionQuickPinCache(
    val include: Set<String> = emptySet(),
    val exclude: Set<String> = emptySet(),
)

interface SessionCacheGateway {
    suspend fun upsertSession(server: String, project: String?, session: SessionState)

    fun recentSession(): RecentSessionCache?

    fun projectFavorites(server: String): Set<String>

    suspend fun setProjectFavorite(server: String, worktree: String, favorite: Boolean)

    fun sessionQuickPins(server: String): SessionQuickPinCache

    suspend fun setSessionQuickPins(server: String, include: Set<String>, exclude: Set<String>)

    suspend fun listMessages(server: String, sessionId: String): List<MessageState>

    fun observeMessages(server: String, sessionId: String): Flow<List<MessageState>>

    suspend fun upsertMessage(server: String, sessionId: String, message: MessageState, updatedAt: Long)

    suspend fun deleteMessage(server: String, sessionId: String, messageId: String)

    suspend fun deleteSessionMessages(server: String, sessionId: String)
}
