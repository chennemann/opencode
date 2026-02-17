package de.chennemann.opencode.mobile.domain.service.sync

import kotlinx.coroutines.CoroutineScope

data class StreamEvent(
    val type: String,
    val payloadJson: String,
    val receivedAt: Long,
)

interface ServerService {
    suspend fun connectStream(onEvent: suspend (StreamEvent) -> Unit)
    suspend fun disconnectStream()
    suspend fun fetchProjects(): String
    suspend fun fetchSessions(projectId: String): String
    suspend fun fetchCommands(projectId: String): String
    suspend fun sendOutbox(actionId: String): Boolean
}

interface ProjectSyncService {
    suspend fun run(projectId: String? = null)
}

interface SessionStateSyncService {
    suspend fun ingestEvent(event: StreamEvent)
    suspend fun runDue(now: Long = System.currentTimeMillis())
    suspend fun onFocusedSessionChanged(sessionId: String?)
    suspend fun onExpectationChanged(sessionId: String, expectsRemoteUpdates: Boolean)
    suspend fun evaluateStreamPolicy(now: Long = System.currentTimeMillis())
}

interface SyncRuntime {
    fun start(scope: CoroutineScope)
    suspend fun tick(now: Long = System.currentTimeMillis())
}

enum class SyncTrigger {
    STREAM_HINT,
    PERIODIC,
    FOREGROUND,
    CONNECTED,
    FOCUS_CHANGED,
    EXPECTATION_CHANGED,
    IDLE_GRACE_EXPIRED,
}
