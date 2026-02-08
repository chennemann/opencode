package de.chennemann.opencode.mobile.data

import android.util.Log
import de.chennemann.opencode.mobile.db.AppDatabase
import de.chennemann.opencode.mobile.home.ServerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
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
            refresh()
            while (true) {
                delay(10_000)
                refresh(false)
            }
        }
        scope.launch {
            mdns.discover().collect { entry ->
                discovered.value = normalizeUrl(entry.url) ?: entry.url
            }
        }
    }

    suspend fun setUrl(next: String) {
        val value = normalizeUrl(next) ?: return
        url.value = value
        db.appDatabaseQueries.upsertSetting(UrlKey, value)
    }

    suspend fun refresh(loading: Boolean = true) {
        val endpoint = url.value
        if (loading) state.value = ServerState.Loading
        val result = runCatching { service.health(endpoint) }
        result.exceptionOrNull()?.let {
            Log.e("ServerRepository", "health failed for $endpoint", it)
        }
        state.value = result.fold(
            onSuccess = {
                if (it.healthy) ServerState.Connected(it.version)
                else ServerState.Failed("Server is unhealthy")
            },
            onFailure = { ServerState.Failed(it.message ?: "Connection failed") },
        )
    }

    suspend fun projects(): List<ProjectInfo> {
        return service.projects(url.value)
    }

    suspend fun sessions(worktree: String): List<SessionInfo> {
        return service.sessions(url.value, worktree)
    }

    suspend fun createSession(worktree: String, title: String): SessionInfo {
        return service.createSession(url.value, worktree, title)
    }

    suspend fun messages(sessionId: String, directory: String, limit: Int? = MessageSyncLimit): List<SessionMessageInfo> {
        return service.sessionMessages(url.value, sessionId, directory, limit)
    }

    suspend fun streamEvents(lastEventId: String?, onEvent: suspend (GlobalStreamEvent) -> Unit): String? {
        return service.streamEvents(url.value, lastEventId, onEvent)
    }

    suspend fun sendMessage(sessionId: String, directory: String, text: String) {
        service.sendMessage(url.value, sessionId, directory, text)
    }

    suspend fun streamCursor(): String? {
        return db.appDatabaseQueries.selectSetting(eventCursorKey(url.value)).executeAsOneOrNull()
    }

    suspend fun setStreamCursor(value: String?) {
        val key = eventCursorKey(url.value)
        if (value.isNullOrBlank()) {
            db.appDatabaseQueries.deleteSetting(key)
            return
        }
        db.appDatabaseQueries.upsertSetting(key, value)
    }

    private suspend fun load() {
        val value = db.appDatabaseQueries.selectSetting(UrlKey).executeAsOneOrNull()
        if (value == null) return
        val normalized = normalizeUrl(value) ?: return
        url.value = normalized
        if (normalized == value) return
        db.appDatabaseQueries.upsertSetting(UrlKey, normalized)
    }
}

private fun normalizeUrl(input: String): String? {
    val trimmed = input.trim()
    if (trimmed.isBlank()) return null
    val withProtocol = if (trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true)) {
        trimmed
    } else {
        "http://$trimmed"
    }
    return withProtocol.replace(Regex("/+$"), "")
}

private const val DefaultUrl = "http://opencode.local:4096"
private const val UrlKey = "server_url"
private const val MessageSyncLimit = 400

private fun eventCursorKey(url: String): String {
    return "event_cursor:$url"
}
