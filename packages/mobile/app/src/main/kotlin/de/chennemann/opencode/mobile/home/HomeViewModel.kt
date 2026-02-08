package de.chennemann.opencode.mobile.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.chennemann.opencode.mobile.data.GlobalStreamEvent
import de.chennemann.opencode.mobile.data.ServerRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class HomeViewModel(
    private val repo: ServerRepository,
) : ViewModel() {
    private data class LocalState(
        val projects: List<ProjectState> = emptyList(),
        val selectedProject: String? = null,
        val sessions: List<SessionState> = emptyList(),
        val loadingProjects: Boolean = false,
        val loadingSessions: Boolean = false,
        val loadingMessages: Boolean = false,
        val opened: SessionState? = null,
        val messages: List<MessageState> = emptyList(),
        val message: String? = null,
    )

    private val input = MutableStateFlow(repo.endpoint.value)
    private val local = MutableStateFlow(LocalState())
    private var manual = false
    private var stream: Job? = null
    private var streamSessionId: String? = null
    private val streamRoles = linkedMapOf<String, String>()
    private val streamParts = linkedMapOf<String, LinkedHashMap<String, String>>()

    val state: StateFlow<HomeState> = combine(
        input,
        repo.found,
        repo.status,
        local,
    ) { url, discovered, status, local ->
        HomeState(
            url = url,
            discovered = discovered,
            status = status,
            projects = local.projects,
            selectedProject = local.selectedProject,
            sessions = local.sessions,
            loadingProjects = local.loadingProjects,
            loadingSessions = local.loadingSessions,
            loadingMessages = local.loadingMessages,
            opened = local.opened,
            messages = local.messages,
            message = local.message,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        HomeState(
            url = repo.endpoint.value,
            discovered = null,
            status = ServerState.Idle,
            projects = emptyList(),
            selectedProject = null,
            sessions = emptyList(),
            loadingProjects = false,
            loadingSessions = false,
            loadingMessages = false,
            opened = null,
            messages = emptyList(),
            message = null,
        )
    )

    init {
        repo.start(viewModelScope)
        viewModelScope.launch {
            repo.endpoint.collect { url ->
                if (manual) return@collect
                input.value = url
            }
        }
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
        viewModelScope.launch {
            repo.setUrl(input.value)
            repo.refresh()
        }
    }

    fun loadProjects() {
        viewModelScope.launch {
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
                    messages = emptyList(),
                )
                if (next == null) {
                    return@onSuccess
                }
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
        viewModelScope.launch {
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
                local.value = local.value.copy(opened = session)
                loadMessages(session.id, session.directory)
                loadSessions(worktree)
            }
            result.onFailure {
                local.value = local.value.copy(message = it.message ?: "Failed to create session")
            }
        }
    }

    fun openSession(id: String) {
        val match = local.value.sessions.find { it.id == id } ?: return
        loadMessages(match.id, match.directory)
    }

    fun consumeOpened() {
        local.value = local.value.copy(opened = null)
    }

    fun loadMessages(sessionId: String, directory: String) {
        viewModelScope.launch {
            local.value = local.value.copy(loadingMessages = true, message = null, messages = emptyList())
            val result = runCatching { repo.messages(sessionId, directory) }
            local.value = local.value.copy(loadingMessages = false)
            result.onSuccess { list ->
                streamRoles.clear()
                streamParts.clear()
                list.forEach { message ->
                    streamRoles[message.id] = message.role
                    streamParts[message.id] = linkedMapOf("initial" to message.text)
                }
                local.value = local.value.copy(
                    messages = list.map {
                        MessageState(
                            id = it.id,
                            role = it.role,
                            text = it.text,
                        )
                    }
                )
            }
            result.onFailure {
                local.value = local.value.copy(message = it.message ?: "Failed to load messages")
            }
        }
    }

    fun startMessageStream(sessionId: String, directory: String) {
        if (streamSessionId == sessionId && stream?.isActive == true) return
        stopMessageStream()
        streamSessionId = sessionId
        stream = viewModelScope.launch {
            while (isActive) {
                val result = runCatching {
                    repo.streamEvents { event ->
                        onEvent(sessionId, directory, event)
                    }
                }
                if (result.isSuccess) return@launch
                delay(1_000)
            }
        }
    }

    fun stopMessageStream() {
        stream?.cancel()
        stream = null
        streamSessionId = null
    }

    private fun onEvent(sessionId: String, directory: String, event: GlobalStreamEvent) {
        if (event.directory != directory && event.directory != "global") return
        if (event.type == "message.updated") {
            val info = event.properties["info"]?.jsonObject ?: return
            val value = info["sessionID"]?.jsonPrimitive?.contentOrNull ?: return
            if (value != sessionId) return
            val id = info["id"]?.jsonPrimitive?.contentOrNull ?: return
            val role = info["role"]?.jsonPrimitive?.contentOrNull ?: "assistant"
            streamRoles[id] = role
            if (streamParts[id] == null) {
                streamParts[id] = linkedMapOf()
                renderStreamMessages()
            }
            return
        }
        if (event.type == "message.part.updated") {
            val part = event.properties["part"]?.jsonObject ?: return
            val value = part["sessionID"]?.jsonPrimitive?.contentOrNull ?: return
            if (value != sessionId) return
            val type = part["type"]?.jsonPrimitive?.contentOrNull ?: return
            if (type != "text") return
            val messageId = part["messageID"]?.jsonPrimitive?.contentOrNull ?: return
            val partId = part["id"]?.jsonPrimitive?.contentOrNull ?: return
            val text = part["text"]?.jsonPrimitive?.contentOrNull ?: ""
            val parts = streamParts.getOrPut(messageId) { linkedMapOf() }
            parts[partId] = text
            renderStreamMessages()
        }
    }

    private fun renderStreamMessages() {
        val messages = streamParts
            .map { entry ->
                val text = entry.value.values
                    .filter { it.isNotBlank() }
                    .joinToString("\n")
                MessageState(
                    id = entry.key,
                    role = streamRoles[entry.key] ?: "assistant",
                    text = if (text.isBlank()) "(streaming...)" else text,
                )
            }
            .sortedBy { it.id }
        local.value = local.value.copy(messages = messages)
    }

    private fun loadSessions(worktree: String) {
        viewModelScope.launch {
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
                    .sortedByDescending { it.id }
                )
            }
            result.onFailure {
                local.value = local.value.copy(message = it.message ?: "Failed to load sessions")
            }
        }
    }

    override fun onCleared() {
        stopMessageStream()
        super.onCleared()
    }
}
