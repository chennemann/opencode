package de.chennemann.opencode.mobile.domain.service.model

data class SendMessageInput(val text: String, val agent: String, val sessionId: String?, val directory: String?)
data class SendMessageResult(val accepted: Boolean, val sessionId: String?, val reason: String?)
data class CommandInput(
    val raw: String,
    val sessionId: String,
    val directory: String,
    val projectId: String,
    val agent: String,
)
data class CommandResult(val accepted: Boolean, val reason: String?)
data class RenameInput(val sessionId: String, val title: String, val directory: String? = null)
data class RefreshInput(val endpoint: String, val userInitiated: Boolean)
data class RefreshResult(val accepted: Boolean, val reason: String?)
data class MessagePageInput(val sessionId: String, val beforeMessageId: String? = null, val limit: Long = 100)
data class MessagePageRequestResult(val accepted: Boolean, val reason: String?)
