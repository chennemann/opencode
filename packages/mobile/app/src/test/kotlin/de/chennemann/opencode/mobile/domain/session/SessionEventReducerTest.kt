package de.chennemann.opencode.mobile.domain.session

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SessionEventReducerTest {
    private val reducer = SessionEventReducer()

    @Test
    fun parsesMessageUpdatedEvent() {
        val event = SessionStreamEvent(
            directory = "repo",
            type = "message.updated",
            properties = buildJsonObject {
                put("info", buildJsonObject {
                    put("sessionID", "s1")
                    put("id", "m1")
                    put("role", "assistant")
                    put("text", "hi")
                })
            },
            id = null,
            retry = null,
        )

        val action = reducer.reduce(event)

        assertTrue(action is SessionEventAction.MessageUpdated)
        val value = action as SessionEventAction.MessageUpdated
        assertEquals("s1", value.sessionId)
        assertEquals("m1", value.messageId)
        assertEquals("assistant", value.role)
        assertEquals("hi", value.text)
    }

    @Test
    fun dropsMessageUpdatedWithoutSessionId() {
        val event = SessionStreamEvent(
            directory = "repo",
            type = "message.updated",
            properties = JsonObject(emptyMap()),
            id = null,
            retry = null,
        )

        val action = reducer.reduce(event)

        assertTrue(action is SessionEventAction.Drop)
        assertEquals("missing sessionID", (action as SessionEventAction.Drop).reason)
    }
}
