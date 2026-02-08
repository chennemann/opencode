package de.chennemann.opencode.mobile.home

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
    val loadingMessages: Boolean,
    val message: String?,
)
