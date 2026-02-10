package de.chennemann.opencode.mobile.domain.session

import de.chennemann.opencode.mobile.data.GlobalStreamEvent
import de.chennemann.opencode.mobile.data.ProjectInfo
import de.chennemann.opencode.mobile.data.SessionInfo
import de.chennemann.opencode.mobile.data.SessionMessageInfo
import de.chennemann.opencode.mobile.home.ServerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

interface SessionGateway {
    val status: StateFlow<ServerState>
    val endpoint: StateFlow<String>
    val found: StateFlow<String?>

    fun start(scope: CoroutineScope)

    suspend fun setUrl(next: String)

    suspend fun refresh(loading: Boolean = true)

    suspend fun projects(): List<ProjectInfo>

    suspend fun sessions(worktree: String): List<SessionInfo>

    suspend fun createSession(worktree: String, title: String): SessionInfo

    suspend fun messages(sessionId: String, directory: String, limit: Int? = 400): List<SessionMessageInfo>

    suspend fun streamEvents(lastEventId: String?, onRawEvent: suspend (String) -> Unit, onEvent: suspend (GlobalStreamEvent) -> Unit): String?

    suspend fun sendMessage(sessionId: String, directory: String, text: String)

    suspend fun streamCursor(): String?

    suspend fun setStreamCursor(value: String?)
}
