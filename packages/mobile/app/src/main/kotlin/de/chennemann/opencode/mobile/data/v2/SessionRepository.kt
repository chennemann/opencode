package de.chennemann.opencode.mobile.data.v2

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import de.chennemann.opencode.mobile.db.AgenticDb
import de.chennemann.opencode.mobile.di.DispatcherProvider
import de.chennemann.opencode.mobile.domain.v2.session.LocalSessionInfo
import de.chennemann.opencode.mobile.domain.v2.session.SessionRepository
import kotlinx.coroutines.flow.Flow

class SqlDelightSessionRepository(
    private val db: AgenticDb,
    private val dispatchers: DispatcherProvider,
) : SessionRepository {
    override fun sessionsOfProject(projectKey: String): Flow<List<LocalSessionInfo>> {
        val key = projectKey.trim()
        require(key.isNotBlank()) { "projectKey must not be blank" }
        return db.sessionCacheQueries
            .listLocalSessionByProject(
                project_key = key,
                mapper = ::mapLocalSession,
            )
            .asFlow()
            .mapToList(dispatchers.io)
    }
}

private fun mapLocalSession(
    id: String,
    project_id: String,
    workspace: String,
    title: String,
    pinned: Long,
    parent_id: String?,
    updated_at: Long,
    last_read_at: Long?,
    archived_at: Long?,
): LocalSessionInfo {
    return LocalSessionInfo(
        id = id,
        projectId = project_id,
        workspace = workspace,
        title = title,
        pinned = pinned != 0L,
        parentId = parent_id,
        updatedAt = updated_at,
        lastReadAt = last_read_at,
        archivedAt = archived_at,
    )
}
