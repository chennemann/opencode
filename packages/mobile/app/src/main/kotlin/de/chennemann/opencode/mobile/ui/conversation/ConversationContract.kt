package de.chennemann.opencode.mobile.ui.conversation

import de.chennemann.opencode.mobile.domain.session.MessageState
import de.chennemann.opencode.mobile.domain.session.ServerState

data class ConversationUiState(
    val title: String,
    val status: ServerState,
    val focusedMessages: List<MessageState>,
    val canLoadMoreMessages: Boolean,
    val loadingMoreMessages: Boolean,
    val scroll: Long,
    val draft: String,
    val stepOpen: Map<String, Boolean>,
    val callOpen: Map<String, Boolean>,
)

sealed interface ConversationEvent {
    data object OpenManageTapped : ConversationEvent

    data class ToggleSteps(val messageId: String) : ConversationEvent

    data class ToggleToolCall(val callId: String) : ConversationEvent

    data class DraftChanged(val value: String) : ConversationEvent

    data object SendTapped : ConversationEvent

    data object ReloadTapped : ConversationEvent

    data object LoadMoreMessagesTapped : ConversationEvent
}
