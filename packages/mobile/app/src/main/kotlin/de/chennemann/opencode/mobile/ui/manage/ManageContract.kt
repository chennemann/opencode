package de.chennemann.opencode.mobile.ui.manage

import de.chennemann.opencode.mobile.domain.session.ProjectState
import de.chennemann.opencode.mobile.domain.session.SessionState

data class ManageUiState(
    val url: String,
    val discovered: String?,
    val loadingProjects: Boolean,
    val projects: List<ProjectState>,
    val selectedProject: String?,
    val loadingSessions: Boolean,
    val sessions: List<SessionState>,
)

sealed interface ManageEvent {
    data class UrlChanged(val value: String) : ManageEvent

    data object UseDiscoveredTapped : ManageEvent

    data object ConnectTapped : ManageEvent

    data object LoadProjectsTapped : ManageEvent

    data class ProjectSelected(val worktree: String) : ManageEvent

    data object CreateSessionTapped : ManageEvent

    data class OpenSessionTapped(val session: SessionState) : ManageEvent

    data object BackTapped : ManageEvent
}
