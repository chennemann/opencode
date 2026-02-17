package de.chennemann.opencode.mobile.domain.service.project

import de.chennemann.opencode.mobile.data.repository.ProjectRepository
import de.chennemann.opencode.mobile.domain.service.sync.ProjectSyncService

class DefaultProjectActionService(
    private val project: ProjectRepository,
    private val sync: ProjectSyncService,
) : ProjectActionService {
    override suspend fun select(projectId: String) {
        project.select(projectId)
    }

    override suspend fun toggleFavorite(projectId: String): Boolean {
        return project.toggleFavorite(projectId)
    }

    override suspend fun toggleHidden(projectId: String): Boolean {
        return project.toggleHidden(projectId)
    }

    override suspend fun refreshProjectContext(projectId: String) {
        project.requestRefresh(projectId)
        sync.run(projectId)
    }
}
