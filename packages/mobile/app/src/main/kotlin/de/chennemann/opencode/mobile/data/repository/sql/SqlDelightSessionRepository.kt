package de.chennemann.opencode.mobile.data.repository.sql

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import de.chennemann.opencode.mobile.data.repository.AppendLocalMessageInput
import de.chennemann.opencode.mobile.data.repository.ConfirmSentMessageInput
import de.chennemann.opencode.mobile.data.repository.MessagePage
import de.chennemann.opencode.mobile.data.repository.MessagePageRequest
import de.chennemann.opencode.mobile.data.repository.PendingMessageRef
import de.chennemann.opencode.mobile.data.repository.RepoResult
import de.chennemann.opencode.mobile.data.repository.SessionListFilter
import de.chennemann.opencode.mobile.data.repository.SessionRemoteBatch
import de.chennemann.opencode.mobile.data.repository.SessionRepository
import de.chennemann.opencode.mobile.data.repository.SessionSyncState
import de.chennemann.opencode.mobile.data.repository.SessionSyncStatus
import de.chennemann.opencode.mobile.data.repository.SyncReason
import de.chennemann.opencode.mobile.db.AppDatabase
import de.chennemann.opencode.mobile.di.DispatcherProvider
import de.chennemann.opencode.mobile.domain.session.MessageState
import de.chennemann.opencode.mobile.domain.session.SessionState
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class SqlDelightSessionRepository(
    private val db: AppDatabase,
    private val dispatchers: DispatcherProvider,
    private val json: Json,
) : SessionRepository {
    override fun observeSessionList(projectId: String, filter: SessionListFilter): Flow<List<SessionState>> {
        return db.appDatabaseQueries
            .observeSessionList(
                project_id = projectId,
                include_archived = if (filter.includeArchived) 1 else 0,
                query = filter.query.trim(),
                limit = filter.limit,
            ) { id, title, version, directory, parentId, updatedAt, archivedAt ->
                SessionState(
                    id = id,
                    title = title,
                    version = version,
                    directory = directory,
                    parentId = parentId,
                    updatedAt = updatedAt,
                    archivedAt = archivedAt,
                )
            }
            .asFlow()
            .mapToList(dispatchers.io)
    }

    override fun observeFocusedSession(): Flow<SessionState?> {
        return db.appDatabaseQueries
            .observeFocusedSession { id, title, version, directory, parentId, updatedAt, archivedAt ->
                SessionState(
                    id = id,
                    title = title,
                    version = version,
                    directory = directory,
                    parentId = parentId,
                    updatedAt = updatedAt,
                    archivedAt = archivedAt,
                )
            }
            .asFlow()
            .mapToList(dispatchers.io)
            .map { it.firstOrNull() }
    }

    override fun observeMessagePage(sessionId: String, request: MessagePageRequest): Flow<MessagePage> {
        return db.appDatabaseQueries
            .observeMessagePage(
                session_id = sessionId,
                before_message_id = request.beforeMessageId,
                limit = request.limit + 1,
            ) { id, role, text, sortKey, createdAt, completedAt ->
                MessageState(
                    id = id,
                    role = role,
                    text = text,
                    sort = sortKey,
                    createdAt = createdAt,
                    completedAt = completedAt,
                )
            }
            .asFlow()
            .mapToList(dispatchers.io)
            .map {
                val hasMore = it.size > request.limit
                val rows = it.take(request.limit.toInt())
                MessagePage(
                    items = rows.reversed(),
                    hasMore = hasMore,
                    nextBeforeMessageId = if (hasMore) rows.lastOrNull()?.id else null,
                )
            }
    }

    override fun observeSyncState(sessionId: String): Flow<SessionSyncState> {
        return db.appDatabaseQueries
            .observeSyncState(sessionId) { id, status, lastSnapshotAt, lastStreamSeenAt, lastErrorAt ->
                SessionSyncState(
                    sessionId = id,
                    status = SessionSyncStatus.valueOf(status),
                    lastSnapshotAt = lastSnapshotAt,
                    lastStreamSeenAt = lastStreamSeenAt,
                    lastErrorAt = lastErrorAt,
                )
            }
            .asFlow()
            .mapToList(dispatchers.io)
            .map {
                it.firstOrNull()
                    ?: SessionSyncState(
                        sessionId = sessionId,
                        status = SessionSyncStatus.IDLE,
                        lastSnapshotAt = null,
                        lastStreamSeenAt = null,
                        lastErrorAt = null,
                    )
            }
    }

    override suspend fun focus(sessionId: String) {
        withContext(dispatchers.io) {
            db.appDatabaseQueries.transaction {
                db.appDatabaseQueries.clearFocusedSession()
                db.appDatabaseQueries.setFocusedSession(sessionId)
            }
        }
    }

    override suspend fun appendLocalMessage(input: AppendLocalMessageInput): PendingMessageRef {
        val local = "local-${UUID.randomUUID()}"
        withContext(dispatchers.io) {
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
        }
        return PendingMessageRef(local)
    }

    override suspend fun confirmSentMessage(input: ConfirmSentMessageInput): RepoResult {
        return withContext(dispatchers.io) {
            val row = db.appDatabaseQueries.selectMessageByLocalId(input.localMessageId).executeAsOneOrNull()
                ?: db.appDatabaseQueries.selectMessageById(input.localMessageId).executeAsOneOrNull()
                ?: return@withContext RepoResult(ok = false, reason = "local_message_missing")
            db.appDatabaseQueries.transaction {
                db.appDatabaseQueries.confirmLocalMessage(
                    id = input.serverUserMessageId,
                    updated_at = input.confirmedAt,
                    id_ = row.id,
                    session_id = input.sessionId,
                )
                db.appDatabaseQueries.insertMessage(
                    id = input.serverAssistantMessageId,
                    session_id = input.sessionId,
                    role = "assistant",
                    text = "",
                    sort_key = "${row.sort_key}:assistant",
                    created_at = input.confirmedAt,
                    completed_at = null,
                    local_message_id = null,
                    pending = 0,
                    updated_at = input.confirmedAt,
                )
            }
            RepoResult(ok = true)
        }
    }

    override suspend fun applyRemoteBatch(batch: SessionRemoteBatch): RepoResult {
        return withContext(dispatchers.io) {
            val payload = runCatching {
                json.parseToJsonElement(batch.payloadJson).jsonObject
            }.getOrNull() ?: return@withContext RepoResult(ok = false, reason = "invalid_payload")
            db.appDatabaseQueries.transaction {
                payload.sessions().forEach {
                    db.appDatabaseQueries.upsertSession(
                        id = it.id,
                        project_id = it.projectId,
                        title = it.title,
                        version = it.version,
                        directory = it.directory,
                        parent_id = it.parentId,
                        updated_at = it.updatedAt,
                        archived_at = it.archivedAt,
                        focused = 0,
                    )
                }
                payload.messages().forEach {
                    db.appDatabaseQueries.insertMessage(
                        id = it.id,
                        session_id = it.sessionId,
                        role = it.role,
                        text = it.text,
                        sort_key = it.sort,
                        created_at = it.createdAt,
                        completed_at = it.completedAt,
                        local_message_id = null,
                        pending = 0,
                        updated_at = it.updatedAt ?: batch.receivedAt,
                    )
                }
            }
            RepoResult(ok = true)
        }
    }

    override suspend fun requestMessagePage(sessionId: String, beforeMessageId: String?, limit: Long): RepoResult {
        requestSync(sessionId, SyncReason.REFRESH)
        return RepoResult(ok = true)
    }

    override suspend fun requestSync(sessionId: String, reason: SyncReason) {
        withContext(dispatchers.io) {
            db.appDatabaseQueries.setSyncQueued(
                session_id = sessionId,
                updated_at = System.currentTimeMillis(),
            )
        }
    }

    override suspend fun archive(sessionId: String): RepoResult {
        withContext(dispatchers.io) {
            db.appDatabaseQueries.archiveSession(System.currentTimeMillis(), sessionId)
        }
        return RepoResult(ok = true)
    }

    override suspend fun rename(sessionId: String, title: String): RepoResult {
        withContext(dispatchers.io) {
            db.appDatabaseQueries.renameSession(title, sessionId)
        }
        return RepoResult(ok = true)
    }
}

