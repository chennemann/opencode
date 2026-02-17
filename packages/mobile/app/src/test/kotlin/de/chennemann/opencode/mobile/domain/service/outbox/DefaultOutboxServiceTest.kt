package de.chennemann.opencode.mobile.domain.service.outbox

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
import de.chennemann.opencode.mobile.domain.session.MessageGateway
import de.chennemann.opencode.mobile.domain.session.MessageSendIds
import de.chennemann.opencode.mobile.domain.session.ProjectGateway
import de.chennemann.opencode.mobile.domain.session.SessionMessage
import de.chennemann.opencode.mobile.domain.session.SessionProject
import de.chennemann.opencode.mobile.domain.session.SessionState
import de.chennemann.opencode.mobile.domain.session.SessionStreamEvent
import de.chennemann.opencode.mobile.domain.session.SessionSummary
import java.net.SocketTimeoutException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DefaultOutboxServiceTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun drainSendMessageSuccessMarksDoneAndRequestsOutboxSync() = runTest {
        val action = OutboxAction(
            outboxId = "o-1",
            type = OutboxType.SEND_MESSAGE,
            sessionId = "s-1",
            directory = "/repo/main",
            payloadJson = """{"text":"hello","agent":"build"}""",
            localMessageId = "local-1",
            serverMessageId = null,
            createdAt = 1,
        )
        val store = RecordingOutboxStore(listOf(action))
        val messages = RecordingMessageGateway()
        val projects = RecordingProjectGateway()
        val session = RecordingSessionRepository()
        val pending = RecordingPendingMessageOutcomeStore()
        val service = DefaultOutboxService(store, messages, projects, session, pending, json)

        service.drain()

        assertEquals(listOf("o-1"), store.sending)
        assertEquals(listOf("o-1"), store.done)
        assertTrue(store.failed.isEmpty())
        assertEquals(listOf("s-1:/repo/main:hello:build"), messages.messageCalls)
        assertEquals(1, session.confirmCalls.size)
        assertEquals("s-1", session.confirmCalls.first().sessionId)
        assertEquals("local-1", session.confirmCalls.first().localMessageId)
        assertEquals("server-user", session.confirmCalls.first().serverUserMessageId)
        assertEquals("server-assistant", session.confirmCalls.first().serverAssistantMessageId)
        assertEquals(listOf("s-1:${SyncReason.OUTBOX_DRAIN}"), session.syncCalls)
        assertTrue(pending.calls.isEmpty())
    }

    @Test
    fun drainTimeoutMarksSendUnknownAndDoesNotRetryInSamePass() = runTest {
        val action = OutboxAction(
            outboxId = "o-1",
            type = OutboxType.SEND_MESSAGE,
            sessionId = "s-1",
            directory = "/repo/main",
            payloadJson = """{"text":"hello","agent":"build"}""",
            localMessageId = "local-1",
            serverMessageId = null,
            createdAt = 1,
        )
        val store = RecordingOutboxStore(listOf(action))
        val messages = RecordingMessageGateway().also {
            it.messageError = SocketTimeoutException("timeout")
        }
        val projects = RecordingProjectGateway()
        val session = RecordingSessionRepository()
        val pending = RecordingPendingMessageOutcomeStore()
        val service = DefaultOutboxService(store, messages, projects, session, pending, json)

        service.drain()

        assertEquals(1, messages.messageCalls.size)
        assertEquals(listOf("s-1:local-1"), pending.calls)
        assertEquals(listOf("o-1:timeout"), store.failed)
        assertTrue(store.done.isEmpty())
        assertTrue(session.syncCalls.isEmpty())
    }

    @Test
    fun drainHandlesCommandArchiveAndRenameActions() = runTest {
        val actions = listOf(
            OutboxAction(
                outboxId = "o-cmd",
                type = OutboxType.EXECUTE_COMMAND,
                sessionId = "s-1",
                directory = "/repo/main",
                payloadJson = """{"command":"build","arguments":"--fast","agent":"build"}""",
                localMessageId = "local-cmd",
                serverMessageId = null,
                createdAt = 1,
            ),
            OutboxAction(
                outboxId = "o-archive",
                type = OutboxType.ARCHIVE_SESSION,
                sessionId = "s-2",
                directory = "/repo/main",
                payloadJson = "{}",
                localMessageId = null,
                serverMessageId = null,
                createdAt = 2,
            ),
            OutboxAction(
                outboxId = "o-rename",
                type = OutboxType.RENAME_SESSION,
                sessionId = "s-3",
                directory = "/repo/main",
                payloadJson = """{"title":"Renamed"}""",
                localMessageId = null,
                serverMessageId = null,
                createdAt = 3,
            ),
        )
        val store = RecordingOutboxStore(actions)
        val messages = RecordingMessageGateway()
        val projects = RecordingProjectGateway()
        val session = RecordingSessionRepository()
        val pending = RecordingPendingMessageOutcomeStore()
        val service = DefaultOutboxService(store, messages, projects, session, pending, json)

        service.drain()

        assertEquals(listOf("s-1:/repo/main:build:--fast:build"), messages.commandCalls)
        assertEquals(1, session.confirmCalls.size)
        assertEquals("local-cmd", session.confirmCalls.first().localMessageId)
        assertEquals("server-user", session.confirmCalls.first().serverUserMessageId)
        assertEquals("server-assistant", session.confirmCalls.first().serverAssistantMessageId)
        assertEquals(listOf("s-2:/repo/main"), projects.archiveCalls)
        assertEquals(listOf("s-3:/repo/main:Renamed"), projects.renameCalls)
        assertEquals(listOf("o-cmd", "o-archive", "o-rename"), store.done)
        assertTrue(store.failed.isEmpty())
        assertEquals(
            setOf("s-1:${SyncReason.OUTBOX_DRAIN}", "s-2:${SyncReason.OUTBOX_DRAIN}", "s-3:${SyncReason.OUTBOX_DRAIN}"),
            session.syncCalls.toSet(),
        )
    }

    @Test
    fun drainSkipsSyncRequestsWhenAnyActionFails() = runTest {
        val actions = listOf(
            OutboxAction(
                outboxId = "o-1",
                type = OutboxType.ARCHIVE_SESSION,
                sessionId = "s-1",
                directory = "/repo/main",
                payloadJson = "{}",
                localMessageId = null,
                serverMessageId = null,
                createdAt = 1,
            ),
            OutboxAction(
                outboxId = "o-2",
                type = OutboxType.RENAME_SESSION,
                sessionId = "s-2",
                directory = "/repo/main",
                payloadJson = """{"title":"Next"}""",
                localMessageId = null,
                serverMessageId = null,
                createdAt = 2,
            ),
        )
        val store = RecordingOutboxStore(actions)
        val messages = RecordingMessageGateway()
        val projects = RecordingProjectGateway().also {
            it.renameError = IllegalStateException("rename failed")
        }
        val session = RecordingSessionRepository()
        val pending = RecordingPendingMessageOutcomeStore()
        val service = DefaultOutboxService(store, messages, projects, session, pending, json)

        service.drain()

        assertEquals(listOf("o-1"), store.done)
        assertEquals(listOf("o-2:rename failed"), store.failed)
        assertTrue(session.syncCalls.isEmpty())
    }
}

