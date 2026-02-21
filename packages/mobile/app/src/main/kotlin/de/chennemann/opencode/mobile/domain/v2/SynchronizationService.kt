package de.chennemann.opencode.mobile.domain.v2

import de.chennemann.opencode.mobile.domain.v2.projects.ProjectRepository
import de.chennemann.opencode.mobile.domain.v2.servers.ServerRepository
import de.chennemann.opencode.mobile.domain.v2.session.SessionRepository

interface SynchronizationService {
    suspend fun syncServer(serverId: String)
}

class DefaultSynchronizationService(
    private val serverRepository: ServerRepository,
    @Suppress("unused") private val projectRepository: ProjectRepository,
    @Suppress("unused") private val sessionRepository: SessionRepository,
) : SynchronizationService {
    override suspend fun syncServer(serverId: String) {
        val id = serverId.trim()
        if (id.isBlank()) return
        val server = serverRepository.selectServer(id) ?: return
        val url = server.url.trim()
        if (url.isBlank()) return
    }
}
