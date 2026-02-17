package de.chennemann.opencode.mobile.domain.usecase.project

import de.chennemann.opencode.mobile.domain.service.project.ProjectActionService
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RequestProjectRefreshUseCaseTest {
    @Test
    fun callsActionWithTrimmedProjectId() = runTest {
        val action = RecordingRefreshProjectActionService()
        val useCase = RequestProjectRefreshUseCase(action)

        useCase("  /repo/main  ")

        assertEquals(listOf("/repo/main"), action.refreshCalls)
    }

    @Test
    fun ignoresBlankProjectId() = runTest {
        val action = RecordingRefreshProjectActionService()
        val useCase = RequestProjectRefreshUseCase(action)

        useCase("   ")

        assertEquals(emptyList<String>(), action.refreshCalls)
    }
}

private class RecordingRefreshProjectActionService : ProjectActionService {
    val refreshCalls = mutableListOf<String>()

    override suspend fun select(projectId: String) = Unit

    override suspend fun toggleFavorite(projectId: String): Boolean {
        return false
    }

    override suspend fun toggleHidden(projectId: String): Boolean {
        return false
    }

    override suspend fun refreshProjectContext(projectId: String) {
        refreshCalls += projectId
    }
}
