package de.chennemann.opencode.mobile.ui.conversation

import de.chennemann.opencode.mobile.domain.session.ServerState
import de.chennemann.opencode.mobile.domain.session.ToolCallState

data class ConversationTurnUiState(
    val id: String,
    val userText: String?,
    val toolCalls: List<ToolCallState>,
    val systemTexts: List<String>,
    val startedAt: Long? = null,
    val completedAt: Long? = null,
)

data class ConversationUiState(
    val title: String,
    val status: ServerState,
    val turns: List<ConversationTurnUiState>,
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
