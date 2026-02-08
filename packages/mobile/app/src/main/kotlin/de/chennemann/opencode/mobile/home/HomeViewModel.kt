package de.chennemann.opencode.mobile.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.chennemann.opencode.mobile.service.HomeService
import kotlinx.coroutines.flow.StateFlow

class HomeViewModel(
    private val service: HomeService,
) : ViewModel() {
    val state: StateFlow<HomeState> = service.state

    init {
        service.start(viewModelScope)
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

    fun focusSession(sessionId: String) {
        service.focusSession(sessionId)
    }

    fun openManagement() {
        service.openManagement()
    }

    fun closeManagement() {
        service.closeManagement()
    }

    override fun onCleared() {
        service.stop()
        super.onCleared()
    }
}
