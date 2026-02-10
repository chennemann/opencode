package de.chennemann.opencode.mobile.ui.manage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.chennemann.opencode.mobile.domain.session.SessionService
import de.chennemann.opencode.mobile.navigation.NavEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class ManageViewModel(
    private val service: SessionService,
) : ViewModel() {
    private val navFlow = MutableSharedFlow<NavEvent>(extraBufferCapacity = 1)

    val nav = navFlow.asSharedFlow()

    val state: StateFlow<ManageUiState> = service.state.map {
        ManageUiState(
            url = it.url,
            discovered = it.discovered,
            loadingProjects = it.loadingProjects,
            projects = it.projects,
            selectedProject = it.selectedProject,
            loadingSessions = it.loadingSessions,
            sessions = it.sessions,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ManageUiState(
            url = service.state.value.url,
            discovered = service.state.value.discovered,
            loadingProjects = false,
            projects = emptyList(),
            selectedProject = null,
            loadingSessions = false,
            sessions = emptyList(),
        ),
    )

    init {
        service.start(viewModelScope)
    }

    fun onEvent(event: ManageEvent) {
        when (event) {
            is ManageEvent.UrlChanged -> service.updateUrl(event.value)
            is ManageEvent.UseDiscoveredTapped -> service.useDiscovered()
            is ManageEvent.ConnectTapped -> service.refresh()
            is ManageEvent.LoadProjectsTapped -> service.loadProjects()
            is ManageEvent.ProjectSelected -> service.selectProject(event.worktree)
            is ManageEvent.CreateSessionTapped -> {
                service.createSession()
                navFlow.tryEmit(NavEvent.Back)
            }

            is ManageEvent.OpenSessionTapped -> {
                service.openSession(event.session)
                navFlow.tryEmit(NavEvent.Back)
            }

            is ManageEvent.BackTapped -> {
                navFlow.tryEmit(NavEvent.Back)
            }
        }
    }
}
