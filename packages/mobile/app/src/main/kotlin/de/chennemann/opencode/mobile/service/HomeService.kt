package de.chennemann.opencode.mobile.service

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

class HomeService(
    private val repo: ServerRepository,
    private val db: AppDatabase,
) {
    private data class Runtime(
        val session: SessionState,
        val project: String?,
        val role: LinkedHashMap<String, String>,
        val part: LinkedHashMap<String, LinkedHashMap<String, String>>,
    )

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

    private val active = linkedMapOf<String, Runtime>()
    private var manual = false
    private var stream: Job? = null
    private var scope: CoroutineScope? = null
    private var started = false

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

    fun focusSession(sessionId: String) {
        val runtime = active.values.find { it.session.id == sessionId } ?: return
        local.value = local.value.copy(
            focusedSession = runtime.session,
            focusedMessages = render(runtime),
        )
    }

    private fun focusSession(session: SessionState, project: String?) {
        val scope = scope ?: return
        val server = repo.endpoint.value
        val key = key(server, session.id)
        val runtime = Runtime(
            session = session,
            project = project,
            role = linkedMapOf(),
            part = linkedMapOf(),
        )
        active[key] = runtime
        local.value = local.value.copy(
            focusedSession = session,
            focusedMessages = emptyList(),
            activeSessions = active.values.map { it.session }.sortedByDescending { it.id },
            managementOpen = false,
        )
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
        scope.launch {
            val cached = db.appDatabaseQueries.listMessageCache(server, session.id) { _, _, messageId, role, text, _, _ ->
                MessageState(
                    id = messageId,
                    role = role,
                    text = text,
                )
            }.executeAsList()
            if (local.value.focusedSession?.id == session.id && cached.isNotEmpty()) {
                local.value = local.value.copy(focusedMessages = cached)
            }
            hydrateRemote(session)
        }
    }

    private fun hydrateRemote(session: SessionState) {
        val scope = scope ?: return
        scope.launch {
            local.value = local.value.copy(loadingMessages = true, message = null)
            val result = runCatching { repo.messages(session.id, session.directory) }
            local.value = local.value.copy(loadingMessages = false)
            result.onSuccess { list ->
                val server = repo.endpoint.value
                val runtime = active[key(server, session.id)] ?: return@onSuccess
                runtime.role.clear()
                runtime.part.clear()
                list.forEach {
                    runtime.role[it.id] = it.role
                    runtime.part[it.id] = linkedMapOf("seed" to it.text)
                    db.appDatabaseQueries.upsertMessageCache(
                        server,
                        session.id,
                        it.id,
                        it.role,
                        it.text,
                        it.id,
                        System.currentTimeMillis(),
                    )
                }
                if (local.value.focusedSession?.id != session.id) return@onSuccess
                local.value = local.value.copy(focusedMessages = render(runtime))
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
            val role = info["role"]?.jsonPrimitive?.contentOrNull ?: "assistant"
            active.forEach { (key, runtime) ->
                if (runtime.session.id != sessionId) return@forEach
                if (event.directory != runtime.session.directory && event.directory != "global") return@forEach
                runtime.role[id] = role
                if (runtime.part[id] == null) {
                    runtime.part[id] = linkedMapOf()
                }
                val text = renderMessage(runtime, id)
                persistMessage(key, sessionId, id, role, text)
                if (local.value.focusedSession?.id == sessionId) {
                    local.value = local.value.copy(focusedMessages = render(runtime))
                }
            }
            return
        }
        if (event.type != "message.part.updated") return
        val part = event.properties["part"]?.jsonObject ?: return
        val sessionId = part["sessionID"]?.jsonPrimitive?.contentOrNull ?: return
        val messageId = part["messageID"]?.jsonPrimitive?.contentOrNull ?: return
        val partId = part["id"]?.jsonPrimitive?.contentOrNull ?: return
        val type = part["type"]?.jsonPrimitive?.contentOrNull ?: return
        if (type != "text") return
        val text = part["text"]?.jsonPrimitive?.contentOrNull ?: ""
        active.forEach { (key, runtime) ->
            if (runtime.session.id != sessionId) return@forEach
            if (event.directory != runtime.session.directory && event.directory != "global") return@forEach
            val parts = runtime.part.getOrPut(messageId) { linkedMapOf() }
            parts[partId] = text
            val value = renderMessage(runtime, messageId)
            persistMessage(key, sessionId, messageId, runtime.role[messageId] ?: "assistant", value)
            if (local.value.focusedSession?.id != sessionId) return@forEach
            local.value = local.value.copy(focusedMessages = render(runtime))
        }
    }

    private fun render(runtime: Runtime): List<MessageState> {
        return runtime.part
            .map { entry ->
                MessageState(
                    id = entry.key,
                    role = runtime.role[entry.key] ?: "assistant",
                    text = renderMessage(runtime, entry.key),
                )
            }
            .sortedBy { it.id }
    }

    private fun renderMessage(runtime: Runtime, messageId: String): String {
        val text = runtime.part[messageId]
            ?.values
            ?.filter { it.isNotBlank() }
            ?.joinToString("\n")
        if (text == null || text.isBlank()) return "(streaming...)"
        return text
    }

    private fun persistMessage(key: String, sessionId: String, messageId: String, role: String, text: String) {
        val server = key.substringBefore("::")
        db.appDatabaseQueries.upsertMessageCache(
            server,
            sessionId,
            messageId,
            role,
            text,
            messageId,
            System.currentTimeMillis(),
        )
    }

    private fun key(server: String, sessionId: String): String {
        return "$server::$sessionId"
    }

    fun stop() {
        stream?.cancel()
        stream = null
    }
}
