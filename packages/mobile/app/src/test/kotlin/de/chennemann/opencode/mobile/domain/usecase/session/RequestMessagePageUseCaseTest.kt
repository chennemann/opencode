package de.chennemann.opencode.mobile.domain.usecase.session

import de.chennemann.opencode.mobile.data.repository.SyncReason
import de.chennemann.opencode.mobile.domain.service.model.MessagePageInput
import de.chennemann.opencode.mobile.domain.service.model.MessagePageRequestResult
import de.chennemann.opencode.mobile.domain.service.model.RenameInput
import de.chennemann.opencode.mobile.domain.service.session.SessionActionService
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RequestMessagePageUseCaseTest {
    @Test
    fun ignoresBlankSessionId() = runTest {
        val action = RequestPageSessionAction()
        val useCase = RequestMessagePageUseCase(action)

        val result = useCase(MessagePageInput(sessionId = "   ", beforeMessageId = "m-1", limit = 20))

        assertEquals(MessagePageRequestResult(accepted = false, reason = "session_id_blank"), result)
        assertTrue(action.pageCalls.isEmpty())
    }

    @Test
    fun trimsInputsAndForwardsToActionService() = runTest {
        val action = RequestPageSessionAction().also {
            it.next = MessagePageRequestResult(accepted = true, reason = null)
        }
        val useCase = RequestMessagePageUseCase(action)

        val result = useCase(MessagePageInput(sessionId = " s-1 ", beforeMessageId = "  m-1  ", limit = 55))

        assertEquals(action.next, result)
        assertEquals(
            listOf(MessagePageInput(sessionId = "s-1", beforeMessageId = "m-1", limit = 55)),
            action.pageCalls,
        )
    }
}

private class RequestPageSessionAction : SessionActionService {
    val pageCalls = mutableListOf<MessagePageInput>()
    var next = MessagePageRequestResult(accepted = false, reason = "unset")

    override suspend fun focus(sessionId: String) {
    }

    override suspend fun requestMessagePage(input: MessagePageInput): MessagePageRequestResult {
        pageCalls += input
        return next
    }

    override suspend fun archive(sessionId: String, directory: String?) {
    }

    override suspend fun rename(input: RenameInput) {
    }

    override suspend fun requestSync(sessionId: String, reason: SyncReason) {
    }
}
