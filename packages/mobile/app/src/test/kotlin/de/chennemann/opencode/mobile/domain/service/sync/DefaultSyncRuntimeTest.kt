package de.chennemann.opencode.mobile.domain.service.sync

import de.chennemann.opencode.mobile.di.DispatcherProvider
import de.chennemann.opencode.mobile.domain.service.logs.LogsService
import de.chennemann.opencode.mobile.domain.service.outbox.OutboxService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DefaultSyncRuntimeTest {
    @Test
    fun tickInvokesAllSyncCollaborators() = runTest {
        val project = RecordingProjectSyncService()
        val session = RecordingSessionStateSyncService()
        val outbox = RecordingOutboxService()
        val logs = RecordingLogsService()
        val runtime = DefaultSyncRuntime(project, session, outbox, logs, runtimeLanes(), intervalMs = 1000)

        runtime.tick(now = 1234)

        assertEquals(1, project.calls)
        assertEquals(listOf(1234L), session.policyCalls)
        assertEquals(listOf(1234L), session.dueCalls)
        assertEquals(1, outbox.calls)
        assertEquals(listOf(1234L), logs.calls)
    }
}

private class RecordingProjectSyncService : ProjectSyncService {
    var calls = 0

    override suspend fun run(projectId: String?) {
        calls += 1
    }
}

private class RecordingSessionStateSyncService : SessionStateSyncService {
    val dueCalls = mutableListOf<Long>()
    val policyCalls = mutableListOf<Long>()

    override suspend fun ingestEvent(event: StreamEvent) {
    }

    override suspend fun runDue(now: Long) {
        dueCalls += now
    }

    override suspend fun onFocusedSessionChanged(sessionId: String?) {
    }

    override suspend fun onExpectationChanged(sessionId: String, expectsRemoteUpdates: Boolean) {
    }

    override suspend fun evaluateStreamPolicy(now: Long) {
        policyCalls += now
    }
}

private class RecordingOutboxService : OutboxService {
    var calls = 0

    override suspend fun enqueue(action: de.chennemann.opencode.mobile.domain.service.outbox.OutboxAction) {
    }

    override suspend fun drain(limit: Int) {
        calls += 1
    }
}

private class RecordingLogsService : LogsService {
    val calls = mutableListOf<Long>()

    override fun observeLogs(filter: de.chennemann.opencode.mobile.domain.session.LogFilter, page: de.chennemann.opencode.mobile.data.repository.LogPage): Flow<List<de.chennemann.opencode.mobile.domain.session.LogEntry>> {
        return emptyFlow()
    }

    override fun observeFacets(filter: de.chennemann.opencode.mobile.domain.session.LogFilter): Flow<de.chennemann.opencode.mobile.domain.session.LogFacet> {
        return emptyFlow()
    }

    override suspend fun runRetention(now: Long) {
        calls += now
    }
}

private fun runtimeLanes(): DispatcherProvider {
    return object : DispatcherProvider {
        override val io = Dispatchers.Unconfined
        override val default = Dispatchers.Unconfined
        override val mainImmediate = Dispatchers.Unconfined
    }
}
