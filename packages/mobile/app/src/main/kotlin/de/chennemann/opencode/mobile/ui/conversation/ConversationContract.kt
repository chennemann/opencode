package de.chennemann.opencode.mobile.ui.conversation

import de.chennemann.opencode.mobile.domain.session.CommandState
import de.chennemann.opencode.mobile.domain.session.SessionState
import de.chennemann.opencode.mobile.domain.session.ServerState
import de.chennemann.opencode.mobile.domain.session.ToolCallState

data class ConversationTurnUiState(
    val id: String,
    val userText: String?,
    val toolCalls: List<ToolCallState>,
    val answerWriting: Boolean = false,
    val systemTexts: List<String>,
    val startedAt: Long? = null,
    val completedAt: Long? = null,
)

data class ConversationUiState(
    val title: String,
    val status: ServerState,
    val message: String?,
    val turns: List<ConversationTurnUiState>,
    val canLoadMoreMessages: Boolean,
    val loadingMoreMessages: Boolean,
    val scroll: Long,
    val draft: String,
    val mode: ConversationMode,
    val slashSuggestions: List<CommandState>,
    val quickSwitches: List<QuickSwitchState>,
    val quickSwitchMenu: QuickSwitchMenuState? = null,
    val stepOpen: Map<String, Boolean>,
    val callOpen: Map<String, Boolean>,
)

enum class ConversationMode {
    PLAN,
    BUILD,
}

data class QuickSwitchState(
    val key: String,
    val worktree: String,
    val label: String,
    val project: String,
    val active: Boolean,
    val processing: Boolean,
    val unread: Int,
)

data class QuickSwitchMenuState(
    val key: String,
    val worktree: String,
    val project: String,
    val sessions: List<SessionState>,
    val loading: Boolean,
    val limit: Int = 11,
    val canLoadMore: Boolean = false,
    val pinned: Set<String> = emptySet(),
    val systemPinned: Set<String> = emptySet(),
)

sealed interface ConversationEvent {
    data object OpenManageTapped : ConversationEvent

    data class ToggleSteps(val messageId: String) : ConversationEvent

    data class ToggleToolCall(val callId: String) : ConversationEvent

    data class DraftChanged(val value: String) : ConversationEvent

    data class ModeChanged(val value: ConversationMode) : ConversationEvent

    data class SlashCommandSelected(val name: String) : ConversationEvent

    data class QuickSwitchTapped(val key: String) : ConversationEvent

    data class QuickSwitchLongPressed(val key: String) : ConversationEvent

    data object QuickSwitchMenuDismissed : ConversationEvent

    data class QuickSwitchMenuSessionTapped(val session: SessionState) : ConversationEvent

    data class QuickSwitchMenuPinTapped(val session: SessionState, val systemPinned: Boolean) : ConversationEvent

    data class QuickSwitchMenuArchiveTapped(val session: SessionState) : ConversationEvent

    data class QuickSwitchMenuRenameSubmitted(val session: SessionState, val title: String) : ConversationEvent

    data object QuickSwitchMenuLoadMoreTapped : ConversationEvent

    data object QuickSwitchMenuCreateTapped : ConversationEvent

    data object SendTapped : ConversationEvent

    data object ReloadTapped : ConversationEvent

    data object LoadMoreMessagesTapped : ConversationEvent
}
