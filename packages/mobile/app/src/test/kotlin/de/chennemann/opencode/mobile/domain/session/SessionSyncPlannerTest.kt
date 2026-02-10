package de.chennemann.opencode.mobile.domain.session

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SessionSyncPlannerTest {
    private val planner = SessionSyncPlanner()

    @Test
    fun plansUpsertsSortsAndRemovals() {
        val cached = listOf(
            MessageState(id = "1", role = "assistant", text = "old", sort = "1"),
            MessageState(id = "2", role = "assistant", text = "bye", sort = "2"),
        )
        val sticky = linkedMapOf("1" to "z-100")
        val incoming = listOf(
            IncomingMessage(id = "1", role = "assistant", text = "new"),
            IncomingMessage(id = "3", role = "user", text = "hi"),
        )

        val value = planner.plan(
            incoming = incoming,
            cached = cached,
            sticky = sticky,
            knownSort = { null },
            claimPendingSort = { "z-200" },
            nextSort = { "z-300" },
            retainRemoved = { false },
            complete = true,
        )

        assertEquals(2, value.upserts.size)
        assertEquals("z-100", value.sorts["1"])
        assertEquals("z-200", value.sorts["3"])
        assertEquals(listOf("2"), value.removedIds)
        assertTrue(value.claimed)
    }

    @Test
    fun skipsRemovalsWhenNotComplete() {
        val value = planner.plan(
            incoming = emptyList(),
            cached = listOf(MessageState(id = "1", role = "assistant", text = "x", sort = "1")),
            sticky = null,
            knownSort = { null },
            claimPendingSort = { null },
            nextSort = { "z-1" },
            retainRemoved = { false },
            complete = false,
        )

        assertEquals(0, value.removedIds.size)
    }
}
