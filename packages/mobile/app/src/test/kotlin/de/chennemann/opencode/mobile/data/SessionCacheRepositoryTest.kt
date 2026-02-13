package de.chennemann.opencode.mobile.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import de.chennemann.opencode.mobile.db.AppDatabase
import de.chennemann.opencode.mobile.di.DispatcherProvider
import de.chennemann.opencode.mobile.domain.session.MessageState
import de.chennemann.opencode.mobile.domain.session.SessionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionCacheRepositoryTest {
    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun observeMessagesCompletesWhileMainPaused() = runTest(TestCoroutineScheduler()) {
        val main = StandardTestDispatcher(TestCoroutineScheduler())
        Dispatchers.setMain(main)
        val worker = StandardTestDispatcher(testScheduler)
        val repo = SessionCacheRepository(
            db = db(),
            dispatchers = lanes(main, worker),
        )

        val save = async {
            repo.upsertMessage(
                server = "http://localhost:4096",
                sessionId = "s1",
                message = MessageState(
                    id = "m1",
                    role = "assistant",
                    text = "hello",
                    sort = "0001",
                    createdAt = 1,
                    completedAt = 2,
                ),
                updatedAt = 3,
            )
        }
        advanceUntilIdle()
        assertTrue(save.isCompleted)
        save.await()

        val observe = async {
            repo.observeMessages("http://localhost:4096", "s1").first()
        }

        advanceUntilIdle()
        assertTrue(observe.isCompleted)
        assertEquals(1, observe.await().size)
    }

    @Test
    fun updatesHiddenProjectsSet() = runTest(TestCoroutineScheduler()) {
        val main = StandardTestDispatcher(TestCoroutineScheduler())
        Dispatchers.setMain(main)
        val worker = StandardTestDispatcher(testScheduler)
        val repo = SessionCacheRepository(
            db = db(),
            dispatchers = lanes(main, worker),
        )
        val server = "http://localhost:4096"

        val hide = async {
            repo.setProjectHidden(server, "/repo/main", true)
        }
        advanceUntilIdle()
        assertTrue(hide.isCompleted)
        hide.await()
        assertEquals(setOf("/repo/main"), repo.hiddenProjects(server))

        val show = async {
            repo.setProjectHidden(server, "/repo/main", false)
        }
        advanceUntilIdle()
        assertTrue(show.isCompleted)
        show.await()
        assertEquals(emptySet<String>(), repo.hiddenProjects(server))
    }

    @Test
    fun loadsProjectSessionsFromCacheInUpdatedOrder() = runTest(TestCoroutineScheduler()) {
        val main = StandardTestDispatcher(TestCoroutineScheduler())
        Dispatchers.setMain(main)
        val worker = StandardTestDispatcher(testScheduler)
        val repo = SessionCacheRepository(
            db = db(),
            dispatchers = lanes(main, worker),
        )
        val server = "http://localhost:4096"

        val write = async {
            repo.upsertSessionSnapshot(
                server = server,
                project = "/repo/main",
                session = SessionState(
                    id = "s1",
                    title = "One",
                    version = "1",
                    directory = "/repo/main",
                    updatedAt = 100,
                ),
            )
            repo.upsertSessionSnapshot(
                server = server,
                project = "/repo/main",
                session = SessionState(
                    id = "s2",
                    title = "Two",
                    version = "1",
                    directory = "/repo/main",
                    updatedAt = 200,
                ),
            )
        }
        advanceUntilIdle()
        assertTrue(write.isCompleted)
        write.await()

        val read = async {
            repo.listProjectSessions(server, "/repo/main", limit = 1)
        }
        advanceUntilIdle()
        assertTrue(read.isCompleted)
        assertEquals(listOf("s2"), read.await().map { it.id })
    }

    @Test
    fun deletesSessionFromCache() = runTest(TestCoroutineScheduler()) {
        val main = StandardTestDispatcher(TestCoroutineScheduler())
        Dispatchers.setMain(main)
        val worker = StandardTestDispatcher(testScheduler)
        val repo = SessionCacheRepository(
            db = db(),
            dispatchers = lanes(main, worker),
        )
        val server = "http://localhost:4096"

        val write = async {
            repo.upsertSessionSnapshot(
                server = server,
                project = "/repo/main",
                session = SessionState(
                    id = "s1",
                    title = "One",
                    version = "1",
                    directory = "/repo/main",
                    updatedAt = 100,
                ),
            )
            repo.deleteSession(server, "s1")
        }
        advanceUntilIdle()
        assertTrue(write.isCompleted)
        write.await()

        val read = async {
            repo.listProjectSessions(server, "/repo/main")
        }
        advanceUntilIdle()
        assertTrue(read.isCompleted)
        assertTrue(read.await().isEmpty())
    }

    @Test
    fun syncProjectSessionsRemovesStaleCachedSessions() = runTest(TestCoroutineScheduler()) {
        val main = StandardTestDispatcher(TestCoroutineScheduler())
        Dispatchers.setMain(main)
        val worker = StandardTestDispatcher(testScheduler)
        val repo = SessionCacheRepository(
            db = db(),
            dispatchers = lanes(main, worker),
        )
        val server = "http://localhost:4096"

        val seed = async {
            repo.upsertSessionSnapshot(
                server = server,
                project = "/repo/main",
                session = SessionState(
                    id = "s-stale",
                    title = "Stale",
                    version = "1",
                    directory = "/repo/main",
                    updatedAt = 100,
                ),
            )
            repo.upsertMessage(
                server = server,
                sessionId = "s-stale",
                message = MessageState(
                    id = "m1",
                    role = "assistant",
                    text = "old",
                    sort = "0001",
                    createdAt = 1,
                    completedAt = 2,
                ),
                updatedAt = 3,
            )
        }
        advanceUntilIdle()
        assertTrue(seed.isCompleted)
        seed.await()

        val sync = async {
            repo.syncProjectSessions(
                server = server,
                project = "/repo/main",
                sessions = listOf(
                    SessionState(
                        id = "s-new",
                        title = "New",
                        version = "1",
                        directory = "/repo/main",
                        updatedAt = 200,
                    )
                ),
            )
        }
        advanceUntilIdle()
        assertTrue(sync.isCompleted)
        sync.await()

        val sessions = async { repo.listProjectSessions(server, "/repo/main") }
        val messages = async { repo.listMessages(server, "s-stale") }
        advanceUntilIdle()
        assertTrue(sessions.isCompleted)
        assertTrue(messages.isCompleted)
        assertEquals(listOf("s-new"), sessions.await().map { it.id })
        assertTrue(messages.await().isEmpty())
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
