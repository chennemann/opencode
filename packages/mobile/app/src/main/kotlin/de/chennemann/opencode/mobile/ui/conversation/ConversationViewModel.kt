package de.chennemann.opencode.mobile.ui.conversation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.chennemann.opencode.mobile.domain.session.ServerState
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
        val canLoadMoreMessages: Boolean,
        val loadingMoreMessages: Boolean,
    )

    private data class LocalState(
        val scroll: Long = 0,
        val draft: String = "",
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
                local.update { it.copy(draft = event.value) }
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
