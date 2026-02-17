package de.chennemann.opencode.mobile.domain.service.project

interface ProjectActionService {
    suspend fun select(projectId: String)
    suspend fun toggleFavorite(projectId: String): Boolean
    suspend fun toggleHidden(projectId: String): Boolean
    suspend fun refreshProjectContext(projectId: String)
}
