package de.chennemann.opencode.mobile.domain.v2

import de.chennemann.opencode.mobile.domain.v2.projects.ProjectRepository
import de.chennemann.opencode.mobile.domain.v2.projects.LocalProjectInfo
import de.chennemann.opencode.mobile.domain.v2.servers.ServerRepository
import de.chennemann.opencode.mobile.domain.v2.session.SessionRepository

interface SynchronizationService {
    suspend fun syncServer(serverId: String)
}

class DefaultSynchronizationService(
    private val serverRepository: ServerRepository,
    private val projectRepository: ProjectRepository,
    @Suppress("unused") private val sessionRepository: SessionRepository,
    private val adapter: OpenCodeServerAdapter,
) : SynchronizationService {
    override suspend fun syncServer(serverId: String) {
        val id = serverId.trim()
        if (id.isBlank()) return
        val server = serverRepository.selectServer(id) ?: return
        val url = server.url.trim()
        if (url.isBlank()) return

        syncProjectsForServer(id, url)
    }

    private suspend fun syncProjectsForServer(serverId: String, url: String) {
        val remoteProjects = adapter.allProjects(url)
        remoteProjects.forEach { remote ->
            val projectId = remote.id.trim()
            val projectPath = remote.worktree.trim()
            if (projectId.isBlank() || projectPath.isBlank()) return@forEach

            val existing = projectRepository.selectProject(projectId)
            val local = LocalProjectInfo(
                id = projectId,
                serverId = serverId,
                name = remote.name.trim().ifBlank { projectPath },
                path = projectPath,
                pinned = existing?.pinned ?: false,
            )

            if (existing == null) {
                projectRepository.insertProject(local)
            } else {
                projectRepository.updateProject(local)
            }
        }
    }
}
