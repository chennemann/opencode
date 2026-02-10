package de.chennemann.opencode.mobile.ui.conversation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.chennemann.opencode.mobile.domain.session.SessionDomainService
import de.chennemann.opencode.mobile.navigation.NavEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ConversationViewModel(
    private val service: SessionDomainService,
) : ViewModel() {
    private data class LocalState(
        val debugOpen: Boolean = false,
        val draft: String = "",
    )

    private val local = MutableStateFlow(LocalState())
    private val navFlow = MutableSharedFlow<NavEvent>(extraBufferCapacity = 1)

    val nav = navFlow.asSharedFlow()

    val state: StateFlow<ConversationUiState> = combine(service.state, local) { global, local ->
        ConversationUiState(
            title = global.focusedSession?.title ?: "No session selected",
            status = global.status,
            focusedMessages = global.focusedMessages,
            canLoadMoreMessages = global.canLoadMoreMessages,
            loadingMoreMessages = global.loadingMoreMessages,
            debug = global.debug,
            debugOpen = local.debugOpen,
            draft = local.draft,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ConversationUiState(
            title = "No session selected",
            status = service.state.value.status,
            focusedMessages = emptyList(),
            canLoadMoreMessages = false,
            loadingMoreMessages = false,
            debug = service.state.value.debug,
            debugOpen = false,
            draft = "",
        ),
    )

    init {
        service.start(viewModelScope)
    }

    fun onEvent(event: ConversationEvent) {
        when (event) {
            is ConversationEvent.OpenManageTapped -> {
                service.openManagement()
                navFlow.tryEmit(NavEvent.ToManage)
            }

            is ConversationEvent.ToggleDebug -> {
                local.update { it.copy(debugOpen = !it.debugOpen) }
            }

            is ConversationEvent.DraftChanged -> {
                local.update { it.copy(draft = event.value) }
            }

            is ConversationEvent.SendTapped -> {
                val value = local.value.draft
                service.send(value)
                if (value.isNotBlank()) {
                    local.update { it.copy(draft = "") }
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
