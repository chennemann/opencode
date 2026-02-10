package de.chennemann.opencode.mobile.ui.conversation

import de.chennemann.opencode.mobile.domain.session.MessageState

class ConversationRenderMapper {
    fun map(messages: List<MessageState>): List<ConversationTurnUiState> {
        val visible = messages.filter {
            it.role == "user" ||
                it.toolCalls.isNotEmpty() ||
                (it.text.isNotBlank() && it.text != "(streaming...)")
        }
        if (visible.isEmpty()) return emptyList()

        val turns = mutableListOf<ConversationTurnUiState>()
        var turn: ConversationTurnUiState? = null

        visible.forEach { message ->
            if (message.role == "user") {
                turn?.let(turns::add)
                turn = ConversationTurnUiState(
                    id = message.id,
                    userText = message.text,
                    toolCalls = emptyList(),
                    systemTexts = emptyList(),
                )
                return@forEach
            }

            val current = turn ?: ConversationTurnUiState(
                id = message.id,
                userText = null,
                toolCalls = emptyList(),
                systemTexts = emptyList(),
            )
            turn = current.copy(
                toolCalls = current.toolCalls + message.toolCalls,
                systemTexts = if (message.text.isNotBlank() && message.text != "(streaming...)") {
                    current.systemTexts + message.text
                } else {
                    current.systemTexts
                },
            )
        }

        turn?.let(turns::add)
        return turns
    }
}
