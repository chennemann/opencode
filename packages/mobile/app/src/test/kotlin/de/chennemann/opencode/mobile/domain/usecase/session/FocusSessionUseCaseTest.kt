package de.chennemann.opencode.mobile.domain.usecase.session

import de.chennemann.opencode.mobile.data.repository.SyncReason
import de.chennemann.opencode.mobile.domain.service.model.MessagePageInput
import de.chennemann.opencode.mobile.domain.service.model.MessagePageRequestResult
import de.chennemann.opencode.mobile.domain.service.model.RenameInput
import de.chennemann.opencode.mobile.domain.service.session.SessionActionService
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FocusSessionUseCaseTest {
    @Test
    fun callsActionWithTrimmedSessionId() = runTest {
        val action = RecordingSessionActionService()
        val useCase = FocusSessionUseCase(action)

        useCase("  s-1  ")

        assertEquals(listOf("s-1"), action.focusCalls)
    }

    @Test
    fun ignoresBlankSessionId() = runTest {
        val action = RecordingSessionActionService()
        val useCase = FocusSessionUseCase(action)

        useCase("   ")

        assertEquals(emptyList<String>(), action.focusCalls)
    }
}

private class RecordingSessionActionService : SessionActionService {
    val focusCalls = mutableListOf<String>()

    override suspend fun focus(sessionId: String) {
        focusCalls += sessionId
    }

    override suspend fun requestMessagePage(input: MessagePageInput): MessagePageRequestResult {
        return MessagePageRequestResult(accepted = true, reason = null)
    }

    override suspend fun archive(sessionId: String, directory: String?) = Unit

    override suspend fun rename(input: RenameInput) = Unit

    override suspend fun requestSync(sessionId: String, reason: SyncReason) = Unit
}
