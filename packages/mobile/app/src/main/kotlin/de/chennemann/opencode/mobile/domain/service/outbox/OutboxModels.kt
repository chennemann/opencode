package de.chennemann.opencode.mobile.domain.service.outbox

import de.chennemann.opencode.mobile.data.repository.PendingMessageRef

enum class OutboxType { SEND_MESSAGE, EXECUTE_COMMAND, ARCHIVE_SESSION, RENAME_SESSION }
enum class OutboxStatus { PENDING, SENDING, FAILED, DONE }

data class OutboxAction(
    val outboxId: String,
    val type: OutboxType,
    val sessionId: String,
    val directory: String,
    val payloadJson: String,
    val localMessageId: String?,
    val serverMessageId: String?,
    val createdAt: Long,
)

data class OutboxMutationInput(
    val outboxId: String,
    val type: OutboxType,
    val sessionId: String,
    val directory: String,
    val text: String,
    val payloadJson: String,
    val createdAt: Long,
)

interface OutboxService {
    suspend fun enqueue(action: OutboxAction)
    suspend fun drain(limit: Int = 50)
}

interface OutboxMutationStore {
    suspend fun enqueueMessage(input: OutboxMutationInput): PendingMessageRef
}
