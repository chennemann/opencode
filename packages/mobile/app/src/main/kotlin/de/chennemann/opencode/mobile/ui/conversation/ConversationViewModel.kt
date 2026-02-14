package de.chennemann.opencode.mobile.ui.conversation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.chennemann.opencode.mobile.di.DispatcherProvider
import de.chennemann.opencode.mobile.domain.session.CommandState
import de.chennemann.opencode.mobile.domain.session.ProjectState
import de.chennemann.opencode.mobile.domain.session.ServerState
import de.chennemann.opencode.mobile.domain.session.SessionServiceApi
import de.chennemann.opencode.mobile.domain.session.SessionState
import de.chennemann.opencode.mobile.navigation.NavEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ConversationViewModel(
    private val service: SessionServiceApi,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {
    private val mapper = ConversationRenderMapper()
    private val lane = dispatchers.default.limitedParallelism(1)

    private data class GlobalRenderState(
        val title: String,
        val status: ServerState,
        val message: String?,
        val focusedSession: SessionState?,
        val turns: List<ConversationTurnUiState>,
        val commands: List<CommandState>,
        val projects: List<ProjectState>,
        val activeSessions: List<SessionState>,
        val quickPinInclude: Set<String>,
        val quickPinExclude: Set<String>,
        val quickProcessing: Set<String>,
        val quickUnread: Set<String>,
        val canLoadMoreMessages: Boolean,
        val loadingMoreMessages: Boolean,
    )

    private data class LocalState(
        val scroll: Long = 0,
        val draft: String = "",
        val mode: ConversationMode = ConversationMode.BUILD,
        val stepOpen: Map<String, Boolean> = emptyMap(),
        val callOpen: Map<String, Boolean> = emptyMap(),
        val quickSwitchMenu: QuickSwitchMenuState? = null,
    )

    private data class QuickSwitchProject(
        val key: String,
        val worktree: String,
        val project: String,
        val primary: SessionState?,
        val cycle: List<SessionState>,
    )

    private data class QuickSwitchModel(
        val focusedKey: String?,
        val switches: List<QuickSwitchState>,
        val projects: Map<String, QuickSwitchProject>,
    )

    private val local = MutableStateFlow(LocalState())
    private val navFlow = MutableSharedFlow<NavEvent>(extraBufferCapacity = 1)
    private val quickOrder = linkedMapOf<String, List<String>>()

    val nav = navFlow.asSharedFlow()

    private val global = service.state
        .map {
            GlobalRenderState(
                title = it.focusedSession?.title ?: "No session selected",
                status = it.status,
                message = it.message,
                focusedSession = it.focusedSession,
                turns = mapper.map(it.focusedMessages),
                commands = mergeCommands(it.commands),
                projects = it.projects,
                activeSessions = it.activeSessions,
                quickPinInclude = it.quickPinInclude,
                quickPinExclude = it.quickPinExclude,
                quickProcessing = it.quickProcessing,
                quickUnread = it.quickUnread,
                canLoadMoreMessages = it.canLoadMoreMessages,
                loadingMoreMessages = it.loadingMoreMessages,
            )
        }
        .flowOn(lane)

    val state: StateFlow<ConversationUiState> = combine(global, local) { global, local ->
        val quick = quickSwitchModel(
            global.projects,
            global.activeSessions,
            global.focusedSession,
            global.quickPinInclude,
            global.quickPinExclude,
            global.quickProcessing,
            global.quickUnread,
        )
        ConversationUiState(
            title = global.title,
            status = global.status,
            message = global.message,
            turns = global.turns,
            canLoadMoreMessages = global.canLoadMoreMessages,
            loadingMoreMessages = global.loadingMoreMessages,
            scroll = local.scroll,
            draft = local.draft,
            mode = local.mode,
            slashSuggestions = slashSuggestions(local.draft, global.commands),
            quickSwitches = quick.switches,
            quickSwitchMenu = quickSwitchMenu(
                local.quickSwitchMenu,
                global.projects,
                global.quickPinInclude,
                global.quickPinExclude,
            ),
            stepOpen = local.stepOpen,
            callOpen = local.callOpen,
        )
    }
        .flowOn(lane)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ConversationUiState(
                title = "No session selected",
                status = service.state.value.status,
                message = service.state.value.message,
                turns = emptyList(),
                canLoadMoreMessages = false,
                loadingMoreMessages = false,
                scroll = 0,
                draft = "",
                mode = ConversationMode.BUILD,
                slashSuggestions = emptyList(),
                quickSwitches = emptyList(),
                quickSwitchMenu = null,
                stepOpen = emptyMap(),
                callOpen = emptyMap(),
            ),
        )

    init {
        service.start(viewModelScope)
    }

    fun onEvent(event: ConversationEvent) {
        when (event) {
            is ConversationEvent.OpenManageTapped -> {
                navFlow.tryEmit(NavEvent.ToManage)
            }

            is ConversationEvent.ToggleSteps -> {
                local.update {
                    it.copy(
                        stepOpen = it.stepOpen + (event.messageId to (it.stepOpen[event.messageId] != true)),
                    )
                }
            }

            is ConversationEvent.ToggleToolCall -> {
                local.update {
                    val selected = if (it.callOpen[event.callId] == true) {
                        emptyMap()
                    } else {
                        mapOf(event.callId to true)
                    }
                    it.copy(
                        callOpen = selected,
                    )
                }
            }

            is ConversationEvent.ToolCallSessionTapped -> {
                openToolCallSession(event.sessionId)
            }

            is ConversationEvent.DraftChanged -> {
                local.update {
                    it.copy(draft = event.value)
                }
            }

            is ConversationEvent.ModeChanged -> {
                local.update {
                    it.copy(mode = event.value)
                }
            }

            is ConversationEvent.SlashCommandSelected -> {
                local.update { it.copy(draft = "/${event.name} ") }
            }

            is ConversationEvent.QuickSwitchTapped -> {
                viewModelScope.launch(lane) {
                    quickSwitchTap(event.key)
                }
            }

            is ConversationEvent.QuickSwitchLongPressed -> {
                viewModelScope.launch(lane) {
                    quickSwitchLongPress(event.key)
                }
            }

            is ConversationEvent.QuickSwitchMenuDismissed -> {
                local.update { it.copy(quickSwitchMenu = null) }
            }

            is ConversationEvent.QuickSwitchMenuSessionTapped -> {
                local.update { it.copy(quickSwitchMenu = null) }
                service.openSession(event.session)
            }

            is ConversationEvent.QuickSwitchMenuPinTapped -> {
                service.toggleSessionQuickPin(event.session, event.systemPinned)
            }

            is ConversationEvent.QuickSwitchMenuArchiveTapped -> {
                quickSwitchArchive(event.session)
            }

            is ConversationEvent.QuickSwitchMenuRenameSubmitted -> {
                quickSwitchRename(event.session, event.title)
            }

            is ConversationEvent.QuickSwitchMenuLoadMoreTapped -> {
                quickSwitchLoadMore()
            }

            is ConversationEvent.QuickSwitchMenuCreateTapped -> {
                val worktree = local.value.quickSwitchMenu?.worktree ?: return
                local.update { it.copy(quickSwitchMenu = null) }
                viewModelScope.launch(lane) {
                    service.createSessionAndFocus(worktree)
                }
            }

            is ConversationEvent.SendTapped -> {
                val value = local.value.draft
                service.send(value, modeAgent(local.value.mode))
                if (value.isNotBlank()) {
                    local.update { it.copy(draft = "", scroll = it.scroll + 1) }
                }
            }

            is ConversationEvent.ReloadTapped -> {
                service.refresh()
            }

            is ConversationEvent.LoadMoreMessagesTapped -> {
                service.loadMoreMessages()
            }
        }
    }

    private fun modeAgent(mode: ConversationMode): String {
        return when (mode) {
            ConversationMode.PLAN -> "plan"
            ConversationMode.BUILD -> "build"
        }
    }

    private fun slashSuggestions(
        draft: String,
        commands: List<CommandState>,
    ): List<CommandState> {
        if (draft.isBlank()) return commands
        val match = SlashRegex.matchEntire(draft) ?: return emptyList()
        val query = match.groupValues.getOrNull(1)?.trim()?.lowercase().orEmpty()
        return commands
            .filter {
                if (query.isBlank()) return@filter true
                it.name.lowercase().contains(query) || it.description?.lowercase()?.contains(query) == true
            }
    }

    private fun mergeCommands(commands: List<CommandState>): List<CommandState> {
        return (BuiltinCommands + commands)
            .distinctBy { it.name.lowercase() }
    }

    private fun quickSwitchTap(key: String) {
        local.update { it.copy(quickSwitchMenu = null) }
        val value = service.state.value
        val model = quickSwitchModel(
            value.projects,
            value.activeSessions,
            value.focusedSession,
            value.quickPinInclude,
            value.quickPinExclude,
            value.quickProcessing,
            value.quickUnread,
        )
        val project = model.projects[key] ?: return
        if (model.focusedKey != key) {
            val primary = project.primary
            if (primary == null) {
                viewModelScope.launch(lane) {
                    service.createSessionAndFocus(project.worktree)
                }
                return
            }
            service.openSession(primary)
            return
        }
        if (project.cycle.isEmpty()) {
            viewModelScope.launch(lane) {
                service.createSessionAndFocus(project.worktree)
            }
            return
        }
        val current = value.focusedSession?.id
        val index = project.cycle.indexOfFirst { it.id == current }
        val next = if (index < 0 || index == project.cycle.lastIndex) {
            project.cycle.firstOrNull()
        } else {
            project.cycle.getOrNull(index + 1)
        } ?: return
        service.openSession(next)
    }

    private suspend fun quickSwitchLongPress(key: String) {
        val value = service.state.value
        val model = quickSwitchModel(
            value.projects,
            value.activeSessions,
            value.focusedSession,
            value.quickPinInclude,
            value.quickPinExclude,
            value.quickProcessing,
            value.quickUnread,
        )
        val project = model.projects[key] ?: return
        val limit = QuickSwitchMenuPageSize
        val cached = runCatching {
            service.cachedSessionsForProject(project.worktree, limit)
        }.getOrDefault(emptyList())
        local.update {
            it.copy(
                quickSwitchMenu = QuickSwitchMenuState(
                    key = project.key,
                    worktree = project.worktree,
                    project = project.project,
                    sessions = cached.take(limit),
                    loading = true,
                    limit = limit,
                    canLoadMore = cached.size >= limit,
                )
            )
        }
        fetchQuickSwitchMenu(project.key, project.worktree, limit)
    }

    private fun quickSwitchArchive(session: SessionState) {
        val menu = local.value.quickSwitchMenu ?: return
        val sessions = menu.sessions.filterNot { it.id == session.id }
        local.update {
            val current = it.quickSwitchMenu ?: return@update it
            if (current.key != menu.key) return@update it
            it.copy(
                quickSwitchMenu = current.copy(sessions = sessions),
            )
        }
        service.archiveSession(session)
    }

    private fun quickSwitchRename(session: SessionState, title: String) {
        val next = title.trim()
        if (next.isBlank()) return
        val menu = local.value.quickSwitchMenu ?: return
        local.update {
            val current = it.quickSwitchMenu ?: return@update it
            if (current.key != menu.key) return@update it
            it.copy(
                quickSwitchMenu = current.copy(
                    sessions = current.sessions.map {
                        if (it.id == session.id) {
                            it.copy(title = next)
                        } else {
                            it
                        }
                    },
                ),
            )
        }
        service.renameSession(session, next)
    }

    private fun quickSwitchLoadMore() {
        val menu = local.value.quickSwitchMenu ?: return
        if (menu.loading || !menu.canLoadMore) return
        val limit = menu.limit + QuickSwitchMenuPageSize
        local.update {
            val current = it.quickSwitchMenu ?: return@update it
            if (current.key != menu.key) return@update it
            it.copy(
                quickSwitchMenu = current.copy(
                    loading = true,
                    limit = limit,
                )
            )
        }
        fetchQuickSwitchMenu(menu.key, menu.worktree, limit)
    }

    private fun fetchQuickSwitchMenu(key: String, worktree: String, limit: Int) {
        viewModelScope.launch(lane) {
            val result = runCatching { service.sessionsForProject(worktree, limit) }
            result.onSuccess { list ->
                local.update {
                    val menu = it.quickSwitchMenu ?: return@update it
                    if (menu.key != key) return@update it
                    val sessions = list.take(limit)
                    val canLoadMore = list.size >= limit
                    if (menu.sessions == sessions && menu.canLoadMore == canLoadMore && menu.limit == limit) {
                        if (!menu.loading) return@update it
                        return@update it.copy(
                            quickSwitchMenu = menu.copy(loading = false),
                        )
                    }
                    it.copy(
                        quickSwitchMenu = menu.copy(
                            sessions = sessions,
                            loading = false,
                            limit = limit,
                            canLoadMore = canLoadMore,
                        )
                    )
                }
            }
            result.onFailure {
                local.update {
                    val menu = it.quickSwitchMenu ?: return@update it
                    if (menu.key != key) return@update it
                    it.copy(
                        quickSwitchMenu = menu.copy(loading = false),
                    )
                }
            }
        }
    }

    private fun quickSwitchModel(
        projects: List<ProjectState>,
        sessions: List<SessionState>,
        focusedSession: SessionState?,
        include: Set<String>,
        exclude: Set<String>,
        processing: Set<String>,
        unread: Set<String>,
    ): QuickSwitchModel {
        val lookup = projectLookup(projects)
        val byKey = projects.associateBy { workspaceId(it.worktree) }
        val cutoff = System.currentTimeMillis() - QuickSwitchWindowMs
        val focused = focusedSession?.let {
            lookup[workspaceId(it.directory)] ?: workspaceId(it.directory)
        }
        val rows = sessions
            .filter { it.archivedAt == null && it.parentId == null }
            .groupBy {
                val directory = workspaceId(it.directory)
                lookup[directory] ?: directory
            }
            .mapNotNull { (key, value) ->
                val all = value
                    .groupBy { it.id }
                    .mapNotNull {
                        it.value.maxWithOrNull(compareBy<SessionState>({ row -> row.updatedAt ?: 0L }, { row -> row.id }))
                    }
                    .sortedWith(
                        compareByDescending<SessionState> { it.updatedAt ?: 0L }
                            .thenByDescending { it.id }
                    )
                val project = byKey[key]
                val system = systemCycle(all, project?.favorite == true, cutoff)
                val eligible = effectiveCycle(all, system, include, exclude)
                if (eligible.isEmpty()) return@mapNotNull null
                val primary = eligible
                    .maxWithOrNull(compareBy<SessionState>({ it.updatedAt ?: 0L }, { it.id }))
                    ?: return@mapNotNull null
                val cycle = stableCycle(key, eligible)
                if (cycle.isEmpty()) return@mapNotNull null
                val label = projectLabel(project, key)
                val cycleIds = cycle.map { it.id }.toSet()
                val state = QuickSwitchState(
                    key = key,
                    worktree = project?.worktree ?: key,
                    label = projectInitial(label),
                    project = label,
                    active = focused == key,
                    processing = cycleIds.any(processing::contains),
                    unread = cycle.count { unread.contains(it.id) && !processing.contains(it.id) },
                )
                state to QuickSwitchProject(
                    key = key,
                    worktree = project?.worktree ?: key,
                    project = label,
                    primary = primary,
                    cycle = cycle,
                )
            }
            .sortedWith(
                compareByDescending<Pair<QuickSwitchState, QuickSwitchProject>> { it.second.primary?.updatedAt ?: Long.MIN_VALUE }
                    .thenByDescending { it.second.primary?.id.orEmpty() }
            )
        val existing = rows.map { it.first.key }.toSet()
        val placeholders = projects
            .filter { it.favorite }
            .mapNotNull {
                val key = workspaceId(it.worktree)
                if (existing.contains(key)) return@mapNotNull null
                val label = projectLabel(it, key)
                val state = QuickSwitchState(
                    key = key,
                    worktree = it.worktree,
                    label = projectInitial(label),
                    project = label,
                    active = focused == key,
                    processing = false,
                    unread = 0,
                )
                state to QuickSwitchProject(
                    key = key,
                    worktree = it.worktree,
                    project = label,
                    primary = null,
                    cycle = emptyList(),
                )
            }
        val merged = (rows + placeholders)
            .sortedWith(
                compareBy<Pair<QuickSwitchState, QuickSwitchProject>> { projectInitial(it.first.project) }
                    .thenBy { it.first.project.lowercase() }
                    .thenBy { workspaceId(it.first.worktree).lowercase() }
            )
        val keys = merged.map { it.first.key }.toSet()
        quickOrder.keys
            .toList()
            .filterNot(keys::contains)
            .forEach(quickOrder::remove)
        return QuickSwitchModel(
            focusedKey = focused,
            switches = merged.map { it.first },
            projects = merged.associate { it.first.key to it.second },
        )
    }

    private fun quickSwitchMenu(
        menu: QuickSwitchMenuState?,
        projects: List<ProjectState>,
        include: Set<String>,
        exclude: Set<String>,
    ): QuickSwitchMenuState? {
        if (menu == null) return null
        val worktree = workspaceId(menu.worktree)
        val favorite = projects.firstOrNull { workspaceId(it.worktree) == worktree }?.favorite == true
        val sessions = menu.sessions
            .filter { it.archivedAt == null && it.parentId == null }
            .groupBy { it.id }
            .mapNotNull {
                it.value.maxWithOrNull(compareBy<SessionState>({ value -> value.updatedAt ?: 0L }, { value -> value.id }))
            }
            .sortedWith(
                compareByDescending<SessionState> { it.updatedAt ?: 0L }
                    .thenByDescending { it.id }
            )
        val cutoff = System.currentTimeMillis() - QuickSwitchWindowMs
        val system = systemCycle(sessions, favorite, cutoff)
        val pinned = effectiveCycle(sessions, system, include, exclude)
            .map { it.id }
            .toSet()
        return menu.copy(
            sessions = sessions,
            pinned = pinned,
            systemPinned = system.map { it.id }.toSet(),
        )
    }

    private fun systemCycle(sessions: List<SessionState>, favorite: Boolean, cutoff: Long): List<SessionState> {
        val recent = sessions.filter { (it.updatedAt ?: 0L) >= cutoff }
        if (recent.isNotEmpty()) return recent
        if (!favorite) return emptyList()
        return favoriteFallback(sessions)
    }

    private fun effectiveCycle(
        sessions: List<SessionState>,
        system: List<SessionState>,
        include: Set<String>,
        exclude: Set<String>,
    ): List<SessionState> {
        val forced = sessions.filter { include.contains(it.id) }
        val base = system.filterNot { exclude.contains(it.id) }
        return (base + forced).distinctBy { it.id }
    }

    private fun stableCycle(key: String, sessions: List<SessionState>): List<SessionState> {
        val sorted = sessions
            .sortedWith(
                compareByDescending<SessionState> { it.updatedAt ?: 0L }
                    .thenByDescending { it.id }
            )
        val ids = sorted.map { it.id }.toSet()
        val keep = quickOrder[key].orEmpty().filter(ids::contains)
        val append = sorted
            .map { it.id }
            .filterNot(keep::contains)
        val next = keep + append
        quickOrder[key] = next
        val map = sorted.associateBy { it.id }
        return next.mapNotNull(map::get)
    }

    private fun favoriteFallback(sessions: List<SessionState>): List<SessionState> {
        val latest = sessions
            .maxWithOrNull(compareBy<SessionState>({ it.updatedAt ?: 0L }, { it.id }))
            ?: return emptyList()
        val updatedAt = latest.updatedAt ?: return listOf(latest)
        val cutoff = updatedAt - QuickSwitchFavoriteWindowMs
        val list = sessions.filter { (it.updatedAt ?: Long.MIN_VALUE) >= cutoff }
        if (list.isEmpty()) return listOf(latest)
        return list
    }

    private fun projectLookup(projects: List<ProjectState>): Map<String, String> {
        return projects
            .flatMap {
                val key = workspaceId(it.worktree)
                (listOf(it.worktree) + it.sandboxes)
                    .map(::workspaceId)
                    .distinct()
                    .map { directory -> directory to key }
            }
            .toMap()
    }

    private fun projectLabel(project: ProjectState?, fallback: String): String {
        if (project == null) return folderName(fallback)
        val name = project.name.trim()
        if (name.isNotBlank()) return name
        return folderName(project.worktree)
    }

    private fun projectInitial(name: String): String {
        return folderName(name)
            .firstOrNull { it.isLetterOrDigit() }
            ?.uppercaseChar()
            ?.toString()
            ?: "?"
    }

    private fun folderName(path: String): String {
        val value = path.trim().trimEnd('/', '\\')
        if (value.isBlank()) return path
        val index = maxOf(value.lastIndexOf('/'), value.lastIndexOf('\\'))
        if (index < 0) return value
        val name = value.substring(index + 1)
        if (name.isBlank()) return value
        return name
    }

    private fun workspaceId(path: String): String {
        return path.trimEnd('/', '\\')
    }

    private fun openToolCallSession(sessionId: String) {
        val id = sessionId.trim()
        if (id.isBlank()) return
        val value = service.state.value
        val known = (value.activeSessions + value.sessions)
            .firstOrNull { it.id == id }
        if (known != null) {
            service.openSession(known)
            return
        }
        val focused = value.focusedSession ?: return
        service.openSession(
            SessionState(
                id = id,
                title = "Subagent ${id.take(8)}",
                version = focused.version,
                directory = focused.directory,
                parentId = focused.id,
            )
        )
    }
}

private val SlashRegex = Regex("^/(\\S*)$")
private const val QuickSwitchMenuPageSize = 11
private const val QuickSwitchWindowMs = 2 * 60 * 60 * 1000L
private const val QuickSwitchFavoriteWindowMs = 30 * 60 * 1000L
private val BuiltinCommands = listOf(
    CommandState(
        name = "new",
        description = "Create a new session",
        source = "builtin",
    ),
)
