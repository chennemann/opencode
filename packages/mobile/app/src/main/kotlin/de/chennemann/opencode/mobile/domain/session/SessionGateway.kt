package de.chennemann.opencode.mobile.domain.session

import de.chennemann.opencode.mobile.ui.state.ServerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

interface SessionGateway {
    val status: StateFlow<ServerState>
    val endpoint: StateFlow<String>
    val found: StateFlow<String?>

    fun start(scope: CoroutineScope)

    suspend fun setUrl(next: String)

    suspend fun refresh(loading: Boolean = true)

    suspend fun projects(): List<SessionProject>

    suspend fun sessions(worktree: String): List<SessionSummary>

    suspend fun createSession(worktree: String, title: String): SessionSummary

    suspend fun messages(sessionId: String, directory: String, limit: Int? = 400): List<SessionMessage>

    suspend fun streamEvents(lastEventId: String?, onRawEvent: suspend (String) -> Unit, onEvent: suspend (SessionStreamEvent) -> Unit): String?

    suspend fun sendMessage(sessionId: String, directory: String, text: String)

    suspend fun streamCursor(): String?

    suspend fun setStreamCursor(value: String?)
}
