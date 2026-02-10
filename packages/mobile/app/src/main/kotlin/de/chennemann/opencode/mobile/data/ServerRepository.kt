package de.chennemann.opencode.mobile.data

import android.util.Log
import de.chennemann.opencode.mobile.db.AppDatabase
import de.chennemann.opencode.mobile.domain.session.SessionGateway
import de.chennemann.opencode.mobile.domain.session.SessionMessage
import de.chennemann.opencode.mobile.domain.session.SessionProject
import de.chennemann.opencode.mobile.domain.session.SessionStreamEvent
import de.chennemann.opencode.mobile.domain.session.SessionSummary
import de.chennemann.opencode.mobile.ui.state.ServerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

class ServerRepository(
    private val db: AppDatabase,
    private val mdns: MdnsService,
    private val service: ServerService,
    private val network: NetworkService,
) : SessionGateway {
    private val state = MutableStateFlow<ServerState>(ServerState.Idle)
    private val url = MutableStateFlow(DefaultUrl)
    private val discovered = MutableStateFlow<String?>(null)

    override val status: StateFlow<ServerState> = state.asStateFlow()
    override val endpoint: StateFlow<String> = url.asStateFlow()
    override val found: StateFlow<String?> = discovered.asStateFlow()

    override fun start(scope: CoroutineScope) {
        scope.launch {
            load()
            refresh(true)
        }
        scope.launch {
            network.changed.drop(1).collect {
                refresh(false)
            }
        }
        scope.launch {
            mdns.discover().collect { entry ->
                discovered.value = normalizeUrl(entry.url) ?: entry.url
            }
        }
    }

    override suspend fun setUrl(next: String) {
        val value = normalizeUrl(next) ?: return
        url.value = value
        db.appDatabaseQueries.upsertSetting(UrlKey, value)
    }

    override suspend fun refresh(loading: Boolean) {
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

    override suspend fun projects(): List<SessionProject> {
        return service.projects(url.value).map {
            SessionProject(
                id = it.id,
                worktree = it.worktree,
                name = it.name,
            )
        }
    }

    override suspend fun sessions(worktree: String): List<SessionSummary> {
        return service.sessions(url.value, worktree).map {
            SessionSummary(
                id = it.id,
                title = it.title,
                version = it.version,
                directory = it.directory,
            )
        }
    }

    override suspend fun createSession(worktree: String, title: String): SessionSummary {
        val created = service.createSession(url.value, worktree, title)
        return SessionSummary(
            id = created.id,
            title = created.title,
            version = created.version,
            directory = created.directory,
        )
    }

    override suspend fun messages(sessionId: String, directory: String, limit: Int?): List<SessionMessage> {
        return service.sessionMessages(url.value, sessionId, directory, limit).map {
            SessionMessage(
                id = it.id,
                role = it.role,
                text = it.text,
                parts = it.parts,
            )
        }
    }

    override suspend fun streamEvents(lastEventId: String?, onRawEvent: suspend (String) -> Unit, onEvent: suspend (SessionStreamEvent) -> Unit): String? {
        return service.streamEvents(url.value, lastEventId, onRawEvent) {
            onEvent(
                SessionStreamEvent(
                    directory = it.directory,
                    type = it.type,
                    properties = it.properties,
                    id = it.id,
                    retry = it.retry,
                )
            )
        }
    }

    override suspend fun sendMessage(sessionId: String, directory: String, text: String) {
        service.sendMessage(url.value, sessionId, directory, text)
    }

    override suspend fun streamCursor(): String? {
        return db.appDatabaseQueries.selectSetting(eventCursorKey(url.value)).executeAsOneOrNull()
    }

    override suspend fun setStreamCursor(value: String?) {
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
private fun eventCursorKey(url: String): String {
    return "event_cursor:$url"
}
