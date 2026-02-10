package de.chennemann.opencode.mobile.domain.session

import de.chennemann.opencode.mobile.ui.state.HomeState
import de.chennemann.opencode.mobile.ui.state.SessionState
import de.chennemann.opencode.mobile.service.SessionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

class SessionDomainService(
    private val service: SessionService,
) {
    val state: StateFlow<HomeState> = service.state

    fun start(scope: CoroutineScope) {
        service.start(scope)
    }

    fun updateUrl(value: String) {
        service.updateUrl(value)
    }

    fun useDiscovered() {
        service.useDiscovered()
    }

    fun refresh() {
        service.refresh()
    }

    fun openManagement() {
        service.openManagement()
    }

    fun closeManagement() {
        service.closeManagement()
    }

    fun loadProjects() {
        service.loadProjects()
    }

    fun selectProject(worktree: String) {
        service.selectProject(worktree)
    }

    fun createSession() {
        service.createSession()
    }

    fun openSession(session: SessionState) {
        service.openSession(session)
    }

    fun send(text: String) {
        service.send(text)
    }

    fun loadMoreMessages() {
        service.loadMoreMessages()
    }
}
