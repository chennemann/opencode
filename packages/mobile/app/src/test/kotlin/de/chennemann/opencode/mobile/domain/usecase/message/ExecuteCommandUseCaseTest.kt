package de.chennemann.opencode.mobile.domain.usecase.message

import de.chennemann.opencode.mobile.domain.service.message.MessageActionService
import de.chennemann.opencode.mobile.domain.service.model.CommandInput
import de.chennemann.opencode.mobile.domain.service.model.CommandResult
import de.chennemann.opencode.mobile.domain.service.model.SendMessageInput
import de.chennemann.opencode.mobile.domain.service.model.SendMessageResult
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ExecuteCommandUseCaseTest {
    @Test
    fun returnsServiceResultUnchanged() = runTest {
        val action = ExecuteUseCaseAction().also {
            it.commandResult = CommandResult(accepted = true, reason = null)
        }
        val useCase = ExecuteCommandUseCase(action)
        val input = CommandInput(
            raw = "/build --fast",
            sessionId = "s-1",
            directory = "/repo/main",
            projectId = "p-1",
            agent = "build",
        )

        val result = useCase(input)

        assertEquals(action.commandResult, result)
        assertEquals(listOf(input), action.commandCalls)
    }
}

private class ExecuteUseCaseAction : MessageActionService {
    val commandCalls = mutableListOf<CommandInput>()
    var commandResult = CommandResult(accepted = false, reason = "unset")

    override suspend fun send(input: SendMessageInput): SendMessageResult {
        return SendMessageResult(accepted = true, sessionId = input.sessionId, reason = null)
    }

    override suspend fun execute(input: CommandInput): CommandResult {
        commandCalls += input
        return commandResult
    }
}
