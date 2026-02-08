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
        val canLoadMoreMessages: Boolean = false,
        val loadingMoreMessages: Boolean = false,
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
            canLoadMoreMessages = false,
            loadingMoreMessages = false,
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
    private val pending = linkedMapOf<String, MutableList<MessageState>>()
    private val pendingPass = linkedMapOf<String, MutableMap<String, Int>>()
    private val retainPass = linkedMapOf<String, MutableMap<String, Int>>()
    private val messageLimit = linkedMapOf<String, Int>()
    private var manual = false
    private var stream: Job? = null
    private var observe: Job? = null
    private var reconcile: Job? = null
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
                    canLoadMoreMessages = local.canLoadMoreMessages,
                    loadingMoreMessages = local.loadingMoreMessages,
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
        reconcile(scope)
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
        val id = "local-${System.currentTimeMillis()}"
        pending.getOrPut(key) { mutableListOf() }.add(
            MessageState(
                id = id,
                role = "user",
                text = value,
            )
        )
        pendingPass.getOrPut(key) { linkedMapOf() }[id] = OptimisticKeepPasses
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
                pendingPass[key]?.remove(id)
                if (focusedKey == key) observeFocused()
                local.value = local.value.copy(message = it.message ?: "Failed to send message")
            }
        }
    }

    fun loadMoreMessages() {
        val focused = local.value.focusedSession ?: return
        val key = focusedKey ?: keyForSession(focused.id) ?: return
        val limit = messageLimit[key] ?: MessageSyncLimit
        messageLimit[key] = limit + MessageSyncLimit
        syncRemote(focused, more = true)
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
        messageLimit[key] = messageLimit[key] ?: MessageSyncLimit
        focusedKey = key
        local.value = local.value.copy(
            focusedSession = session,
            activeSessions = active.values.sortedByDescending { it.id },
            canLoadMoreMessages = false,
            loadingMoreMessages = false,
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
        syncRemote(session, true)
    }

    private fun syncRemote(session: SessionState, loading: Boolean = false, more: Boolean = false) {
        val scope = scope ?: return
        scope.launch {
            if (loading) {
                local.value = local.value.copy(loadingMessages = true, message = null)
            }
            if (more) {
                local.value = local.value.copy(loadingMoreMessages = true, message = null)
            }
            val key = keyForSession(session.id)
            val limit = key?.let { messageLimit[it] } ?: MessageSyncLimit
            val result = runCatching { repo.messages(session.id, session.directory, limit) }
            if (loading) {
                local.value = local.value.copy(loadingMessages = false)
            }
            if (more) {
                local.value = local.value.copy(loadingMoreMessages = false)
            }
            result.onSuccess { list ->
                val key = keyForSession(session.id) ?: return@onSuccess
                val server = key.substringBefore("::")
                val now = System.currentTimeMillis()
                val next = list
                    .sortedBy { it.id }
                val complete = next.size < (messageLimit[key] ?: MessageSyncLimit)
                val nextIds = next
                    .map { it.id }
                    .toHashSet()

                next.forEach {
                    val message = messageKey(key, it.id)
                    role[message] = it.role
                    db.appDatabaseQueries.upsertMessageCache(
                        server,
                        session.id,
                        it.id,
                        it.role,
                        it.text,
                        it.id,
                        now,
                    )
                }

                val cached = db.appDatabaseQueries
                    .listMessageCache(server, session.id) { _, _, messageId, _, _, _, _ -> messageId }
                    .executeAsList()

                if (complete) {
                    cached.forEach { id ->
                        if (nextIds.contains(id)) return@forEach
                        if (keepForPass(retainPass, key, id)) return@forEach
                        db.appDatabaseQueries.deleteMessageCache(server, session.id, id)
                        role.remove(messageKey(key, id))
                        part.remove(messageKey(key, id))
                    }
                }

                trimPending(key)
                if (focusedKey == key) {
                    local.value = local.value.copy(canLoadMoreMessages = !complete)
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
            var retryDelay = StreamRetryDefaultMs
            var attempt = 0
            var cursor = runCatching { repo.streamCursor() }.getOrNull()
            while (isActive) {
                val result = runCatching {
                    repo.streamEvents(cursor) { event ->
                        if (!event.id.isNullOrBlank()) {
                            cursor = event.id
                            runCatching { repo.setStreamCursor(cursor) }
                        }
                        if (event.retry != null) {
                            retryDelay = event.retry.coerceAtLeast(StreamRetryMinMs)
                        }
                        onEvent(event)
                    }
                }
                if (result.isSuccess) {
                    attempt = 0
                    retryDelay = StreamRetryDefaultMs
                    continue
                }
                attempt += 1
                val backoff = (retryDelay * (1 shl (attempt - 1).coerceAtMost(8)))
                    .coerceAtMost(StreamRetryMaxMs)
                    .coerceAtLeast(StreamRetryMinMs)
                delay(backoff.toLong())
            }
        }
    }

    private fun reconcile(scope: CoroutineScope) {
        reconcile?.cancel()
        reconcile = scope.launch {
            while (isActive) {
                delay(ReconcileIntervalMs)
                val focused = local.value.focusedSession ?: continue
                syncRemote(focused)
            }
        }
    }

    private fun onEvent(event: GlobalStreamEvent) {
        when (event.type) {
            "server.instance.disposed", "global.disposed" -> {
                loadProjects()
                local.value.focusedSession?.let { syncRemote(it) }
            }

            "session.created", "session.updated", "session.deleted" -> {
                val selected = local.value.selectedProject
                if (!selected.isNullOrBlank() && event.directory == selected) {
                    loadSessions(selected)
                }
                if (event.type == "session.deleted") {
                    val sessionId = event.properties["info"]
                        ?.jsonObject
                        ?.get("id")
                        ?.jsonPrimitive
                        ?.contentOrNull
                        ?: return
                    removeSession(sessionId)
                }
            }

            "message.updated" -> {
                val info = event.properties["info"]?.jsonObject ?: return
                val sessionId = info["sessionID"]?.jsonPrimitive?.contentOrNull ?: return
                val id = info["id"]?.jsonPrimitive?.contentOrNull ?: return
                val messageRole = info["role"]?.jsonPrimitive?.contentOrNull ?: "assistant"
                val key = keyForSession(sessionId) ?: return
                val message = messageKey(key, id)

                if (messageRole == "user") {
                    pending[key]?.let {
                        if (it.isNotEmpty()) {
                            val removed = it.removeAt(0)
                            pendingPass[key]?.remove(removed.id)
                        }
                    }
                }

                role[message] = messageRole
                retainPass.getOrPut(key) { linkedMapOf() }[id] = ReconcileKeepPasses
                persistMessage(
                    key,
                    sessionId,
                    id,
                    messageRole,
                    renderMessage(message),
                    id,
                )
                scheduleSync(sessionId)
            }

            "message.removed" -> {
                val sessionId = event.properties["sessionID"]?.jsonPrimitive?.contentOrNull ?: return
                val messageId = event.properties["messageID"]?.jsonPrimitive?.contentOrNull ?: return
                val key = keyForSession(sessionId) ?: return
                val server = key.substringBefore("::")
                db.appDatabaseQueries.deleteMessageCache(server, sessionId, messageId)
                role.remove(messageKey(key, messageId))
                part.remove(messageKey(key, messageId))
            }

            "message.part.updated" -> {
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
                retainPass.getOrPut(key) { linkedMapOf() }[messageId] = ReconcileKeepPasses
                persistMessage(
                    key,
                    sessionId,
                    messageId,
                    role[message] ?: "assistant",
                    renderMessage(message),
                    messageId,
                )
                scheduleSync(sessionId)
            }

            "message.part.removed" -> {
                val messageId = event.properties["messageID"]?.jsonPrimitive?.contentOrNull ?: return
                val partId = event.properties["partID"]?.jsonPrimitive?.contentOrNull ?: return
                val entry = active.entries.find { messageKey(it.key, messageId).let(part::containsKey) } ?: return
                val key = entry.key
                val message = messageKey(key, messageId)
                val parts = part[message] ?: return
                parts.remove(partId)
                if (parts.isEmpty()) {
                    part.remove(message)
                }
                val sessionId = key.substringAfter("::")
                persistMessage(
                    key,
                    sessionId,
                    messageId,
                    role[message] ?: "assistant",
                    renderMessage(message),
                    messageId,
                )
                scheduleSync(sessionId)
            }

            "session.status" -> {
                val sessionId = event.properties["sessionID"]?.jsonPrimitive?.contentOrNull ?: return
                scheduleSync(sessionId)
            }
        }
    }

    private fun scheduleSync(sessionId: String) {
        val scope = scope ?: return
        sync[sessionId]?.cancel()
        sync[sessionId] = scope.launch {
            repeat(6) {
                delay(if (it == 0) 300 else 900)
                val entry = active.values.find { value -> value.id == sessionId } ?: return@launch
                syncRemote(entry)
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

    private fun keepForPass(map: MutableMap<String, MutableMap<String, Int>>, key: String, id: String): Boolean {
        val passes = map[key] ?: return false
        val value = passes[id] ?: return false
        if (value <= 0) {
            passes.remove(id)
            if (passes.isEmpty()) map.remove(key)
            return false
        }
        passes[id] = value - 1
        if (passes[id] == 0) {
            passes.remove(id)
        }
        if (passes.isEmpty()) {
            map.remove(key)
        }
        return true
    }

    private fun trimPending(key: String) {
        val list = pending[key] ?: return
        if (list.isEmpty()) return

        val filtered = list.filter { keepForPass(pendingPass, key, it.id) }
        if (filtered.isEmpty()) {
            pending.remove(key)
            pendingPass.remove(key)
            if (focusedKey == key) {
                observeFocused()
            }
            return
        }

        if (filtered.size != list.size) {
            pending[key] = filtered.toMutableList()
            if (focusedKey == key) {
                observeFocused()
            }
        }
    }

    private fun removeSession(sessionId: String) {
        val entry = active.entries.find { it.value.id == sessionId } ?: return
        val key = entry.key
        val server = key.substringBefore("::")
        active.remove(key)
        sessionProject.remove(key)
        pending.remove(key)
        pendingPass.remove(key)
        retainPass.remove(key)
        messageLimit.remove(key)
        db.appDatabaseQueries.deleteMessageCacheSession(server, sessionId)
        if (focusedKey == key) {
            focusedKey = active.keys.firstOrNull()
            local.value = local.value.copy(
                focusedSession = focusedKey?.let(active::get),
                activeSessions = active.values.sortedByDescending { it.id },
                canLoadMoreMessages = false,
                loadingMoreMessages = false,
            )
            observeFocused()
            return
        }
        local.value = local.value.copy(activeSessions = active.values.sortedByDescending { it.id })
    }

    private fun key(server: String, sessionId: String): String {
        return "$server::$sessionId"
    }

    fun stop() {
        stream?.cancel()
        stream = null
        reconcile?.cancel()
        reconcile = null
        observe?.cancel()
        observe = null
        sync.values.forEach { it.cancel() }
        sync.clear()
    }
}

private const val StreamRetryDefaultMs = 3000
private const val StreamRetryMinMs = 1000
private const val StreamRetryMaxMs = 30000
private const val MessageSyncLimit = 400
private const val ReconcileIntervalMs = 10000L
private const val ReconcileKeepPasses = 1
private const val OptimisticKeepPasses = 1
