package de.chennemann.opencode.mobile.ui.manage

import de.chennemann.opencode.mobile.data.repository.AppendLocalMessageInput
import de.chennemann.opencode.mobile.data.repository.MessagePage
import de.chennemann.opencode.mobile.data.repository.MessagePageRequest
import de.chennemann.opencode.mobile.data.repository.PendingMessageRef
import de.chennemann.opencode.mobile.data.repository.ProjectRepository
import de.chennemann.opencode.mobile.data.repository.RemoteBatchSource
import de.chennemann.opencode.mobile.data.repository.RepoResult
import de.chennemann.opencode.mobile.data.repository.SessionListFilter
import de.chennemann.opencode.mobile.data.repository.SessionRemoteBatch
import de.chennemann.opencode.mobile.data.repository.SessionRepository
import de.chennemann.opencode.mobile.data.repository.SessionSyncState
import de.chennemann.opencode.mobile.data.repository.SessionSyncStatus
import de.chennemann.opencode.mobile.data.repository.SyncReason
import de.chennemann.opencode.mobile.data.repository.ConfirmSentMessageInput
import de.chennemann.opencode.mobile.di.DispatcherProvider
import de.chennemann.opencode.mobile.domain.service.connection.ConnectionActionService
import de.chennemann.opencode.mobile.domain.service.model.MessagePageInput
import de.chennemann.opencode.mobile.domain.service.model.MessagePageRequestResult
import de.chennemann.opencode.mobile.domain.service.model.RefreshInput
import de.chennemann.opencode.mobile.domain.service.model.RefreshResult
import de.chennemann.opencode.mobile.domain.service.model.RenameInput
import de.chennemann.opencode.mobile.domain.service.project.ProjectActionService
import de.chennemann.opencode.mobile.domain.service.session.SessionActionService
import de.chennemann.opencode.mobile.domain.service.session.SessionReadService
import de.chennemann.opencode.mobile.domain.session.ProjectGateway
import de.chennemann.opencode.mobile.domain.session.ProjectState
import de.chennemann.opencode.mobile.domain.session.ServerState
import de.chennemann.opencode.mobile.domain.session.SessionProject
import de.chennemann.opencode.mobile.domain.session.SessionState
import de.chennemann.opencode.mobile.domain.session.SessionSummary
import de.chennemann.opencode.mobile.domain.session.SessionUiState
import de.chennemann.opencode.mobile.domain.usecase.connection.RefreshServerUseCase
import de.chennemann.opencode.mobile.domain.usecase.project.RemoveProjectUseCase
import de.chennemann.opencode.mobile.domain.usecase.project.SelectProjectUseCase
import de.chennemann.opencode.mobile.domain.usecase.project.ToggleProjectFavoriteUseCase
import de.chennemann.opencode.mobile.domain.usecase.session.CreateSessionUseCase
import de.chennemann.opencode.mobile.domain.usecase.session.FocusSessionUseCase
import de.chennemann.opencode.mobile.navigation.NavEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ManageViewModelTest {
    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun mapsProjectsAndSectionsFromReadState() = runTest(TestCoroutineScheduler()) {
        val main = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(main)
        val worker = StandardTestDispatcher(testScheduler)
        val read = StubSessionReadService()
        val viewModel = viewModel(read, main, worker)
        read.state.value = state(
            projects = listOf(
                ProjectState(id = "p1", worktree = "/repo/main", name = "Main", sandboxes = listOf("/repo/main/s1"), favorite = true),
                ProjectState(id = "p2", worktree = "/repo/other", name = "Other", favorite = false),
            ),
            selectedProject = "/repo/main",
            sessions = listOf(
                SessionState(id = "s1", title = "One", version = "1", directory = "/repo/main", updatedAt = 100),
                SessionState(id = "s2", title = "Two", version = "1", directory = "/repo/main", updatedAt = 300),
                SessionState(id = "s3", title = "Three", version = "1", directory = "/repo/main/s1", updatedAt = 200),
            ),
        )

        advanceUntilIdle()

        val value = viewModel.state.value
        assertEquals(listOf("main"), value.favoriteProjects.map { it.name })
        assertEquals(listOf("other"), value.otherProjects.map { it.name })
        assertEquals(listOf("/repo/main", "/repo/main/s1"), value.workspaceOptions.map { it.directory })
        assertEquals(2, value.sessionSections.size)
        assertEquals(listOf("s2", "s1"), value.sessionSections[0].sessions.map { it.id })
    }

    @Test
    fun openProjectTappedUsesSelectProjectUseCase() = runTest(TestCoroutineScheduler()) {
        val main = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(main)
        val worker = StandardTestDispatcher(testScheduler)
        val read = StubSessionReadService()
        val project = RecordingProjectActionService()
        val viewModel = viewModel(
            read = read,
            main = main,
            worker = worker,
            project = project,
        )

        viewModel.onEvent(ManageEvent.ProjectPathChanged("  /repo/main  "))
        viewModel.onEvent(ManageEvent.OpenProjectTapped)
        advanceUntilIdle()

        assertEquals(listOf("/repo/main"), project.selectCalls)
    }

    @Test
    fun projectSelectedUsesProjectIdFromReadState() = runTest(TestCoroutineScheduler()) {
        val main = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(main)
        val worker = StandardTestDispatcher(testScheduler)
        val read = StubSessionReadService()
        val project = RecordingProjectActionService()
        val viewModel = viewModel(
            read = read,
            main = main,
            worker = worker,
            project = project,
        )
        read.state.value = state(
            projects = listOf(
                ProjectState(id = "p-main", worktree = "/repo/main", name = "Main"),
            ),
            selectedProject = "/repo/main",
        )

        viewModel.onEvent(ManageEvent.ProjectSelected("/repo/main"))
        advanceUntilIdle()

        assertEquals(listOf("p-main"), project.selectCalls)
    }

    @Test
    fun projectFavoriteToggleUsesProjectIdFromReadState() = runTest(TestCoroutineScheduler()) {
        val main = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(main)
        val worker = StandardTestDispatcher(testScheduler)
        val read = StubSessionReadService()
        val project = RecordingProjectActionService()
        val viewModel = viewModel(
            read = read,
            main = main,
            worker = worker,
            project = project,
        )
        read.state.value = state(
            projects = listOf(
                ProjectState(id = "p-main", worktree = "/repo/main", name = "Main"),
            ),
            selectedProject = "/repo/main",
        )

        viewModel.onEvent(ManageEvent.ProjectFavoriteToggled("/repo/main"))
        advanceUntilIdle()

        assertEquals(listOf("p-main"), project.favoriteCalls)
    }

    @Test
    fun openSessionTappedFocusesAndNavigates() = runTest(TestCoroutineScheduler()) {
        val main = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(main)
        val worker = StandardTestDispatcher(testScheduler)
        val read = StubSessionReadService()
        val session = RecordingSessionActionService()
        val viewModel = viewModel(
            read = read,
            main = main,
            worker = worker,
            session = session,
        )
        val row = SessionState(id = "s1", title = "One", version = "1", directory = "/repo/main", updatedAt = 100)

        val nav = async { viewModel.nav.first() }
        advanceUntilIdle()
        viewModel.onEvent(ManageEvent.OpenSessionTapped(row))
        advanceUntilIdle()

        assertEquals(listOf("s1"), session.focusCalls)
        assertTrue(nav.await() is NavEvent.ToConversation)
    }

    @Test
    fun createSessionTappedUsesCreateUseCaseAndNavigates() = runTest(TestCoroutineScheduler()) {
        val main = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(main)
        val worker = StandardTestDispatcher(testScheduler)
        val read = StubSessionReadService()
        val repo = FakeProjectRepository(
            listOf(ProjectState(id = "p1", worktree = "/repo/main", name = "Main", sandboxes = listOf("/repo/main/s1"), favorite = true))
        )
        val gateway = FakeProjectGateway()
        val viewModel = viewModel(
            read = read,
            main = main,
            worker = worker,
            createSession = CreateSessionUseCase(gateway, repo, FakeSessionRepository()),
        )
        read.state.value = state(
            projects = repo.rows,
            selectedProject = "/repo/main",
        )
        advanceUntilIdle()

        val nav = async { viewModel.nav.first() }
        viewModel.onEvent(ManageEvent.WorkspaceSelected("/repo/main/s1"))
        viewModel.onEvent(ManageEvent.CreateSessionTapped)
        advanceUntilIdle()

        assertEquals(listOf("/repo/main/s1"), gateway.createCalls)
        assertTrue(nav.await() is NavEvent.ToConversation)
    }

    @Test
    fun connectTappedUsesProvidedUrl() = runTest(TestCoroutineScheduler()) {
        val main = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(main)
        val worker = StandardTestDispatcher(testScheduler)
        val read = StubSessionReadService()
        val connection = FakeConnectionActionService()
        val viewModel = viewModel(
            read = read,
            main = main,
            worker = worker,
            refreshServer = RefreshServerUseCase(connection),
        )
        read.state.value = state(
            selectedProject = "/repo/main",
            discovered = "http://demo.local:4096",
            url = "http://127.0.0.1",
        )

        viewModel.onEvent(ManageEvent.ConnectTapped("http://demo.local:4096"))
        advanceUntilIdle()

        assertEquals(null, viewModel.state.value.urlError)
        assertEquals(listOf("http://demo.local:4096"), connection.calls)
    }

    @Test
    fun connectWithMalformedUrlShowsError() = runTest(TestCoroutineScheduler()) {
        val main = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(main)
        val worker = StandardTestDispatcher(testScheduler)
        val read = StubSessionReadService()
        val connection = FakeConnectionActionService()
        val viewModel = viewModel(
            read = read,
            main = main,
            worker = worker,
            refreshServer = RefreshServerUseCase(connection),
        )

        viewModel.onEvent(ManageEvent.ConnectTapped("bad url"))
        advanceUntilIdle()

        assertTrue(connection.calls.isEmpty())
        assertEquals("Enter a valid server URL", viewModel.state.value.urlError)
    }

    @Test
    fun connectShowsActionFailureReason() = runTest(TestCoroutineScheduler()) {
        val main = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(main)
        val worker = StandardTestDispatcher(testScheduler)
        val read = StubSessionReadService()
        val connection = FakeConnectionActionService().also {
            it.result = RefreshResult(accepted = false, reason = "Connection failed")
        }
        val viewModel = viewModel(
            read = read,
            main = main,
            worker = worker,
            refreshServer = RefreshServerUseCase(connection),
        )

        viewModel.onEvent(ManageEvent.ConnectTapped("http://demo.local:4096"))
        advanceUntilIdle()

        assertEquals("Connection failed", viewModel.state.value.urlError)
    }

    @Test
    fun connectShowsLoadingWhileRefreshRuns() = runTest(TestCoroutineScheduler()) {
        val main = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(main)
        val worker = StandardTestDispatcher(testScheduler)
        val read = StubSessionReadService()
        val connection = FakeConnectionActionService().also {
            it.block = CompletableDeferred()
        }
        val viewModel = viewModel(
            read = read,
            main = main,
            worker = worker,
            refreshServer = RefreshServerUseCase(connection),
        )

        viewModel.onEvent(ManageEvent.ConnectTapped("http://demo.local:4096"))
        advanceUntilIdle()

        assertTrue(viewModel.state.value.connecting)

        connection.block?.complete(Unit)
        advanceUntilIdle()

        assertTrue(!viewModel.state.value.connecting)
    }

    private fun state(
        projects: List<ProjectState> = emptyList(),
        selectedProject: String? = null,
        sessions: List<SessionState> = emptyList(),
        discovered: String? = null,
        url: String = "http://127.0.0.1",
    ): SessionUiState {
        return SessionUiState(
            url = url,
            discovered = discovered,
            status = ServerState.Connected("1"),
            projects = projects,
            selectedProject = selectedProject,
            selectedProjectId = projects.firstOrNull { it.worktree == selectedProject }?.id,
            commands = emptyList(),
            sessions = sessions,
            globalSessions = sessions,
            activeSessions = emptyList(),
            focusedSession = null,
            focusedMessages = emptyList(),
            canLoadMoreMessages = false,
            loadingMoreMessages = false,
            loadingProjects = false,
            loadingSessions = false,
            sessionRecentOnly = false,
            quickPinInclude = emptySet(),
            quickPinExclude = emptySet(),
            quickProcessing = emptySet(),
            quickUnread = emptySet(),
            message = null,
        )
    }

    private fun lanes(main: TestDispatcher, worker: TestDispatcher): DispatcherProvider {
        return object : DispatcherProvider {
            override val io = worker
            override val default = worker
            override val mainImmediate = main
        }
    }

    private fun viewModel(
        read: StubSessionReadService,
        main: TestDispatcher,
        worker: TestDispatcher,
        project: RecordingProjectActionService = RecordingProjectActionService(),
        session: RecordingSessionActionService = RecordingSessionActionService(),
        refreshServer: RefreshServerUseCase = RefreshServerUseCase(FakeConnectionActionService()),
        createSession: CreateSessionUseCase = CreateSessionUseCase(FakeProjectGateway(), FakeProjectRepository(emptyList()), FakeSessionRepository()),
    ): ManageViewModel {
        return ManageViewModel(
            read = read,
            dispatchers = lanes(main, worker),
            selectProject = SelectProjectUseCase(project),
            focusSession = FocusSessionUseCase(session),
            toggleProjectFavorite = ToggleProjectFavoriteUseCase(project),
            removeProject = RemoveProjectUseCase(project),
            refreshServer = refreshServer,
            createSession = createSession,
        )
    }
}

