package de.chennemann.opencode.mobile.service

import android.util.Log
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import de.chennemann.opencode.mobile.data.GlobalStreamEvent
import de.chennemann.opencode.mobile.data.NetworkService
import de.chennemann.opencode.mobile.data.ServerRepository
import de.chennemann.opencode.mobile.db.AppDatabase
import de.chennemann.opencode.mobile.home.HomeState
import de.chennemann.opencode.mobile.home.MessageState
import de.chennemann.opencode.mobile.home.ProjectState
import de.chennemann.opencode.mobile.home.ServerState
import de.chennemann.opencode.mobile.home.SessionState
import de.chennemann.opencode.mobile.home.DebugState
import de.chennemann.opencode.mobile.home.ToolCallState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.JsonObject
import java.util.concurrent.atomic.AtomicLong

class SessionService(
    private val repo: ServerRepository,
    private val db: AppDatabase,
    private val network: NetworkService,
) {
    private data class PartState(
        val id: String,
        val type: String,
        val text: String,
        val tool: String? = null,
        val status: String? = null,
        val title: String? = null,
        val output: String? = null,
    )

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
        val message: String? = null,
    )

    private val input = MutableStateFlow(repo.endpoint.value)
    private val local = MutableStateFlow(LocalState())
    private val debug = MutableStateFlow(DebugState())
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
            message = null,
            debug = DebugState(),
        )
    )

    private val active = linkedMapOf<String, SessionState>()
    private val sessionProject = linkedMapOf<String, String?>()
    private val part = linkedMapOf<String, LinkedHashMap<String, PartState>>()
    private val role = linkedMapOf<String, String>()
    private val pending = linkedMapOf<String, MutableList<MessageState>>()
    private val pendingPass = linkedMapOf<String, MutableMap<String, Int>>()
    private val retainPass = linkedMapOf<String, MutableMap<String, Int>>()
    private val stickySort = linkedMapOf<String, MutableMap<String, String>>()
    private val order = linkedMapOf<String, String>()
    private val messageLimit = linkedMapOf<String, Int>()
    private val seq = AtomicLong(System.currentTimeMillis() * 1000)
    private var manual = false
    private var stream: Job? = null
    private var observe: Job? = null
    private var reconcile: Job? = null
    private val sync = linkedMapOf<String, Job>()
    private val syncActive = linkedSetOf<String>()
    private val syncGuard = Mutex()
    private val flush = linkedMapOf<String, Job>()
    private val sessionResolve = linkedMapOf<String, Long>()
    private val sseLog = ArrayDeque<String>()
    private val overlay = linkedMapOf<String, MessageState>()
    private val overlayDirty = linkedMapOf<String, LinkedHashSet<String>>()
    private var focusedDb = emptyList<MessageState>()
    private var sseSeen = 0
    private var sseRaw = 0
    private var sseApplied = 0
    private var sseDropped = 0
    private var sseConnected = 0
    private var sseErrors = 0
    private var syncRuns = 0
    private var syncFails = 0
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
            combine(input, repo.found, repo.status, local, debug) { url, discovered, status, local, debug ->
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
                    message = local.message,
                    debug = debug,
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
        val sort = sequence()
        pending.getOrPut(key) { mutableListOf() }.add(
            MessageState(
                id = id,
                role = "user",
                text = value,
                sort = sort,
            )
        )
        pendingPass.getOrPut(key) { linkedMapOf() }[id] = OptimisticKeepPasses
        local.value = local.value.copy(
            focusedMessages = local.value.focusedMessages + MessageState(
                id = id,
                role = "user",
                text = value,
                sort = sort,
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
        val previous = focusedKey
        if (previous != null && previous != key) {
            flush.remove(previous)?.cancel()
            val sessionId = previous.substringAfter("::")
            scope?.launch {
                flushStaged(previous, sessionId)
            }
        }
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
        syncRemote(session)
    }

    private fun syncRemote(session: SessionState, more: Boolean = false) {
        if (repo.status.value !is ServerState.Connected) return
        val scope = scope ?: return
        scope.launch(Dispatchers.Default) {
            if (!beginSync(session.id)) return@launch
            try {
            val started = System.currentTimeMillis()
            syncRuns += 1
            debug.value = debug.value.copy(syncRuns = syncRuns)
            if (more) {
                local.value = local.value.copy(loadingMoreMessages = true, message = null)
            }
            val key = keyForSession(session.id)
            val limit = key?.let { messageLimit[it] } ?: MessageSyncLimit
            val result = runCatching { repo.messages(session.id, session.directory, limit) }
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
                val cached = withContext(Dispatchers.IO) {
                    db.appDatabaseQueries
                        .listMessageCache(server, session.id) { _, _, messageId, messageRole, messageText, sortKey, _ ->
                            MessageState(
                                id = messageId,
                                role = messageRole,
                                text = messageText,
                                sort = sortKey,
                            )
                        }
                        .executeAsList()
                }
                val cachedMap = cached.associateBy { it.id }
                val sticky = stickySort[key]
                var claimed = false
                val upserts = mutableListOf<Array<String>>()

                next.forEach {
                    val id = requireNotNull(it.id)
                    val message = messageKey(key, id)
                    val messageRole = it.role
                    val parsed = parseParts(it.parts)
                    if (parsed.isEmpty()) {
                        part.remove(message)
                    } else {
                        val map = linkedMapOf<String, PartState>()
                        parsed.forEach { item ->
                            map[item.id] = item
                        }
                        part[message] = map
                    }
                    val messageText = if (messageRole == "assistant") {
                        renderMessage(message)
                    } else {
                        it.text
                    }
                    role[message] = messageRole
                    val cachedMessage = cachedMap[id]
                    val known = cachedMessage?.sort ?: order[message]
                    val stickySort = sticky?.remove(id)
                    val pendingSort = if (messageRole == "user") claimPendingSort(key, messageText) else null
                    val sort = when {
                        stickySort != null -> stickySort
                        known != null -> known
                        pendingSort != null -> pendingSort
                        else -> sequence()
                    }
                    if (messageRole == "user" && known == null) {
                        if (sort.startsWith("z-")) {
                            claimed = true
                        }
                    }
                    order[message] = sort
                    if (cachedMessage == null || cachedMessage.role != messageRole || cachedMessage.text != messageText || cachedMessage.sort != sort) {
                        upserts.add(arrayOf(id, messageRole, messageText, sort))
                    }
                }

                withContext(Dispatchers.IO) {
                    upserts.forEach {
                        db.appDatabaseQueries.upsertMessageCache(
                            server,
                            session.id,
                            it[0],
                            it[1],
                            it[2],
                            it[3],
                            now,
                        )
                    }
                }

                if (complete) {
                    val removed = mutableListOf<String>()
                    cachedMap.keys.forEach { id ->
                        if (nextIds.contains(id)) return@forEach
                        if (keepForPass(retainPass, key, id)) return@forEach
                        removed.add(id)
                        role.remove(messageKey(key, id))
                        part.remove(messageKey(key, id))
                        order.remove(messageKey(key, id))
                    }
                    withContext(Dispatchers.IO) {
                        removed.forEach {
                            db.appDatabaseQueries.deleteMessageCache(server, session.id, it)
                        }
                    }
                }

                clearOverlay(key)
                trimPending(key)
                if (focusedKey == key) {
                    if (claimed) {
                        observeFocused()
                    }
                    local.value = local.value.copy(canLoadMoreMessages = !complete)
                }
                if (syncRuns % 10 == 0) {
                    Log.d(LogTag, "sync ok session=${session.id} messages=${next.size} dt=${System.currentTimeMillis() - started}ms")
                }
            }
            result.onFailure {
                syncFails += 1
                debug.value = debug.value.copy(syncFails = syncFails)
                Log.w(LogTag, "sync failed session=${session.id} reason=${it.message}")
                local.value = local.value.copy(message = it.message ?: "Failed to load messages")
            }
            } finally {
                endSync(session.id)
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
            var attempt = 0
            var cursor = runCatching { repo.streamCursor() }.getOrNull()
            while (isActive) {
                val endpoint = repo.endpoint.value
                Log.d(LogTag, "sse connect attempt=${attempt + 1} endpoint=$endpoint cursor=$cursor")
                pushSseLog("connect attempt=${attempt + 1} endpoint=$endpoint cursor=$cursor")
                val result = runCatching {
                    sseConnected += 1
                    debug.value = debug.value.copy(sseConnected = sseConnected, lastStreamError = null)
                    repo.streamEvents(cursor, { chunk ->
                        sseRaw += 1
                        debug.value = debug.value.copy(sseRaw = sseRaw)
                        pushSseLog("raw ${chunk.replace("\n", "\\n")}")
                    }) { event ->
                        Log.d(LogTag, "sse event type=${event.type} dir=${event.directory} id=${event.id}")
                        pushSseLog(
                            "event type=${event.type} dir=${event.directory} id=${event.id} retry=${event.retry} properties=${event.properties}",
                        )
                        if (!event.id.isNullOrBlank()) {
                            cursor = event.id
                            runCatching { repo.setStreamCursor(cursor) }
                        }
                        onEvent(event)
                    }
                }
                if (result.isSuccess) {
                    Log.d(LogTag, "sse stream ended normally reconnecting")
                    pushSseLog("stream ended; reconnecting")
                    attempt = 0
                    continue
                }
                sseErrors += 1
                val reason = result.exceptionOrNull()?.message ?: "unknown stream error"
                debug.value = debug.value.copy(sseErrors = sseErrors, lastStreamError = reason)
                Log.w(LogTag, "sse stream error attempt=${attempt + 1} reason=$reason")
                pushSseLog("error attempt=${attempt + 1} reason=$reason")
                attempt += 1
                val seen = network.changed.value
                if (!network.online.value) {
                    pushSseLog("offline; waiting for network change")
                } else {
                    pushSseLog("waiting for network change before reconnect")
                }
                network.changed.first { it > seen }
                pushSseLog("network changed; retrying stream")
                delay(StreamRestartDelayMs)
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

    private suspend fun onEvent(event: GlobalStreamEvent) {
        sseSeen += 1
        debug.value = debug.value.copy(sseSeen = sseSeen)
        when (event.type) {
            "server.connected", "server.heartbeat" -> {
                pushSseLog("ignore type=${event.type}")
                return
            }

            "server.instance.disposed", "global.disposed" -> {
                markSseApplied(event.type, null)
                loadProjects()
                local.value.focusedSession?.let { syncRemote(it) }
            }

            "session.created", "session.updated", "session.deleted" -> {
                markSseApplied(event.type, null)
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
                val info = event.properties["info"]?.jsonObject ?: event.properties
                val sessionId = info["sessionID"]?.jsonPrimitive?.contentOrNull
                if (sessionId.isNullOrBlank()) {
                    markSseDropped(event.type, "missing sessionID")
                    return
                }
                val id = info["id"]?.jsonPrimitive?.contentOrNull
                if (id.isNullOrBlank()) {
                    markSseDropped(event.type, "missing message id")
                    return
                }
                val messageRole = info["role"]?.jsonPrimitive?.contentOrNull ?: "assistant"
                val messageText = info["text"]?.jsonPrimitive?.contentOrNull?.trim()
                val key = keyForSession(sessionId)
                if (key == null) {
                    markSseDropped(event.type, "session not focused/active id=$sessionId")
                    resolveSession(sessionId, event.directory)
                    return
                }
                val message = messageKey(key, id)

                if (messageRole == "user") {
                    pending[key]?.let {
                        if (it.isNotEmpty()) {
                            val index = if (messageText.isNullOrBlank()) {
                                0
                            } else {
                                it.indexOfFirst { pending -> pending.text.trim() == messageText }
                                    .let { found -> if (found >= 0) found else 0 }
                            }
                            val removed = it.removeAt(index)
                            pendingPass[key]?.remove(removed.id)
                            stickySort.getOrPut(key) { linkedMapOf() }[id] = removed.sort
                        }
                    }
                }

                role[message] = messageRole
                retainPass.getOrPut(key) { linkedMapOf() }[id] = ReconcileKeepPasses
                val sort = stickySort[key]?.remove(id) ?: order[message] ?: sequence()
                stageMessage(
                    key,
                    sessionId,
                    id,
                    messageRole,
                    if (!messageText.isNullOrBlank()) messageText else renderMessage(message),
                    sort,
                )
                markSseApplied(event.type, sessionId)
                scheduleSync(sessionId, true)
            }

            "message.removed" -> {
                val sessionId = event.properties["sessionID"]?.jsonPrimitive?.contentOrNull
                if (sessionId.isNullOrBlank()) {
                    markSseDropped(event.type, "missing sessionID")
                    return
                }
                val messageId = event.properties["messageID"]?.jsonPrimitive?.contentOrNull
                if (messageId.isNullOrBlank()) {
                    markSseDropped(event.type, "missing messageID")
                    return
                }
                val key = keyForSession(sessionId)
                if (key == null) {
                    markSseDropped(event.type, "session not focused/active id=$sessionId")
                    resolveSession(sessionId, event.directory)
                    return
                }
                val server = key.substringBefore("::")
                withContext(Dispatchers.IO) {
                    db.appDatabaseQueries.deleteMessageCache(server, sessionId, messageId)
                }
                role.remove(messageKey(key, messageId))
                part.remove(messageKey(key, messageId))
                order.remove(messageKey(key, messageId))
                overlay.remove(messageKey(key, messageId))
                overlayDirty[key]?.remove(messageKey(key, messageId))
                if (focusedKey == key) {
                    publishFocused(key)
                }
                markSseApplied(event.type, sessionId)
            }

            "message.part.updated" -> {
                val payload = event.properties["part"]?.jsonObject ?: event.properties
                val sessionId = payload["sessionID"]?.jsonPrimitive?.contentOrNull
                if (sessionId.isNullOrBlank()) {
                    markSseDropped(event.type, "missing sessionID")
                    return
                }
                val messageId = payload["messageID"]?.jsonPrimitive?.contentOrNull
                if (messageId.isNullOrBlank()) {
                    markSseDropped(event.type, "missing messageID")
                    return
                }
                val partId = payload["id"]?.jsonPrimitive?.contentOrNull
                if (partId.isNullOrBlank()) {
                    markSseDropped(event.type, "missing part id")
                    return
                }
                val next = parsePart(payload)
                if (next == null) {
                    markSseDropped(event.type, "missing part type")
                    return
                }
                val key = keyForSession(sessionId)
                if (key == null) {
                    markSseDropped(event.type, "session not focused/active id=$sessionId")
                    resolveSession(sessionId, event.directory)
                    return
                }
                val message = messageKey(key, messageId)
                val parts = part.getOrPut(message) { linkedMapOf() }
                parts[next.id] = next
                retainPass.getOrPut(key) { linkedMapOf() }[messageId] = ReconcileKeepPasses
                val sort = order[message] ?: sequence()
                stageMessage(
                    key,
                    sessionId,
                    messageId,
                    role[message] ?: "assistant",
                    renderMessage(message),
                    sort,
                )
                markSseApplied(event.type, sessionId)
                scheduleSync(sessionId, true)
            }

            "message.part.removed" -> {
                val messageId = event.properties["messageID"]?.jsonPrimitive?.contentOrNull
                if (messageId.isNullOrBlank()) {
                    markSseDropped(event.type, "missing messageID")
                    return
                }
                val partId = event.properties["partID"]?.jsonPrimitive?.contentOrNull
                if (partId.isNullOrBlank()) {
                    markSseDropped(event.type, "missing partID")
                    return
                }
                val entry = active.entries.find { messageKey(it.key, messageId).let(part::containsKey) }
                if (entry == null) {
                    markSseDropped(event.type, "message not in active cache")
                    return
                }
                val key = entry.key
                val message = messageKey(key, messageId)
                val parts = part[message] ?: return
                parts.remove(partId)
                if (parts.isEmpty()) {
                    part.remove(message)
                }
                val sessionId = key.substringAfter("::")
                val sort = order[message] ?: sequence()
                stageMessage(
                    key,
                    sessionId,
                    messageId,
                    role[message] ?: "assistant",
                    renderMessage(message),
                    sort,
                )
                markSseApplied(event.type, sessionId)
                scheduleSync(sessionId, true)
            }

            "session.status" -> {
                val sessionId = event.properties["sessionID"]?.jsonPrimitive?.contentOrNull
                if (sessionId.isNullOrBlank()) {
                    markSseDropped(event.type, "missing sessionID")
                    return
                }
                if (keyForSession(sessionId) == null) {
                    resolveSession(sessionId, event.directory)
                }
                markSseApplied(event.type, sessionId)
                scheduleSync(sessionId)
            }

            "session.diff" -> {
                val sessionId = event.properties["sessionID"]?.jsonPrimitive?.contentOrNull
                if (sessionId.isNullOrBlank()) {
                    markSseDropped(event.type, "missing sessionID")
                    return
                }
                if (keyForSession(sessionId) == null) {
                    resolveSession(sessionId, event.directory)
                }
                markSseApplied(event.type, sessionId)
            }

            else -> markSseDropped(event.type, "unhandled event")
        }
    }

    private fun scheduleSync(sessionId: String, burst: Boolean = false) {
        val scope = scope ?: return
        sync[sessionId]?.cancel()
        sync[sessionId] = scope.launch {
            delay(if (burst) SyncBurstDelayMs else SyncDelayMs)
            val entry = active.values.find { value -> value.id == sessionId } ?: return@launch
            syncRemote(entry)
        }
    }

    private fun resolveSession(sessionId: String, directory: String?) {
        val scope = scope ?: return
        val now = System.currentTimeMillis()
        val seen = sessionResolve[sessionId]
        if (seen != null && now - seen < SessionResolveCooldownMs) return
        sessionResolve[sessionId] = now
        scope.launch {
            val worktree = if (!directory.isNullOrBlank() && directory != "global") {
                directory
            } else {
                local.value.selectedProject
            }
            if (worktree.isNullOrBlank()) return@launch
            val result = runCatching { repo.sessions(worktree) }
            result.onFailure {
                pushSseLog("resolve session failed id=$sessionId reason=${it.message}")
            }
            val found = result.getOrNull()
                ?.firstOrNull { it.id == sessionId }
                ?: return@launch
            val session = SessionState(
                id = found.id,
                title = found.title,
                version = found.version,
                directory = found.directory,
            )
            pushSseLog("resolve session id=$sessionId switch=${session.title}")
            focusSession(session, worktree)
            scheduleSync(sessionId)
        }
    }

    private fun parseParts(parts: List<JsonObject>): List<PartState> {
        return parts.mapNotNull(::parsePart)
    }

    private fun parsePart(part: JsonObject): PartState? {
        val id = part["id"]?.jsonPrimitive?.contentOrNull ?: return null
        val type = part["type"]?.jsonPrimitive?.contentOrNull ?: return null
        if (type == "text") {
            return PartState(
                id = id,
                type = type,
                text = part["text"]?.jsonPrimitive?.contentOrNull ?: "",
            )
        }
        if (type == "reasoning") {
            return PartState(
                id = id,
                type = type,
                text = part["text"]?.jsonPrimitive?.contentOrNull ?: "",
            )
        }
        if (type == "tool") {
            val tool = part["tool"]?.jsonPrimitive?.contentOrNull ?: "tool"
            val state = part["state"]?.jsonObject
            val status = state?.get("status")?.jsonPrimitive?.contentOrNull ?: "running"
            val title = state?.get("title")?.jsonPrimitive?.contentOrNull
            val output = state?.get("output")?.jsonPrimitive?.contentOrNull
            val text = listOfNotNull(title?.line(), output?.line()).joinToString("\n")
            return PartState(
                id = id,
                type = type,
                text = text,
                tool = tool,
                status = status,
                title = title,
                output = output,
            )
        }
        if (type == "step-start") {
            return PartState(id = id, type = type, text = "")
        }
        if (type == "step-finish") {
            val reason = part["reason"]?.jsonPrimitive?.contentOrNull ?: "done"
            return PartState(id = id, type = type, text = reason)
        }
        if (type == "patch") {
            val files = part["files"]
                ?.jsonArray
                ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                ?: emptyList()
            val text = if (files.isEmpty()) {
                "changed files"
            } else {
                "${files.size} files: ${files.take(3).joinToString(", ")}" + if (files.size > 3) "..." else ""
            }
            return PartState(id = id, type = type, text = text)
        }
        return PartState(id = id, type = type, text = "[$type]")
    }

    private fun renderMessage(message: String): String {
        val text = part[message]
            ?.values
            ?.filter { it.type == "text" }
            ?.map { it.text }
            ?.filter { it.isNotBlank() }
            ?.joinToString("\n")
        if (text == null || text.isBlank()) return "(streaming...)"
        return text
    }

    private fun decorate(key: String, message: MessageState): MessageState {
        if (message.role == "user") return message
        val messageKey = messageKey(key, message.id)
        val parts = part[messageKey]?.values?.toList() ?: emptyList()
        if (parts.isEmpty()) return message
        return message.copy(
            text = renderMessage(messageKey),
            toolCalls = toolCalls(parts),
        )
    }

    private fun toolCalls(parts: List<PartState>): List<ToolCallState> {
        return parts
            .filter { it.type == "tool" }
            .map {
                ToolCallState(
                    id = it.id,
                    title = it.tool ?: "tool",
                    status = it.status,
                    details = listOfNotNull(
                        it.status?.let { status -> "Status: $status" },
                        it.title?.let { title -> "Title: ${title.line()}" },
                        it.output?.let { output -> "Output: ${output.line()}" },
                    ),
                )
            }
    }

    private fun String.line(): String {
        val value = lineSequence().firstOrNull()?.trim().orEmpty()
        if (value.length <= 160) return value
        return value.take(157) + "..."
    }

    private fun stageMessage(key: String, sessionId: String, messageId: String, role: String, text: String, sort: String) {
        val message = messageKey(key, messageId)
        order[message] = sort
        overlay[message] = MessageState(
            id = messageId,
            role = role,
            text = text,
            sort = sort,
        )
        overlayDirty.getOrPut(key) { linkedSetOf() }.add(message)
        if (focusedKey == key) {
            publishFocused(key)
        }
        queueFlush(key, sessionId)
    }

    private fun queueFlush(key: String, sessionId: String) {
        val scope = scope ?: return
        flush[key]?.cancel()
        flush[key] = scope.launch {
            delay(OverlayFlushDelayMs)
            flushStaged(key, sessionId)
        }
    }

    private suspend fun flushStaged(key: String, sessionId: String) {
        val staged = overlayDirty.remove(key)?.toList() ?: return
        val server = key.substringBefore("::")
        val now = System.currentTimeMillis()
        withContext(Dispatchers.IO) {
            staged.forEach {
                val message = overlay[it] ?: return@forEach
                db.appDatabaseQueries.upsertMessageCache(
                    server,
                    sessionId,
                    message.id,
                    message.role,
                    message.text,
                    message.sort,
                    now,
                )
            }
        }
    }

    private fun clearOverlay(key: String) {
        overlay.keys
            .filter { it.startsWith("$key::") }
            .forEach(overlay::remove)
        overlayDirty.remove(key)
        if (focusedKey == key) {
            publishFocused(key)
        }
    }

    private fun observeFocused() {
        observe?.cancel()
        val key = focusedKey ?: return
        focusedDb = emptyList()
        val server = key.substringBefore("::")
        val session = key.substringAfter("::")
        observe = (scope ?: return).launch {
            db.appDatabaseQueries
                .listMessageCache(server, session) { _, _, messageId, role, text, sortKey, _ ->
                    MessageState(
                        id = messageId,
                        role = role,
                        text = text,
                        sort = sortKey,
                    )
                }
                .asFlow()
                .mapToList(Dispatchers.IO)
                .collect {
                    it.forEach { message ->
                        order[messageKey(key, message.id)] = message.sort
                    }
                    focusedDb = it
                    publishFocused(key)
                }
        }
    }

    private fun publishFocused(key: String) {
        if (focusedKey != key) return
        val base = focusedDb.associateBy { it.id }.toMutableMap()
        overlay
            .filterKeys { it.startsWith("$key::") }
            .values
            .forEach {
                base[it.id] = it
            }
        val list = pending[key]
        val merged = if (list.isNullOrEmpty()) {
            base.values.toList()
        } else {
            base.values.toList() + list
        }
        local.value = local.value.copy(
            focusedMessages = merged
                .sortedBy { it.sort }
                .map { decorate(key, it) },
        )
    }

    private fun keyForSession(sessionId: String): String? {
        return active.keys.find { it.endsWith("::$sessionId") }
    }

    private fun messageKey(key: String, messageId: String): String {
        return "$key::$messageId"
    }

    private fun sequence(): String {
        return "z-${seq.incrementAndGet().toString().padStart(20, '0')}"
    }

    private fun claimPendingSort(key: String, text: String): String? {
        val list = pending[key] ?: return null
        if (list.isEmpty()) return null

        val target = text.trim()
        val index = if (target.isBlank()) {
            0
        } else {
            list.indexOfFirst { it.text.trim() == target }
                .let { found -> if (found >= 0) found else if (list.size == 1) 0 else -1 }
        }
        if (index < 0) return null

        val removed = list.removeAt(index)
        pendingPass[key]?.remove(removed.id)
        if (list.isEmpty()) {
            pending.remove(key)
            pendingPass.remove(key)
        }
        return removed.sort
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
        stickySort.remove(key)
        order.keys
            .filter { it.startsWith("$key::") }
            .forEach(order::remove)
        clearOverlay(key)
        flush.remove(key)?.cancel()
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

    private suspend fun beginSync(sessionId: String): Boolean {
        return syncGuard.withLock {
            if (syncActive.contains(sessionId)) return@withLock false
            syncActive.add(sessionId)
            true
        }
    }

    private suspend fun endSync(sessionId: String) {
        syncGuard.withLock {
            syncActive.remove(sessionId)
        }
    }

    private fun markSseApplied(type: String, sessionId: String?) {
        sseApplied += 1
        debug.value = debug.value.copy(sseApplied = sseApplied)
        if (sseApplied % 5 == 0) {
            Log.d(
                LogTag,
                "sse applied=$sseApplied dropped=$sseDropped seen=$sseSeen connected=$sseConnected errors=$sseErrors last=$type session=$sessionId",
            )
        }
    }

    private fun markSseDropped(type: String, reason: String) {
        sseDropped += 1
        debug.value = debug.value.copy(sseDropped = sseDropped, lastDrop = "$type: $reason")
        pushSseLog("drop type=$type reason=$reason")
        Log.w(LogTag, "sse drop type=$type reason=$reason seen=$sseSeen applied=$sseApplied dropped=$sseDropped")
    }

    private fun pushSseLog(line: String) {
        val stamp = System.currentTimeMillis().toString()
        sseLog.addLast("$stamp | $line")
        while (sseLog.size > SseLogLimit) {
            sseLog.removeFirst()
        }
        debug.value = debug.value.copy(sseLog = sseLog.toList())
    }

    fun clearDebug() {
        sseLog.clear()
        debug.value = debug.value.copy(sseLog = emptyList())
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
        flush.values.forEach { it.cancel() }
        flush.clear()
    }
}

private const val StreamRestartDelayMs = 3000L
private const val SyncDelayMs = 300L
private const val SyncBurstDelayMs = 1200L
private const val OverlayFlushDelayMs = 500L
private const val MessageSyncLimit = 400
private const val ReconcileIntervalMs = 10000L
private const val ReconcileKeepPasses = 1
private const val OptimisticKeepPasses = 1
private const val LogTag = "SessionService"
private const val SseLogLimit = 300
private const val SessionResolveCooldownMs = 5000L
