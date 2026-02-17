package de.chennemann.opencode.mobile.domain.service.sync

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
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
import de.chennemann.opencode.mobile.domain.session.SessionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DefaultSessionStateSyncServiceTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun ingestEventWritesStreamSeenAndQueuesSessionSync() = runTest {
        val db = db()
        val session = SessionSyncSessionRepo()
        val server = SessionSyncServer()
        val service = DefaultSessionStateSyncService(db, session, server, sessionSyncLanes(), json)

        service.ingestEvent(
            StreamEvent(
                type = "session.updated",
                payloadJson = """{"sessionId":"s-1"}""",
                receivedAt = 100,
            )
        )

        val row = db.appDatabaseQueries.observeSyncState("s-1") { _, status, _, lastStreamSeenAt, _ ->
            status to lastStreamSeenAt
        }.executeAsOneOrNull()
        assertEquals("QUEUED", row?.first)
        assertEquals(100L, row?.second)
        assertEquals(listOf("s-1:${SyncReason.SCHEDULED}"), session.syncCalls)
    }

    @Test
    fun repeatedStreamHintsCoalesceIntoSingleSyncRow() = runTest {
        val db = db()
        val session = SessionSyncSessionRepo()
        val server = SessionSyncServer()
        val service = DefaultSessionStateSyncService(db, session, server, sessionSyncLanes(), json)

        service.ingestEvent(StreamEvent("session.updated", "{\"sessionId\":\"s-1\"}", 100))
        service.ingestEvent(StreamEvent("session.updated", "{\"sessionId\":\"s-1\"}", 110))

        val rows = db.appDatabaseQueries.listDueSyncState(now = 9999, limit = 20) { sessionId, _, _, _, _, _, _, _, _ ->
            sessionId
        }.executeAsList()
        assertEquals(listOf("s-1"), rows)
    }

    @Test
    fun runDueProcessesOnlyReadyRows() = runTest {
        val db = db()
        val session = SessionSyncSessionRepo()
        val server = SessionSyncServer().also {
            it.sessions["s-ready"] = """{"sessions":[],"messages":[]}"""
            it.sessions["s-later"] = """{"sessions":[],"messages":[]}"""
        }
        db.appDatabaseQueries.setSyncQueued(session_id = "s-ready", updated_at = 10)
        db.appDatabaseQueries.setSyncQueued(session_id = "s-later", updated_at = 10_000)
        val service = DefaultSessionStateSyncService(db, session, server, sessionSyncLanes(), json)

        service.runDue(now = 100)

        assertEquals(listOf("s-ready"), session.batchCalls)
        val ready = db.appDatabaseQueries.observeSyncState("s-ready") { _, status, _, _, _ -> status }.executeAsOneOrNull()
        val later = db.appDatabaseQueries.observeSyncState("s-later") { _, status, _, _, _ -> status }.executeAsOneOrNull()
        assertEquals("IDLE", ready)
        assertEquals("QUEUED", later)
    }

    @Test
    fun runDueMovesTransportFailureToBackoff() = runTest {
        val db = db()
        val session = SessionSyncSessionRepo()
        val server = SessionSyncServer().also {
            it.error = IllegalStateException("offline")
            it.sessions["s-1"] = "{}"
        }
        db.appDatabaseQueries.setSyncQueued(session_id = "s-1", updated_at = 10)
        val service = DefaultSessionStateSyncService(db, session, server, sessionSyncLanes(), json)

        service.runDue(now = 100)

        val status = db.appDatabaseQueries.observeSyncState("s-1") { _, value, _, _, _ -> value }.executeAsOneOrNull()
        assertEquals("BACKOFF", status)
    }

    @Test
    fun runDueMovesDecodeFailureToFailed() = runTest {
        val db = db()
        val session = SessionSyncSessionRepo()
        val server = SessionSyncServer().also {
            it.sessions["s-1"] = "not-json"
        }
        db.appDatabaseQueries.setSyncQueued(session_id = "s-1", updated_at = 10)
        val service = DefaultSessionStateSyncService(db, session, server, sessionSyncLanes(), json)

        service.runDue(now = 100)

        val status = db.appDatabaseQueries.observeSyncState("s-1") { _, value, _, _, _ -> value }.executeAsOneOrNull()
        assertEquals("FAILED", status)
    }

    @Test
    fun streamPolicyConnectsWhenFocusedSessionExpectsUpdates() = runTest {
        val db = db()
        val session = SessionSyncSessionRepo()
        val server = SessionSyncServer().also { it.keepAlive = true }
        val service = DefaultSessionStateSyncService(db, session, server, sessionSyncLanes(), json)

        service.onExpectationChanged("s-1", true)
        service.onFocusedSessionChanged("s-1")
        service.evaluateStreamPolicy(now = 100)

        assertEquals(1, server.connectCalls)
        val row = db.appDatabaseQueries.selectStreamPolicy { connected, deadline, focused, _ ->
            Triple(connected, deadline, focused)
        }.executeAsOneOrNull()
        assertEquals(1L, row?.first)
        assertEquals(null, row?.second)
        assertEquals("s-1", row?.third)
    }

    @Test
    fun streamPolicyDisconnectsAfterIdleGrace() = runTest {
        val db = db()
        val session = SessionSyncSessionRepo()
        val server = SessionSyncServer().also { it.keepAlive = true }
        val service = DefaultSessionStateSyncService(db, session, server, sessionSyncLanes(), json)

        service.onExpectationChanged("s-1", true)
        service.onFocusedSessionChanged("s-1")
        service.evaluateStreamPolicy(now = 100)
        service.onExpectationChanged("s-1", false)
        val deadline = db.appDatabaseQueries
            .selectStreamPolicy { _, value, _, _ -> value ?: 0L }
            .executeAsOneOrNull()
            ?: 0L
        service.evaluateStreamPolicy(now = deadline)

        assertEquals(1, server.disconnectCalls)
        val row = db.appDatabaseQueries.selectStreamPolicy { connected, deadline, _, _ ->
            connected to deadline
        }.executeAsOneOrNull()
        assertEquals(0L, row?.first)
        assertEquals(null, row?.second)
    }

    @Test
    fun expectationChangeBeforeDeadlineCancelsPendingDisconnect() = runTest {
        val db = db()
        val session = SessionSyncSessionRepo()
        val server = SessionSyncServer().also { it.keepAlive = true }
        val service = DefaultSessionStateSyncService(db, session, server, sessionSyncLanes(), json)

        service.onExpectationChanged("s-1", true)
        service.onFocusedSessionChanged("s-1")
        service.evaluateStreamPolicy(now = 100)
        service.onExpectationChanged("s-1", false)
        val deadline = db.appDatabaseQueries
            .selectStreamPolicy { _, value, _, _ -> value ?: 0L }
            .executeAsOneOrNull()
            ?: 0L
        service.evaluateStreamPolicy(now = deadline - 1L)
        service.onExpectationChanged("s-1", true)
        service.evaluateStreamPolicy(now = 10_000)

        assertEquals(0, server.disconnectCalls)
        val row = db.appDatabaseQueries.selectStreamPolicy { connected, deadline, _, _ ->
            connected to deadline
        }.executeAsOneOrNull()
        assertEquals(1L, row?.first)
        assertEquals(null, row?.second)
    }

    private fun db(): AppDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AppDatabase.Schema.create(driver)
        return AppDatabase(driver)
    }
}

