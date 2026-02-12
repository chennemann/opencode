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

class ConversationViewModel(
    private val service: SessionService,
) : ViewModel() {
    private val mapper = ConversationRenderMapper()
    private data class ActiveToolSlot(
        val id: String,
        val size: Int,
    )

    private val active = linkedMapOf<String, ActiveToolSlot>()

    private data class GlobalRenderState(
        val title: String,
        val status: ServerState,
        val turns: List<ConversationTurnUiState>,
        val commands: List<CommandState>,
        val projects: List<ProjectState>,
        val activeSessions: List<SessionState>,
        val canLoadMoreMessages: Boolean,
        val loadingMoreMessages: Boolean,
    )

    private data class LocalState(
        val scroll: Long = 0,
        val draft: String = "",
        val commandOpen: Boolean = false,
        val stepOpen: Map<String, Boolean> = emptyMap(),
        val callOpen: Map<String, Boolean> = emptyMap(),
    )

    private val local = MutableStateFlow(LocalState())
    private val navFlow = MutableSharedFlow<NavEvent>(extraBufferCapacity = 1)

    val nav = navFlow.asSharedFlow()

    private val global = service.state
        .map {
            GlobalRenderState(
                title = it.focusedSession?.title ?: "No session selected",
                status = it.status,
                turns = splitActiveTools(mapper.map(it.focusedMessages)),
                commands = mergeCommands(it.commands),
                projects = it.projects,
                activeSessions = it.activeSessions,
                canLoadMoreMessages = it.canLoadMoreMessages,
                loadingMoreMessages = it.loadingMoreMessages,
            )
        }
        .flowOn(Dispatchers.Default)

    val state: StateFlow<ConversationUiState> = combine(global, local) { global, local ->
        ConversationUiState(
            title = global.title,
            status = global.status,
            turns = global.turns,
            canLoadMoreMessages = global.canLoadMoreMessages,
            loadingMoreMessages = global.loadingMoreMessages,
            scroll = local.scroll,
            draft = local.draft,
            slashSuggestions = slashSuggestions(local.draft, global.commands, local.commandOpen),
            commandOpen = local.commandOpen,
            quickSwitches = quickSwitches(global.projects, global.activeSessions),
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
                commandOpen = false,
                quickSwitches = emptyList(),
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
                    it.copy(
                        callOpen = it.callOpen + (event.callId to (it.callOpen[event.callId] != true)),
                    )
                }
            }

            is ConversationEvent.DraftChanged -> {
                local.update {
                    it.copy(
                        draft = event.value,
                        commandOpen = it.commandOpen && (event.value.isBlank() || event.value.startsWith("/")),
                    )
                }
            }

            is ConversationEvent.SlashCommandSelected -> {
                local.update { it.copy(draft = "/${event.name} ", commandOpen = false) }
            }

            is ConversationEvent.CommandListToggled -> {
                local.update { it.copy(commandOpen = !it.commandOpen) }
            }

            is ConversationEvent.CommandListDismissed -> {
                local.update { it.copy(commandOpen = false) }
            }

            is ConversationEvent.QuickSwitchTapped -> {
                service.focusSession(event.sessionId)
            }

            is ConversationEvent.SendTapped -> {
                val value = local.value.draft
                service.send(value)
                if (value.isNotBlank()) {
                    local.update { it.copy(draft = "", scroll = it.scroll + 1, commandOpen = false) }
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
        open: Boolean,
    ): List<CommandState> {
        val match = SlashRegex.matchEntire(draft)
        if (match == null && !open) return emptyList()
        val query = match?.groupValues?.get(1)?.trim()?.lowercase().orEmpty()
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

    private fun quickSwitches(projects: List<ProjectState>, sessions: List<SessionState>): List<QuickSwitchState> {
        val cutoff = System.currentTimeMillis() - QuickSwitchWindowMs
        return sessions
            .groupBy { workspaceId(it.directory) }
            .mapNotNull { (directory, list) ->
                val favorite = isFavoriteProject(projects, directory)
                val fresh = list.any { (it.updatedAt ?: 0L) >= cutoff }
                if (!favorite && !fresh) return@mapNotNull null
                val session = list.maxWithOrNull(compareBy<SessionState>({ it.updatedAt ?: 0L }, { it.id }))
                    ?: return@mapNotNull null
                val project = projectName(projects, directory)
                QuickSwitchState(
                    key = directory,
                    label = projectInitial(project),
                    project = project,
                    session = session,
                )
            }
            .sortedWith(compareByDescending<QuickSwitchState> { it.session.updatedAt ?: 0L }.thenByDescending { it.session.id })
    }

    private fun isFavoriteProject(projects: List<ProjectState>, directory: String): Boolean {
        return projects.any {
            it.favorite && (workspaceId(it.worktree) == directory || it.sandboxes.any { value -> workspaceId(value) == directory })
        }
    }

    private fun projectName(projects: List<ProjectState>, directory: String): String {
        return projects
            .firstOrNull {
                workspaceId(it.worktree) == directory || it.sandboxes.any { value -> workspaceId(value) == directory }
            }
            ?.name
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?: folderName(directory)
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

    private fun splitActiveTools(turns: List<ConversationTurnUiState>): List<ConversationTurnUiState> {
        val latest = turns.lastOrNull()?.id
        active.keys.toList().forEach {
            if (it != latest) {
                active.remove(it)
            }
        }
        return turns.mapIndexed { index, turn ->
            if (index != turns.lastIndex) {
                turn.copy(activeTool = null)
            } else {
                splitActiveTool(turn)
            }
        }
    }

    private fun splitActiveTool(turn: ConversationTurnUiState): ConversationTurnUiState {
        if (turn.answerWriting) {
            active.remove(turn.id)
            return turn.copy(activeTool = null)
        }
        if (turn.toolCalls.isEmpty()) {
            active.remove(turn.id)
            return turn.copy(activeTool = null)
        }
        val current = active[turn.id]
        val id = if (current == null || turn.toolCalls.none { it.id == current.id }) {
            turn.toolCalls.first().id
        } else {
            current.id
        }
        val next = if ((current?.size ?: 0) < turn.toolCalls.size) {
            val i = turn.toolCalls.indexOfFirst { it.id == id }
            if (i >= 0 && i < turn.toolCalls.lastIndex) {
                turn.toolCalls[i + 1].id
            } else {
                id
            }
        } else {
            id
        }
        active[turn.id] = ActiveToolSlot(id = next, size = turn.toolCalls.size)
        val tool = turn.toolCalls.firstOrNull { it.id == next } ?: return turn.copy(activeTool = null)
        return turn.copy(
            toolCalls = turn.toolCalls.filterNot { it.id == tool.id },
            activeTool = tool,
        )
    }
}

private val SlashRegex = Regex("^/(\\S*)$")
private const val QuickSwitchWindowMs = 2 * 60 * 60 * 1000L
private val BuiltinCommands = listOf(
    CommandState(
        name = "new",
        description = "Create a new session",
        source = "builtin",
    ),
)
