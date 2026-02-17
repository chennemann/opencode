package de.chennemann.opencode.mobile.domain.usecase.session

import de.chennemann.opencode.mobile.data.repository.SyncReason
import de.chennemann.opencode.mobile.domain.service.model.MessagePageInput
import de.chennemann.opencode.mobile.domain.service.model.MessagePageRequestResult
import de.chennemann.opencode.mobile.domain.service.model.RenameInput
import de.chennemann.opencode.mobile.domain.service.session.SessionActionService
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RenameSessionUseCaseTest {
    @Test
    fun trimsTitleAndInvokesServiceOnce() = runTest {
        val action = RenameSessionAction()
        val useCase = RenameSessionUseCase(action)

        useCase(RenameInput(sessionId = " s-1 ", title = "  Renamed  ", directory = " /repo/main "))

        assertEquals(
            listOf(RenameInput(sessionId = "s-1", title = "Renamed", directory = "/repo/main")),
            action.renameCalls,
        )
    }

    @Test
    fun ignoresBlankTitle() = runTest {
        val action = RenameSessionAction()
        val useCase = RenameSessionUseCase(action)

        useCase(RenameInput(sessionId = "s-1", title = "   ", directory = "/repo/main"))

        assertEquals(emptyList<RenameInput>(), action.renameCalls)
    }
}

private class RenameSessionAction : SessionActionService {
    val renameCalls = mutableListOf<RenameInput>()

    override suspend fun focus(sessionId: String) {
    }

    override suspend fun requestMessagePage(input: MessagePageInput): MessagePageRequestResult {
        return MessagePageRequestResult(accepted = true, reason = null)
    }

    override suspend fun archive(sessionId: String, directory: String?) {
    }

    override suspend fun rename(input: RenameInput) {
        renameCalls += input
    }

    override suspend fun requestSync(sessionId: String, reason: SyncReason) {
    }
}
