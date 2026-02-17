package de.chennemann.opencode.mobile.domain.service.sync

import de.chennemann.opencode.mobile.domain.session.CommandGateway
import de.chennemann.opencode.mobile.domain.session.MessageGateway
import de.chennemann.opencode.mobile.domain.session.ProjectGateway
import de.chennemann.opencode.mobile.domain.session.SessionProject
import de.chennemann.opencode.mobile.domain.session.SessionSummary
import de.chennemann.opencode.mobile.domain.session.StreamGateway
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class DefaultServerService(
    private val stream: StreamGateway,
    private val project: ProjectGateway,
    private val command: CommandGateway,
    private val message: MessageGateway,
) : ServerService {
    override suspend fun connectStream(onEvent: suspend (StreamEvent) -> Unit) {
        // Stream events are invalidation hints for DB-first convergence, not replayable source of truth.
        stream.streamEvents(
            lastEventId = null,
            onRawEvent = {},
        ) {
            onEvent(
                StreamEvent(
                    type = it.type,
                    payloadJson = it.properties.toString(),
                    receivedAt = System.currentTimeMillis(),
                )
            )
        }
    }

    override suspend fun disconnectStream() {
    }

    override suspend fun fetchProjects(): String {
        return buildJsonArray {
            project.projects().forEach {
                add(
                    buildJsonObject {
                        put("id", it.id)
                        put("worktree", it.worktree)
                        put("name", it.name)
                        put(
                            "sandboxes",
                            buildJsonArray {
                                it.sandboxes.forEach { value ->
                                    add(JsonPrimitive(value))
                                }
                            },
                        )
                    }
                )
            }
        }.toString()
    }

    override suspend fun fetchSessions(projectId: String): String {
        val projects = project.projects()
        val selected = selectSessions(projectId, projects)
        val target = selected.messageSession
        val messages = target?.let {
            message.messages(it.id, it.directory, SnapshotLimit)
        }.orEmpty()
        return buildJsonObject {
            put(
                "sessions",
                buildJsonArray {
                    selected.sessions.forEach {
                        add(
                            buildJsonObject {
                                put("id", it.id)
                                put("projectId", selected.projectId)
                                put("title", it.title)
                                put("version", it.version)
                                put("directory", it.directory)
                                put("parentId", it.parentId)
                                put("updatedAt", it.updatedAt)
                                put("archivedAt", it.archivedAt)
                            }
                        )
                    }
                },
            )
            put(
                "messages",
                buildJsonArray {
                    messages.forEach {
                        add(
                            buildJsonObject {
                                put("id", it.id)
                                put("sessionId", target?.id)
                                put("role", it.role)
                                put("text", it.text)
                                put("sort", sort(it.createdAt ?: 0, it.id))
                                put("createdAt", it.createdAt)
                                put("completedAt", it.completedAt)
                                put(
                                    "parts",
                                    buildJsonArray {
                                        it.parts.forEach { value ->
                                            add(value)
                                        }
                                    },
                                )
                            }
                        )
                    }
                },
            )
        }.toString()
    }

    override suspend fun fetchCommands(projectId: String): String {
        return buildJsonArray {
            command.commands(projectId).forEach {
                add(
                    buildJsonObject {
                        put("name", it.name)
                        put("description", it.description)
                        put("source", it.source)
                    }
                )
            }
        }.toString()
    }

    override suspend fun sendOutbox(actionId: String): Boolean {
        return false
    }
    private suspend fun selectSessions(selector: String, projects: List<SessionProject>): SessionSelection {
        val direct = projects.firstOrNull { it.id == selector || it.worktree == selector }
        if (direct != null) {
            return SessionSelection(
                projectId = direct.id,
                sessions = loadSessions(direct),
            )
        }
        projects.forEach {
            val sessions = project.sessions(it.worktree, SnapshotLimit)
            val found = sessions.firstOrNull { value -> value.id == selector }
            if (found == null) return@forEach
            return SessionSelection(
                projectId = it.id,
                sessions = listOf(found),
                messageSession = found,
            )
        }
        return SessionSelection(
            projectId = selector,
            sessions = project.sessions(selector, SnapshotLimit),
        )
    }

    private suspend fun loadSessions(projectRow: SessionProject): List<SessionSummary> {
        return (listOf(projectRow.worktree) + projectRow.sandboxes)
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
            .flatMap { project.sessions(it, SnapshotLimit) }
            .sortedWith(
                compareByDescending<SessionSummary> { it.updatedAt ?: 0L }
                    .thenByDescending { it.id }
            )
            .distinctBy { it.id }
            .take(SnapshotLimit)
    }

    private data class SessionSelection(
        val projectId: String,
        val sessions: List<SessionSummary>,
        val messageSession: SessionSummary? = null,
    )
}

private const val SnapshotLimit = 200

private fun sort(createdAt: Long, id: String): String {
    return "%020d:%s".format(createdAt, id)
}
