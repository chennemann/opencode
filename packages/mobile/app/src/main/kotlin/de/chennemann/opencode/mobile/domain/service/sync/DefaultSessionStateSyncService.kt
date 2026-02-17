package de.chennemann.opencode.mobile.domain.service.sync

import de.chennemann.opencode.mobile.data.repository.RemoteBatchSource
import de.chennemann.opencode.mobile.data.repository.SessionRemoteBatch
import de.chennemann.opencode.mobile.data.repository.SessionRepository
import de.chennemann.opencode.mobile.data.repository.SyncReason
import de.chennemann.opencode.mobile.db.AppDatabase
import de.chennemann.opencode.mobile.di.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class DefaultSessionStateSyncService(
    private val db: AppDatabase,
    private val session: SessionRepository,
    private val server: ServerService,
    private val dispatchers: DispatcherProvider,
    private val json: Json,
) : SessionStateSyncService {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.io)
    private var stream = null as kotlinx.coroutines.Job?

    override suspend fun ingestEvent(event: StreamEvent) {
        // Stream payloads are invalidation hints only; snapshots remain the canonical source of truth.
        val payload = runCatching {
            json.parseToJsonElement(event.payloadJson).jsonObject
        }.getOrNull() ?: return
        val ids = sessionIds(payload)
        if (ids.isEmpty()) return
        withContext(dispatchers.io) {
            db.appDatabaseQueries.transaction {
                ids.forEach {
                    db.appDatabaseQueries.markSyncStreamSeen(
                        session_id = it,
                        seen_at = event.receivedAt,
                        updated_at = event.receivedAt,
                    )
                    db.appDatabaseQueries.setSyncQueued(
                        session_id = it,
                        updated_at = event.receivedAt,
                    )
                }
            }
        }
        ids.forEach {
            session.requestSync(it, SyncReason.SCHEDULED)
        }
    }

    override suspend fun runDue(now: Long) {
        val rows = withContext(dispatchers.io) {
            db.appDatabaseQueries
                .listDueSyncState(now = now, limit = DueBatch) { sessionId, _, _, _, _, _, _, _, _ ->
                    sessionId
                }
                .executeAsList()
        }
        rows.forEach {
            withContext(dispatchers.io) {
                db.appDatabaseQueries.markSyncRunning(
                    updated_at = now,
                    session_id = it,
                )
            }
            val result = runCatching {
                syncSession(it, now)
            }
            if (result.isSuccess) {
                withContext(dispatchers.io) {
                    db.appDatabaseQueries.markSyncIdle(
                        last_snapshot_at = now,
                        next_sync_at = now + SnapshotIntervalMs,
                        updated_at = now,
                        session_id = it,
                    )
                }
                return@forEach
            }
            val error = result.exceptionOrNull()
            val reason = error?.message ?: "snapshot_failed"
            if (error is IllegalArgumentException) {
                withContext(dispatchers.io) {
                    db.appDatabaseQueries.markSyncFailed(
                        last_error_at = now,
                        next_sync_at = now + FailedBackoffMs,
                        updated_at = now,
                        session_id = it,
                    )
                }
                return@forEach
            }
            withContext(dispatchers.io) {
                db.appDatabaseQueries.markSyncBackoff(
                    last_error_at = now,
                    next_sync_at = now + BackoffMs,
                    updated_at = now,
                    session_id = it,
                )
            }
            if (reason.isBlank()) return@forEach
        }
    }

    override suspend fun onFocusedSessionChanged(sessionId: String?) {
        val now = System.currentTimeMillis()
        val state = policy()
        writePolicy(
            state.copy(
                focusedSessionId = sessionId?.trim()?.ifBlank { null },
                updatedAt = now,
            )
        )
        evaluateStreamPolicy(now)
    }

    override suspend fun onExpectationChanged(sessionId: String, expectsRemoteUpdates: Boolean) {
        val id = sessionId.trim()
        if (id.isBlank()) return
        val now = System.currentTimeMillis()
        withContext(dispatchers.io) {
            db.appDatabaseQueries.setSyncExpectation(
                session_id = id,
                expects_remote_updates = if (expectsRemoteUpdates) 1 else 0,
                updated_at = now,
            )
            if (expectsRemoteUpdates) {
                db.appDatabaseQueries.setSyncQueued(
                    session_id = id,
                    updated_at = now,
                )
            }
        }
        evaluateStreamPolicy(now)
    }

    override suspend fun evaluateStreamPolicy(now: Long) {
        val state = policy()
        val focused = state.focusedSessionId
        val expects = if (focused == null) {
            false
        } else {
            expectsRemoteUpdates(focused)
        }
        if (expects) {
            ensureConnected()
            writePolicy(
                state.copy(
                    streamConnected = true,
                    disconnectDeadlineAt = null,
                    updatedAt = now,
                )
            )
            return
        }
        if (!state.streamConnected) {
            writePolicy(
                state.copy(
                    disconnectDeadlineAt = null,
                    updatedAt = now,
                )
            )
            return
        }
        val deadline = state.disconnectDeadlineAt ?: (now + IdleGraceMs)
        if (state.disconnectDeadlineAt == null) {
            writePolicy(state.copy(disconnectDeadlineAt = deadline, updatedAt = now))
            return
        }
        if (deadline > now) return
        ensureDisconnected()
        writePolicy(
            state.copy(
                streamConnected = false,
                disconnectDeadlineAt = null,
                updatedAt = now,
            )
        )
    }

    private suspend fun syncSession(sessionId: String, now: Long) {
        val payload = server.fetchSessions(sessionId)
        val batch = normalizeBatch(payload)
        val result = session.applyRemoteBatch(
            SessionRemoteBatch(
                sessionId = sessionId,
                payloadJson = batch,
                receivedAt = now,
                source = RemoteBatchSource.SNAPSHOT_SYNC,
            )
        )
        if (!result.ok) {
            if (result.reason == "invalid_payload") {
                throw IllegalArgumentException("decode_error")
            }
            throw IllegalStateException(result.reason ?: "snapshot_failed")
        }
    }

    private fun normalizeBatch(payload: String): String {
        val root = runCatching {
            json.parseToJsonElement(payload)
        }.getOrElse {
            throw IllegalArgumentException("decode_error")
        }
        if (root is JsonObject && root["sessions"] != null) return root.toString()
        if (root is JsonArray) {
            return buildJsonObject {
                put("sessions", root)
                put("messages", buildJsonArray {})
            }.toString()
        }
        throw IllegalArgumentException("decode_error")
    }

    private suspend fun expectsRemoteUpdates(sessionId: String): Boolean {
        return withContext(dispatchers.io) {
            db.appDatabaseQueries.selectSyncExpectation(session_id = sessionId).executeAsOneOrNull() == 1L
        }
    }

    private suspend fun ensureConnected() {
        val current = stream
        if (current != null && current.isActive) return
        stream = scope.launch {
            try {
                server.connectStream(::ingestEvent)
            } finally {
                stream = null
            }
        }
    }

    private suspend fun ensureDisconnected() {
        stream?.cancel()
        stream = null
        server.disconnectStream()
    }

    private suspend fun policy(): StreamPolicy {
        return withContext(dispatchers.io) {
            db.appDatabaseQueries
                .selectStreamPolicy { connected, deadline, focused, updatedAt ->
                    StreamPolicy(
                        streamConnected = connected == 1L,
                        disconnectDeadlineAt = deadline,
                        focusedSessionId = focused,
                        updatedAt = updatedAt,
                    )
                }
                .executeAsOneOrNull()
                ?: StreamPolicy(
                    streamConnected = false,
                    disconnectDeadlineAt = null,
                    focusedSessionId = null,
                    updatedAt = 0,
                )
        }
    }

    private suspend fun writePolicy(state: StreamPolicy) {
        withContext(dispatchers.io) {
            db.appDatabaseQueries.upsertStreamPolicy(
                stream_connected = if (state.streamConnected) 1 else 0,
                disconnect_deadline_at = state.disconnectDeadlineAt,
                focused_session_id = state.focusedSessionId,
                updated_at = state.updatedAt,
            )
        }
    }
}

private data class StreamPolicy(
    val streamConnected: Boolean,
    val disconnectDeadlineAt: Long?,
    val focusedSessionId: String?,
    val updatedAt: Long,
)

private fun sessionIds(payload: JsonObject): Set<String> {
    val ids = linkedSetOf<String>()
    payload.id("sessionId")?.let(ids::add)
    payload.id("sessionID")?.let(ids::add)
    val session = payload["session"] as? JsonObject
    session?.id("id")?.let(ids::add)
    if (ids.isNotEmpty()) return ids
    payload.id("id")?.let(ids::add)
    return ids
}

private fun JsonObject.id(key: String): String? {
    return this[key]?.jsonPrimitive?.contentOrNull?.trim()?.ifBlank { null }
}

private const val IdleGraceMs = 30_000L
private const val SnapshotIntervalMs = 60_000L
private const val BackoffMs = 15_000L
private const val FailedBackoffMs = 60_000L
private const val DueBatch = 50L