private data class SessionBatch(
    val id: String,
    val projectId: String,
    val title: String,
    val version: String,
    val directory: String,
    val parentId: String?,
    val updatedAt: Long?,
    val archivedAt: Long?,
)

private data class MessageBatch(
    val id: String,
    val sessionId: String,
    val role: String,
    val text: String,
    val sort: String,
    val createdAt: Long?,
    val completedAt: Long?,
    val updatedAt: Long?,
)

private fun JsonObject.sessions(): List<SessionBatch> {
    return value(this["sessions"]).mapNotNull {
        val obj = it.jsonObject
        val id = obj.text("id") ?: return@mapNotNull null
        SessionBatch(
            id = id,
            projectId = obj.text("projectId") ?: "",
            title = obj.text("title") ?: "",
            version = obj.text("version") ?: "",
            directory = obj.text("directory") ?: "",
            parentId = obj.text("parentId"),
            updatedAt = obj.long("updatedAt"),
            archivedAt = obj.long("archivedAt"),
        )
    }
}

private fun JsonObject.messages(): List<MessageBatch> {
    return value(this["messages"]).mapNotNull {
        val obj = it.jsonObject
        val id = obj.text("id") ?: return@mapNotNull null
        MessageBatch(
            id = id,
            sessionId = obj.text("sessionId") ?: return@mapNotNull null,
            role = obj.text("role") ?: "assistant",
            text = obj.text("text") ?: "",
            sort = obj.text("sort") ?: sort(obj.long("createdAt") ?: 0, id),
            createdAt = obj.long("createdAt"),
            completedAt = obj.long("completedAt"),
            updatedAt = obj.long("updatedAt"),
        )
    }
}

private fun value(input: kotlinx.serialization.json.JsonElement?): JsonArray {
    return (input as? JsonArray) ?: JsonArray(emptyList())
}

private fun JsonObject.text(key: String): String? {
    return this[key]?.jsonPrimitive?.content
}

private fun JsonObject.long(key: String): Long? {
    return this[key]?.jsonPrimitive?.content?.toLongOrNull()
}

private fun sort(createdAt: Long, id: String): String {
    return "%020d:%s".format(createdAt, id)
}
