package de.chennemann.opencode.mobile.data.repository.sql

import de.chennemann.opencode.mobile.data.repository.PendingMessageRef
import de.chennemann.opencode.mobile.db.AppDatabase
import de.chennemann.opencode.mobile.di.DispatcherProvider
import de.chennemann.opencode.mobile.domain.service.outbox.OutboxAction
import de.chennemann.opencode.mobile.domain.service.outbox.OutboxMutationInput
import de.chennemann.opencode.mobile.domain.service.outbox.OutboxMutationStore
import de.chennemann.opencode.mobile.domain.service.outbox.OutboxStore
import de.chennemann.opencode.mobile.domain.service.outbox.OutboxType
import de.chennemann.opencode.mobile.domain.service.outbox.PendingMessageOutcomeStore
import de.chennemann.opencode.mobile.data.repository.RepoResult
import java.util.UUID
import kotlinx.coroutines.withContext

data class OutboxWrite(
    val outboxId: String,
    val type: String,
    val status: String = "PENDING",
    val sessionId: String,
    val directory: String,
    val payloadJson: String,
    val localMessageId: String?,
    val serverMessageId: String?,
    val availableAt: Long,
    val createdAt: Long,
)

data class OutboxRow(
    val outboxId: String,
    val type: String,
    val status: String,
    val sessionId: String,
    val directory: String,
    val payloadJson: String,
    val localMessageId: String?,
    val serverMessageId: String?,
    val attemptCount: Long,
    val availableAt: Long,
    val lastError: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

data class MessagePatchMeta(
    val id: String,
    val sessionId: String,
    val sortKey: String,
    val pending: Long,
    val sendState: String,
    val updatedAt: Long,
)

class SqlDelightOutboxStore(
    private val db: AppDatabase,
    private val dispatchers: DispatcherProvider,
) : OutboxStore, PendingMessageOutcomeStore, OutboxMutationStore {
    override suspend fun enqueue(action: OutboxAction) {
        enqueue(
            OutboxWrite(
                outboxId = action.outboxId,
                type = action.type.name,
                sessionId = action.sessionId,
                directory = action.directory,
                payloadJson = action.payloadJson,
                localMessageId = action.localMessageId,
                serverMessageId = action.serverMessageId,
                availableAt = action.createdAt,
                createdAt = action.createdAt,
            )
        )
    }

    override suspend fun pending(limit: Int): List<OutboxAction> {
        return listDue(System.currentTimeMillis(), limit.toLong()).mapNotNull {
            val type = runCatching { OutboxType.valueOf(it.type) }.getOrNull() ?: return@mapNotNull null
            OutboxAction(
                outboxId = it.outboxId,
                type = type,
                sessionId = it.sessionId,
                directory = it.directory,
                payloadJson = it.payloadJson,
                localMessageId = it.localMessageId,
                serverMessageId = it.serverMessageId,
                createdAt = it.createdAt,
            )
        }
    }

    override suspend fun markSending(outboxId: String, at: Long) {
        updateSending(outboxId, at)
    }

    override suspend fun markFailed(outboxId: String, reason: String, at: Long) {
        markFailed(
            outboxId = outboxId,
            availableAt = at,
            updatedAt = at,
            lastError = reason,
        )
    }

    override suspend fun markDone(outboxId: String, at: Long) {
        updateDone(outboxId = outboxId, updatedAt = at)
    }

    override suspend fun markSendUnknown(sessionId: String, localMessageId: String, updatedAt: Long): RepoResult {
        updateSendUnknown(sessionId, localMessageId, updatedAt)
        return RepoResult(ok = true)
    }

    override suspend fun enqueueMessage(input: OutboxMutationInput): PendingMessageRef {
        val local = "local-${UUID.randomUUID()}"
        withContext(dispatchers.io) {
            db.appDatabaseQueries.transaction {
                db.appDatabaseQueries.insertMessage(
                    id = local,
                    session_id = input.sessionId,
                    role = "user",
                    text = input.text,
                    sort_key = sort(input.createdAt, local),
                    created_at = input.createdAt,
                    completed_at = null,
                    local_message_id = local,
                    pending = 1,
                    updated_at = input.createdAt,
                )
                db.appDatabaseQueries.setSyncQueued(
                    session_id = input.sessionId,
                    updated_at = input.createdAt,
                )
                db.appDatabaseQueries.enqueueOutbox(
                    outbox_id = input.outboxId,
                    type = input.type.name,
                    status = "PENDING",
                    session_id = input.sessionId,
                    directory = input.directory,
                    payload_json = input.payloadJson,
                    local_message_id = local,
                    server_message_id = null,
                    available_at = input.createdAt,
                    created_at = input.createdAt,
                    updated_at = input.createdAt,
                )
            }
        }
        return PendingMessageRef(local)
    }

    suspend fun enqueue(input: OutboxWrite) {
        withContext(dispatchers.io) {
            db.appDatabaseQueries.enqueueOutbox(
                outbox_id = input.outboxId,
                type = input.type,
                status = input.status,
                session_id = input.sessionId,
                directory = input.directory,
                payload_json = input.payloadJson,
                local_message_id = input.localMessageId,
                server_message_id = input.serverMessageId,
                available_at = input.availableAt,
                created_at = input.createdAt,
                updated_at = input.createdAt,
            )
        }
    }

    suspend fun listDue(availableAt: Long, limit: Long = 50): List<OutboxRow> {
        return withContext(dispatchers.io) {
            db.appDatabaseQueries
                .listDueOutbox(available_at = availableAt, limit = limit) {
                    outboxId,
                    type,
                    status,
                    sessionId,
                    directory,
                    payloadJson,
                    localMessageId,
                    serverMessageId,
                    attemptCount,
                    dueAt,
                    lastError,
                    createdAt,
                    updatedAt,
                    ->
                    OutboxRow(
                        outboxId = outboxId,
                        type = type,
                        status = status,
                        sessionId = sessionId,
                        directory = directory,
                        payloadJson = payloadJson,
                        localMessageId = localMessageId,
                        serverMessageId = serverMessageId,
                        attemptCount = attemptCount,
                        availableAt = dueAt,
                        lastError = lastError,
                        createdAt = createdAt,
                        updatedAt = updatedAt,
                    )
                }
                .executeAsList()
        }
    }

    private suspend fun updateSending(outboxId: String, updatedAt: Long) {
        withContext(dispatchers.io) {
            db.appDatabaseQueries.markOutboxSending(
                updated_at = updatedAt,
                outbox_id = outboxId,
            )
        }
    }

    private suspend fun updateDone(outboxId: String, updatedAt: Long, serverMessageId: String? = null) {
        withContext(dispatchers.io) {
            db.appDatabaseQueries.markOutboxDone(
                server_message_id = serverMessageId,
                updated_at = updatedAt,
                outbox_id = outboxId,
            )
        }
    }

    suspend fun markFailed(outboxId: String, availableAt: Long, updatedAt: Long, lastError: String?) {
        withContext(dispatchers.io) {
            db.appDatabaseQueries.markOutboxFailed(
                available_at = availableAt,
                last_error = lastError,
                updated_at = updatedAt,
                outbox_id = outboxId,
            )
        }
    }

    suspend fun find(outboxId: String): OutboxRow? {
        return withContext(dispatchers.io) {
            db.appDatabaseQueries
                .selectOutboxById(outboxId) {
                    id,
                    type,
                    status,
                    sessionId,
                    directory,
                    payloadJson,
                    localMessageId,
                    serverMessageId,
                    attemptCount,
                    availableAt,
                    lastError,
                    createdAt,
                    updatedAt,
                    ->
                    OutboxRow(
                        outboxId = id,
                        type = type,
                        status = status,
                        sessionId = sessionId,
                        directory = directory,
                        payloadJson = payloadJson,
                        localMessageId = localMessageId,
                        serverMessageId = serverMessageId,
                        attemptCount = attemptCount,
                        availableAt = availableAt,
                        lastError = lastError,
                        createdAt = createdAt,
                        updatedAt = updatedAt,
                    )
                }
                .executeAsOneOrNull()
        }
    }

    suspend fun lookupMessagePatchMeta(localMessageId: String): MessagePatchMeta? {
        return withContext(dispatchers.io) {
            db.appDatabaseQueries
                .selectMessagePatchMetaByLocalId(
                    local_message_id = localMessageId,
                    id_ = localMessageId,
                ) { id, sessionId, sortKey, pending, sendState, updatedAt ->
                    MessagePatchMeta(
                        id = id,
                        sessionId = sessionId,
                        sortKey = sortKey,
                        pending = pending,
                        sendState = sendState,
                        updatedAt = updatedAt,
                    )
                }
                .executeAsOneOrNull()
        }
    }

    private suspend fun updateSendUnknown(sessionId: String, localMessageId: String, updatedAt: Long) {
        withContext(dispatchers.io) {
            db.appDatabaseQueries.markMessageSendUnknown(
                updated_at = updatedAt,
                session_id = sessionId,
                local_message_id = localMessageId,
            )
        }
    }

    suspend fun setMessageSendState(sessionId: String, localMessageId: String, sendState: String, updatedAt: Long) {
        withContext(dispatchers.io) {
            db.appDatabaseQueries.setMessageSendState(
                send_state = sendState,
                updated_at = updatedAt,
                session_id = sessionId,
                local_message_id = localMessageId,
            )
        }
    }

    suspend fun markFailedUnknown(
        outboxId: String,
        sessionId: String,
        localMessageId: String,
        availableAt: Long,
        updatedAt: Long,
        lastError: String?,
    ) {
        withContext(dispatchers.io) {
            db.appDatabaseQueries.transaction {
                db.appDatabaseQueries.markOutboxFailed(
                    available_at = availableAt,
                    last_error = lastError,
                    updated_at = updatedAt,
                    outbox_id = outboxId,
                )
                db.appDatabaseQueries.markMessageSendUnknown(
                    updated_at = updatedAt,
                    session_id = sessionId,
                    local_message_id = localMessageId,
                )
            }
        }
    }
}

private fun sort(createdAt: Long, id: String): String {
    return "%020d:%s".format(createdAt, id)
}
