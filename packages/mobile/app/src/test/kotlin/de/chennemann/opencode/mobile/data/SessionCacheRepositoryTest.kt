package de.chennemann.opencode.mobile.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import de.chennemann.opencode.mobile.db.AppDatabase
import de.chennemann.opencode.mobile.di.DispatcherProvider
import de.chennemann.opencode.mobile.domain.session.MessageState
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
