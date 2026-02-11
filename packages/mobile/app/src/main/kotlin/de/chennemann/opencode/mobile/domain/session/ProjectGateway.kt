package de.chennemann.opencode.mobile.domain.session

interface ProjectGateway {
    suspend fun projects(): List<SessionProject>

    suspend fun sessions(worktree: String, limit: Int? = null): List<SessionSummary>

    suspend fun createSession(worktree: String, title: String): SessionSummary
}
