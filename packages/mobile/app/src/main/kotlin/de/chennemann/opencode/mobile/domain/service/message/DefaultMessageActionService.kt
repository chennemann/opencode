package de.chennemann.opencode.mobile.domain.service.message

import de.chennemann.opencode.mobile.data.repository.CommandRepository
import de.chennemann.opencode.mobile.domain.service.model.CommandInput
import de.chennemann.opencode.mobile.domain.service.model.CommandResult
import de.chennemann.opencode.mobile.domain.service.model.SendMessageInput
import de.chennemann.opencode.mobile.domain.service.model.SendMessageResult
import de.chennemann.opencode.mobile.domain.service.outbox.OutboxMutationInput
import de.chennemann.opencode.mobile.domain.service.outbox.OutboxMutationStore
import de.chennemann.opencode.mobile.domain.service.outbox.OutboxService
import de.chennemann.opencode.mobile.domain.service.outbox.OutboxType
import java.util.UUID
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class DefaultMessageActionService(
    private val command: CommandRepository,
    private val mutation: OutboxMutationStore,
    private val outbox: OutboxService,
) : MessageActionService {
    override suspend fun send(input: SendMessageInput): SendMessageResult {
        val text = input.text
        if (text.isBlank()) {
            return SendMessageResult(accepted = false, sessionId = input.sessionId, reason = "blank_text")
        }
        val sessionId = input.sessionId?.trim().orEmpty()
        val directory = input.directory?.trim().orEmpty()
        if (sessionId.isBlank() || directory.isBlank()) {
            return SendMessageResult(accepted = false, sessionId = input.sessionId, reason = "missing_session_context")
        }
        val createdAt = System.currentTimeMillis()
        val payload = buildJsonObject {
            put("text", text)
            put("agent", input.agent)
        }.toString()
        mutation.enqueueMessage(
            OutboxMutationInput(
                outboxId = UUID.randomUUID().toString(),
                type = OutboxType.SEND_MESSAGE,
                sessionId = sessionId,
                directory = directory,
                text = text,
                payloadJson = payload,
                createdAt = createdAt,
            )
        )
        outbox.drain()
        return SendMessageResult(accepted = true, sessionId = sessionId, reason = null)
    }

    override suspend fun execute(input: CommandInput): CommandResult {
        val raw = input.raw.trim()
        val match = Regex("^/(\\S+)").find(raw)
        val name = match?.groupValues?.getOrNull(1)
        if (name.isNullOrBlank()) {
            return CommandResult(accepted = false, reason = "invalid_command")
        }
        if (input.projectId.isBlank()) {
            return CommandResult(accepted = false, reason = "missing_project_context")
        }
        val known = command.find(input.projectId, name)
        if (known == null) {
            return CommandResult(accepted = false, reason = "unknown_command")
        }
        val args = raw.split(Regex("\\s+"), limit = 2).getOrNull(1).orEmpty()
        val createdAt = System.currentTimeMillis()
        val payload = buildJsonObject {
            put("command", name)
            put("arguments", args)
            put("agent", input.agent)
        }.toString()
        mutation.enqueueMessage(
            OutboxMutationInput(
                outboxId = UUID.randomUUID().toString(),
                type = OutboxType.EXECUTE_COMMAND,
                sessionId = input.sessionId,
                directory = input.directory,
                text = raw,
                payloadJson = payload,
                createdAt = createdAt,
            )
        )
        outbox.drain()
        return CommandResult(accepted = true, reason = null)
    }
}
