package de.chennemann.opencode.mobile.domain.v2.session

import kotlinx.coroutines.flow.Flow

interface SessionRepository {
    fun sessionsOfProject(projectKey: String): Flow<List<LocalSessionInfo>>
}
