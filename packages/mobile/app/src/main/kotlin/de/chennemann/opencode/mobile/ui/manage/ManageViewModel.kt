package de.chennemann.opencode.mobile.ui.manage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.chennemann.opencode.mobile.domain.session.ProjectState
import de.chennemann.opencode.mobile.domain.session.ServerState
import de.chennemann.opencode.mobile.domain.session.SessionState
import de.chennemann.opencode.mobile.domain.session.SessionService
import de.chennemann.opencode.mobile.navigation.NavEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

class ManageViewModel(
    private val service: SessionService,
) : ViewModel() {
    private data class LocalState(
        val projectPath: String,
        val projectQuery: String,
        val projectsExpanded: Boolean,
        val sessionScroll: Long,
    )

    private val local = MutableStateFlow(
        LocalState(
            projectPath = service.state.value.selectedProject.orEmpty(),
            projectQuery = "",
            projectsExpanded = false,
            sessionScroll = 0L,
        )
    )
    private val navFlow = MutableSharedFlow<NavEvent>(extraBufferCapacity = 1)

    val nav = navFlow.asSharedFlow()

    val state: StateFlow<ManageUiState> = combine(service.state, local) { global, local ->
        val listed = global.projects.map(::displayProject)
        val projects = filterProjects(listed, local.projectQuery)
        ManageUiState(
            url = global.url,
            discovered = global.discovered,
            status = global.status,
            projectPath = local.projectPath,
            projectQuery = local.projectQuery,
            loadingProjects = global.loadingProjects,
            projectsExpanded = local.projectsExpanded,
            favoriteProjects = projects.filter { it.favorite },
            otherProjects = projects.filterNot { it.favorite },
            selectedProject = global.selectedProject,
            selectedProjectName = selectedProjectName(listed, global.selectedProject),
            sessionScroll = local.sessionScroll,
            loadingSessions = global.loadingSessions,
            sessionRecentOnly = global.sessionRecentOnly,
            sessionSections = sessionSections(global.sessions),
            message = global.message,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ManageUiState(
            url = service.state.value.url,
            discovered = service.state.value.discovered,
            status = ServerState.Idle,
            projectPath = service.state.value.selectedProject.orEmpty(),
            projectQuery = "",
            loadingProjects = false,
            projectsExpanded = false,
            favoriteProjects = emptyList(),
            otherProjects = emptyList(),
            selectedProject = null,
            selectedProjectName = null,
            sessionScroll = 0L,
            loadingSessions = false,
            sessionRecentOnly = true,
            sessionSections = emptyList(),
            message = null,
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
            is ManageEvent.ProjectPathChanged -> {
                local.value = local.value.copy(projectPath = event.value)
            }

            is ManageEvent.ProjectQueryChanged -> {
                local.value = local.value.copy(projectQuery = event.value)
            }

            is ManageEvent.ProjectListToggleTapped -> {
                local.value = local.value.copy(projectsExpanded = !local.value.projectsExpanded)
            }

            is ManageEvent.OpenProjectTapped -> {
                val worktree = local.value.projectPath.trim()
                if (worktree.isBlank()) return
                service.selectProject(worktree)
                local.value = local.value.copy(
                    projectPath = worktree,
                    sessionScroll = local.value.sessionScroll + 1,
                )
            }

            is ManageEvent.ProjectSelected -> {
                local.value = local.value.copy(
                    projectPath = event.worktree,
                    sessionScroll = local.value.sessionScroll + 1,
                )
                service.selectProject(event.worktree)
            }

            is ManageEvent.ProjectFavoriteToggled -> {
                service.toggleProjectFavorite(event.worktree)
            }

            is ManageEvent.CreateSessionTapped -> {
                viewModelScope.launch {
                    if (service.createSessionAndFocus()) {
                        navFlow.tryEmit(NavEvent.ToConversation)
                    }
                }
            }

            is ManageEvent.LoadMoreSessionsTapped -> {
                service.loadMoreSessions()
            }

            is ManageEvent.OpenSessionTapped -> {
                service.openSession(event.session)
                navFlow.tryEmit(NavEvent.ToConversation)
            }

            is ManageEvent.BackTapped -> {
                navFlow.tryEmit(NavEvent.Back)
            }
        }
    }

    private fun filterProjects(projects: List<ProjectState>, query: String): List<ProjectState> {
        val value = query.trim().lowercase()
        if (value.isBlank()) return projects
        return projects.filter {
            it.name.lowercase().contains(value) || it.worktree.lowercase().contains(value)
        }
    }

    private fun displayProject(project: ProjectState): ProjectState {
        return project.copy(name = folderName(project.worktree))
    }

    private fun selectedProjectName(projects: List<ProjectState>, selected: String?): String? {
        if (selected.isNullOrBlank()) return null
        return projects.firstOrNull { it.worktree == selected }?.name ?: folderName(selected)
    }

    private fun folderName(path: String): String {
        val value = path.trimEnd('/', '\\')
        if (value.isBlank()) return path
        val slash = value.lastIndexOf('/')
        val backslash = value.lastIndexOf('\\')
        val index = maxOf(slash, backslash)
        if (index < 0) return value
        val name = value.substring(index + 1)
        if (name.isBlank()) return value
        return name
    }

    private fun sessionSections(sessions: List<SessionState>): List<SessionSectionState> {
        if (sessions.isEmpty()) return emptyList()
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val todayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val yesterdayStart = today.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val todaySessions = sessions.filter { (it.updatedAt ?: 0L) >= todayStart }
        val yesterdaySessions = sessions.filter {
            val updatedAt = it.updatedAt ?: return@filter false
            updatedAt >= yesterdayStart && updatedAt < todayStart
        }
        val olderSessions = sessions.filter {
            val updatedAt = it.updatedAt
            if (updatedAt == null) return@filter true
            updatedAt < yesterdayStart
        }
        return buildList {
            if (todaySessions.isNotEmpty()) add(SessionSectionState("Today", todaySessions))
            if (yesterdaySessions.isNotEmpty()) add(SessionSectionState("Yesterday", yesterdaySessions))
            if (olderSessions.isNotEmpty()) add(SessionSectionState("Older", olderSessions))
        }
    }
}
