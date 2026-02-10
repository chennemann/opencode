package de.chennemann.opencode.mobile.domain.message

data class MessagePart(
    val id: String,
    val type: String,
    val text: String,
    val tool: String? = null,
    val status: String? = null,
    val title: String? = null,
    val output: String? = null,
)
