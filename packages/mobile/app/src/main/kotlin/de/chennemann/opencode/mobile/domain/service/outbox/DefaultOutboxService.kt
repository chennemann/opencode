package de.chennemann.opencode.mobile.domain.service.outbox

import de.chennemann.opencode.mobile.data.repository.ConfirmSentMessageInput
import de.chennemann.opencode.mobile.data.repository.RepoResult
import de.chennemann.opencode.mobile.data.repository.SessionRepository
import de.chennemann.opencode.mobile.data.repository.SyncReason
import de.chennemann.opencode.mobile.domain.session.MessageGateway
import de.chennemann.opencode.mobile.domain.session.MessageSendIds
import de.chennemann.opencode.mobile.domain.session.ProjectGateway
import java.io.IOException
import java.net.SocketTimeoutException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

interface OutboxStore {
    suspend fun enqueue(action: OutboxAction)
    suspend fun pending(limit: Int): List<OutboxAction>
    suspend fun markSending(outboxId: String, at: Long)
    suspend fun markFailed(outboxId: String, reason: String, at: Long)
    suspend fun markDone(outboxId: String, at: Long)
}

interface PendingMessageOutcomeStore {
    suspend fun markSendUnknown(sessionId: String, localMessageId: String, updatedAt: Long): RepoResult
}

class DefaultOutboxService(
    private val store: OutboxStore,
    private val messages: MessageGateway,
    private val projects: ProjectGateway,
    private val session: SessionRepository,
    private val pending: PendingMessageOutcomeStore,
    private val json: Json,
) : OutboxService {
    override suspend fun enqueue(action: OutboxAction) {
        store.enqueue(action)
    }

    override suspend fun drain(limit: Int) {
        val rows = store.pending(limit)
        if (rows.isEmpty()) return
        val synced = linkedSetOf<String>()
        var failed = false
        rows.forEach { action ->
            val now = System.currentTimeMillis()
            store.markSending(action.outboxId, now)
            val sent = runCatching { send(action) }
            val err = sent.exceptionOrNull()
            if (err == null) {
                store.markDone(action.outboxId, System.currentTimeMillis())
                synced += action.sessionId
                return@forEach
            }
            failed = true
            if (uncertain(err) && tracksMessage(action.type)) {
                val local = action.localMessageId?.trim().orEmpty()
                if (local.isNotBlank()) {
                    pending.markSendUnknown(action.sessionId, local, System.currentTimeMillis())
                }
            }
            store.markFailed(
                outboxId = action.outboxId,
                reason = err.message ?: "transport_failed",
                at = System.currentTimeMillis(),
            )
        }
        if (failed || synced.isEmpty()) return
        synced.forEach {
            session.requestSync(it, SyncReason.OUTBOX_DRAIN)
        }
    }

    private suspend fun send(action: OutboxAction) {
        if (action.type == OutboxType.SEND_MESSAGE) {
            val body = payload(action)
            val text = body.value("text") ?: throw IllegalStateException("send_text_missing")
            val agent = body.value("agent") ?: throw IllegalStateException("send_agent_missing")
            val ids = messages.sendMessage(action.sessionId, action.directory, text, agent)
            patch(action, ids)
            return
        }
        if (action.type == OutboxType.EXECUTE_COMMAND) {
            val body = payload(action)
            val name = body.value("command", "name") ?: throw IllegalStateException("command_name_missing")
            val args = body.value("arguments") ?: ""
            val agent = body.value("agent") ?: throw IllegalStateException("command_agent_missing")
            val ids = messages.sendCommand(action.sessionId, action.directory, name, args, agent)
            patch(action, ids)
            return
        }
        if (action.type == OutboxType.ARCHIVE_SESSION) {
            projects.archiveSession(action.sessionId, action.directory)
            return
        }
        val body = payload(action)
        val title = body.value("title")?.trim().orEmpty()
        if (title.isBlank()) throw IllegalStateException("rename_title_missing")
        projects.renameSession(action.sessionId, action.directory, title)
    }

    private suspend fun patch(action: OutboxAction, ids: MessageSendIds) {
        val local = action.localMessageId?.trim().orEmpty()
        if (local.isBlank()) return
        session.confirmSentMessage(
            ConfirmSentMessageInput(
                sessionId = action.sessionId,
                localMessageId = local,
                serverUserMessageId = ids.parentId,
                serverAssistantMessageId = ids.messageId,
                confirmedAt = System.currentTimeMillis(),
            )
        )
    }

    private fun payload(action: OutboxAction): JsonObject {
        val parsed = runCatching { json.parseToJsonElement(action.payloadJson).jsonObject }.getOrNull()
        return parsed ?: JsonObject(emptyMap())
    }
}

private fun tracksMessage(type: OutboxType): Boolean {
    return type == OutboxType.SEND_MESSAGE || type == OutboxType.EXECUTE_COMMAND
}

private fun uncertain(error: Throwable): Boolean {
    if (error is TimeoutCancellationException) return true
    if (error is SocketTimeoutException) return true
    if (error is IOException) return true
    return false
}

private fun JsonObject.value(vararg keys: String): String? {
    keys.forEach {
        val value = this[it]?.jsonPrimitive?.contentOrNull
        if (!value.isNullOrBlank()) return value
    }
    return null
}
