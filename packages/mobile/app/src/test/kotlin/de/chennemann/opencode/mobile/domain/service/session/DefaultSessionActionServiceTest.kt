package de.chennemann.opencode.mobile.domain.service.session

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
import de.chennemann.opencode.mobile.domain.service.model.MessagePageInput
import de.chennemann.opencode.mobile.domain.service.model.RenameInput
import de.chennemann.opencode.mobile.domain.service.outbox.OutboxAction
import de.chennemann.opencode.mobile.domain.service.outbox.OutboxService
import de.chennemann.opencode.mobile.domain.service.outbox.OutboxType
import de.chennemann.opencode.mobile.domain.service.sync.SessionStateSyncService
import de.chennemann.opencode.mobile.domain.service.sync.StreamEvent
import de.chennemann.opencode.mobile.domain.session.SessionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DefaultSessionActionServiceTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun requestMessagePageForwardsBeforeAndLimit() = runTest {
        val session = RecordingSessionRepository()
        val outbox = RecordingOutboxService()
        val sync = RecordingSessionStateSyncService()
        val service = DefaultSessionActionService(session, outbox, sync)

        val result = service.requestMessagePage(
            MessagePageInput(
                sessionId = "s-1",
                beforeMessageId = "m-10",
                limit = 77,
            )
        )

        assertTrue(result.accepted)
        assertEquals(listOf("s-1:m-10:77"), session.pageCalls)
    }

    @Test
    fun archiveWritesLocalThenEnqueuesOutboxAndDrains() = runTest {
        val session = RecordingSessionRepository()
        val calls = mutableListOf<String>()
        val outbox = RecordingOutboxService(calls)
        val sync = RecordingSessionStateSyncService()
        val service = DefaultSessionActionService(session, outbox, sync)

        service.archive(" s-1 ", " /repo/main ")

        assertEquals(listOf("enqueue", "drain"), calls)
        assertEquals(listOf("s-1"), session.archiveCalls)
        assertEquals(1, outbox.actions.size)

        val action = outbox.actions.first()
        assertEquals(OutboxType.ARCHIVE_SESSION, action.type)
        assertEquals("s-1", action.sessionId)
        assertEquals("/repo/main", action.directory)
    }

    @Test
    fun renameTrimsTitleUpdatesLocalAndEnqueuesOutbox() = runTest {
        val session = RecordingSessionRepository()
        val calls = mutableListOf<String>()
        val outbox = RecordingOutboxService(calls)
        val sync = RecordingSessionStateSyncService()
        val service = DefaultSessionActionService(session, outbox, sync)

        service.rename(
            RenameInput(
                sessionId = " s-1 ",
                title = "  New Title  ",
                directory = " /repo/main ",
            )
        )

        assertEquals(listOf("enqueue", "drain"), calls)
        assertEquals(listOf("s-1:New Title"), session.renameCalls)
        assertEquals(1, outbox.actions.size)
        val action = outbox.actions.first()
        assertEquals(OutboxType.RENAME_SESSION, action.type)
        assertEquals("/repo/main", action.directory)
        val payload = json.parseToJsonElement(action.payloadJson).jsonObject
        assertEquals("New Title", payload["title"]?.jsonPrimitive?.content)
    }

    @Test
    fun archiveSkipsOutboxWhenDirectoryIsMissing() = runTest {
        val session = RecordingSessionRepository()
        val outbox = RecordingOutboxService(mutableListOf())
        val sync = RecordingSessionStateSyncService()
        val service = DefaultSessionActionService(session, outbox, sync)

        service.archive("s-1", null)

        assertEquals(listOf("s-1"), session.archiveCalls)
        assertTrue(outbox.actions.isEmpty())
        assertEquals(0, outbox.drainCalls)
    }

    @Test
    fun renameRejectsBlankTitle() = runTest {
        val session = RecordingSessionRepository()
        val outbox = RecordingOutboxService(mutableListOf())
        val sync = RecordingSessionStateSyncService()
        val service = DefaultSessionActionService(session, outbox, sync)

        service.rename(RenameInput(sessionId = "s-1", title = "   ", directory = "/repo/main"))

        assertTrue(session.renameCalls.isEmpty())
        assertTrue(outbox.actions.isEmpty())
        assertFalse(session.pageCalls.any())
    }

    @Test
    fun focusUpdatesSessionSyncAndPolicy() = runTest {
        val session = RecordingSessionRepository()
        val outbox = RecordingOutboxService()
        val sync = RecordingSessionStateSyncService()
        val service = DefaultSessionActionService(session, outbox, sync)

        service.focus("s-1")

        assertEquals(listOf("s-1"), session.focusCalls)
        assertEquals(listOf("s-1:${SyncReason.FOCUS}"), session.syncCalls)
        assertEquals(listOf("s-1"), sync.focusedCalls)
        assertEquals(listOf("s-1:true"), sync.expectationCalls)
    }
}

private class RecordingOutboxService(
    private val calls: MutableList<String> = mutableListOf(),
) : OutboxService {
    val actions = mutableListOf<OutboxAction>()
    var drainCalls = 0

    override suspend fun enqueue(action: OutboxAction) {
        calls += "enqueue"
        actions += action
    }

    override suspend fun drain(limit: Int) {
        calls += "drain"
        drainCalls += 1
    }
}

private class RecordingSessionRepository : SessionRepository {
    val pageCalls = mutableListOf<String>()
    val archiveCalls = mutableListOf<String>()
    val renameCalls = mutableListOf<String>()
    val focusCalls = mutableListOf<String>()
    val syncCalls = mutableListOf<String>()

    override fun observeSessionList(projectId: String, filter: SessionListFilter): Flow<List<SessionState>> {
        return flowOf(emptyList())
    }

    override fun observeRecentSessionList(limit: Long): Flow<List<SessionState>> {
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
        focusCalls += sessionId
    }

    override suspend fun appendLocalMessage(input: AppendLocalMessageInput): PendingMessageRef {
        return PendingMessageRef("local-1")
    }

    override suspend fun confirmSentMessage(input: ConfirmSentMessageInput): RepoResult {
        return RepoResult(ok = true)
    }

    override suspend fun applyRemoteBatch(batch: SessionRemoteBatch): RepoResult {
        return RepoResult(ok = true)
    }

    override suspend fun requestMessagePage(sessionId: String, beforeMessageId: String?, limit: Long): RepoResult {
        pageCalls += "$sessionId:$beforeMessageId:$limit"
        return RepoResult(ok = true)
    }

    override suspend fun archive(sessionId: String): RepoResult {
        archiveCalls += sessionId
        return RepoResult(ok = true)
    }

    override suspend fun rename(sessionId: String, title: String): RepoResult {
        renameCalls += "$sessionId:$title"
        return RepoResult(ok = true)
    }

    override suspend fun requestSync(sessionId: String, reason: SyncReason) {
        syncCalls += "$sessionId:$reason"
    }
}

private class RecordingSessionStateSyncService : SessionStateSyncService {
    val focusedCalls = mutableListOf<String?>()
    val expectationCalls = mutableListOf<String>()

    override suspend fun ingestEvent(event: StreamEvent) {
    }

    override suspend fun runDue(now: Long) {
    }

    override suspend fun onFocusedSessionChanged(sessionId: String?) {
        focusedCalls += sessionId
    }

    override suspend fun onExpectationChanged(sessionId: String, expectsRemoteUpdates: Boolean) {
        expectationCalls += "$sessionId:$expectsRemoteUpdates"
    }

    override suspend fun evaluateStreamPolicy(now: Long) {
    }
}
