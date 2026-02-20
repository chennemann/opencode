package de.chennemann.opencode.mobile.domain.v2

import de.chennemann.opencode.mobile.domain.v2.projects.ProjectRepository
import de.chennemann.opencode.mobile.domain.v2.servers.ServerRepository
import de.chennemann.opencode.mobile.domain.v2.session.SessionRepository

interface SynchronizationService {
    
}

class DefaultSynchronizationService(
    val serverRepository: ServerRepository,
    val projectRepository: ProjectRepository,
    val sessionRepository: SessionRepository,
) : SynchronizationService {

}
