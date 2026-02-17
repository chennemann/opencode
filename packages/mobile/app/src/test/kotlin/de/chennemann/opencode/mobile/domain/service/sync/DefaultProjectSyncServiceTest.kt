package de.chennemann.opencode.mobile.domain.service.sync

import de.chennemann.opencode.mobile.data.repository.CommandRepository
import de.chennemann.opencode.mobile.data.repository.ProjectRepository
import de.chennemann.opencode.mobile.domain.session.CommandState
import de.chennemann.opencode.mobile.domain.session.ProjectState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DefaultProjectSyncServiceTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun runUpsertsProjectsAndReplacesCommandsPerProject() = runTest {
        val server = ProjectSyncServer().also {
            it.projectsJson = """
                [
                  {"id":"p-1","worktree":"/repo/main","name":"Main","sandboxes":["/repo/main-sb"]},
                  {"id":"p-2","worktree":"/repo/aux","name":"Aux","sandboxes":[]}
                ]
            """.trimIndent()
            it.commandsByProject["/repo/main"] = """
                [{"name":"build","description":"Build","source":"local"}]
            """.trimIndent()
            it.commandsByProject["/repo/aux"] = """
                [{"name":"deploy","description":"Deploy","source":"remote"}]
            """.trimIndent()
        }
        val projects = ProjectSyncProjectRepo(
            listOf(
                ProjectState(id = "p-1", worktree = "/repo/main", name = "Main", favorite = true),
            )
        )
        val commands = ProjectSyncCommandRepo()
        val service = DefaultProjectSyncService(server, projects, commands, json)

        service.run()

        assertEquals(listOf("p-1", "p-2"), projects.upserted.single().map { it.id })
        assertEquals(true, projects.upserted.single().first { it.id == "p-1" }.favorite)
        assertEquals(
            listOf("p-1:build", "p-2:deploy"),
            commands.replaceCalls,
        )
    }

    @Test
    fun runWithProjectFilterRefreshesOnlyTargetProjectCommands() = runTest {
        val server = ProjectSyncServer().also {
            it.projectsJson = """
                [
                  {"id":"p-1","worktree":"/repo/main","name":"Main","sandboxes":[]},
                  {"id":"p-2","worktree":"/repo/aux","name":"Aux","sandboxes":[]}
                ]
            """.trimIndent()
            it.commandsByProject["/repo/main"] = "[{\"name\":\"build\"}]"
            it.commandsByProject["/repo/aux"] = "[{\"name\":\"deploy\"}]"
        }
        val projects = ProjectSyncProjectRepo(emptyList())
        val commands = ProjectSyncCommandRepo()
        val service = DefaultProjectSyncService(server, projects, commands, json)

        service.run("p-2")

        assertEquals(listOf("p-2:deploy"), commands.replaceCalls)
    }
}

private class ProjectSyncServer : ServerService {
    var projectsJson = "[]"
    val commandsByProject = linkedMapOf<String, String>()

    override suspend fun connectStream(onEvent: suspend (StreamEvent) -> Unit) {
    }

    override suspend fun disconnectStream() {
    }

    override suspend fun fetchProjects(): String {
        return projectsJson
    }

    override suspend fun fetchSessions(projectId: String): String {
        return "{}"
    }

    override suspend fun fetchCommands(projectId: String): String {
        return commandsByProject[projectId] ?: "[]"
    }

    override suspend fun sendOutbox(actionId: String): Boolean {
        return false
    }
}

private class ProjectSyncProjectRepo(
    seeded: List<ProjectState>,
) : ProjectRepository {
    private val flow = MutableStateFlow(seeded)
    val upserted = mutableListOf<List<ProjectState>>()

    override fun observeProjects(): Flow<List<ProjectState>> {
        return flowOf(flow.value)
    }

    override fun observeSelectedProject(): Flow<ProjectState?> {
        return flowOf(flow.value.firstOrNull())
    }

    override suspend fun select(projectId: String) {
    }

    override suspend fun toggleFavorite(projectId: String): Boolean {
        return false
    }

    override suspend fun toggleHidden(projectId: String): Boolean {
        return false
    }

    override suspend fun upsertProjects(items: List<ProjectState>) {
        upserted += items
        flow.value = items
    }

    override suspend fun requestRefresh(projectId: String) {
    }
}

private class ProjectSyncCommandRepo : CommandRepository {
    val replaceCalls = mutableListOf<String>()

    override fun observeCommands(projectId: String): Flow<List<CommandState>> {
        return flowOf(emptyList())
    }

    override suspend fun replaceCommands(projectId: String, commands: List<CommandState>) {
        replaceCalls += "$projectId:${commands.joinToString(",") { it.name }}"
    }

    override suspend fun find(projectId: String, commandName: String): CommandState? {
        return null
    }
}
