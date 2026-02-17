package de.chennemann.opencode.mobile.domain.usecase.message

import de.chennemann.opencode.mobile.domain.service.message.MessageActionService
import de.chennemann.opencode.mobile.domain.service.model.CommandInput
import de.chennemann.opencode.mobile.domain.service.model.CommandResult
import de.chennemann.opencode.mobile.domain.service.model.SendMessageInput
import de.chennemann.opencode.mobile.domain.service.model.SendMessageResult
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SendMessageUseCaseTest {
    @Test
    fun returnsServiceResultUnchanged() = runTest {
        val action = SendUseCaseAction().also {
            it.sendResult = SendMessageResult(accepted = true, sessionId = "s-1", reason = null)
        }
        val useCase = SendMessageUseCase(action)
        val input = SendMessageInput(text = "hello", agent = "build", sessionId = "s-1", directory = "/repo/main")

        val result = useCase(input)

        assertEquals(action.sendResult, result)
        assertEquals(listOf(input), action.sendCalls)
    }
}

private class SendUseCaseAction : MessageActionService {
    val sendCalls = mutableListOf<SendMessageInput>()
    var sendResult = SendMessageResult(accepted = false, sessionId = null, reason = "unset")

    override suspend fun send(input: SendMessageInput): SendMessageResult {
        sendCalls += input
        return sendResult
    }

    override suspend fun execute(input: CommandInput): CommandResult {
        return CommandResult(accepted = true, reason = null)
    }
}
