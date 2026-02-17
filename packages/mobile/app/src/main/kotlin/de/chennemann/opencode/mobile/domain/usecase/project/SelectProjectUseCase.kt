package de.chennemann.opencode.mobile.domain.usecase.project

import de.chennemann.opencode.mobile.domain.service.project.ProjectActionService

class SelectProjectUseCase(
    private val action: ProjectActionService,
) {
    suspend operator fun invoke(projectId: String) {
        val id = projectId.trim()
        if (id.isBlank()) return
        action.select(id)
    }
}
