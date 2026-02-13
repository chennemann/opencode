package de.chennemann.opencode.mobile.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import de.chennemann.opencode.mobile.db.AppDatabase
import de.chennemann.opencode.mobile.domain.session.MessageState
import de.chennemann.opencode.mobile.domain.session.RecentSessionCache
import de.chennemann.opencode.mobile.domain.session.SessionCacheGateway
import de.chennemann.opencode.mobile.domain.session.SessionQuickPinCache
import de.chennemann.opencode.mobile.domain.session.SessionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow

class SessionCacheRepository(
    private val db: AppDatabase,
) : SessionCacheGateway {
    override suspend fun upsertSession(server: String, project: String?, session: SessionState) {
        val now = System.currentTimeMillis()
        db.appDatabaseQueries.upsertSessionCache(
            server,
            session.id,
            project,
            session.directory,
            session.title,
            session.version,
            now,
            now,
        )
    }

    override fun recentSession(): RecentSessionCache? {
        return db.appDatabaseQueries.selectRecentSessionCache(
            mapper = { serverUrl, sessionId, projectId, directory, title, version, _, _ ->
                RecentSessionCache(
                    server = serverUrl,
                    project = projectId,
                    session = SessionState(
                        id = sessionId,
                        title = title,
                        version = version,
                        directory = directory,
                    ),
                )
            }
        ).executeAsOneOrNull()
    }

    override fun projectFavorites(server: String): Set<String> {
        return settingSet(db, projectFavoriteKey(server))
    }

    override suspend fun setProjectFavorite(server: String, worktree: String, favorite: Boolean) {
        val key = projectFavoriteKey(server)
        val next = projectFavorites(server)
            .toMutableSet()
            .also {
                if (favorite) it.add(worktree) else it.remove(worktree)
            }
            .toList()
            .sorted()

        if (next.isEmpty()) {
            db.appDatabaseQueries.deleteSetting(key)
            return
        }

        db.appDatabaseQueries.upsertSetting(key, next.joinToString(ProjectFavoriteSeparator))
    }

    override fun sessionQuickPins(server: String): SessionQuickPinCache {
        return SessionQuickPinCache(
            include = settingSet(db, sessionQuickIncludeKey(server)),
            exclude = settingSet(db, sessionQuickExcludeKey(server)),
        )
    }

    override suspend fun setSessionQuickPins(server: String, include: Set<String>, exclude: Set<String>) {
        setSettingSet(db, sessionQuickIncludeKey(server), include)
        setSettingSet(db, sessionQuickExcludeKey(server), exclude)
    }

    override suspend fun listMessages(server: String, sessionId: String): List<MessageState> {
        return db.appDatabaseQueries
            .listMessageCache(server, sessionId) { _, _, messageId, role, text, sortKey, createdAt, completedAt, _ ->
                MessageState(
                    id = messageId,
                    role = role,
                    text = text,
                    sort = sortKey,
                    createdAt = createdAt,
                    completedAt = completedAt,
                )
            }
            .executeAsList()
    }

    override fun observeMessages(server: String, sessionId: String): Flow<List<MessageState>> {
        return db.appDatabaseQueries
            .listMessageCache(server, sessionId) { _, _, messageId, role, text, sortKey, createdAt, completedAt, _ ->
                MessageState(
                    id = messageId,
                    role = role,
                    text = text,
                    sort = sortKey,
                    createdAt = createdAt,
                    completedAt = completedAt,
                )
            }
            .asFlow()
            .mapToList(Dispatchers.IO)
    }

    override suspend fun upsertMessage(server: String, sessionId: String, message: MessageState, updatedAt: Long) {
        db.appDatabaseQueries.upsertMessageCache(
            server,
            sessionId,
            message.id,
            message.role,
            message.text,
            message.sort,
            message.createdAt,
            message.completedAt,
            updatedAt,
        )
    }

    override suspend fun deleteMessage(server: String, sessionId: String, messageId: String) {
        db.appDatabaseQueries.deleteMessageCache(server, sessionId, messageId)
    }

    override suspend fun deleteSessionMessages(server: String, sessionId: String) {
        db.appDatabaseQueries.deleteMessageCacheSession(server, sessionId)
    }
}

private const val ProjectFavoriteSeparator = "\n"

private fun settingSet(db: AppDatabase, key: String): Set<String> {
    return db.appDatabaseQueries
        .selectSetting(key)
        .executeAsOneOrNull()
        ?.split(ProjectFavoriteSeparator)
        ?.map(String::trim)
        ?.filter(String::isNotEmpty)
        ?.toSet()
        ?: emptySet()
}

private fun setSettingSet(db: AppDatabase, key: String, value: Set<String>) {
    val next = value
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinct()
        .sorted()
    if (next.isEmpty()) {
        db.appDatabaseQueries.deleteSetting(key)
        return
    }
    db.appDatabaseQueries.upsertSetting(key, next.joinToString(ProjectFavoriteSeparator))
}

private fun projectFavoriteKey(server: String): String {
    return "project_favorite:$server"
}

private fun sessionQuickIncludeKey(server: String): String {
    return "session_quick_include:$server"
}

private fun sessionQuickExcludeKey(server: String): String {
    return "session_quick_exclude:$server"
}
