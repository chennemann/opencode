package de.chennemann.opencode.mobile.domain.service.sync

import de.chennemann.opencode.mobile.data.repository.CommandRepository
import de.chennemann.opencode.mobile.data.repository.ProjectRepository
import de.chennemann.opencode.mobile.domain.session.CommandState
import de.chennemann.opencode.mobile.domain.session.ProjectState
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class DefaultProjectSyncService(
    private val server: ServerService,
    private val project: ProjectRepository,
    private val command: CommandRepository,
    private val json: Json,
) : ProjectSyncService {
    override suspend fun run(projectId: String?) {
        val rows = parseProjects(server.fetchProjects())
        if (rows.isEmpty()) return
        val pinned = project.observeProjects().first().associateBy { it.id }
        val merged = rows.map {
            it.copy(favorite = pinned[it.id]?.favorite == true)
        }
        project.upsertProjects(merged)
        val targets = if (projectId.isNullOrBlank()) {
            merged
        } else {
            merged.filter { it.id == projectId || it.worktree == projectId }
        }
        targets.forEach {
            command.replaceCommands(it.id, parseCommands(server.fetchCommands(it.worktree)))
        }
    }

    private fun parseProjects(payload: String): List<ProjectState> {
        val root = runCatching {
            json.parseToJsonElement(payload).jsonArray
        }.getOrNull() ?: JsonArray(emptyList())
        return root.mapNotNull {
            val obj = it.jsonObject
            val id = obj.text("id") ?: return@mapNotNull null
            val worktree = obj.text("worktree") ?: return@mapNotNull null
            ProjectState(
                id = id,
                worktree = worktree,
                name = obj.text("name") ?: worktree,
                sandboxes = parseSandboxes(obj),
                favorite = false,
            )
        }
    }

    private fun parseCommands(payload: String): List<CommandState> {
        val root = runCatching {
            json.parseToJsonElement(payload).jsonArray
        }.getOrNull() ?: JsonArray(emptyList())
        return root.mapNotNull {
            val obj = it.jsonObject
            val name = obj.text("name") ?: return@mapNotNull null
            CommandState(
                name = name,
                description = obj.text("description"),
                source = obj.text("source"),
            )
        }
    }

    private fun parseSandboxes(obj: JsonObject): List<String> {
        val value = obj["sandboxes"] as? JsonArray ?: return emptyList()
        return value.mapNotNull { it.jsonPrimitive.contentOrNull }.map(String::trim).filter(String::isNotBlank)
    }
}

private fun JsonObject.text(key: String): String? {
    return this[key]?.jsonPrimitive?.contentOrNull
}
