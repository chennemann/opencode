package de.chennemann.opencode.mobile.domain.service.message

import de.chennemann.opencode.mobile.data.repository.CommandRepository
import de.chennemann.opencode.mobile.data.repository.PendingMessageRef
import de.chennemann.opencode.mobile.domain.service.model.CommandInput
import de.chennemann.opencode.mobile.domain.service.model.CommandResult
import de.chennemann.opencode.mobile.domain.service.model.SendMessageInput
import de.chennemann.opencode.mobile.domain.service.model.SendMessageResult
import de.chennemann.opencode.mobile.domain.service.outbox.OutboxAction
import de.chennemann.opencode.mobile.domain.service.outbox.OutboxMutationInput
import de.chennemann.opencode.mobile.domain.service.outbox.OutboxMutationStore
import de.chennemann.opencode.mobile.domain.service.outbox.OutboxService
import de.chennemann.opencode.mobile.domain.service.outbox.OutboxType
import de.chennemann.opencode.mobile.domain.session.CommandState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DefaultMessageActionServiceTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun sendRejectsBlankText() = runTest {
        val command = RecordingCommandRepository()
        val mutation = RecordingOutboxMutationStore()
        val outbox = RecordingOutboxService()
        val service = DefaultMessageActionService(command, mutation, outbox)

        val result = service.send(
            SendMessageInput(
                text = "   ",
                agent = "build",
                sessionId = "s-1",
                directory = "/repo/main",
            )
        )

        assertFalse(result.accepted)
        assertEquals("blank_text", result.reason)
        assertTrue(mutation.calls.isEmpty())
        assertEquals(0, outbox.drainCalls)
    }

    @Test
    fun sendEnqueuesPendingMutationAndDrainsOutbox() = runTest {
        val command = RecordingCommandRepository()
        val mutation = RecordingOutboxMutationStore()
        val outbox = RecordingOutboxService()
        val service = DefaultMessageActionService(command, mutation, outbox)

        val result = service.send(
            SendMessageInput(
                text = "hello",
                agent = "build",
                sessionId = "s-1",
                directory = "/repo/main",
            )
        )

        assertTrue(result.accepted)
        assertEquals(1, mutation.calls.size)
        assertEquals(1, outbox.drainCalls)

        val call = mutation.calls.first()
        assertEquals(OutboxType.SEND_MESSAGE, call.type)
        assertEquals("s-1", call.sessionId)
        assertEquals("/repo/main", call.directory)
        assertEquals("hello", call.text)
        val payload = json.parseToJsonElement(call.payloadJson).jsonObject
        assertEquals("hello", payload["text"]?.jsonPrimitive?.content)
        assertEquals("build", payload["agent"]?.jsonPrimitive?.content)
    }

    @Test
    fun executeRejectsUnknownCommand() = runTest {
        val command = RecordingCommandRepository()
        val mutation = RecordingOutboxMutationStore()
        val outbox = RecordingOutboxService()
        val service = DefaultMessageActionService(command, mutation, outbox)

        val result = service.execute(
            CommandInput(
                raw = "/build --fast",
                sessionId = "s-1",
                directory = "/repo/main",
                projectId = "p-1",
                agent = "build",
            )
        )

        assertFalse(result.accepted)
        assertEquals("unknown_command", result.reason)
        assertEquals(listOf("p-1:build"), command.findCalls)
        assertTrue(mutation.calls.isEmpty())
        assertEquals(0, outbox.drainCalls)
    }

    @Test
    fun executeEnqueuesPendingCommandMutationForKnownCommand() = runTest {
        val command = RecordingCommandRepository().also {
            it.next = CommandState(name = "build", description = "Build", source = "local")
        }
        val mutation = RecordingOutboxMutationStore()
        val outbox = RecordingOutboxService()
        val service = DefaultMessageActionService(command, mutation, outbox)

        val result = service.execute(
            CommandInput(
                raw = "/build --fast",
                sessionId = "s-1",
                directory = "/repo/main",
                projectId = "p-1",
                agent = "build",
            )
        )

        assertTrue(result.accepted)
        assertEquals(1, mutation.calls.size)
        assertEquals(1, outbox.drainCalls)

        val call = mutation.calls.first()
        assertEquals(OutboxType.EXECUTE_COMMAND, call.type)
        assertEquals("s-1", call.sessionId)
        assertEquals("/repo/main", call.directory)
        assertEquals("/build --fast", call.text)
        val payload = json.parseToJsonElement(call.payloadJson).jsonObject
        assertEquals("build", payload["command"]?.jsonPrimitive?.content)
        assertEquals("--fast", payload["arguments"]?.jsonPrimitive?.content)
        assertEquals("build", payload["agent"]?.jsonPrimitive?.content)
    }
}

private class RecordingOutboxService : OutboxService {
    var drainCalls = 0

    override suspend fun enqueue(action: OutboxAction) {
    }

    override suspend fun drain(limit: Int) {
        drainCalls += 1
    }
}

private class RecordingOutboxMutationStore : OutboxMutationStore {
    val calls = mutableListOf<OutboxMutationInput>()

    override suspend fun enqueueMessage(input: OutboxMutationInput): PendingMessageRef {
        calls += input
        return PendingMessageRef("local-${calls.size}")
    }
}

private class RecordingCommandRepository : CommandRepository {
    var next: CommandState? = null
    val findCalls = mutableListOf<String>()

    override fun observeCommands(projectId: String): Flow<List<CommandState>> {
        return flowOf(emptyList())
    }

    override suspend fun replaceCommands(projectId: String, commands: List<CommandState>) {
    }

    override suspend fun find(projectId: String, commandName: String): CommandState? {
        findCalls += "$projectId:$commandName"
        return next
    }
}
