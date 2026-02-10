package de.chennemann.opencode.mobile.domain.session

data class ProjectState(
    val id: String,
    val worktree: String,
    val name: String,
)

data class SessionState(
    val id: String,
    val title: String,
    val version: String,
    val directory: String,
)

data class MessageState(
    val id: String,
    val role: String,
    val text: String,
    val sort: String,
    val toolCalls: List<ToolCallState> = emptyList(),
)

data class ToolCallState(
    val id: String,
    val title: String,
    val status: String? = null,
    val details: List<String>,
)

data class DebugState(
    val sseRaw: Int = 0,
    val sseSeen: Int = 0,
    val sseApplied: Int = 0,
    val sseDropped: Int = 0,
    val sseConnected: Int = 0,
    val sseErrors: Int = 0,
    val syncRuns: Int = 0,
    val syncFails: Int = 0,
    val lastDrop: String? = null,
    val lastStreamError: String? = null,
    val sseLog: List<String> = emptyList(),
)

data class HomeState(
    val url: String,
    val discovered: String?,
    val status: ServerState,
    val projects: List<ProjectState>,
    val selectedProject: String?,
    val sessions: List<SessionState>,
    val activeSessions: List<SessionState>,
    val focusedSession: SessionState?,
    val focusedMessages: List<MessageState>,
    val canLoadMoreMessages: Boolean,
    val loadingMoreMessages: Boolean,
    val managementOpen: Boolean,
    val loadingProjects: Boolean,
    val loadingSessions: Boolean,
    val message: String?,
    val debug: DebugState,
)
