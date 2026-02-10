package de.chennemann.opencode.mobile.domain.session

import de.chennemann.opencode.mobile.domain.message.MessageDecorator
import de.chennemann.opencode.mobile.domain.message.MessagePart

class FocusedMessageProjector(
    private val decorator: MessageDecorator,
) {
    fun project(
        key: String,
        base: List<MessageState>,
        staged: Map<String, MessageState>,
        pending: List<MessageState>?,
        parts: Map<String, LinkedHashMap<String, MessagePart>>,
    ): List<MessageState> {
        val merged = base.associateBy { it.id }.toMutableMap().apply {
            staged
                .filterKeys { it.startsWith("$key::") }
                .values
                .forEach { this[it.id] = it }
        }.values.toList()
            .let {
                if (pending.isNullOrEmpty()) it else it + pending
            }

        return merged
            .sortedBy { it.sort }
            .map {
                val rendered = decorator.decorate(
                    it.role,
                    it.text,
                    parts[messageKey(key, it.id)]?.values?.toList() ?: emptyList(),
                )
                it.copy(
                    text = rendered.text,
                    toolCalls = rendered.toolCalls.map { call ->
                        ToolCallState(
                            id = call.id,
                            title = call.title,
                            status = call.status,
                            details = call.details,
                        )
                    },
                )
            }
    }

    private fun messageKey(key: String, messageId: String): String {
        return "$key::$messageId"
    }
}
