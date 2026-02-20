package de.chennemann.opencode.mobile.domain.v2

import de.chennemann.opencode.mobile.domain.v2.projects.ProjectRepository

interface SynchronizationService {

}

class DefaultSynchronizationService(
    val projectRepository: ProjectRepository,
    val sessionRepository: ProjectRepository
): SynchronizationService {

}
