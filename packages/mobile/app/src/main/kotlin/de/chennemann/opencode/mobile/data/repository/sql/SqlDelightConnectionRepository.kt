package de.chennemann.opencode.mobile.data.repository.sql

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import de.chennemann.opencode.mobile.data.repository.ConnectionRepository
import de.chennemann.opencode.mobile.data.repository.ConnectionSnapshot
import de.chennemann.opencode.mobile.db.AppDatabase
import de.chennemann.opencode.mobile.di.DispatcherProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class SqlDelightConnectionRepository(
    private val db: AppDatabase,
    private val dispatchers: DispatcherProvider,
) : ConnectionRepository {
    override fun observeConnection(): Flow<ConnectionSnapshot> {
        return db.appDatabaseQueries
            .observeConnection { endpoint, discovered, status ->
                ConnectionSnapshot(
                    endpoint = endpoint,
                    discovered = discovered,
                    status = status,
                )
            }
            .asFlow()
            .mapToList(dispatchers.io)
            .map { it.firstOrNull() ?: ConnectionSnapshot(endpoint = "", discovered = null, status = "IDLE") }
    }

    override suspend fun setEndpoint(url: String) {
        withContext(dispatchers.io) {
            db.appDatabaseQueries.setConnectionEndpoint(url, System.currentTimeMillis())
        }
    }

    override suspend fun setStatus(status: String) {
        withContext(dispatchers.io) {
            db.appDatabaseQueries.setConnectionStatus(status, System.currentTimeMillis())
        }
    }

    override suspend fun recordDiscovery(items: List<String>) {
        withContext(dispatchers.io) {
            db.appDatabaseQueries.setConnectionDiscovery(items.firstOrNull(), System.currentTimeMillis())
        }
    }
}
