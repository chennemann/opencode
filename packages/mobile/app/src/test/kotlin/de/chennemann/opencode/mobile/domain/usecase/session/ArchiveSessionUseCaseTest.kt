package de.chennemann.opencode.mobile.domain.usecase.session

import de.chennemann.opencode.mobile.data.repository.SyncReason
import de.chennemann.opencode.mobile.domain.service.model.MessagePageInput
import de.chennemann.opencode.mobile.domain.service.model.MessagePageRequestResult
import de.chennemann.opencode.mobile.domain.service.model.RenameInput
import de.chennemann.opencode.mobile.domain.service.session.SessionActionService
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ArchiveSessionUseCaseTest {
    @Test
    fun invokesActionOnceWithTrimmedSessionId() = runTest {
        val action = ArchiveSessionAction()
        val useCase = ArchiveSessionUseCase(action)

        useCase(" s-1 ")

        assertEquals(listOf("s-1:"), action.archiveCalls)
    }

    @Test
    fun forwardsDirectoryWhenProvided() = runTest {
        val action = ArchiveSessionAction()
        val useCase = ArchiveSessionUseCase(action)

        useCase(" s-1 ", " /repo/main ")

        assertEquals(listOf("s-1:/repo/main"), action.archiveCalls)
    }

    @Test
    fun ignoresBlankSessionId() = runTest {
        val action = ArchiveSessionAction()
        val useCase = ArchiveSessionUseCase(action)

        useCase("   ")

        assertEquals(emptyList<String>(), action.archiveCalls)
    }
}

private class ArchiveSessionAction : SessionActionService {
    val archiveCalls = mutableListOf<String>()

    override suspend fun focus(sessionId: String) {
    }

    override suspend fun requestMessagePage(input: MessagePageInput): MessagePageRequestResult {
        return MessagePageRequestResult(accepted = true, reason = null)
    }

    override suspend fun archive(sessionId: String, directory: String?) {
        archiveCalls += "$sessionId:${directory.orEmpty()}"
    }

    override suspend fun rename(input: RenameInput) {
    }

    override suspend fun requestSync(sessionId: String, reason: SyncReason) {
    }
}