private class StubSessionReadService : SessionReadService {
    override val state = MutableStateFlow(
        SessionUiState(
            url = "http://127.0.0.1",
            discovered = null,
            status = ServerState.Idle,
            projects = emptyList(),
            selectedProject = null,
            selectedProjectId = null,
            commands = emptyList(),
            sessions = emptyList(),
            globalSessions = emptyList(),
            activeSessions = emptyList(),
            focusedSession = null,
            focusedMessages = emptyList(),
            canLoadMoreMessages = false,
            loadingMoreMessages = false,
            loadingProjects = false,
            loadingSessions = false,
            sessionRecentOnly = false,
            quickPinInclude = emptySet(),
            quickPinExclude = emptySet(),
            quickProcessing = emptySet(),
            quickUnread = emptySet(),
            message = null,
        )
    )

    override suspend fun sessionsForProject(worktree: String, limit: Int?): List<SessionState> {
        return emptyList()
    }
}

private class RecordingProjectActionService : ProjectActionService {
    val selectCalls = mutableListOf<String>()
    val favoriteCalls = mutableListOf<String>()
    val hiddenCalls = mutableListOf<String>()

    override suspend fun select(projectId: String) {
        selectCalls += projectId
    }

    override suspend fun toggleFavorite(projectId: String): Boolean {
        favoriteCalls += projectId
        return true
    }

