package de.chennemann.opencode.mobile.domain.message

import de.chennemann.opencode.mobile.home.MessageState
import de.chennemann.opencode.mobile.home.ToolCallState

class MessageDecorator {
    fun render(parts: Collection<MessagePart>?): String {
        val text = parts
            ?.filter { it.type == "text" }
            ?.map { it.text }
            ?.filter { it.isNotBlank() }
            ?.joinToString("\n")
        if (text == null || text.isBlank()) return "(streaming...)"
        return text
    }

    fun decorate(message: MessageState, parts: List<MessagePart>): MessageState {
        if (message.role == "user") return message
        if (parts.isEmpty()) return message
        return message.copy(
            text = render(parts),
            toolCalls = toolCalls(parts),
        )
    }

    private fun toolCalls(parts: List<MessagePart>): List<ToolCallState> {
        return parts
            .filter { it.type == "tool" }
            .map {
                ToolCallState(
                    id = it.id,
                    title = it.tool ?: "tool",
                    status = it.status,
                    details = listOfNotNull(
                        it.status?.let { status -> "Status: $status" },
                        it.title?.let { title -> "Title: ${title.line()}" },
                        it.output?.let { output -> "Output: ${output.line()}" },
                    ),
                )
            }
    }

    private fun String.line(): String {
        val value = lineSequence().firstOrNull()?.trim().orEmpty()
        if (value.length <= 160) return value
        return value.take(157) + "..."
    }
}
