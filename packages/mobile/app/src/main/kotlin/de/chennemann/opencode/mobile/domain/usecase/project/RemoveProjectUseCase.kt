package de.chennemann.opencode.mobile.domain.usecase.project

import de.chennemann.opencode.mobile.domain.service.project.ProjectActionService

class RemoveProjectUseCase(
    private val action: ProjectActionService,
) {
    suspend operator fun invoke(projectId: String): Boolean {
        val id = projectId.trim()
        if (id.isBlank()) return false
        return action.toggleHidden(id)
    }
}
