package de.chennemann.opencode.mobile.domain.service.session

import de.chennemann.opencode.mobile.data.repository.AppendLocalMessageInput
import de.chennemann.opencode.mobile.data.repository.CommandRepository
import de.chennemann.opencode.mobile.data.repository.ConfirmSentMessageInput
import de.chennemann.opencode.mobile.data.repository.MessagePage
import de.chennemann.opencode.mobile.data.repository.MessagePageRequest
import de.chennemann.opencode.mobile.data.repository.PendingMessageRef
import de.chennemann.opencode.mobile.data.repository.ProjectRepository
import de.chennemann.opencode.mobile.data.repository.RepoResult
import de.chennemann.opencode.mobile.data.repository.SessionListFilter
import de.chennemann.opencode.mobile.data.repository.SessionRemoteBatch
import de.chennemann.opencode.mobile.data.repository.SessionRepository
import de.chennemann.opencode.mobile.data.repository.SessionSyncState
import de.chennemann.opencode.mobile.data.repository.SessionSyncStatus
import de.chennemann.opencode.mobile.data.repository.SyncReason
import de.chennemann.opencode.mobile.domain.session.CommandState
import de.chennemann.opencode.mobile.domain.session.ConnectionGateway
import de.chennemann.opencode.mobile.domain.session.ConnectionState
import de.chennemann.opencode.mobile.domain.session.ProjectState
import de.chennemann.opencode.mobile.domain.session.SessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DefaultSessionReadServiceTest {
    @Test
    fun sessionsForProjectUsesExpandedWindowAndIncludesForkRows() = runTest {
        val projects = listOf(
            ProjectState(
                id = "p-1",
                worktree = "/repo/main",
                name = "Main",
                sandboxes = listOf("/repo/main-sb"),
            )
        )
        val session = ReadServiceSessionRepository().also {
            it.rows["p-1"] = (1..11).map { i ->
                SessionState(
                    id = "s-arch-$i",
                    title = "Archived $i",
                    version = "1",
                    directory = "/repo/main",
                    parentId = "root",
                    updatedAt = i.toLong(),
                    archivedAt = 1_000L + i,
                )
            } + SessionState(
                id = "s-visible",
                title = "Visible",
                version = "1",
                directory = "/repo/main-sb",
                parentId = "parent",
                updatedAt = 5_000L,
                archivedAt = null,
            )
        }
        val service = DefaultSessionReadService(
            connection = FakeConnectionGateway(),
            project = FakeProjectRepository(projects, selectedId = null),
            command = FakeCommandRepository(),
            session = session,
            scope = backgroundScope,
        )

        val rows = service.sessionsForProject("/repo/main", 11)

        assertEquals(listOf(200L), session.limits)
        assertEquals(listOf("s-visible"), rows.map { it.id })
    }

    @Test
    fun sessionsForProjectFallsBackToProjectRowsWhenWorkspaceMappingMisses() = runTest {
        val projects = listOf(
            ProjectState(
                id = "p-1",
                worktree = "/repo/main",
                name = "Main",
                sandboxes = emptyList(),
            )
        )
        val session = ReadServiceSessionRepository().also {
            it.rows["p-1"] = listOf(
                SessionState(
                    id = "s-1",
                    title = "Session",
                    version = "1",
                    directory = "/repo/renamed-workspace",
                    updatedAt = 200L,
                )
            )
        }
        val service = DefaultSessionReadService(
            connection = FakeConnectionGateway(),
            project = FakeProjectRepository(projects, selectedId = null),
            command = FakeCommandRepository(),
            session = session,
            scope = backgroundScope,
        )

        val rows = service.sessionsForProject("/repo/main", 11)

        assertEquals(listOf("s-1"), rows.map { it.id })
    }
}

private class FakeConnectionGateway : ConnectionGateway {
    private val state = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    private val url = MutableStateFlow("")
    private val foundServer = MutableStateFlow<String?>(null)

    override val status: StateFlow<ConnectionState> = state
    override val endpoint: StateFlow<String> = url
    override val found: StateFlow<String?> = foundServer

    override fun start(scope: CoroutineScope) {
    }

    override suspend fun setUrl(next: String) {
        url.value = next
    }

    override suspend fun refresh(loading: Boolean) {
    }
}

private class FakeProjectRepository(
    private val rows: List<ProjectState>,
    private val selectedId: String?,
) : ProjectRepository {
    override fun observeProjects(): Flow<List<ProjectState>> {
        return flowOf(rows)
    }

    override fun observeSelectedProject(): Flow<ProjectState?> {
        return flowOf(rows.firstOrNull { it.id == selectedId })
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
    }

    override suspend fun requestRefresh(projectId: String) {
    }
}

private class FakeCommandRepository : CommandRepository {
    override fun observeCommands(projectId: String): Flow<List<CommandState>> {
        return flowOf(emptyList())
    }

    override suspend fun replaceCommands(projectId: String, commands: List<CommandState>) {
    }

    override suspend fun find(projectId: String, commandName: String): CommandState? {
        return null
    }
}

private class ReadServiceSessionRepository : SessionRepository {
    val rows = linkedMapOf<String, List<SessionState>>()
    val limits = mutableListOf<Long>()

    override fun observeSessionList(projectId: String, filter: SessionListFilter): Flow<List<SessionState>> {
        limits += filter.limit
        val list = rows[projectId].orEmpty().take(filter.limit.toInt())
        if (filter.includeArchived) return flowOf(list)
        return flowOf(list.filter { it.archivedAt == null })
    }

    override fun observeRecentSessionList(limit: Long): Flow<List<SessionState>> {
        return flowOf(emptyList())
    }

    override fun observeFocusedSession(): Flow<SessionState?> {
        return flowOf(null)
    }

    override fun observeMessagePage(sessionId: String, request: MessagePageRequest): Flow<MessagePage> {
        return flowOf(MessagePage(emptyList(), false, null))
    }

    override fun observeSyncState(sessionId: String): Flow<SessionSyncState> {
        return flowOf(
            SessionSyncState(
                sessionId = sessionId,
                status = SessionSyncStatus.IDLE,
                lastSnapshotAt = null,
                lastStreamSeenAt = null,
                lastErrorAt = null,
            )
        )
    }

    override suspend fun focus(sessionId: String) {
    }

    override suspend fun appendLocalMessage(input: AppendLocalMessageInput): PendingMessageRef {
        return PendingMessageRef("local")
    }

    override suspend fun confirmSentMessage(input: ConfirmSentMessageInput): RepoResult {
        return RepoResult(ok = true)
    }

    override suspend fun applyRemoteBatch(batch: SessionRemoteBatch): RepoResult {
        return RepoResult(ok = true)
    }

    override suspend fun requestMessagePage(sessionId: String, beforeMessageId: String?, limit: Long): RepoResult {
        return RepoResult(ok = true)
    }

    override suspend fun archive(sessionId: String): RepoResult {
        return RepoResult(ok = true)
    }

    override suspend fun rename(sessionId: String, title: String): RepoResult {
        return RepoResult(ok = true)
    }

    override suspend fun requestSync(sessionId: String, reason: SyncReason) {
    }
}
