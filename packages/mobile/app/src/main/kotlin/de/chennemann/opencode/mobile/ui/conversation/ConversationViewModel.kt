package de.chennemann.opencode.mobile.ui.conversation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.chennemann.opencode.mobile.domain.session.CommandState
import de.chennemann.opencode.mobile.domain.session.ProjectState
import de.chennemann.opencode.mobile.domain.session.ServerState
import de.chennemann.opencode.mobile.domain.session.SessionState
import de.chennemann.opencode.mobile.domain.session.SessionService
import de.chennemann.opencode.mobile.navigation.NavEvent
import kotlinx.coroutines.Dispatchers
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
    private val service: SessionService,
) : ViewModel() {
    private val mapper = ConversationRenderMapper()

    private data class GlobalRenderState(
        val title: String,
        val status: ServerState,
        val focusedSession: SessionState?,
        val turns: List<ConversationTurnUiState>,
        val commands: List<CommandState>,
        val projects: List<ProjectState>,
        val activeSessions: List<SessionState>,
        val quickPinInclude: Set<String>,
        val quickPinExclude: Set<String>,
        val canLoadMoreMessages: Boolean,
        val loadingMoreMessages: Boolean,
    )

    private data class LocalState(
        val scroll: Long = 0,
        val draft: String = "",
        val stepOpen: Map<String, Boolean> = emptyMap(),
        val callOpen: Map<String, Boolean> = emptyMap(),
        val quickSwitchMenu: QuickSwitchMenuState? = null,
    )

    private data class QuickSwitchProject(
        val key: String,
        val worktree: String,
        val project: String,
        val primary: SessionState,
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
                focusedSession = it.focusedSession,
                turns = mapper.map(it.focusedMessages),
                commands = mergeCommands(it.commands),
                projects = it.projects,
                activeSessions = it.activeSessions,
                quickPinInclude = it.quickPinInclude,
                quickPinExclude = it.quickPinExclude,
                canLoadMoreMessages = it.canLoadMoreMessages,
                loadingMoreMessages = it.loadingMoreMessages,
            )
        }
        .flowOn(Dispatchers.Default)

    val state: StateFlow<ConversationUiState> = combine(global, local) { global, local ->
        val quick = quickSwitchModel(
            global.projects,
            global.activeSessions,
            global.focusedSession,
            global.quickPinInclude,
            global.quickPinExclude,
        )
        ConversationUiState(
            title = global.title,
            status = global.status,
            turns = global.turns,
            canLoadMoreMessages = global.canLoadMoreMessages,
            loadingMoreMessages = global.loadingMoreMessages,
            scroll = local.scroll,
            draft = local.draft,
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
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ConversationUiState(
                title = "No session selected",
                status = service.state.value.status,
                turns = emptyList(),
                canLoadMoreMessages = false,
                loadingMoreMessages = false,
                scroll = 0,
                draft = "",
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

            is ConversationEvent.DraftChanged -> {
                local.update {
                    it.copy(draft = event.value)
                }
            }

            is ConversationEvent.SlashCommandSelected -> {
                local.update { it.copy(draft = "/${event.name} ") }
            }

            is ConversationEvent.QuickSwitchTapped -> {
                quickSwitchTap(event.key)
            }

            is ConversationEvent.QuickSwitchLongPressed -> {
                quickSwitchLongPress(event.key)
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

            is ConversationEvent.QuickSwitchMenuCreateTapped -> {
                val worktree = local.value.quickSwitchMenu?.worktree ?: return
                local.update { it.copy(quickSwitchMenu = null) }
                viewModelScope.launch {
                    service.createSessionAndFocus(worktree)
                }
            }

            is ConversationEvent.SendTapped -> {
                val value = local.value.draft
                service.send(value)
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
        )
        val project = model.projects[key] ?: return
        if (model.focusedKey != key) {
            service.openSession(project.primary)
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

    private fun quickSwitchLongPress(key: String) {
        val value = service.state.value
        val model = quickSwitchModel(
            value.projects,
            value.activeSessions,
            value.focusedSession,
            value.quickPinInclude,
            value.quickPinExclude,
        )
        val project = model.projects[key] ?: return
        local.update {
            it.copy(
                quickSwitchMenu = QuickSwitchMenuState(
                    key = project.key,
                    worktree = project.worktree,
                    project = project.project,
                    sessions = project.cycle,
                    loading = true,
                )
            )
        }
        viewModelScope.launch {
            val result = runCatching { service.sessionsForProject(project.worktree) }
            result.onSuccess { list ->
                local.update {
                    val menu = it.quickSwitchMenu ?: return@update it
                    if (menu.key != key) return@update it
                    it.copy(
                        quickSwitchMenu = menu.copy(
                            sessions = list,
                            loading = false,
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
    ): QuickSwitchModel {
        val lookup = projectLookup(projects)
        val byKey = projects.associateBy { workspaceId(it.worktree) }
        val cutoff = System.currentTimeMillis() - QuickSwitchWindowMs
        val focused = focusedSession?.let {
            lookup[workspaceId(it.directory)] ?: workspaceId(it.directory)
        }
        val rows = sessions
            .filter { it.archivedAt == null }
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
                val state = QuickSwitchState(
                    key = key,
                    worktree = project?.worktree ?: key,
                    label = projectInitial(label),
                    project = label,
                    active = focused == key,
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
                compareByDescending<Pair<QuickSwitchState, QuickSwitchProject>> { it.second.primary.updatedAt ?: 0L }
                    .thenByDescending { it.second.primary.id }
            )
        val keys = rows.map { it.first.key }.toSet()
        quickOrder.keys
            .toList()
            .filterNot(keys::contains)
            .forEach(quickOrder::remove)
        return QuickSwitchModel(
            focusedKey = focused,
            switches = rows.map { it.first },
            projects = rows.associate { it.first.key to it.second },
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
            .filter { it.archivedAt == null }
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
}

private val SlashRegex = Regex("^/(\\S*)$")
private const val QuickSwitchWindowMs = 2 * 60 * 60 * 1000L
private const val QuickSwitchFavoriteWindowMs = 30 * 60 * 1000L
private val BuiltinCommands = listOf(
    CommandState(
        name = "new",
        description = "Create a new session",
        source = "builtin",
    ),
)
