package de.chennemann.opencode.mobile.ui.conversation

import de.chennemann.opencode.mobile.domain.session.MessageState
import de.chennemann.opencode.mobile.domain.session.ToolCallState

class ConversationRenderMapper {
    private data class Turn(
        val id: String,
        val userText: String?,
        val toolCalls: List<ToolCallState>,
        val systemTexts: List<String>,
        val startedAt: Long?,
        val completedAt: Long?,
    )

    fun map(messages: List<MessageState>): List<ConversationTurnUiState> {
        val visible = messages.filter {
            it.role == "user" ||
                it.toolCalls.isNotEmpty() ||
                (it.text.isNotBlank() && it.text != "(streaming...)")
        }
        if (visible.isEmpty()) return emptyList()

        val turns = mutableListOf<Turn>()
        var turn: Turn? = null

        visible.forEach { message ->
            if (message.role == "user") {
                turn?.let(turns::add)
                turn = Turn(
                    id = message.id,
                    userText = message.text,
                    toolCalls = emptyList(),
                    systemTexts = emptyList(),
                    startedAt = message.createdAt,
                    completedAt = null,
                )
                return@forEach
            }

            val current = turn ?: Turn(
                id = message.id,
                userText = null,
                toolCalls = emptyList(),
                systemTexts = emptyList(),
                startedAt = message.createdAt,
                completedAt = message.completedAt,
            )
            turn = current.copy(
                toolCalls = current.toolCalls + message.toolCalls,
                systemTexts = if (message.text.isNotBlank() && message.text != "(streaming...)") {
                    current.systemTexts + message.text
                } else {
                    current.systemTexts
                },
                startedAt = current.startedAt ?: message.createdAt,
                completedAt = message.completedAt ?: current.completedAt,
            )
        }

        turn?.let(turns::add)

        return turns.map { value ->
            ConversationTurnUiState(
                id = value.id,
                userText = value.userText,
                toolCalls = value.toolCalls,
                systemTexts = value.systemTexts,
                startedAt = value.startedAt,
                completedAt = value.completedAt,
            )
        }
    }
}
