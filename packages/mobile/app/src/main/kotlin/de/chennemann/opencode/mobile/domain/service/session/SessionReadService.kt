package de.chennemann.opencode.mobile.domain.service.session

import de.chennemann.opencode.mobile.domain.session.SessionState
import de.chennemann.opencode.mobile.domain.session.SessionUiState
import kotlinx.coroutines.flow.StateFlow

interface SessionReadService {
    val state: StateFlow<SessionUiState>

    suspend fun sessionsForProject(worktree: String, limit: Int? = null): List<SessionState>
}
