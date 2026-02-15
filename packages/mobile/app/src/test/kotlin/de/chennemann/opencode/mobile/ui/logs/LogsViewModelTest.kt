package de.chennemann.opencode.mobile.ui.logs

import de.chennemann.opencode.mobile.di.DispatcherProvider
import de.chennemann.opencode.mobile.domain.session.LogEntry
import de.chennemann.opencode.mobile.domain.session.LogFilter
import de.chennemann.opencode.mobile.domain.session.LogFacet
import de.chennemann.opencode.mobile.domain.session.LogLevel
import de.chennemann.opencode.mobile.domain.session.LogProjectOption
import de.chennemann.opencode.mobile.domain.session.LogRecord
import de.chennemann.opencode.mobile.domain.session.LogSessionOption
import de.chennemann.opencode.mobile.domain.session.LogStoreGateway
import de.chennemann.opencode.mobile.domain.session.LogUnit
import de.chennemann.opencode.mobile.navigation.NavEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
class LogsViewModelTest {
    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun appliesUnitAndLevelFilters() = runTest(TestCoroutineScheduler()) {
        val main = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(main)
        val lane = StandardTestDispatcher(testScheduler)
        val store = StubStore(
            listOf(
                row(1, LogLevel.info, LogUnit.sync, "sync_ok", "Sync done"),
                row(2, LogLevel.error, LogUnit.network, "health_failed", "Network failed"),
            )
        )
        val model = LogsViewModel(store, lanes(main, lane))
        val collect = backgroundScope.launch(lane) { model.state.collect {} }

        model.onEvent(LogsEvent.UnitChanged(LogUnit.sync))
        advanceUntilIdle()
        assertEquals(listOf("sync_ok"), model.state.value.rows.map { it.event })

        model.onEvent(LogsEvent.UnitChanged(null))
        model.onEvent(LogsEvent.LevelChanged(LogLevel.error))
        advanceUntilIdle()
        assertEquals(listOf("health_failed"), model.state.value.rows.map { it.event })
        collect.cancel()
    }

    @Test
    fun rowFilterSetsSessionFilter() = runTest(TestCoroutineScheduler()) {
        val main = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(main)
        val lane = StandardTestDispatcher(testScheduler)
        val store = StubStore(
            listOf(
                row(1, LogLevel.info, LogUnit.sync, "sync_ok", "Sync done"),
                row(2, LogLevel.info, LogUnit.sync, "sync_ok", "Sync two").copy(sessionId = "s2", sessionTitle = "Session Two"),
            )
        )
        val model = LogsViewModel(store, lanes(main, lane))
        val collect = backgroundScope.launch(lane) { model.state.collect {} }

        model.onEvent(LogsEvent.AddFilterFromRow(LogsFilterKey.session, "s2"))
        advanceUntilIdle()

        assertEquals("s2", model.state.value.selectedSessionId)
        assertEquals(listOf(2L), model.state.value.rows.map { it.id })
        collect.cancel()
    }

    @Test
    fun backEventEmitsNavigationBack() = runTest(TestCoroutineScheduler()) {
        val main = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(main)
        val lane = StandardTestDispatcher(testScheduler)
        val model = LogsViewModel(StubStore(emptyList()), lanes(main, lane))

        val nav = async { model.nav.first() }
        advanceUntilIdle()
        model.onEvent(LogsEvent.BackTapped)
        advanceUntilIdle()

        assertTrue(nav.await() is NavEvent.Back)
    }

    private fun lanes(main: TestDispatcher, worker: TestDispatcher): DispatcherProvider {
        return object : DispatcherProvider {
            override val io = worker
            override val default = worker
            override val mainImmediate = main
        }
    }
}

private class StubStore(seed: List<LogEntry>) : LogStoreGateway {
    private val rows = MutableStateFlow(seed)
    private val facet = MutableStateFlow(
        LogFacet(
            projects = listOf(LogProjectOption("p1", "Project One")),
            sessions = listOf(LogSessionOption("s1", "Session One")),
            events = listOf("sync_ok", "health_failed", "render"),
        )
    )

    override suspend fun append(record: LogRecord) {
    }

    override fun observe(filter: LogFilter): Flow<List<LogEntry>> {
        return rows.map { list ->
            list
                .filter { filter.unit == null || it.unit == filter.unit }
                .filter { filter.level == null || it.level == filter.level }
                .filter { filter.event == null || it.event == filter.event }
                .filter { filter.projectId == null || it.projectId == filter.projectId }
                .filter { filter.sessionId == null || it.sessionId == filter.sessionId }
                .filter { filter.from == null || it.createdAt >= filter.from }
                .filter { filter.until == null || it.createdAt <= filter.until }
                .filter {
                    val q = filter.query.trim()
                    q.isBlank() ||
                        it.message.contains(q, ignoreCase = true) ||
                        it.event.contains(q, ignoreCase = true) ||
                        (it.projectName?.contains(q, ignoreCase = true) == true) ||
                        (it.sessionTitle?.contains(q, ignoreCase = true) == true)
                }
                .take(filter.limit.toInt())
        }
    }

    override fun observeFacet(): Flow<LogFacet> {
        return facet
    }

    override suspend fun prune(now: Long) {
    }

    override suspend fun clear() {
        rows.value = emptyList()
    }
}

private fun row(id: Long, level: LogLevel, unit: LogUnit, event: String, message: String): LogEntry {
    return LogEntry(
        id = id,
        createdAt = System.currentTimeMillis() + id,
        level = level,
        unit = unit,
        tag = "Test",
        event = event,
        projectId = "p1",
        projectName = "Project One",
        sessionId = "s1",
        sessionTitle = "Session One",
        message = message,
        context = emptyMap(),
        throwable = null,
    )
}
