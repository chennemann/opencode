package de.chennemann.opencode.mobile.domain.service.sync

import de.chennemann.opencode.mobile.domain.session.CommandGateway
import de.chennemann.opencode.mobile.domain.session.ProjectGateway
import de.chennemann.opencode.mobile.domain.session.StreamGateway
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class DefaultServerService(
    private val stream: StreamGateway,
    private val project: ProjectGateway,
    private val command: CommandGateway,
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
        return buildJsonObject {
            put(
                "sessions",
                buildJsonArray {
                    project.sessions(projectId, SnapshotLimit).forEach {
                        add(
                            buildJsonObject {
                                put("id", it.id)
                                put("projectId", projectId)
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
            put("messages", buildJsonArray {})
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
}

private const val SnapshotLimit = 200