    override suspend fun toggleHidden(projectId: String): Boolean {
        hiddenCalls += projectId
        return true
    }

    override suspend fun refreshProjectContext(projectId: String) {
    }
}

private class RecordingSessionActionService : SessionActionService {
    val focusCalls = mutableListOf<String>()

    override suspend fun focus(sessionId: String) {
        focusCalls += sessionId
    }

    override suspend fun requestMessagePage(input: MessagePageInput): MessagePageRequestResult {
        return MessagePageRequestResult(accepted = true, reason = null)
    }

    override suspend fun archive(sessionId: String, directory: String?) {
    }

    override suspend fun rename(input: RenameInput) {
    }

    override suspend fun requestSync(sessionId: String, reason: SyncReason) {
    }
}

private class FakeConnectionActionService : ConnectionActionService {
    val calls = mutableListOf<String>()
    var block: CompletableDeferred<Unit>? = null
    var result = RefreshResult(accepted = true, reason = null)

    override suspend fun refresh(input: RefreshInput): RefreshResult {
        calls += input.endpoint
        block?.await()
        return result
    }
}

private class FakeProjectGateway : ProjectGateway {
    val createCalls = mutableListOf<String>()

    override suspend fun projects(): List<SessionProject> {
        return emptyList()
    }

    override suspend fun sessions(worktree: String, limit: Int?): List<SessionSummary> {
        return emptyList()
    }

