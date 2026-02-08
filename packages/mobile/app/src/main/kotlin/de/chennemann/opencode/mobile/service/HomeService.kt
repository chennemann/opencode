package de.chennemann.opencode.mobile.service

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import de.chennemann.opencode.mobile.data.GlobalStreamEvent
import de.chennemann.opencode.mobile.data.ServerRepository
import de.chennemann.opencode.mobile.db.AppDatabase
import de.chennemann.opencode.mobile.home.HomeState
import de.chennemann.opencode.mobile.home.MessageState
import de.chennemann.opencode.mobile.home.ProjectState
import de.chennemann.opencode.mobile.home.ServerState
import de.chennemann.opencode.mobile.home.SessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.atomic.AtomicLong

class HomeService(
    private val repo: ServerRepository,
    private val db: AppDatabase,
) {
    private data class LocalState(
        val projects: List<ProjectState> = emptyList(),
        val selectedProject: String? = null,
        val sessions: List<SessionState> = emptyList(),
        val activeSessions: List<SessionState> = emptyList(),
        val focusedSession: SessionState? = null,
        val focusedMessages: List<MessageState> = emptyList(),
        val managementOpen: Boolean = false,
        val loadingProjects: Boolean = false,
        val loadingSessions: Boolean = false,
        val loadingMessages: Boolean = false,
        val message: String? = null,
    )

    private val input = MutableStateFlow(repo.endpoint.value)
    private val local = MutableStateFlow(LocalState())
    private val output = MutableStateFlow(
        HomeState(
            url = repo.endpoint.value,
            discovered = null,
            status = ServerState.Idle,
            projects = emptyList(),
            selectedProject = null,
            sessions = emptyList(),
            activeSessions = emptyList(),
            focusedSession = null,
            focusedMessages = emptyList(),
            managementOpen = false,
            loadingProjects = false,
            loadingSessions = false,
            loadingMessages = false,
            message = null,
        )
    )

    private val active = linkedMapOf<String, SessionState>()
    private val sessionProject = linkedMapOf<String, String?>()
    private val part = linkedMapOf<String, LinkedHashMap<String, String>>()
    private val role = linkedMapOf<String, String>()
    private val sort = linkedMapOf<String, String>()
    private val pending = linkedMapOf<String, MutableList<MessageState>>()
    private val seq = AtomicLong(System.currentTimeMillis() * 1000)
    private var manual = false
    private var stream: Job? = null
    private var observe: Job? = null
    private val sync = linkedMapOf<String, Job>()
    private var scope: CoroutineScope? = null
    private var started = false
    private var focusedKey: String? = null

    val state: StateFlow<HomeState> = output.asStateFlow()

    fun start(scope: CoroutineScope) {
        if (started) return
        started = true
        this.scope = scope
        repo.start(scope)
        scope.launch {
            combine(input, repo.found, repo.status, local) { url, discovered, status, local ->
                HomeState(
                    url = url,
                    discovered = discovered,
                    status = status,
                    projects = local.projects,
                    selectedProject = local.selectedProject,
                    sessions = local.sessions,
                    activeSessions = local.activeSessions,
                    focusedSession = local.focusedSession,
                    focusedMessages = local.focusedMessages,
                    managementOpen = local.managementOpen,
                    loadingProjects = local.loadingProjects,
                    loadingSessions = local.loadingSessions,
                    loadingMessages = local.loadingMessages,
                    message = local.message,
                )
            }.collect {
                output.value = it
            }
        }
        scope.launch {
            repo.endpoint.collect {
                if (manual) return@collect
                input.value = it
            }
        }
        scope.launch {
            hydrateLast()
        }
        stream(scope)
    }

    fun updateUrl(value: String) {
        manual = true
        input.value = value
    }

    fun useDiscovered() {
        val value = repo.found.value ?: return
        manual = true
        input.value = value
    }

    fun refresh() {
        val scope = scope ?: return
        scope.launch {
            repo.setUrl(input.value)
            repo.refresh()
        }
    }

    fun openManagement() {
        local.value = local.value.copy(managementOpen = true)
    }

    fun closeManagement() {
        local.value = local.value.copy(managementOpen = false)
    }

    fun loadProjects() {
        val scope = scope ?: return
        scope.launch {
            local.value = local.value.copy(loadingProjects = true, message = null)
            val result = runCatching { repo.projects() }
            local.value = local.value.copy(loadingProjects = false)
            result.onSuccess { list ->
                val projects = list
                    .map {
                        ProjectState(
                            id = it.id,
                            worktree = it.worktree,
                            name = it.name,
                        )
                    }
                    .sortedBy { it.name.lowercase() }
                val current = local.value.selectedProject
                val next = if (current != null && projects.any { it.worktree == current }) {
                    current
                } else {
                    projects.firstOrNull()?.worktree
                }
                local.value = local.value.copy(
                    projects = projects,
                    selectedProject = next,
                    sessions = if (next == null) emptyList() else local.value.sessions,
                )
                if (next == null) return@onSuccess
                loadSessions(next)
            }
            result.onFailure {
                local.value = local.value.copy(message = it.message ?: "Failed to load projects")
            }
        }
    }

    fun selectProject(worktree: String) {
        if (local.value.selectedProject == worktree) return
        local.value = local.value.copy(selectedProject = worktree)
        loadSessions(worktree)
    }

    fun createSession() {
        val worktree = local.value.selectedProject ?: return
        val scope = scope ?: return
        scope.launch {
            local.value = local.value.copy(loadingSessions = true, message = null)
            val result = runCatching { repo.createSession(worktree, "Mobile session") }
            local.value = local.value.copy(loadingSessions = false)
            result.onSuccess {
                val session = SessionState(
                    id = it.id,
                    title = it.title,
                    version = it.version,
                    directory = it.directory,
                )
                focusSession(session, worktree)
                loadSessions(worktree)
            }
            result.onFailure {
                local.value = local.value.copy(message = it.message ?: "Failed to create session")
            }
        }
    }

    fun openSession(session: SessionState) {
        focusSession(session, local.value.selectedProject)
    }

    fun send(text: String) {
        val value = text.trim()
        if (value.isBlank()) return
        val scope = scope ?: return
        val focused = local.value.focusedSession ?: return
        val key = focusedKey ?: keyForSession(focused.id) ?: key(repo.endpoint.value, focused.id)
        if (active[key] == null) {
            active[key] = focused
            focusedKey = key
        }
        val server = key.substringBefore("::")
        val id = "local-${System.currentTimeMillis()}"
        pending.getOrPut(key) { mutableListOf() }.add(
            MessageState(
                id = id,
                role = "user",
                text = value,
            )
        )
        local.value = local.value.copy(
            focusedMessages = local.value.focusedMessages + MessageState(
                id = id,
                role = "user",
                text = value,
            )
        )
        scope.launch {
            val result = runCatching {
                repo.sendMessage(focused.id, focused.directory, value)
            }
            result.onSuccess {
                scheduleSync(focused.id)
            }
            result.onFailure {
                pending[key]?.removeAll { it.id == id }
                if (focusedKey == key) observeFocused()
                local.value = local.value.copy(message = it.message ?: "Failed to send message")
            }
        }
    }

    fun focusSession(sessionId: String) {
        val entry = active.entries.find { it.value.id == sessionId } ?: return
        focusedKey = entry.key
        local.value = local.value.copy(
            focusedSession = entry.value,
        )
        observeFocused()
    }

    private fun focusSession(session: SessionState, project: String?) {
        val server = if (manual) input.value else repo.endpoint.value
        val key = key(server, session.id)
        active[key] = session
        sessionProject[key] = project
        focusedKey = key
        local.value = local.value.copy(
            focusedSession = session,
            activeSessions = active.values.sortedByDescending { it.id },
            managementOpen = false,
        )
        val now = System.currentTimeMillis()
        db.appDatabaseQueries.upsertSessionCache(
            server,
            session.id,
            sessionProject[key],
            session.directory,
            session.title,
            session.version,
            now,
            now,
        )
        observeFocused()
        hydrateRemote(session)
    }

    private fun hydrateRemote(session: SessionState) {
        val scope = scope ?: return
        scope.launch {
            local.value = local.value.copy(loadingMessages = true, message = null)
            val result = runCatching { repo.messages(session.id, session.directory) }
            local.value = local.value.copy(loadingMessages = false)
            result.onSuccess { list ->
                val key = keyForSession(session.id) ?: return@onSuccess
                list.forEach {
                    val message = messageKey(key, it.id)
                    role[message] = it.role
                    if (sort[message] == null) {
                        sort[message] = sequence()
                    }
                    db.appDatabaseQueries.upsertMessageCache(
                        key.substringBefore("::"),
                        session.id,
                        it.id,
                        it.role,
                        it.text,
                        sort[message] ?: sequence(),
                        System.currentTimeMillis(),
                    )
                }
            }
            result.onFailure {
                local.value = local.value.copy(message = it.message ?: "Failed to load messages")
            }
        }
    }

    private fun loadSessions(worktree: String) {
        val scope = scope ?: return
        scope.launch {
            local.value = local.value.copy(loadingSessions = true, message = null)
            val result = runCatching { repo.sessions(worktree) }
            local.value = local.value.copy(loadingSessions = false)
            result.onSuccess { list ->
                local.value = local.value.copy(
                    sessions = list
                        .map {
                            SessionState(
                                id = it.id,
                                title = it.title,
                                version = it.version,
                                directory = it.directory,
                            )
                        }
                        .sortedByDescending { it.id },
                )
            }
            result.onFailure {
                local.value = local.value.copy(message = it.message ?: "Failed to load sessions")
            }
        }
    }

    private fun hydrateLast() {
        val recent = db.appDatabaseQueries.selectRecentSessionCache(
            mapper = { serverUrl, sessionId, projectId, directory, title, version, _, _ ->
                Triple(
                    serverUrl,
                    projectId,
                    SessionState(
                        id = sessionId,
                        title = title,
                        version = version,
                        directory = directory,
                    ),
                )
            }
        ).executeAsOneOrNull() ?: return
        val scope = scope ?: return
        scope.launch {
            manual = true
            input.value = recent.first
            repo.setUrl(recent.first)
            repo.refresh(false)
            manual = false
            focusSession(recent.third, recent.second)
        }
    }

    private fun stream(scope: CoroutineScope) {
        stream?.cancel()
        stream = scope.launch {
            while (isActive) {
                val result = runCatching {
                    repo.streamEvents { event ->
                        onEvent(event)
                    }
                }
                if (result.isSuccess) return@launch
                delay(1_000)
            }
        }
    }

    private fun onEvent(event: GlobalStreamEvent) {
        if (event.type == "message.updated") {
            val info = event.properties["info"]?.jsonObject ?: return
            val sessionId = info["sessionID"]?.jsonPrimitive?.contentOrNull ?: return
            val id = info["id"]?.jsonPrimitive?.contentOrNull ?: return
            val messageRole = info["role"]?.jsonPrimitive?.contentOrNull ?: "assistant"
            val key = keyForSession(sessionId) ?: return
            val message = messageKey(key, id)
            if (messageRole == "user") {
                pending[key]?.let {
                    if (it.isNotEmpty()) it.removeAt(0)
                }
            }
            role[message] = messageRole
            if (sort[message] == null) {
                sort[message] = sequence()
            }
            persistMessage(
                key,
                sessionId,
                id,
                messageRole,
                renderMessage(message),
                sort[message] ?: sequence(),
            )
            scheduleSync(sessionId)
            return
        }
        if (event.type != "message.part.updated") return
        val payload = event.properties["part"]?.jsonObject ?: return
        val sessionId = payload["sessionID"]?.jsonPrimitive?.contentOrNull ?: return
        val messageId = payload["messageID"]?.jsonPrimitive?.contentOrNull ?: return
        val partId = payload["id"]?.jsonPrimitive?.contentOrNull ?: return
        val type = payload["type"]?.jsonPrimitive?.contentOrNull ?: return
        if (type != "text") return
        val text = payload["text"]?.jsonPrimitive?.contentOrNull ?: ""
        val key = keyForSession(sessionId) ?: return
        val message = messageKey(key, messageId)
        val parts = part.getOrPut(message) { linkedMapOf() }
        parts[partId] = text
        if (sort[message] == null) {
            sort[message] = sequence()
        }
        persistMessage(
            key,
            sessionId,
            messageId,
            role[message] ?: "assistant",
            renderMessage(message),
            sort[message] ?: sequence(),
        )
        scheduleSync(sessionId)
    }

    private fun scheduleSync(sessionId: String) {
        val scope = scope ?: return
        sync[sessionId]?.cancel()
        sync[sessionId] = scope.launch {
            repeat(6) {
                delay(if (it == 0) 300 else 900)
                val entry = active.values.find { value -> value.id == sessionId } ?: return@launch
                hydrateRemote(entry)
            }
        }
    }

    private fun renderMessage(message: String): String {
        val text = part[message]
            ?.values
            ?.filter { it.isNotBlank() }
            ?.joinToString("\n")
        if (text == null || text.isBlank()) return "(streaming...)"
        return text
    }

    private fun persistMessage(key: String, sessionId: String, messageId: String, role: String, text: String, sort: String) {
        val server = key.substringBefore("::")
        db.appDatabaseQueries.upsertMessageCache(
            server,
            sessionId,
            messageId,
            role,
            text,
            sort,
            System.currentTimeMillis(),
        )
    }

    private fun observeFocused() {
        observe?.cancel()
        val key = focusedKey ?: return
        val server = key.substringBefore("::")
        val session = key.substringAfter("::")
        observe = (scope ?: return).launch {
            db.appDatabaseQueries
                .listMessageCache(server, session) { _, _, messageId, role, text, _, _ ->
                    MessageState(
                        id = messageId,
                        role = role,
                        text = text,
                    )
                }
                .asFlow()
                .mapToList(Dispatchers.IO)
                .collect {
                    val list = pending[key]
                    val merged = if (list.isNullOrEmpty()) it else it + list
                    local.value = local.value.copy(focusedMessages = merged)
                }
        }
    }

    private fun keyForSession(sessionId: String): String? {
        return active.keys.find { it.endsWith("::$sessionId") }
    }

    private fun messageKey(key: String, messageId: String): String {
        return "$key::$messageId"
    }

    private fun sequence(): String {
        return System.currentTimeMillis().toString().padStart(20, '0')
    }

    private fun key(server: String, sessionId: String): String {
        return "$server::$sessionId"
    }

    fun stop() {
        stream?.cancel()
        stream = null
        observe?.cancel()
        observe = null
        sync.values.forEach { it.cancel() }
        sync.clear()
    }
}
