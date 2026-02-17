package de.chennemann.opencode.mobile.data

import de.chennemann.opencode.mobile.db.AppDatabase
import de.chennemann.opencode.mobile.di.DispatcherProvider
import de.chennemann.opencode.mobile.domain.session.CommandGateway
import de.chennemann.opencode.mobile.domain.session.CommandState
import de.chennemann.opencode.mobile.domain.session.ConnectivityGateway
import de.chennemann.opencode.mobile.domain.session.ConnectionGateway
import de.chennemann.opencode.mobile.domain.session.MessageGateway
import de.chennemann.opencode.mobile.domain.session.ProjectGateway
import de.chennemann.opencode.mobile.domain.session.SessionMessage
import de.chennemann.opencode.mobile.domain.session.SessionProject
import de.chennemann.opencode.mobile.domain.session.SessionStreamEvent
import de.chennemann.opencode.mobile.domain.session.SessionSummary
import de.chennemann.opencode.mobile.domain.session.ConnectionState
import de.chennemann.opencode.mobile.domain.session.LogGateway
import de.chennemann.opencode.mobile.domain.session.LogUnit
import de.chennemann.opencode.mobile.domain.session.MessageSendIds
import de.chennemann.opencode.mobile.domain.session.StreamGateway
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ServerRepository(
    private val db: AppDatabase,
    private val mdns: MdnsGateway,
    private val service: ServerGateway,
    private val network: ConnectivityGateway,
    private val dispatchers: DispatcherProvider,
    private val log: LogGateway,
) : ConnectionGateway, ProjectGateway, MessageGateway, StreamGateway, CommandGateway {
    private val state = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    private val url = MutableStateFlow(DefaultUrl)
    private val discovered = MutableStateFlow<String?>(null)

    override val status: StateFlow<ConnectionState> = state.asStateFlow()
    override val endpoint: StateFlow<String> = url.asStateFlow()
    override val found: StateFlow<String?> = discovered.asStateFlow()

    override fun start(scope: CoroutineScope) {
        scope.launch {
            load()
            refresh("startup", true)
        }
        scope.launch {
            network.changed.drop(1).collect {
                refresh("network_change", false)
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
        withContext(dispatchers.io) {
            db.appDatabaseQueries.upsertSetting(UrlKey, value)
        }
    }

    override suspend fun refresh(loading: Boolean) {
        val trigger = if (loading) "manual_connect" else "background_check"
        refresh(trigger, loading)
    }

    private suspend fun refresh(trigger: String, loading: Boolean) {
        val endpoint = url.value
        val startedAt = System.currentTimeMillis()
        log.info(
            unit = LogUnit.network,
            tag = LogTag,
            event = "health_check_started",
            message = "Health check started",
            context = mapOf(
                "endpoint" to endpoint,
                "trigger" to trigger,
            ),
        )
        if (loading) state.value = ConnectionState.Loading
        val result = runCatching {
            withContext(dispatchers.io) {
                service.health(endpoint)
            }
        }
        result.exceptionOrNull()?.let {
            log.error(
                unit = LogUnit.network,
                tag = LogTag,
                event = "health_check_failed",
                message = "Health check failed",
                context = mapOf(
                    "endpoint" to endpoint,
                    "trigger" to trigger,
                    "duration_ms" to (System.currentTimeMillis() - startedAt).toString(),
                ),
                error = it,
            )
        }
        val next = withContext(dispatchers.default) {
            result.fold(
                onSuccess = {
                    if (it.healthy) {
                        log.info(
                            unit = LogUnit.network,
                            tag = LogTag,
                            event = "health_check_succeeded",
                            message = "Health check succeeded",
                            context = mapOf(
                                "endpoint" to endpoint,
                                "trigger" to trigger,
                                "version" to it.version,
                                "duration_ms" to (System.currentTimeMillis() - startedAt).toString(),
                            ),
                        )
                        ConnectionState.Connected(it.version)
                    } else {
                        log.warn(
                            unit = LogUnit.network,
                            tag = LogTag,
                            event = "health_check_unhealthy",
                            message = "Server reported unhealthy",
                            context = mapOf(
                                "endpoint" to endpoint,
                                "trigger" to trigger,
                                "version" to it.version,
                                "duration_ms" to (System.currentTimeMillis() - startedAt).toString(),
                            ),
                        )
                        ConnectionState.Failed("Server is unhealthy")
                    }
                },
                onFailure = { ConnectionState.Failed(it.message ?: "Connection failed") },
            )
        }
        state.value = next
    }

    override suspend fun projects(): List<SessionProject> {
        val rows = withContext(dispatchers.io) {
            service.projects(url.value)
        }
        return withContext(dispatchers.default) {
            rows.map {
                SessionProject(
                    id = it.id,
                    worktree = it.worktree,
                    name = it.name,
                    sandboxes = it.sandboxes,
                )
            }
        }
    }

    override suspend fun sessions(worktree: String, limit: Int?): List<SessionSummary> {
        val rows = withContext(dispatchers.io) {
            service.sessions(url.value, worktree, limit)
        }
        return withContext(dispatchers.default) {
            rows.map {
                SessionSummary(
                    id = it.id,
                    title = it.title,
                    version = it.version,
                    directory = it.directory,
                    parentId = it.parentId,
                    updatedAt = it.updatedAt,
                    archivedAt = it.archivedAt,
                )
            }
        }
    }

    override suspend fun archiveSession(sessionId: String, directory: String) {
        withContext(dispatchers.io) {
            service.archiveSession(url.value, sessionId, directory)
        }
    }

    override suspend fun renameSession(sessionId: String, directory: String, title: String) {
        withContext(dispatchers.io) {
            service.renameSession(url.value, sessionId, directory, title)
        }
    }

    override suspend fun createSession(worktree: String, title: String): SessionSummary {
        val row = withContext(dispatchers.io) {
            service.createSession(url.value, worktree, title)
        }
        return withContext(dispatchers.default) {
            SessionSummary(
                id = row.id,
                title = row.title,
                version = row.version,
                directory = row.directory,
            )
        }
    }

    override suspend fun commands(directory: String): List<CommandState> {
        val rows = withContext(dispatchers.io) {
            service.commands(url.value, directory)
        }
        return withContext(dispatchers.default) {
            rows.map {
                CommandState(
                    name = it.name,
                    description = it.description,
                    source = it.source,
                )
            }
        }
    }

    override suspend fun messages(sessionId: String, directory: String, limit: Int?): List<SessionMessage> {
        val rows = withContext(dispatchers.io) {
            service.sessionMessages(url.value, sessionId, directory, limit)
        }
        return withContext(dispatchers.default) {
            rows.map {
                SessionMessage(
                    id = it.id,
                    role = it.role,
                    text = it.text,
                    parts = it.parts,
                    createdAt = it.createdAt,
                    completedAt = it.completedAt,
                )
            }
        }
    }

    override suspend fun updatedAt(sessionId: String, directory: String): Long? {
        return withContext(dispatchers.io) {
            service.sessionUpdatedAt(url.value, sessionId, directory)
        }
    }

    override suspend fun status(directory: String): Map<String, String> {
        return withContext(dispatchers.io) {
            service.sessionStatus(url.value, directory)
        }
    }

    override suspend fun streamEvents(lastEventId: String?, onRawEvent: suspend (String) -> Unit, onEvent: suspend (SessionStreamEvent) -> Unit): String? {
        return withContext(dispatchers.io) {
            service.streamEvents(url.value, lastEventId, onRawEvent) {
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
    }

    override suspend fun sendMessage(sessionId: String, directory: String, text: String, agent: String): MessageSendIds {
        val res = withContext(dispatchers.io) {
            service.sendMessage(url.value, sessionId, directory, text, agent)
        }
        return MessageSendIds(
            parentId = res.parentId,
            messageId = res.messageId,
        )
    }

    override suspend fun sendCommand(sessionId: String, directory: String, name: String, arguments: String, agent: String): MessageSendIds {
        val res = withContext(dispatchers.io) {
            service.sendCommand(url.value, sessionId, directory, name, arguments, agent)
        }
        return MessageSendIds(
            parentId = res.parentId,
            messageId = res.messageId,
        )
    }

    private suspend fun load() {
        val value = withContext(dispatchers.io) {
            db.appDatabaseQueries.selectSetting(UrlKey).executeAsOneOrNull()
        }
        if (value == null) return
        val normalized = normalizeUrl(value) ?: return
        url.value = normalized
        if (normalized == value) return
        withContext(dispatchers.io) {
            db.appDatabaseQueries.upsertSetting(UrlKey, normalized)
        }
    }
}

private const val LogTag = "ServerRepository"

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
