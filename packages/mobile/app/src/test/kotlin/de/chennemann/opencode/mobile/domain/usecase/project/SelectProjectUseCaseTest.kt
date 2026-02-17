package de.chennemann.opencode.mobile.domain.usecase.project

import de.chennemann.opencode.mobile.domain.service.project.ProjectActionService
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SelectProjectUseCaseTest {
    @Test
    fun callsActionWithTrimmedProjectId() = runTest {
        val action = RecordingProjectActionService()
        val useCase = SelectProjectUseCase(action)

        useCase("  /repo/main  ")

        assertEquals(listOf("/repo/main"), action.selectCalls)
    }

    @Test
    fun ignoresBlankProjectId() = runTest {
        val action = RecordingProjectActionService()
        val useCase = SelectProjectUseCase(action)

        useCase("   ")

        assertEquals(emptyList<String>(), action.selectCalls)
    }
}

private class RecordingProjectActionService : ProjectActionService {
    val selectCalls = mutableListOf<String>()

    override suspend fun select(projectId: String) {
        selectCalls += projectId
    }

    override suspend fun toggleFavorite(projectId: String): Boolean {
        return false
    }

    override suspend fun toggleHidden(projectId: String): Boolean {
        return false
    }

    override suspend fun refreshProjectContext(projectId: String) = Unit
}
