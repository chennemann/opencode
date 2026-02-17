package de.chennemann.opencode.mobile.domain.service.session

import de.chennemann.opencode.mobile.data.repository.CommandRepository
import de.chennemann.opencode.mobile.data.repository.ConnectionRepository
import de.chennemann.opencode.mobile.data.repository.MessagePageRequest
import de.chennemann.opencode.mobile.data.repository.ProjectRepository
import de.chennemann.opencode.mobile.data.repository.SessionListFilter
import de.chennemann.opencode.mobile.data.repository.SessionRepository
import de.chennemann.opencode.mobile.domain.session.ServerState
import de.chennemann.opencode.mobile.domain.session.SessionState
import de.chennemann.opencode.mobile.domain.session.SessionUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class DefaultSessionReadService(
    private val connection: ConnectionRepository,
    private val project: ProjectRepository,
    private val command: CommandRepository,
    private val session: SessionRepository,
    scope: CoroutineScope,
) : SessionReadService {
    private val selected = project.observeSelectedProject()
    private val commands = selected.flatMapLatest {
        if (it == null) return@flatMapLatest flowOf(emptyList())
        command.observeCommands(it.id)
    }
    private val sessions = selected.flatMapLatest {
        if (it == null) return@flatMapLatest flowOf(emptyList())
        session.observeSessionList(it.id, SessionListFilter(limit = SessionLimit))
    }
    private val base = combine(
        connection.observeConnection(),
        project.observeProjects(),
        selected,
        commands,
        sessions,
    ) { connection, projects, selected, commands, sessions ->
        SessionBaseState(
            url = connection.endpoint,
            discovered = connection.discovered,
            status = serverState(connection.status),
            projects = projects,
            selected = selected,
            commands = commands,
            sessions = sessions,
        )
    }

    override val state: StateFlow<SessionUiState> = combine(base, session.observeFocusedSession()) { base, focused ->
        SessionStateData(base, focused)
    }
        .flatMapLatest { value ->
            val focused = value.focused
            if (focused == null) {
                return@flatMapLatest flowOf(
                    SessionUiState(
                        url = value.base.url,
                        discovered = value.base.discovered,
                        status = value.base.status,
                        projects = value.base.projects,
                        selectedProject = value.base.selected?.worktree,
                        commands = value.base.commands,
                        sessions = value.base.sessions,
                        activeSessions = activeSessions(value.base.sessions, null),
                        focusedSession = null,
                        focusedMessages = emptyList(),
                        canLoadMoreMessages = false,
                        loadingMoreMessages = false,
                        loadingProjects = false,
                        loadingSessions = false,
                        sessionRecentOnly = false,
                        quickPinInclude = emptySet(),
                        quickPinExclude = emptySet(),
                        quickProcessing = emptySet(),
                        quickUnread = emptySet(),
                        message = null,
                    )
                )
            }
            session.observeMessagePage(focused.id, MessagePageRequest(limit = MessageLimit)).map { page ->
                SessionUiState(
                    url = value.base.url,
                    discovered = value.base.discovered,
                    status = value.base.status,
                    projects = value.base.projects,
                    selectedProject = value.base.selected?.worktree,
                    commands = value.base.commands,
                    sessions = value.base.sessions,
                    activeSessions = activeSessions(value.base.sessions, focused),
                    focusedSession = focused,
                    focusedMessages = page.items,
                    canLoadMoreMessages = page.hasMore,
                    loadingMoreMessages = false,
                    loadingProjects = false,
                    loadingSessions = false,
                    sessionRecentOnly = false,
                    quickPinInclude = emptySet(),
                    quickPinExclude = emptySet(),
                    quickProcessing = emptySet(),
                    quickUnread = emptySet(),
                    message = null,
                )
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, InitialState)

    override suspend fun sessionsForProject(worktree: String, limit: Int?): List<SessionState> {
        val key = workspaceId(worktree)
        if (key.isBlank()) return emptyList()
        val projects = project.observeProjects().first()
        val current = projects.firstOrNull {
            workspaceId(it.worktree) == key || it.sandboxes.any { sandbox -> workspaceId(sandbox) == key }
        } ?: return emptyList()
        val directories = (listOf(current.worktree) + current.sandboxes)
            .map(::workspaceId)
            .toSet()
        val rows = session
            .observeSessionList(current.id, SessionListFilter(limit = (limit ?: SessionLimit.toInt()).toLong()))
            .first()
            .filter {
                it.parentId == null && it.archivedAt == null && directories.contains(workspaceId(it.directory))
            }
            .sortedWith(
                compareByDescending<SessionState> { it.updatedAt ?: 0L }
                    .thenByDescending { it.id }
            )
        if (limit == null) return rows
        return rows.take(limit)
    }
}

private data class SessionBaseState(
    val url: String,
    val discovered: String?,
    val status: ServerState,
    val projects: List<de.chennemann.opencode.mobile.domain.session.ProjectState>,
    val selected: de.chennemann.opencode.mobile.domain.session.ProjectState?,
    val commands: List<de.chennemann.opencode.mobile.domain.session.CommandState>,
    val sessions: List<SessionState>,
)

private data class SessionStateData(
    val base: SessionBaseState,
    val focused: SessionState?,
)

private fun serverState(value: String): ServerState {
    if (value == "CONNECTED") return ServerState.Connected("connected")
    if (value == "LOADING") return ServerState.Loading
    if (value == "FAILED") return ServerState.Failed("Connection failed")
    return ServerState.Idle
}

private fun activeSessions(sessions: List<SessionState>, focused: SessionState?): List<SessionState> {
    if (focused == null) return sessions
    return (listOf(focused) + sessions).distinctBy { it.id }
}

private fun workspaceId(path: String): String {
    return path.trimEnd('/', '\\')
}

private val InitialState = SessionUiState(
    url = "",
    discovered = null,
    status = ServerState.Idle,
    projects = emptyList(),
    selectedProject = null,
    commands = emptyList(),
    sessions = emptyList(),
    activeSessions = emptyList(),
    focusedSession = null,
    focusedMessages = emptyList(),
    canLoadMoreMessages = false,
    loadingMoreMessages = false,
    loadingProjects = false,
    loadingSessions = false,
    sessionRecentOnly = false,
    quickPinInclude = emptySet(),
    quickPinExclude = emptySet(),
    quickProcessing = emptySet(),
    quickUnread = emptySet(),
    message = null,
)

private const val SessionLimit = 200L
private const val MessageLimit = 100L
