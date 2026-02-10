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
                turns = mapper.map(it.focusedMessages),
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
}
