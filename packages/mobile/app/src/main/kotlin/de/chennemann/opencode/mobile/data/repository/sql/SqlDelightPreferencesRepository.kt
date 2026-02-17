package de.chennemann.opencode.mobile.data.repository.sql

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import de.chennemann.opencode.mobile.data.repository.PrefsState
import de.chennemann.opencode.mobile.data.repository.PreferencesRepository
import de.chennemann.opencode.mobile.db.AppDatabase
import de.chennemann.opencode.mobile.di.DispatcherProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class SqlDelightPreferencesRepository(
    private val db: AppDatabase,
    private val dispatchers: DispatcherProvider,
) : PreferencesRepository {
    override fun observePrefs(): Flow<PrefsState> {
        return db.appDatabaseQueries
            .observePrefs { quickSwitchScope, sortMode, logsFilterJson, logsRetentionPolicy ->
                PrefsState(
                    quickSwitchScope = quickSwitchScope,
                    sortMode = sortMode,
                    logsFilterJson = logsFilterJson,
                    logsRetentionPolicy = logsRetentionPolicy,
                )
            }
            .asFlow()
            .mapToList(dispatchers.io)
            .map { it.firstOrNull() ?: defaultPrefs() }
    }

    override suspend fun setQuickSwitchScope(scope: String) {
        withContext(dispatchers.io) {
            val prefs = read()
            write(prefs.copy(quickSwitchScope = scope))
        }
    }

    override suspend fun setSortMode(mode: String) {
        withContext(dispatchers.io) {
            val prefs = read()
            write(prefs.copy(sortMode = mode))
        }
    }

    override suspend fun setLogsFilter(filterJson: String) {
        withContext(dispatchers.io) {
            val prefs = read()
            write(prefs.copy(logsFilterJson = filterJson))
        }
    }

    override suspend fun setLogsRetentionPolicy(policy: String) {
        withContext(dispatchers.io) {
            val prefs = read()
            write(prefs.copy(logsRetentionPolicy = policy))
        }
    }

    private fun read(): PrefsState {
        return db.appDatabaseQueries.observePrefs { quickSwitchScope, sortMode, logsFilterJson, logsRetentionPolicy ->
            PrefsState(
                quickSwitchScope = quickSwitchScope,
                sortMode = sortMode,
                logsFilterJson = logsFilterJson,
                logsRetentionPolicy = logsRetentionPolicy,
            )
        }.executeAsOneOrNull() ?: defaultPrefs()
    }

    private fun write(value: PrefsState) {
        db.appDatabaseQueries.upsertPreference(
            quick_switch_scope = value.quickSwitchScope,
            sort_mode = value.sortMode,
            logs_filter_json = value.logsFilterJson,
            logs_retention_policy = value.logsRetentionPolicy,
            updated_at = System.currentTimeMillis(),
        )
    }
}

private fun defaultPrefs(): PrefsState {
    return PrefsState(
        quickSwitchScope = "project",
        sortMode = "updated_desc",
        logsFilterJson = "{}",
        logsRetentionPolicy = "window_7d_max_5000",
    )
}
