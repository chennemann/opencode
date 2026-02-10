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
    val createdAt: Long? = null,
    val completedAt: Long? = null,
    val toolCalls: List<ToolCallState> = emptyList(),
)

data class ToolCallState(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val status: String? = null,
    val details: List<String>,
)

data class SessionUiState(
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
    val loadingProjects: Boolean,
    val loadingSessions: Boolean,
    val message: String?,
)
