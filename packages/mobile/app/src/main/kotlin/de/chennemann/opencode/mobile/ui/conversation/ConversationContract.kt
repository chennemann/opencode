package de.chennemann.opencode.mobile.ui.conversation

import de.chennemann.opencode.mobile.domain.session.CommandState
import de.chennemann.opencode.mobile.domain.session.SessionState
import de.chennemann.opencode.mobile.domain.session.ServerState
import de.chennemann.opencode.mobile.domain.session.ToolCallState

data class ConversationTurnUiState(
    val id: String,
    val userText: String?,
    val toolCalls: List<ToolCallState>,
    val activeTool: ToolCallState? = null,
    val answerWriting: Boolean = false,
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
    val slashSuggestions: List<CommandState>,
    val quickSwitches: List<QuickSwitchState>,
    val stepOpen: Map<String, Boolean>,
    val callOpen: Map<String, Boolean>,
)

data class QuickSwitchState(
    val key: String,
    val label: String,
    val project: String,
    val session: SessionState,
    val active: Boolean,
)

sealed interface ConversationEvent {
    data object OpenManageTapped : ConversationEvent

    data class ToggleSteps(val messageId: String) : ConversationEvent

    data class ToggleToolCall(val callId: String) : ConversationEvent

    data class DraftChanged(val value: String) : ConversationEvent

    data class SlashCommandSelected(val name: String) : ConversationEvent

    data class QuickSwitchTapped(val sessionId: String) : ConversationEvent

    data object SendTapped : ConversationEvent

    data object ReloadTapped : ConversationEvent

    data object LoadMoreMessagesTapped : ConversationEvent
}
