package de.chennemann.opencode.mobile.data

import de.chennemann.opencode.mobile.db.AppDatabase
import de.chennemann.opencode.mobile.home.ServerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ServerRepository(
    private val db: AppDatabase,
    private val mdns: MdnsService,
    private val service: ServerService,
) {
    private val state = MutableStateFlow<ServerState>(ServerState.Idle)
    private val url = MutableStateFlow(DefaultUrl)
    private val discovered = MutableStateFlow<String?>(null)

    val status: StateFlow<ServerState> = state.asStateFlow()
    val endpoint: StateFlow<String> = url.asStateFlow()
    val found: StateFlow<String?> = discovered.asStateFlow()

    fun start(scope: CoroutineScope) {
        scope.launch {
            load()
        }
        scope.launch {
            mdns.discover().collect { entry ->
                discovered.value = entry.url
            }
        }
    }

    suspend fun setUrl(next: String) {
        val value = next.trim()
        if (value.isBlank()) return
        url.value = value
        db.appDatabaseQueries.upsertSetting(UrlKey, value)
    }

    suspend fun refresh() {
        state.value = ServerState.Loading
        val result = runCatching { service.health(url.value) }
        state.value = result.fold(
            onSuccess = { ServerState.Connected(it.version) },
            onFailure = { ServerState.Failed(it.message ?: "Connection failed") },
        )
    }

    private suspend fun load() {
        val value = db.appDatabaseQueries.selectSetting(UrlKey).executeAsOneOrNull()
        if (value == null) return
        if (value.isBlank()) return
        url.value = value
    }
}

private const val DefaultUrl = "http://opencode.local:4000"
private const val UrlKey = "server_url"
