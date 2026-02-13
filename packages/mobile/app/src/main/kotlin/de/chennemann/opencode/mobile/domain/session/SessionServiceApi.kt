package de.chennemann.opencode.mobile.domain.session

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

interface SessionServiceApi {
    val state: StateFlow<SessionUiState>

    fun start(scope: CoroutineScope)

    fun updateUrl(value: String)

    fun useDiscovered()

    fun refresh()

    fun selectProject(worktree: String)

    fun toggleProjectFavorite(worktree: String)

    fun toggleSessionQuickPin(session: SessionState, systemPinned: Boolean)

    suspend fun createSessionAndFocus(worktree: String): Boolean

    fun openSession(session: SessionState)

    fun send(text: String)

    fun loadMoreMessages()

    suspend fun sessionsForProject(worktree: String, limit: Int? = null): List<SessionState>
}
