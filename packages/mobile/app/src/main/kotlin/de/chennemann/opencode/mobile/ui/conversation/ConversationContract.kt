package de.chennemann.opencode.mobile.ui.conversation

import de.chennemann.opencode.mobile.home.DebugState
import de.chennemann.opencode.mobile.home.MessageState
import de.chennemann.opencode.mobile.home.ServerState

data class ConversationUiState(
    val title: String,
    val status: ServerState,
    val focusedMessages: List<MessageState>,
    val canLoadMoreMessages: Boolean,
    val loadingMoreMessages: Boolean,
    val debug: DebugState,
    val debugOpen: Boolean,
    val draft: String,
)

sealed interface ConversationEvent {
    data object OpenManageTapped : ConversationEvent

    data object ToggleDebug : ConversationEvent

    data class DraftChanged(val value: String) : ConversationEvent

    data object SendTapped : ConversationEvent

    data object ReloadTapped : ConversationEvent

    data object LoadMoreMessagesTapped : ConversationEvent
}
