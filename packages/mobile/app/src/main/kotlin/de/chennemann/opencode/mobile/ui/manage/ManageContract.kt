package de.chennemann.opencode.mobile.ui.manage

import de.chennemann.opencode.mobile.domain.session.ProjectState
import de.chennemann.opencode.mobile.domain.session.ServerState
import de.chennemann.opencode.mobile.domain.session.SessionState

data class SessionSectionState(
    val title: String,
    val sessions: List<SessionState>,
)

data class ManageUiState(
    val url: String,
    val discovered: String?,
    val status: ServerState,
    val projectPath: String,
    val projectQuery: String,
    val loadingProjects: Boolean,
    val projectsExpanded: Boolean,
    val favoriteProjects: List<ProjectState>,
    val otherProjects: List<ProjectState>,
    val selectedProject: String?,
    val selectedProjectName: String?,
    val sessionScroll: Long,
    val loadingSessions: Boolean,
    val sessionRecentOnly: Boolean,
    val sessionSections: List<SessionSectionState>,
    val message: String?,
)

sealed interface ManageEvent {
    data class UrlChanged(val value: String) : ManageEvent

    data object UseDiscoveredTapped : ManageEvent

    data object ConnectTapped : ManageEvent

    data class ProjectPathChanged(val value: String) : ManageEvent

    data class ProjectQueryChanged(val value: String) : ManageEvent

    data object ProjectListToggleTapped : ManageEvent

    data object OpenProjectTapped : ManageEvent

    data class ProjectSelected(val worktree: String) : ManageEvent

    data class ProjectFavoriteToggled(val worktree: String) : ManageEvent

    data object CreateSessionTapped : ManageEvent

    data object LoadMoreSessionsTapped : ManageEvent

    data class OpenSessionTapped(val session: SessionState) : ManageEvent

    data object BackTapped : ManageEvent
}
