package de.chennemann.opencode.mobile.domain.message

data class ToolCallRender(
    val id: String,
    val title: String,
    val status: String? = null,
    val details: List<String>,
)

data class MessageRender(
    val text: String,
    val toolCalls: List<ToolCallRender>,
)
