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
    val loadingProjects: Boolean,
    val loadingSessions: Boolean,
    val loadingMessages: Boolean,
    val opened: SessionState?,
    val messages: List<MessageState>,
    val message: String?,
)
