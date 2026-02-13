package de.chennemann.opencode.mobile.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import de.chennemann.opencode.mobile.db.AppDatabase
import de.chennemann.opencode.mobile.di.DispatcherProvider
import de.chennemann.opencode.mobile.domain.session.ConnectivityGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ServerRepositoryTest {
    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun projectsCompletesWhileMainPaused() = runTest(TestCoroutineScheduler()) {
        val main = StandardTestDispatcher(TestCoroutineScheduler())
        Dispatchers.setMain(main)
        val worker = StandardTestDispatcher(testScheduler)
        val repo = ServerRepository(
            db = db(),
            mdns = StubMdns(),
            service = StubServer(),
            network = StubNetwork(),
            dispatchers = lanes(main, worker),
        )

        val load = async { repo.projects() }
        advanceUntilIdle()

        assertTrue(load.isCompleted)
        assertEquals("p1", load.await().first().id)
    }

    @Test
    fun setStreamCursorCompletesWhileMainPaused() = runTest(TestCoroutineScheduler()) {
        val main = StandardTestDispatcher(TestCoroutineScheduler())
        Dispatchers.setMain(main)
        val worker = StandardTestDispatcher(testScheduler)
        val repo = ServerRepository(
            db = db(),
            mdns = StubMdns(),
            service = StubServer(),
            network = StubNetwork(),
            dispatchers = lanes(main, worker),
        )

        val write = async { repo.setStreamCursor("cursor-1") }
        advanceUntilIdle()
        assertTrue(write.isCompleted)
        write.await()

        val read = async { repo.streamCursor() }
        advanceUntilIdle()
        assertTrue(read.isCompleted)
        assertEquals("cursor-1", read.await())
    }

    private fun db(): AppDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AppDatabase.Schema.create(driver)
        return AppDatabase(driver)
    }

    private fun lanes(main: TestDispatcher, worker: TestDispatcher): DispatcherProvider {
        return object : DispatcherProvider {
            override val io = worker
            override val default = worker
            override val mainImmediate = main
        }
    }
}

private class StubMdns : MdnsGateway {
    override fun discover(): Flow<MdnsEntry> {
        return emptyFlow()
    }
}

private class StubNetwork : ConnectivityGateway {
    override val online: StateFlow<Boolean> = MutableStateFlow(true)
    override val changed: StateFlow<Long> = MutableStateFlow(0)
}

private class StubServer : ServerGateway {
    override suspend fun health(baseUrl: String): Health {
        return Health(healthy = true, version = "1")
    }

    override suspend fun projects(baseUrl: String): List<ProjectInfo> {
        return listOf(
            ProjectInfo(
                id = "p1",
                worktree = "/tmp/p1",
                name = "Project 1",
            )
        )
    }

    override suspend fun sessions(baseUrl: String, worktree: String, limit: Int?): List<SessionInfo> {
        return emptyList()
    }

    override suspend fun createSession(baseUrl: String, worktree: String, title: String): SessionInfo {
        return SessionInfo(id = "s1", title = title, version = "1", directory = worktree)
    }

    override suspend fun commands(baseUrl: String, directory: String): List<CommandInfo> {
        return emptyList()
    }

    override suspend fun sessionMessages(baseUrl: String, sessionId: String, directory: String, limit: Int?): List<SessionMessageInfo> {
        return emptyList()
    }

    override suspend fun streamEvents(
        baseUrl: String,
        lastEventId: String?,
        onRawEvent: suspend (String) -> Unit,
        onEvent: suspend (GlobalStreamEvent) -> Unit,
    ): String? {
        onEvent(
            GlobalStreamEvent(
                directory = "global",
                type = "server.heartbeat",
                properties = JsonObject(emptyMap()),
                id = null,
                retry = null,
            )
        )
        return lastEventId
    }

    override suspend fun sendMessage(baseUrl: String, sessionId: String, directory: String, text: String) = Unit

    override suspend fun sendCommand(baseUrl: String, sessionId: String, directory: String, name: String, arguments: String) = Unit
}
