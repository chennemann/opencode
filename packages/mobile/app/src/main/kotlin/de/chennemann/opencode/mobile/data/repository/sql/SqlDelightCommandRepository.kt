package de.chennemann.opencode.mobile.data.repository.sql

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import de.chennemann.opencode.mobile.data.repository.CommandRepository
import de.chennemann.opencode.mobile.db.AppDatabase
import de.chennemann.opencode.mobile.di.DispatcherProvider
import de.chennemann.opencode.mobile.domain.session.CommandState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class SqlDelightCommandRepository(
    private val db: AppDatabase,
    private val dispatchers: DispatcherProvider,
) : CommandRepository {
    override fun observeCommands(projectId: String): Flow<List<CommandState>> {
        return db.appDatabaseQueries
            .observeCommands(projectId) { _, name, description, source ->
                CommandState(
                    name = name,
                    description = description,
                    source = source,
                )
            }
            .asFlow()
            .mapToList(dispatchers.io)
    }

    override suspend fun replaceCommands(projectId: String, commands: List<CommandState>) {
        withContext(dispatchers.io) {
            db.appDatabaseQueries.transaction {
                db.appDatabaseQueries.deleteCommandsByProject(projectId)
                commands.forEach {
                    db.appDatabaseQueries.upsertCommand(projectId, it.name, it.description, it.source)
                }
            }
        }
    }

    override suspend fun find(projectId: String, commandName: String): CommandState? {
        return withContext(dispatchers.io) {
            db.appDatabaseQueries.findCommand(projectId, commandName) { _, name, description, source ->
                CommandState(
                    name = name,
                    description = description,
                    source = source,
                )
            }.executeAsOneOrNull()
        }
    }
}
