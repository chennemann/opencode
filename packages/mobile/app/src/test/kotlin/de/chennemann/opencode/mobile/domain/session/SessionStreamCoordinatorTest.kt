package de.chennemann.opencode.mobile.domain.session

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SessionStreamCoordinatorTest {
    private class StubLog : LogGateway {
        override fun debug(tag: String, message: String) {}

        override fun warn(tag: String, message: String) {}
    }

    private class StubConn : ConnectionGateway {
        override val status: StateFlow<ConnectionState> = MutableStateFlow(ConnectionState.Connected("1"))
        override val endpoint: StateFlow<String> = MutableStateFlow("http://localhost")
        override val found: StateFlow<String?> = MutableStateFlow(null)

        override fun start(scope: kotlinx.coroutines.CoroutineScope) {}

        override suspend fun setUrl(next: String) {}

        override suspend fun refresh(loading: Boolean) {}
    }

    private class StubNet : ConnectivityGateway {
        override val online: StateFlow<Boolean> = MutableStateFlow(true)
        override val changed: StateFlow<Long> = MutableStateFlow(0)
    }

    private class StubFeed : StreamGateway {
        var emitted = false

        override suspend fun streamEvents(
            lastEventId: String?,
            onRawEvent: suspend (String) -> Unit,
            onEvent: suspend (SessionStreamEvent) -> Unit,
        ): String? {
            if (!emitted) {
                emitted = true
                onRawEvent("raw")
                onEvent(
                    SessionStreamEvent(
                        directory = "repo",
                        type = "server.heartbeat",
                        properties = kotlinx.serialization.json.JsonObject(emptyMap()),
                        id = "e1",
                        retry = null,
                    ),
                )
            }
            delay(20)
            return null
        }

        override suspend fun streamCursor(): String? {
            return null
        }

        override suspend fun setStreamCursor(value: String?) {}
    }

    @Test
    fun emitsEventsThroughCallback() = runBlocking {
        val feed = StubFeed()
        val coordinator = SessionStreamCoordinator(
            conn = StubConn(),
            feed = feed,
            net = StubNet(),
            log = StubLog(),
        )
        val debug = SessionDebugTracker(StubLog(), "test", 10)
        var events = 0

        val job = coordinator.start(this, debug) {
            events += 1
        }

        delay(60)
        job.cancel()

        assertTrue(feed.emitted)
        assertTrue(events >= 1)
    }
}
