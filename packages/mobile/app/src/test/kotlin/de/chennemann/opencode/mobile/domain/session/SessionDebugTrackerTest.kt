package de.chennemann.opencode.mobile.domain.session

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SessionDebugTrackerTest {
    private class StubLog : LogGateway {
        val lines = mutableListOf<String>()

        override fun debug(tag: String, message: String) {
            lines.add("d:$tag:$message")
        }

        override fun warn(tag: String, message: String) {
            lines.add("w:$tag:$message")
        }
    }

    @Test
    fun updatesCountersAndLogs() {
        val log = StubLog()
        val tracker = SessionDebugTracker(log, "tag", 2)

        tracker.onSeen()
        tracker.onConnected()
        tracker.onRaw("a")
        tracker.onApplied("message.updated", "s1")
        tracker.onDropped("message.updated", "bad")

        assertEquals(1, tracker.state.value.sseSeen)
        assertEquals(1, tracker.state.value.sseConnected)
        assertEquals(1, tracker.state.value.sseRaw)
        assertEquals(1, tracker.state.value.sseApplied)
        assertEquals(1, tracker.state.value.sseDropped)
        assertEquals("message.updated: bad", tracker.state.value.lastDrop)
        assertEquals(1, log.lines.count { it.startsWith("w:") })
    }

    @Test
    fun keepsLogWithinLimit() {
        val tracker = SessionDebugTracker(StubLog(), "tag", 2)

        tracker.push("1")
        tracker.push("2")
        tracker.push("3")

        assertEquals(2, tracker.state.value.sseLog.size)
    }
}
