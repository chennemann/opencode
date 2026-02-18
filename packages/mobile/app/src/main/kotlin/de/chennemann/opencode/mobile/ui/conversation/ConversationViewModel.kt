package de.chennemann.opencode.mobile.ui.conversation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.chennemann.opencode.mobile.di.DispatcherProvider
import de.chennemann.opencode.mobile.domain.service.model.CommandInput
import de.chennemann.opencode.mobile.domain.service.model.MessagePageInput
import de.chennemann.opencode.mobile.domain.service.model.RenameInput
import de.chennemann.opencode.mobile.domain.service.model.SendMessageInput
import de.chennemann.opencode.mobile.domain.service.session.SessionReadService
import de.chennemann.opencode.mobile.domain.usecase.connection.RefreshServerUseCase
import de.chennemann.opencode.mobile.domain.usecase.message.ExecuteCommandUseCase
import de.chennemann.opencode.mobile.domain.usecase.message.SendMessageUseCase
import de.chennemann.opencode.mobile.domain.usecase.session.ArchiveSessionUseCase
import de.chennemann.opencode.mobile.domain.usecase.session.CreateSessionUseCase
import de.chennemann.opencode.mobile.domain.usecase.session.FocusSessionUseCase
import de.chennemann.opencode.mobile.domain.usecase.session.RenameSessionUseCase
import de.chennemann.opencode.mobile.domain.usecase.session.RequestMessagePageUseCase
import de.chennemann.opencode.mobile.domain.session.CommandState
import de.chennemann.opencode.mobile.domain.session.MessageState
import de.chennemann.opencode.mobile.domain.session.ProjectState
import de.chennemann.opencode.mobile.domain.session.ServerState
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
    private val read: SessionReadService,
    private val dispatchers: DispatcherProvider,
    private val focusSession: FocusSessionUseCase,
    private val sendMessage: SendMessageUseCase,
    private val executeCommand: ExecuteCommandUseCase,
    private val requestMessagePage: RequestMessagePageUseCase,
    private val archiveSession: ArchiveSessionUseCase,
    private val renameSession: RenameSessionUseCase,
    private val createSession: CreateSessionUseCase,
    private val refreshServer: RefreshServerUseCase,
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
        val globalSessions: List<SessionState>,
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
        val quickPinInclude: Set<String> = emptySet(),
        val quickPinExclude: Set<String> = emptySet(),
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

    private val global = read.state
        .map {
            GlobalRenderState(
                title = it.focusedSession?.title ?: "No session selected",
                status = it.status,
                message = it.message,
                focusedSession = it.focusedSession,
                turns = mapper.map(it.focusedMessages),
                commands = mergeCommands(it.commands),
                projects = it.projects,
                globalSessions = it.globalSessions,
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
        val include = effectiveInclude(global.quickPinInclude, local.quickPinInclude, local.quickPinExclude)
        val exclude = effectiveExclude(global.quickPinExclude, local.quickPinExclude, local.quickPinInclude)
        val quick = quickSwitchModel(
            global.projects,
            global.globalSessions,
            global.focusedSession,
            include,
            exclude,
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
                include,
                exclude,
            ),
            stepOpen = local.stepOpen,
            callOpen = local.callOpen,
        )
    }
        .flowOn(lane)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = ConversationUiState(
                title = "No session selected",
                status = read.state.value.status,
                message = read.state.value.message,
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
                viewModelScope.launch(lane) {
                    focusSession(event.session.id)
                }
            }

            is ConversationEvent.QuickSwitchMenuPinTapped -> {
                quickSwitchPin(event.session, event.systemPinned)
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
                    createSession(worktree)
                }
            }

            is ConversationEvent.SendTapped -> {
                val value = local.value.draft
                val agent = modeAgent(local.value.mode)
                if (value.isNotBlank()) {
                    local.update { it.copy(draft = "", scroll = it.scroll + 1) }
                }
                viewModelScope.launch(lane) {
                    if (handleBuiltinCommand(value)) {
                        return@launch
                    }
                    val command = commandInput(value, agent)
                    if (command != null) {
                        executeCommand(command)
                        return@launch
                    }
                    val focused = read.state.value.focusedSession
                    sendMessage(
                        SendMessageInput(
                            text = value,
                            agent = agent,
                            sessionId = focused?.id,
                            directory = focused?.directory,
                        )
                    )
                }
            }

            is ConversationEvent.ReloadTapped -> {
                viewModelScope.launch(lane) {
                    refreshServer(read.state.value.url)
                }
            }

            is ConversationEvent.LoadMoreMessagesTapped -> {
                viewModelScope.launch(lane) {
                    val focused = read.state.value.focusedSession ?: return@launch
                    val before = earliestLoadedMessageId(read.state.value.focusedMessages)
                    requestMessagePage(
                        MessagePageInput(
                            sessionId = focused.id,
                            beforeMessageId = before,
                        )
                    )
                }
            }
        }
    }

    private fun commandInput(value: String, agent: String): CommandInput? {
        val raw = value.trim()
        if (!raw.startsWith("/")) return null
        val focused = read.state.value.focusedSession ?: return null
        val parts = raw.split(Regex("\\s+"), limit = 2)
        val name = parts.firstOrNull()?.removePrefix("/")?.trim().orEmpty()
        if (name.isBlank()) return null
        val known = read.state.value.commands.any { it.name == name }
        if (!known) return null
        return CommandInput(
            raw = raw,
            sessionId = focused.id,
            directory = focused.directory,
            projectId = read.state.value.selectedProjectId.orEmpty(),
            agent = agent,
        )
    }

    private suspend fun handleBuiltinCommand(value: String): Boolean {
        val raw = value.trim()
        if (!raw.startsWith("/")) return false
        val name = raw.substringAfter('/').substringBefore(' ').trim().lowercase()
        if (name != "new") return false
        val focused = read.state.value.focusedSession?.directory?.trim().orEmpty()
        val selected = read.state.value.selectedProject?.trim().orEmpty()
        val directory = focused.ifBlank { selected }
        if (directory.isBlank()) return false
        createSession(directory)
        return true
    }

    private fun earliestLoadedMessageId(messages: List<MessageState>): String? {
        return messages
            .minWithOrNull(compareBy<MessageState> { it.sort }.thenBy { it.id })
            ?.id
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

    private suspend fun quickSwitchTap(key: String) {
        local.update { it.copy(quickSwitchMenu = null) }
        val value = read.state.value
        val include = effectiveInclude(value.quickPinInclude, local.value.quickPinInclude, local.value.quickPinExclude)
        val exclude = effectiveExclude(value.quickPinExclude, local.value.quickPinExclude, local.value.quickPinInclude)
        val model = quickSwitchModel(
            value.projects,
            value.globalSessions,
            value.focusedSession,
            include,
            exclude,
            value.quickProcessing,
            value.quickUnread,
        )
        val project = model.projects[key] ?: return
        if (model.focusedKey != key) {
            val primary = project.primary ?: quickSwitchTapSessions(project.key, project.worktree, include, exclude)
                .firstOrNull()
            if (primary == null) {
                viewModelScope.launch(lane) {
                    createSession(project.worktree)
                }
                return
            }
            viewModelScope.launch(lane) {
                focusSession(primary.id)
            }
            return
        }
        val cycle = if (project.cycle.isNotEmpty()) {
            project.cycle
        } else {
            quickSwitchTapSessions(project.key, project.worktree, include, exclude)
        }
        if (cycle.isEmpty()) {
            viewModelScope.launch(lane) {
                createSession(project.worktree)
            }
            return
        }
        val current = value.focusedSession?.id
        val index = cycle.indexOfFirst { it.id == current }
        val next = if (index < 0 || index == cycle.lastIndex) {
            cycle.firstOrNull()
        } else {
            cycle.getOrNull(index + 1)
        } ?: return
        viewModelScope.launch(lane) {
            focusSession(next.id)
        }
    }

    private suspend fun quickSwitchTapSessions(
        key: String,
        worktree: String,
        include: Set<String>,
        exclude: Set<String>,
    ): List<SessionState> {
        val rows = runCatching { read.sessionsForProject(worktree, QuickSwitchTapLimit) }
            .getOrDefault(emptyList())
            .filter { it.archivedAt == null }
            .groupBy { it.id }
            .mapNotNull {
                it.value.maxWithOrNull(compareBy<SessionState>({ value -> value.updatedAt ?: 0L }, { value -> value.id }))
            }
            .sortedWith(
                compareByDescending<SessionState> { it.updatedAt ?: 0L }
                    .thenByDescending { it.id }
            )
        if (rows.isEmpty()) return emptyList()
        val favorite = read.state.value.projects
            .firstOrNull { workspaceId(it.worktree) == workspaceId(worktree) }
            ?.favorite == true
        val cutoff = System.currentTimeMillis() - QuickSwitchWindowMs
        val system = systemCycle(rows, favorite, cutoff)
        val cycle = effectiveCycle(rows, system, include, exclude)
        if (cycle.isEmpty()) return emptyList()
        return stableCycle(key, cycle)
    }

    private suspend fun quickSwitchLongPress(key: String) {
        val value = read.state.value
        val model = quickSwitchModel(
            value.projects,
            value.globalSessions,
            value.focusedSession,
            effectiveInclude(value.quickPinInclude, local.value.quickPinInclude, local.value.quickPinExclude),
            effectiveExclude(value.quickPinExclude, local.value.quickPinExclude, local.value.quickPinInclude),
            value.quickProcessing,
            value.quickUnread,
        )
        val project = model.projects[key] ?: return
        val limit = QuickSwitchMenuPageSize
        val cached = runCatching {
            read.sessionsForProject(project.worktree, limit)
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
        viewModelScope.launch(lane) {
            archiveSession(session.id, session.directory)
        }
    }

    private fun quickSwitchPin(session: SessionState, systemPinned: Boolean) {
        val menu = state.value.quickSwitchMenu ?: return
        val id = session.id
        val pinned = menu.pinned.contains(id)
        local.update {
            if (pinned) {
                val include = it.quickPinInclude - id
                val exclude = if (systemPinned || read.state.value.quickPinInclude.contains(id)) {
                    it.quickPinExclude + id
                } else {
                    it.quickPinExclude - id
                }
                return@update it.copy(
                    quickPinInclude = include,
                    quickPinExclude = exclude,
                )
            }
            it.copy(
                quickPinInclude = it.quickPinInclude + id,
                quickPinExclude = it.quickPinExclude - id,
            )
        }
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
        viewModelScope.launch(lane) {
            renameSession(RenameInput(sessionId = session.id, title = next, directory = session.directory))
        }
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
            val result = runCatching { read.sessionsForProject(worktree, limit) }
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

private fun effectiveInclude(base: Set<String>, include: Set<String>, exclude: Set<String>): Set<String> {
    return (base + include) - exclude
}

private fun effectiveExclude(base: Set<String>, exclude: Set<String>, include: Set<String>): Set<String> {
    return (base + exclude) - include
}

    private fun openToolCallSession(sessionId: String) {
        val id = sessionId.trim()
        if (id.isBlank()) return
        val value = read.state.value
        val known = (value.activeSessions + value.sessions)
            .firstOrNull { it.id == id }
        if (known != null) {
            viewModelScope.launch(lane) {
                focusSession(id)
            }
            return
        }
        viewModelScope.launch(lane) {
            focusSession(id)
        }
    }
}

private val SlashRegex = Regex("^/(\\S*)$")
private const val QuickSwitchMenuPageSize = 11
private const val QuickSwitchTapLimit = 200
private const val QuickSwitchWindowMs = 2 * 60 * 60 * 1000L
private const val QuickSwitchFavoriteWindowMs = 30 * 60 * 1000L
private val BuiltinCommands = listOf(
    CommandState(
        name = "new",
        description = "Create a new session",
        source = "builtin",
    ),
)