private class RecordingOutboxStore(
    private val rows: List<OutboxAction>,
) : OutboxStore {
    val sending = mutableListOf<String>()
    val failed = mutableListOf<String>()
    val done = mutableListOf<String>()

    override suspend fun enqueue(action: OutboxAction) {
    }

    override suspend fun pending(limit: Int): List<OutboxAction> {
        return rows.take(limit)
    }

    override suspend fun markSending(outboxId: String, at: Long) {
        sending += outboxId
    }

    override suspend fun markFailed(outboxId: String, reason: String, at: Long) {
        failed += "$outboxId:$reason"
    }

    override suspend fun markDone(outboxId: String, at: Long) {
        done += outboxId
    }
}

private class RecordingPendingMessageOutcomeStore : PendingMessageOutcomeStore {
    val calls = mutableListOf<String>()

    override suspend fun markSendUnknown(sessionId: String, localMessageId: String, updatedAt: Long): RepoResult {
        calls += "$sessionId:$localMessageId"
        return RepoResult(ok = true)
    }
}

private class RecordingProjectGateway : ProjectGateway {
    val archiveCalls = mutableListOf<String>()
    val renameCalls = mutableListOf<String>()
    var renameError: Throwable? = null

    override suspend fun projects(): List<SessionProject> {
        return emptyList()
    }

    override suspend fun sessions(worktree: String, limit: Int?): List<SessionSummary> {
        return emptyList()
    }

    override suspend fun archiveSession(sessionId: String, directory: String) {
        archiveCalls += "$sessionId:$directory"
    }

    override suspend fun renameSession(sessionId: String, directory: String, title: String) {
        renameError?.let { throw it }
        renameCalls += "$sessionId:$directory:$title"
    }

    override suspend fun createSession(worktree: String, title: String): SessionSummary {
        throw UnsupportedOperationException()
    }
}

private class RecordingMessageGateway : MessageGateway {
    val messageCalls = mutableListOf<String>()
    val commandCalls = mutableListOf<String>()
    var messageError: Throwable? = null

    override suspend fun messages(sessionId: String, directory: String, limit: Int?): List<SessionMessage> {
        return emptyList()
    }

    override suspend fun updatedAt(sessionId: String, directory: String): Long? {
        return null
    }

    override suspend fun status(directory: String): Map<String, String> {
        return emptyMap()
    }

    override suspend fun sendMessage(sessionId: String, directory: String, text: String, agent: String): MessageSendIds {
        messageCalls += "$sessionId:$directory:$text:$agent"
        messageError?.let { throw it }
        return MessageSendIds(parentId = "server-user", messageId = "server-assistant")
    }

    override suspend fun sendCommand(sessionId: String, directory: String, name: String, arguments: String, agent: String): MessageSendIds {
        commandCalls += "$sessionId:$directory:$name:$arguments:$agent"
        return MessageSendIds(parentId = "server-user", messageId = "server-assistant")
    }
}

private class RecordingSessionRepository : SessionRepository {
    val confirmCalls = mutableListOf<ConfirmSentMessageInput>()
    val syncCalls = mutableListOf<String>()

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
        confirmCalls += input
        return RepoResult(ok = true)
    }

    override suspend fun applyRemoteBatch(batch: SessionRemoteBatch): RepoResult {
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
