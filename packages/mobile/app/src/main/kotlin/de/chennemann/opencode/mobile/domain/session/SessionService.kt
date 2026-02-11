package de.chennemann.opencode.mobile.domain.session

import de.chennemann.opencode.mobile.domain.message.MessageDecorator
import de.chennemann.opencode.mobile.domain.message.MessagePart
import de.chennemann.opencode.mobile.domain.message.MessagePartParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong

class SessionService(
    private val conn: ConnectionGateway,
    private val proj: ProjectGateway,
    private val cmd: CommandGateway,
    private val msg: MessageGateway,
    private val cache: SessionCacheGateway,
    private val log: LogGateway,
    private val parser: MessagePartParser,
    private val decorator: MessageDecorator,
    private val projector: FocusedMessageProjector,
    private val planner: SessionSyncPlanner,
    private val reducer: SessionEventReducer,
    private val streamer: SessionStreamCoordinator,
    private val reconciler: ReconcileCoordinator,
) {
    private data class LocalState(
        val projects: List<ProjectState> = emptyList(),
        val favoriteProjects: Set<String> = emptySet(),
        val selectedProject: String? = null,
        val commands: List<CommandState> = emptyList(),
        val sessions: List<SessionState> = emptyList(),
        val activeSessions: List<SessionState> = emptyList(),
        val focusedSession: SessionState? = null,
        val focusedMessages: List<MessageState> = emptyList(),
        val canLoadMoreMessages: Boolean = false,
        val loadingMoreMessages: Boolean = false,
        val loadingProjects: Boolean = false,
        val loadingSessions: Boolean = false,
        val sessionRecentOnly: Boolean = false,
        val sessionLimit: Int = InitialSessionLimit,
        val message: String? = null,
    )

    private val input = MutableStateFlow(conn.endpoint.value)
    private val local = MutableStateFlow(LocalState())
    private val output = MutableStateFlow(
        SessionUiState(
            url = conn.endpoint.value,
            discovered = null,
            status = ServerState.Idle,
            projects = emptyList(),
            selectedProject = null,
            commands = emptyList(),
            sessions = emptyList(),
            activeSessions = emptyList(),
            focusedSession = null,
            focusedMessages = emptyList(),
            canLoadMoreMessages = false,
            loadingMoreMessages = false,
            loadingProjects = false,
            loadingSessions = false,
            sessionRecentOnly = false,
            message = null,
        )
    )

    private val active = linkedMapOf<String, SessionState>()
    private val sessionProject = linkedMapOf<String, String?>()
    private val part = linkedMapOf<String, Map<String, MessagePart>>()
    private val role = linkedMapOf<String, String>()
    private val pending = PendingBuffer()
    private val retainPass = PassCounter()
    private val stickySort = linkedMapOf<String, MutableMap<String, String>>()
    private val order = linkedMapOf<String, String>()
    private val messageLimit = linkedMapOf<String, Int>()
    private val seq = AtomicLong(System.currentTimeMillis() * 1000)
    private var manual = false
    private var stream: Job? = null
    private var observe: Job? = null
    private var reconcile: Job? = null
    private var publish: Job? = null
    private val sync = SyncCoordinator()
    private val flush = linkedMapOf<String, Job>()
    private val resolver = SessionResolver(SessionResolveCooldownMs)
    private val overlay = linkedMapOf<String, MessageState>()
    private val overlayDirty = linkedMapOf<String, LinkedHashSet<String>>()
    private val mapLock = Any()
    private var focusedDb = emptyList<MessageState>()
    private var scope: CoroutineScope? = null
    private var started = false
    private var focusedKey: String? = null
    private var publishToken = 0L

    val state: StateFlow<SessionUiState> = output.asStateFlow()

    fun start(scope: CoroutineScope) {
        if (started) return
        started = true
        this.scope = scope
        conn.start(scope)
        scope.launch {
            combine(input, conn.found, conn.status, local) { url, discovered, status, local ->
                SessionUiState(
                    url = url,
                    discovered = discovered,
                    status = status.toUi(),
                    projects = local.projects,
                    selectedProject = local.selectedProject,
                    commands = local.commands,
                    sessions = local.sessions,
                    activeSessions = local.activeSessions,
                    focusedSession = local.focusedSession,
                    focusedMessages = local.focusedMessages,
                    canLoadMoreMessages = local.canLoadMoreMessages,
                    loadingMoreMessages = local.loadingMoreMessages,
                    loadingProjects = local.loadingProjects,
                    loadingSessions = local.loadingSessions,
                    sessionRecentOnly = local.sessionRecentOnly,
                    message = local.message,
                )
            }.collect {
                output.value = it
            }
        }
        scope.launch {
            conn.endpoint.collect {
                if (manual) return@collect
                input.value = it
            }
        }
        scope.launch {
            conn.status.collect {
                if (it !is ConnectionState.Connected) return@collect
                loadProjects()
            }
        }
        scope.launch {
            hydrateLast()
        }
        reconcile(scope)
        stream(scope)
    }

    private fun ConnectionState.toUi(): ServerState {
        return when (this) {
            is ConnectionState.Idle -> ServerState.Idle
            is ConnectionState.Loading -> ServerState.Loading
            is ConnectionState.Connected -> ServerState.Connected(version)
            is ConnectionState.Failed -> ServerState.Failed(reason)
        }
    }

    fun updateUrl(value: String) {
        manual = true
        input.value = value
    }

    fun useDiscovered() {
        val value = conn.found.value ?: return
        manual = true
        input.value = value
    }

    fun refresh() {
        val scope = scope ?: return
        scope.launch {
            conn.setUrl(input.value)
            conn.refresh()
        }
    }

    fun loadProjects() {
        val scope = scope ?: return
        if (local.value.loadingProjects) return
        scope.launch {
            local.value = local.value.copy(loadingProjects = true, message = null)
            val server = serverForCache()
            val favorites = runCatching { cache.projectFavorites(server) }.getOrDefault(emptySet())
            val result = runCatching { proj.projects() }
            local.value = local.value.copy(loadingProjects = false)
            result.onSuccess { list ->
                val projects = mergeProjects(
                    list.map {
                        ProjectState(
                            id = it.id,
                            worktree = it.worktree,
                            name = if (it.name.isBlank()) projectName(it.worktree) else it.name,
                            sandboxes = it.sandboxes,
                        )
                    },
                    favorites,
                )
                val current = local.value.selectedProject
                val next = if (current != null && projects.any { it.worktree == current }) {
                    current
                } else {
                    projects.firstOrNull()?.worktree
                }
                local.value = local.value.copy(
                    projects = projects,
                    favoriteProjects = favorites,
                    selectedProject = next,
                    commands = if (next == null) emptyList() else local.value.commands,
                    sessions = if (next == null) emptyList() else local.value.sessions,
                    sessionRecentOnly = false,
                    sessionLimit = InitialSessionLimit,
                )
                if (next == null) return@onSuccess
                loadSessions(next)
                loadCommands(next)
            }
            result.onFailure {
                local.value = local.value.copy(message = it.message ?: "Failed to load projects")
            }
        }
    }

    fun selectProject(worktree: String) {
        local.value = local.value.copy(
            selectedProject = worktree,
            commands = emptyList(),
            sessions = emptyList(),
            sessionRecentOnly = false,
            sessionLimit = InitialSessionLimit,
        )
        loadSessions(worktree)
        loadCommands(worktree)
    }

    fun toggleProjectFavorite(worktree: String) {
        val value = worktree.trim()
        if (value.isBlank()) return
        val scope = scope ?: return
        val server = serverForCache()
        val current = local.value.favoriteProjects
        val next = if (current.contains(value)) {
            current - value
        } else {
            current + value
        }

        local.value = local.value.copy(
            projects = mergeProjects(local.value.projects, next),
            favoriteProjects = next,
        )

        scope.launch {
            runCatching {
                cache.setProjectFavorite(server, value, next.contains(value))
            }.onFailure {
                local.value = local.value.copy(
                    projects = mergeProjects(local.value.projects, current),
                    favoriteProjects = current,
                    message = it.message ?: "Failed to update project favorite",
                )
            }
        }
    }

    fun loadMoreSessions() {
        val worktree = local.value.selectedProject ?: return
        val limit = if (local.value.sessionRecentOnly) {
            InitialSessionLimit + SessionLimitStep
        } else {
            local.value.sessionLimit + SessionLimitStep
        }
        local.value = local.value.copy(
            sessionRecentOnly = false,
            sessionLimit = limit,
        )
        loadSessions(worktree)
    }

    fun createSession() {
        val scope = scope ?: return
        scope.launch {
            createSessionAndFocus()
        }
    }

    suspend fun createSessionAndFocus(): Boolean {
        val worktree = local.value.focusedSession?.directory ?: local.value.selectedProject ?: return false
        return createSessionAndFocus(worktree)
    }

    suspend fun createSessionAndFocus(worktree: String): Boolean {
        local.value = local.value.copy(loadingSessions = true, message = null)
        val result = runCatching { proj.createSession(worktree, "Mobile session") }
        local.value = local.value.copy(loadingSessions = false)
        result.onFailure {
            local.value = local.value.copy(message = it.message ?: "Failed to create session")
        }
        val created = result.getOrNull() ?: return false
        val session = SessionState(
            id = created.id,
            title = created.title,
            version = created.version,
            directory = created.directory,
            updatedAt = created.updatedAt,
            archivedAt = created.archivedAt,
        )
        focusSession(session, session.directory)
        loadSessions(local.value.selectedProject ?: worktree)
        return true
    }

    fun openSession(session: SessionState) {
        focusSession(session, session.directory)
    }

    fun send(text: String) {
        val value = text.trim()
        if (value.isBlank()) return
        val builtin = resolveBuiltin(value)
        if (builtin == "new") {
            createSession()
            return
        }
        val scope = scope ?: return
        val focused = local.value.focusedSession ?: return
        val command = resolveCommand(value)
        if (command != null) {
            scope.launch {
                val result = runCatching {
                    msg.sendCommand(focused.id, focused.directory, command.first.name, command.second)
                }
                result.onSuccess {
                    scheduleSync(focused.id)
                }
                result.onFailure {
                    local.value = local.value.copy(message = it.message ?: "Failed to run command")
                }
            }
            return
        }
        val key = focusedKey ?: keyForSession(focused.id) ?: key(conn.endpoint.value, focused.id)
        if (active[key] == null) {
            active[key] = focused
            focusedKey = key
        }
        val now = System.currentTimeMillis()
        val id = "local-$now"
        val sort = sequence()
        pending.add(
            key,
            MessageState(
                id = id,
                role = "user",
                text = value,
                sort = sort,
                createdAt = now,
            ),
            OptimisticKeepPasses,
        )
        local.value = local.value.copy(
            focusedMessages = local.value.focusedMessages + MessageState(
                id = id,
                role = "user",
                text = value,
                sort = sort,
                createdAt = now,
            )
        )
        scope.launch {
            val result = runCatching {
                msg.sendMessage(focused.id, focused.directory, value)
            }
            result.onSuccess {
                scheduleSync(focused.id)
            }
            result.onFailure {
                pending.remove(key, id)
                if (focusedKey == key) observeFocused()
                local.value = local.value.copy(message = it.message ?: "Failed to send message")
            }
        }
    }

    private fun resolveBuiltin(value: String): String? {
        if (!value.startsWith("/")) return null
        val parts = value.split(Regex("\\s+"), limit = 2)
        val name = parts.firstOrNull()?.removePrefix("/")?.trim().orEmpty()
        if (name.equals("new", true)) return "new"
        return null
    }

    private fun resolveCommand(value: String): Pair<CommandState, String>? {
        if (!value.startsWith("/")) return null
        val parts = value.split(Regex("\\s+"), limit = 2)
        val name = parts.firstOrNull()?.removePrefix("/")?.trim().orEmpty()
        if (name.isBlank()) return null
        val match = local.value.commands.firstOrNull { it.name == name } ?: return null
        val args = if (parts.size > 1) parts[1].trim() else ""
        return match to args
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
            focusedMessages = emptyList(),
            canLoadMoreMessages = false,
            loadingMoreMessages = false,
        )
        observeFocused()
    }

    private fun upsertActiveSession(session: SessionState, project: String?): String {
        val server = if (manual) input.value else conn.endpoint.value
        val key = key(server, session.id)
        active[key] = session
        sessionProject[key] = project
        messageLimit[key] = messageLimit[key] ?: MessageSyncLimit
        scope?.launch {
            cache.upsertSession(server, sessionProject[key], session)
        }
        return key
    }

    private fun focusSession(session: SessionState, project: String?) {
        val key = upsertActiveSession(session, project)
        val previous = focusedKey
        if (previous != null && previous != key) {
            flush.remove(previous)?.cancel()
            val sessionId = previous.substringAfter("::")
            scope?.launch {
                flushStaged(previous, sessionId)
            }
        }
        focusedKey = key
        local.value = local.value.copy(
            focusedSession = session,
            activeSessions = active.values.sortedByDescending { it.id },
            focusedMessages = emptyList(),
            canLoadMoreMessages = false,
            loadingMoreMessages = false,
        )
        if (!project.isNullOrBlank()) {
            loadCommands(project)
        }
        observeFocused()
        syncRemote(session)
    }

    private fun syncRemote(session: SessionState, more: Boolean = false) {
        if (conn.status.value !is ConnectionState.Connected) return
        val scope = scope ?: return
        scope.launch(Dispatchers.Default) {
            if (!beginSync(session.id)) return@launch
            try {
            val started = System.currentTimeMillis()
            if (more) {
                local.value = local.value.copy(loadingMoreMessages = true, message = null)
            }
            val key = keyForSession(session.id)
            val limit = key?.let { messageLimit[it] } ?: MessageSyncLimit
            val result = runCatching { msg.messages(session.id, session.directory, limit) }
            if (more) {
                local.value = local.value.copy(loadingMoreMessages = false)
            }
            result.onSuccess { list ->
                val key = keyForSession(session.id) ?: return@onSuccess
                val server = key.substringBefore("::")
                val now = System.currentTimeMillis()
                val next = list
                val complete = next.size < (messageLimit[key] ?: MessageSyncLimit)
                val cached = withContext(Dispatchers.IO) { cache.listMessages(server, session.id) }
                val sticky = stickySort[key]
                val incoming = mutableListOf<IncomingMessage>()

                next.forEach {
                    val id = requireNotNull(it.id)
                    val message = messageKey(key, id)
                    val messageRole = it.role
                    val parsed = parser.parseParts(it.parts)
                    synchronized(mapLock) {
                        if (parsed.isEmpty()) {
                            part.remove(message)
                        } else {
                            val map = linkedMapOf<String, MessagePart>()
                            parsed.forEach { item ->
                                map[item.id] = item
                            }
                            part[message] = map
                        }
                    }
                    val messageText = if (messageRole == "assistant") {
                        decorator.render(synchronized(mapLock) { part[message]?.values })
                    } else {
                        it.text
                    }
                    role[message] = messageRole
                    incoming.add(
                        IncomingMessage(
                            id = id,
                            role = messageRole,
                            text = messageText,
                            createdAt = it.createdAt,
                            completedAt = it.completedAt,
                        ),
                    )
                }

                val plan = planner.plan(
                    incoming = incoming,
                    cached = cached,
                    sticky = sticky,
                    remoteSort = ::remoteSort,
                    knownSort = { order[messageKey(key, it)] },
                    claimPendingSort = { claimPendingSort(key, it) },
                    retainRemoved = { retainPass.consume(key, it) },
                    complete = complete,
                )

                synchronized(mapLock) {
                    plan.sorts.forEach { (id, sort) ->
                        order[messageKey(key, id)] = sort
                    }
                }

                withContext(Dispatchers.IO) {
                    plan.upserts.forEach {
                        cache.upsertMessage(
                            server,
                            session.id,
                            it,
                            now,
                        )
                    }
                }

                if (complete) {
                    synchronized(mapLock) {
                        plan.removedIds.forEach { id ->
                            role.remove(messageKey(key, id))
                            part.remove(messageKey(key, id))
                            order.remove(messageKey(key, id))
                        }
                    }
                    withContext(Dispatchers.IO) {
                        plan.removedIds.forEach {
                            cache.deleteMessage(server, session.id, it)
                        }
                    }
                }

                clearOverlay(key)
                trimPending(key)
                if (focusedKey == key) {
                    if (plan.claimed) {
                        observeFocused()
                    }
                    local.value = local.value.copy(canLoadMoreMessages = !complete)
                }
                log.debug(LogTag, "sync ok session=${session.id} messages=${next.size} dt=${System.currentTimeMillis() - started}ms")
            }
            result.onFailure {
                log.warn(LogTag, "sync failed session=${session.id} reason=${it.message}")
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
            var partialFailure = false
            val directories = workspaceDirectoriesForProject(worktree)
            val result = runCatching {
                directories
                    .flatMap { directory ->
                        runCatching { proj.sessions(directory, SessionFetchLimit) }
                            .onFailure {
                                partialFailure = true
                                log.warn(LogTag, "sessions failed worktree=$directory reason=${it.message}")
                            }
                            .getOrDefault(emptyList())
                            .map {
                                SessionState(
                                    id = it.id,
                                    title = it.title,
                                    version = it.version,
                                    directory = workspaceId(if (it.directory.isBlank()) directory else it.directory),
                                    updatedAt = it.updatedAt,
                                    archivedAt = it.archivedAt,
                                )
                            }
                    }
            }
            local.value = local.value.copy(loadingSessions = false)
            result.onSuccess { list ->
                local.value = local.value.copy(
                    sessions = limitSessionsPerWorkspace(list, WorkspaceSessionDisplayLimit),
                    message = if (partialFailure) "Some workspaces failed to load sessions" else null,
                )
            }
            result.onFailure {
                local.value = local.value.copy(
                    sessions = emptyList(),
                    message = it.message ?: "Failed to load sessions",
                )
            }
        }
    }

    private fun workspaceDirectoriesForProject(worktree: String): List<String> {
        val selected = workspaceId(worktree)
        val project = local.value.projects.firstOrNull { workspaceId(it.worktree) == selected }
        if (project == null) {
            return listOf(selected)
        }
        return (listOf(project.worktree) + project.sandboxes)
            .map(::workspaceId)
            .distinct()
    }

    private fun limitSessionsPerWorkspace(sessions: List<SessionState>, limit: Int): List<SessionState> {
        return sessions
            .filter { it.archivedAt == null }
            .groupBy { workspaceId(it.directory) }
            .values
            .flatMap {
                it.sortedWith(
                    compareByDescending<SessionState> { item -> item.updatedAt ?: 0L }
                        .thenByDescending { item -> item.id }
                ).take(limit)
            }
            .sortedWith(
                compareByDescending<SessionState> { it.updatedAt ?: 0L }
                    .thenByDescending { it.id }
            )
    }

    private fun workspaceId(path: String): String {
        val value = path.trimEnd('/', '\\')
        if (value.isBlank()) return path
        return value
    }

    private fun loadCommands(worktree: String) {
        val scope = scope ?: return
        scope.launch {
            val result = runCatching { cmd.commands(worktree) }
            result.onSuccess { list ->
                local.value = local.value.copy(commands = list)
            }
            result.onFailure {
                log.warn(LogTag, "commands failed worktree=$worktree reason=${it.message}")
            }
        }
    }

    private fun hydrateLast() {
        val recent = runCatching { cache.recentSession() }.getOrNull() ?: return
        val scope = scope ?: return
        scope.launch {
            manual = true
            input.value = recent.server
            conn.setUrl(recent.server)
            conn.refresh(false)
            manual = false
            focusSession(recent.session, recent.project)
        }
    }

    private fun stream(scope: CoroutineScope) {
        stream?.cancel()
        stream = streamer.start(scope, ::onEvent)
    }

    private fun reconcile(scope: CoroutineScope) {
        reconcile?.cancel()
        reconcile = reconciler.start(scope) {
            if (conn.status.value !is ConnectionState.Connected) {
                conn.refresh(false)
            }
            val focused = local.value.focusedSession ?: return@start
            syncRemote(focused)
        }
    }

    private suspend fun onEvent(event: SessionStreamEvent) {
        when (val action = reducer.reduce(event)) {
            is SessionEventAction.Ignore -> {
                log.debug(LogTag, "ignore type=${action.type}")
                return
            }

            is SessionEventAction.ReloadProjects -> {
                markSseApplied(event.type, null)
                loadProjects()
                local.value.focusedSession?.let { syncRemote(it) }
            }

            is SessionEventAction.SessionChanged -> {
                handleSessionChanged(action)
            }

            is SessionEventAction.MessageUpdated -> {
                handleMessageUpdated(action, event.type)
            }

            is SessionEventAction.MessageRemoved -> {
                handleMessageRemoved(action, event.type)
            }

            is SessionEventAction.MessagePartUpdated -> {
                handleMessagePartUpdated(action, event.type)
            }

            is SessionEventAction.MessagePartRemoved -> {
                handleMessagePartRemoved(action, event.type)
            }

            is SessionEventAction.SessionStatus -> {
                handleSessionStatus(action, event.type)
            }

            is SessionEventAction.SessionDiff -> {
                handleSessionDiff(action, event.type)
            }

            is SessionEventAction.Drop -> {
                markSseDropped(action.type, action.reason)
            }
        }
    }

    private fun handleSessionChanged(action: SessionEventAction.SessionChanged) {
        markSseApplied(action.type, null)
        val selected = local.value.selectedProject
        if (!selected.isNullOrBlank() && workspaceDirectoriesForProject(selected).contains(workspaceId(action.directory))) {
            loadSessions(selected)
        }
        action.deletedSessionId?.let(::removeSession)
    }

    private fun handleMessageUpdated(action: SessionEventAction.MessageUpdated, type: String) {
        val key = keyForSession(action.sessionId)
        if (key == null) {
            markSseDropped(type, "session not focused/active id=${action.sessionId}")
            resolveSession(action.sessionId, action.directory)
            return
        }
        val message = messageKey(key, action.messageId)
        if (action.role == "user") {
            pending.claim(key, action.text ?: "")?.let {
                stickySort.getOrPut(key) { linkedMapOf() }[action.messageId] = it.sort
            }
        }
        role[message] = action.role
        retainPass.set(key, action.messageId, ReconcileKeepPasses)
        val sort = stickySort[key]?.remove(action.messageId) ?: order[message] ?: sequence()
        stageMessage(
            key,
            action.sessionId,
            action.messageId,
            action.role,
            if (!action.text.isNullOrBlank()) action.text else decorator.render(part[message]?.values),
            sort,
            action.createdAt,
            action.completedAt,
        )
        markSseApplied(type, action.sessionId)
        scheduleSync(action.sessionId, true)
    }

    private suspend fun handleMessageRemoved(action: SessionEventAction.MessageRemoved, type: String) {
        val key = keyForSession(action.sessionId)
        if (key == null) {
            markSseDropped(type, "session not focused/active id=${action.sessionId}")
            resolveSession(action.sessionId, action.directory)
            return
        }
        val server = key.substringBefore("::")
        withContext(Dispatchers.IO) {
            cache.deleteMessage(server, action.sessionId, action.messageId)
        }
        role.remove(messageKey(key, action.messageId))
        synchronized(mapLock) {
            part.remove(messageKey(key, action.messageId))
            order.remove(messageKey(key, action.messageId))
            overlay.remove(messageKey(key, action.messageId))
            overlayDirty[key]?.remove(messageKey(key, action.messageId))
        }
        if (focusedKey == key) {
            publishFocused(key)
        }
        markSseApplied(type, action.sessionId)
    }

    private fun handleMessagePartUpdated(action: SessionEventAction.MessagePartUpdated, type: String) {
        val next = parser.parsePart(action.part)
        if (next == null) {
            markSseDropped(type, "missing part type")
            return
        }
        val key = keyForSession(action.sessionId)
        if (key == null) {
            markSseDropped(type, "session not focused/active id=${action.sessionId}")
            resolveSession(action.sessionId, action.directory)
            return
        }
        val message = messageKey(key, action.messageId)
        val updated = synchronized(mapLock) {
            LinkedHashMap(part[message] ?: emptyMap<String, MessagePart>()).apply {
                this[next.id] = next
            }
        }
        synchronized(mapLock) {
            part[message] = updated
        }
        retainPass.set(key, action.messageId, ReconcileKeepPasses)
        val sort = synchronized(mapLock) { order[message] } ?: sequence()
        stageMessage(
            key,
            action.sessionId,
            action.messageId,
            role[message] ?: "assistant",
            synchronized(mapLock) { overlay[message]?.text }
                ?: focusedDb.firstOrNull { it.id == action.messageId }?.text
                ?: "(streaming...)",
            sort,
        )
        markSseApplied(type, action.sessionId)
        scheduleSync(action.sessionId, true)
    }

    private fun handleMessagePartRemoved(action: SessionEventAction.MessagePartRemoved, type: String) {
        val entry = active.entries.find { messageKey(it.key, action.messageId).let(part::containsKey) }
        if (entry == null) {
            markSseDropped(type, "message not in active cache")
            return
        }
        val key = entry.key
        val message = messageKey(key, action.messageId)
        val updated = synchronized(mapLock) {
            LinkedHashMap(part[message] ?: return).apply {
                remove(action.partId)
            }
        }
        synchronized(mapLock) {
            if (updated.isEmpty()) {
                part.remove(message)
            }
            if (updated.isNotEmpty()) part[message] = updated
        }
        val sessionId = key.substringAfter("::")
        val sort = synchronized(mapLock) { order[message] } ?: sequence()
        stageMessage(
            key,
            sessionId,
            action.messageId,
            role[message] ?: "assistant",
            synchronized(mapLock) { overlay[message]?.text }
                ?: focusedDb.firstOrNull { it.id == action.messageId }?.text
                ?: "(streaming...)",
            sort,
        )
        markSseApplied(type, sessionId)
        scheduleSync(sessionId, true)
    }

    private fun handleSessionStatus(action: SessionEventAction.SessionStatus, type: String) {
        if (keyForSession(action.sessionId) == null) {
            resolveSession(action.sessionId, action.directory)
        }
        markSseApplied(type, action.sessionId)
        scheduleSync(action.sessionId)
    }

    private fun handleSessionDiff(action: SessionEventAction.SessionDiff, type: String) {
        if (keyForSession(action.sessionId) == null) {
            resolveSession(action.sessionId, action.directory)
        }
        markSseApplied(type, action.sessionId)
    }

    private fun scheduleSync(sessionId: String, burst: Boolean = false) {
        val scope = scope ?: return
        sync.schedule(scope, sessionId, if (burst) SyncBurstDelayMs else SyncDelayMs) {
            val entry = active.values.find { value -> value.id == sessionId }
            if (entry != null) {
                syncRemote(entry)
            }
        }
    }

    private fun resolveSession(sessionId: String, directory: String?) {
        val scope = scope ?: return
        if (!resolver.allow(sessionId)) return
        scope.launch {
            val worktree = if (!directory.isNullOrBlank() && directory != "global") {
                directory
            } else {
                local.value.selectedProject
            }
            if (worktree.isNullOrBlank()) return@launch
            val result = runCatching { proj.sessions(worktree) }
            result.onFailure {
                log.warn(LogTag, "resolve session failed id=$sessionId reason=${it.message}")
            }
            val found = result.getOrNull()
                ?.firstOrNull { it.id == sessionId }
                ?: return@launch
            val session = SessionState(
                id = found.id,
                title = found.title,
                version = found.version,
                directory = found.directory,
                updatedAt = found.updatedAt,
                archivedAt = found.archivedAt,
            )
            log.debug(LogTag, "resolve session id=$sessionId track=${session.title}")
            upsertActiveSession(session, worktree)
            local.value = local.value.copy(activeSessions = active.values.sortedByDescending { it.id })
            scheduleSync(sessionId)
        }
    }

    private fun stageMessage(
        key: String,
        sessionId: String,
        messageId: String,
        role: String,
        text: String,
        sort: String,
        createdAt: Long? = null,
        completedAt: Long? = null,
    ) {
        val message = messageKey(key, messageId)
        synchronized(mapLock) {
            order[message] = sort
            val current = overlay[message] ?: focusedDb.firstOrNull { it.id == messageId }
            overlay[message] = MessageState(
                id = messageId,
                role = role,
                text = text,
                sort = sort,
                createdAt = createdAt ?: current?.createdAt,
                completedAt = completedAt ?: current?.completedAt,
            )
            overlayDirty.getOrPut(key) { linkedSetOf() }.add(message)
        }
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
        val staged = synchronized(mapLock) { overlayDirty.remove(key)?.toList() } ?: return
        val server = key.substringBefore("::")
        val now = System.currentTimeMillis()
        withContext(Dispatchers.IO) {
            staged.forEach {
                val message = synchronized(mapLock) { overlay[it] } ?: return@forEach
                cache.upsertMessage(
                    server,
                    sessionId,
                    message,
                    now,
                )
            }
        }
    }

    private fun clearOverlay(key: String) {
        synchronized(mapLock) {
            overlay.keys
                .filter { it.startsWith("$key::") }
                .forEach(overlay::remove)
            overlayDirty.remove(key)
        }
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
            cache.observeMessages(server, session)
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
        val scope = scope ?: return
        val prefix = "$key::"
        val base = focusedDb
        val snapshot = synchronized(mapLock) {
            Triple(
                overlay.filterKeys { it.startsWith(prefix) },
                pending.list(key),
                part.filterKeys { it.startsWith(prefix) },
            )
        }
        val staged = snapshot.first
        val queued = snapshot.second
        val parts = snapshot.third
        val token = ++publishToken
        publish?.cancel()
        publish = scope.launch(Dispatchers.Default) {
            val next = projector.project(key, base, staged, queued, parts)
            withContext(Dispatchers.Main.immediate) {
                if (focusedKey != key || token != publishToken) return@withContext
                local.value = local.value.copy(focusedMessages = next)
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
        return "z-${seq.incrementAndGet().toString().padStart(20, '0')}"
    }

    private fun remoteSort(index: Int): String {
        return "r-${index.toString().padStart(20, '0')}"
    }

    private fun claimPendingSort(key: String, text: String): String? {
        return pending.claim(key, text)?.sort
    }

    private fun trimPending(key: String) {
        if (pending.trim(key)) {
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
        pending.clear(key)
        retainPass.clear(key)
        stickySort.remove(key)
        order.keys
            .filter { it.startsWith("$key::") }
            .forEach(order::remove)
        clearOverlay(key)
        flush.remove(key)?.cancel()
        messageLimit.remove(key)
        scope?.launch(Dispatchers.IO) {
            cache.deleteSessionMessages(server, sessionId)
        }
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

    private fun serverForCache(): String {
        return if (manual) input.value else conn.endpoint.value
    }

    private fun mergeProjects(projects: List<ProjectState>, favorites: Set<String>): List<ProjectState> {
        val next = projects.map {
            it.copy(favorite = favorites.contains(it.worktree))
        }
        val known = next.map { it.worktree }.toSet()
        val missing = favorites
            .filterNot(known::contains)
            .map {
                ProjectState(
                    id = "favorite:$it",
                    worktree = it,
                    name = projectName(it),
                    favorite = true,
                )
            }

        return sortProjects(next + missing)
    }

    private fun sortProjects(projects: List<ProjectState>): List<ProjectState> {
        return projects.sortedWith(
            compareByDescending<ProjectState> { it.favorite }
                .thenBy { it.name.lowercase() }
        )
    }

    private fun projectName(worktree: String): String {
        val value = worktree.trimEnd('/', '\\')
        if (value.isBlank()) return worktree
        val slash = value.lastIndexOf('/')
        val backslash = value.lastIndexOf('\\')
        val index = maxOf(slash, backslash)
        if (index < 0) return value
        val name = value.substring(index + 1)
        if (name.isBlank()) return value
        return name
    }

    private fun key(server: String, sessionId: String): String {
        return "$server::$sessionId"
    }

    private suspend fun beginSync(sessionId: String): Boolean {
        return sync.begin(sessionId)
    }

    private suspend fun endSync(sessionId: String) {
        sync.end(sessionId)
    }

    private fun markSseApplied(type: String, sessionId: String?) {
        log.debug(LogTag, "sse apply type=$type session=$sessionId")
    }

    private fun markSseDropped(type: String, reason: String) {
        log.warn(LogTag, "sse drop type=$type reason=$reason")
    }

    fun stop() {
        stream?.cancel()
        stream = null
        reconcile?.cancel()
        reconcile = null
        observe?.cancel()
        observe = null
        publish?.cancel()
        publish = null
        sync.cancelAll()
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
private const val SessionResolveCooldownMs = 5000L
private const val InitialSessionLimit = 50
private const val SessionLimitStep = 50
private const val SessionFetchLimit = 50
private const val WorkspaceSessionDisplayLimit = 3