    override suspend fun archiveSession(sessionId: String, directory: String) {
    }

    override suspend fun renameSession(sessionId: String, directory: String, title: String) {
    }

    override suspend fun createSession(worktree: String, title: String): SessionSummary {
        createCalls += worktree
        return SessionSummary(
            id = "created",
            title = title,
            version = "1",
            directory = worktree,
        )
    }
}

private class FakeProjectRepository(
    val rows: List<ProjectState>,
) : ProjectRepository {
    override fun observeProjects(): Flow<List<ProjectState>> {
        return flowOf(rows)
    }

    override fun observeSelectedProject(): Flow<ProjectState?> {
        return flowOf(rows.firstOrNull())
    }

    override suspend fun select(projectId: String) {
    }

    override suspend fun toggleFavorite(projectId: String): Boolean {
        return true
    }

    override suspend fun toggleHidden(projectId: String): Boolean {
        return true
    }

    override suspend fun upsertProjects(items: List<ProjectState>) {
    }

    override suspend fun requestRefresh(projectId: String) {
    }
}

private class FakeSessionRepository : SessionRepository {
    override fun observeSessionList(projectId: String, filter: SessionListFilter): Flow<List<SessionState>> {
        return flowOf(emptyList())
    }

    override fun observeFocusedSession(): Flow<SessionState?> {
        return flowOf(null)
    }

    override fun observeRecentSessionList(limit: Long): Flow<List<SessionState>> {
        return flowOf(emptyList())
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
