package de.chennemann.opencode.mobile.data.repository.sql

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import de.chennemann.opencode.mobile.data.repository.ProjectRepository
import de.chennemann.opencode.mobile.db.AppDatabase
import de.chennemann.opencode.mobile.di.DispatcherProvider
import de.chennemann.opencode.mobile.domain.session.ProjectState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SqlDelightProjectRepository(
    private val db: AppDatabase,
    private val dispatchers: DispatcherProvider,
    private val json: Json,
) : ProjectRepository {
    override fun observeProjects(): Flow<List<ProjectState>> {
        return db.appDatabaseQueries
            .observeProjects { id, worktree, name, sandboxesJson, favorite ->
                ProjectState(
                    id = id,
                    worktree = worktree,
                    name = name,
                    sandboxes = parseSandboxes(sandboxesJson),
                    favorite = favorite == 1L,
                )
            }
            .asFlow()
            .mapToList(dispatchers.io)
    }

    override fun observeSelectedProject(): Flow<ProjectState?> {
        return db.appDatabaseQueries
            .observeSelectedProject { id, worktree, name, sandboxesJson, favorite ->
                ProjectState(
                    id = id,
                    worktree = worktree,
                    name = name,
                    sandboxes = parseSandboxes(sandboxesJson),
                    favorite = favorite == 1L,
                )
            }
            .asFlow()
            .mapToList(dispatchers.io)
            .map { it.firstOrNull() }
    }

    override suspend fun select(projectId: String) {
        withContext(dispatchers.io) {
            db.appDatabaseQueries.transaction {
                db.appDatabaseQueries.clearProjectSelection()
                db.appDatabaseQueries.setProjectSelected(System.currentTimeMillis(), projectId)
            }
        }
    }

    override suspend fun toggleFavorite(projectId: String): Boolean {
        return withContext(dispatchers.io) {
            val row = db.appDatabaseQueries.selectProjectById(projectId).executeAsOneOrNull() ?: return@withContext false
            val next = row.favorite == 0L
            db.appDatabaseQueries.setProjectFavorite(if (next) 1 else 0, System.currentTimeMillis(), projectId)
            next
        }
    }

    override suspend fun toggleHidden(projectId: String): Boolean {
        return withContext(dispatchers.io) {
            val row = db.appDatabaseQueries.selectProjectById(projectId).executeAsOneOrNull() ?: return@withContext false
            val next = row.hidden == 0L
            db.appDatabaseQueries.setProjectHidden(if (next) 1 else 0, System.currentTimeMillis(), projectId)
            next
        }
    }

    override suspend fun upsertProjects(items: List<ProjectState>) {
        withContext(dispatchers.io) {
            val now = System.currentTimeMillis()
            db.appDatabaseQueries.transaction {
                items.forEach {
                    db.appDatabaseQueries.upsertProject(
                        id = it.id,
                        worktree = it.worktree,
                        name = it.name,
                        sandboxes_json = json.encodeToString(it.sandboxes),
                        favorite = if (it.favorite) 1 else 0,
                        updated_at = now,
                    )
                }
            }
        }
    }

    override suspend fun requestRefresh(projectId: String) {
        withContext(dispatchers.io) {
            val row = db.appDatabaseQueries.selectProjectById(projectId).executeAsOneOrNull() ?: return@withContext
            db.appDatabaseQueries.upsertProject(
                id = row.id,
                worktree = row.worktree,
                name = row.name,
                sandboxes_json = row.sandboxes_json,
                favorite = row.favorite,
                updated_at = System.currentTimeMillis(),
            )
        }
    }

    private fun parseSandboxes(value: String): List<String> {
        return runCatching {
            json.decodeFromString<List<String>>(value)
        }.getOrDefault(emptyList())
    }
}
