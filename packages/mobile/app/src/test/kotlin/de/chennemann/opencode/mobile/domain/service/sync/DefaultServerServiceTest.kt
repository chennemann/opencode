package de.chennemann.opencode.mobile.domain.service.sync

import de.chennemann.opencode.mobile.domain.session.CommandGateway
import de.chennemann.opencode.mobile.domain.session.CommandState
import de.chennemann.opencode.mobile.domain.session.ProjectGateway
import de.chennemann.opencode.mobile.domain.session.SessionProject
import de.chennemann.opencode.mobile.domain.session.SessionStreamEvent
import de.chennemann.opencode.mobile.domain.session.SessionSummary
import de.chennemann.opencode.mobile.domain.session.StreamGateway
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DefaultServerServiceTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun connectStreamForwardsEventsWithoutCursorState() = runTest {
        val stream = RecordingStreamGateway()
        val projects = RecordingProjectGateway()
        val commands = RecordingCommandGateway()
        val service = DefaultServerService(stream, projects, commands)
        val events = mutableListOf<StreamEvent>()

        service.connectStream {
            events += it
        }

        assertEquals(listOf<String?>(null), stream.lastEventIds)
        assertEquals(1, events.size)
        assertEquals("session.updated", events.first().type)
        assertTrue(events.first().payloadJson.contains("s-1"))
    }

    @Test
    fun fetchProjectsSerializesProjectList() = runTest {
        val stream = RecordingStreamGateway()
        val projects = RecordingProjectGateway().also {
            it.rows = listOf(
                SessionProject(
                    id = "p-1",
                    worktree = "/repo/main",
                    name = "Main",
                    sandboxes = listOf("/repo/main-sb"),
                )
            )
        }
        val commands = RecordingCommandGateway()
        val service = DefaultServerService(stream, projects, commands)

        val payload = json.parseToJsonElement(service.fetchProjects()).jsonArray

        assertEquals(1, payload.size)
        assertEquals("p-1", payload[0].jsonObject["id"]?.jsonPrimitive?.content)
        assertEquals("/repo/main", payload[0].jsonObject["worktree"]?.jsonPrimitive?.content)
    }

    @Test
    fun fetchSessionsSerializesBatchShape() = runTest {
        val stream = RecordingStreamGateway()
        val projects = RecordingProjectGateway().also {
            it.sessions["/repo/main"] = listOf(
                SessionSummary(
                    id = "s-1",
                    title = "Session",
                    version = "1",
                    directory = "/repo/main",
                    parentId = null,
                    updatedAt = 10,
                    archivedAt = null,
                )
            )
        }
        val commands = RecordingCommandGateway()
        val service = DefaultServerService(stream, projects, commands)

        val payload = json.parseToJsonElement(service.fetchSessions("/repo/main")).jsonObject

        assertEquals(1, payload["sessions"]?.jsonArray?.size)
        assertEquals(0, payload["messages"]?.jsonArray?.size)
        assertEquals("s-1", payload["sessions"]?.jsonArray?.get(0)?.jsonObject?.get("id")?.jsonPrimitive?.content)
    }

    @Test
    fun fetchCommandsSerializesCommandList() = runTest {
        val stream = RecordingStreamGateway()
        val projects = RecordingProjectGateway()
        val commands = RecordingCommandGateway().also {
            it.rows["/repo/main"] = listOf(CommandState(name = "build", description = "Build", source = "local"))
        }
        val service = DefaultServerService(stream, projects, commands)

        val payload = json.parseToJsonElement(service.fetchCommands("/repo/main")).jsonArray

        assertEquals(1, payload.size)
        assertEquals("build", payload[0].jsonObject["name"]?.jsonPrimitive?.content)
    }
}

private class RecordingStreamGateway : StreamGateway {
    val lastEventIds = mutableListOf<String?>()

    override suspend fun streamEvents(lastEventId: String?, onRawEvent: suspend (String) -> Unit, onEvent: suspend (SessionStreamEvent) -> Unit): String? {
        lastEventIds += lastEventId
        onEvent(
            SessionStreamEvent(
                directory = "/repo/main",
                type = "session.updated",
                properties = kotlinx.serialization.json.buildJsonObject {
                    put("sessionId", "s-1")
                },
                id = "evt-1",
                retry = null,
            )
        )
        return "evt-1"
    }

}

private class RecordingProjectGateway : ProjectGateway {
    var rows = emptyList<SessionProject>()
    val sessions = linkedMapOf<String, List<SessionSummary>>()

    override suspend fun projects(): List<SessionProject> {
        return rows
    }

    override suspend fun sessions(worktree: String, limit: Int?): List<SessionSummary> {
        return sessions[worktree].orEmpty()
    }

    override suspend fun archiveSession(sessionId: String, directory: String) {
    }

    override suspend fun renameSession(sessionId: String, directory: String, title: String) {
    }

    override suspend fun createSession(worktree: String, title: String): SessionSummary {
        throw UnsupportedOperationException()
    }
}

private class RecordingCommandGateway : CommandGateway {
    val rows = linkedMapOf<String, List<CommandState>>()

    override suspend fun commands(directory: String): List<CommandState> {
        return rows[directory].orEmpty()
    }
}
