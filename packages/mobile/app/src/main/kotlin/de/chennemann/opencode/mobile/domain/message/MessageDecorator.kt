package de.chennemann.opencode.mobile.domain.message

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

    fun decorate(role: String, text: String, parts: List<MessagePart>): MessageRender {
        if (role == "user") return MessageRender(text, emptyList())
        if (parts.isEmpty()) return MessageRender(text, emptyList())
        return MessageRender(
            text = render(parts),
            toolCalls = toolCalls(parts),
        )
    }

    private fun toolCalls(parts: List<MessagePart>): List<ToolCallRender> {
        return parts
            .filter { it.type == "tool" }
            .map {
                ToolCallRender(
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