private class SessionSyncServer : ServerService {
    var keepAlive = false
    var connectCalls = 0
    var disconnectCalls = 0
    var error: Throwable? = null
    val sessions = linkedMapOf<String, String>()

    override suspend fun connectStream(onEvent: suspend (StreamEvent) -> Unit) {
        connectCalls += 1
        if (keepAlive) {
            awaitCancellation()
        }
    }

    override suspend fun disconnectStream() {
        disconnectCalls += 1
    }

    override suspend fun fetchProjects(): String {
        return "[]"
    }

    override suspend fun fetchSessions(projectId: String): String {
        error?.let { throw it }
        return sessions[projectId] ?: "{\"sessions\":[],\"messages\":[]}"
    }

    override suspend fun fetchCommands(projectId: String): String {
        return "[]"
    }

    override suspend fun sendOutbox(actionId: String): Boolean {
        return false
    }
}

private class SessionSyncSessionRepo : SessionRepository {
    val syncCalls = mutableListOf<String>()
    val batchCalls = mutableListOf<String>()

    override fun observeSessionList(projectId: String, filter: SessionListFilter): Flow<List<SessionState>> {
        return flowOf(emptyList())
    }

    override fun observeFocusedSession(): Flow<SessionState?> {
        return flowOf(null)
    }

    override fun observeMessagePage(sessionId: String, request: MessagePageRequest): Flow<MessagePage> {
        return flowOf(MessagePage(emptyList(), false, null))
    }

    override fun observeSyncState(sessionId: String): Flow<SessionSyncState> {
        return flowOf(
            SessionSyncState(
                sessionId = sessionId,
                status = SessionSyncStatus.IDLE,
                lastSnapshotAt = null,
                lastStreamSeenAt = null,
                lastErrorAt = null,
            )
        )
    }

    override suspend fun focus(sessionId: String) {
    }

    override suspend fun appendLocalMessage(input: AppendLocalMessageInput): PendingMessageRef {
        return PendingMessageRef("local")
    }

    override suspend fun confirmSentMessage(input: ConfirmSentMessageInput): RepoResult {
        return RepoResult(ok = true)
    }

    override suspend fun applyRemoteBatch(batch: SessionRemoteBatch): RepoResult {
        batchCalls += batch.sessionId
        return RepoResult(ok = true)
    }

    override suspend fun requestMessagePage(sessionId: String, beforeMessageId: String?, limit: Long): RepoResult {
        return RepoResult(ok = true)
    }

    override suspend fun archive(sessionId: String): RepoResult {
        return RepoResult(ok = true)
    }

    override suspend fun rename(sessionId: String, title: String): RepoResult {
        return RepoResult(ok = true)
    }

    override suspend fun requestSync(sessionId: String, reason: SyncReason) {
        syncCalls += "$sessionId:$reason"
    }
}

private fun sessionSyncLanes(): DispatcherProvider {
    return object : DispatcherProvider {
        override val io = Dispatchers.Unconfined
        override val default = Dispatchers.Unconfined
        override val mainImmediate = Dispatchers.Unconfined
    }
}
