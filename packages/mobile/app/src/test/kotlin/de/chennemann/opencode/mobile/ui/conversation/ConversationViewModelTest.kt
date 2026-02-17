package de.chennemann.opencode.mobile.ui.conversation

import de.chennemann.opencode.mobile.data.repository.AppendLocalMessageInput
import de.chennemann.opencode.mobile.data.repository.CommandRepository
import de.chennemann.opencode.mobile.data.repository.ConfirmSentMessageInput
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
import de.chennemann.opencode.mobile.di.DispatcherProvider
import de.chennemann.opencode.mobile.domain.service.connection.ConnectionActionService
import de.chennemann.opencode.mobile.domain.service.message.MessageActionService
import de.chennemann.opencode.mobile.domain.service.model.CommandInput
import de.chennemann.opencode.mobile.domain.service.model.CommandResult
import de.chennemann.opencode.mobile.domain.service.model.MessagePageInput
import de.chennemann.opencode.mobile.domain.service.model.MessagePageRequestResult
import de.chennemann.opencode.mobile.domain.service.model.RefreshInput
import de.chennemann.opencode.mobile.domain.service.model.RefreshResult
import de.chennemann.opencode.mobile.domain.service.model.RenameInput
import de.chennemann.opencode.mobile.domain.service.model.SendMessageInput
import de.chennemann.opencode.mobile.domain.service.model.SendMessageResult
import de.chennemann.opencode.mobile.domain.service.session.SessionActionService
import de.chennemann.opencode.mobile.domain.service.session.SessionReadService
import de.chennemann.opencode.mobile.domain.session.CommandState
import de.chennemann.opencode.mobile.domain.session.ProjectGateway
import de.chennemann.opencode.mobile.domain.session.ProjectState
import de.chennemann.opencode.mobile.domain.session.ServerState
import de.chennemann.opencode.mobile.domain.session.SessionState
import de.chennemann.opencode.mobile.domain.session.SessionUiState
import de.chennemann.opencode.mobile.domain.session.SessionProject
import de.chennemann.opencode.mobile.domain.session.SessionSummary
import de.chennemann.opencode.mobile.domain.usecase.connection.RefreshServerUseCase
import de.chennemann.opencode.mobile.domain.usecase.message.ExecuteCommandUseCase
import de.chennemann.opencode.mobile.domain.usecase.message.SendMessageUseCase
import de.chennemann.opencode.mobile.domain.usecase.session.ArchiveSessionUseCase
import de.chennemann.opencode.mobile.domain.usecase.session.CreateSessionUseCase
import de.chennemann.opencode.mobile.domain.usecase.session.FocusSessionUseCase
import de.chennemann.opencode.mobile.domain.usecase.session.RenameSessionUseCase
import de.chennemann.opencode.mobile.domain.usecase.session.RequestMessagePageUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
class ConversationViewModelTest {
    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun mapsReadStateAndSlashSuggestions() = runTest(TestCoroutineScheduler()) {
        val main = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(main)
        val worker = StandardTestDispatcher(testScheduler)
        val read = StubSessionReadService()
        val viewModel = viewModel(read, main, worker)
        val focused = SessionState(
            id = "s1",
            title = "Session 1",
            version = "1",
            directory = "/repo/main",
            updatedAt = 100,
        )
        read.state.value = state(
            status = ServerState.Connected("1"),
            commands = listOf(CommandState(name = "help", description = "Help")),
            focusedSession = focused,
            focusedMessages = listOf(
                de.chennemann.opencode.mobile.domain.session.MessageState(id = "u1", role = "user", text = "Hi", sort = "1"),
                de.chennemann.opencode.mobile.domain.session.MessageState(id = "a1", role = "assistant", text = "Hello", sort = "2"),
            ),
            projects = listOf(ProjectState(id = "p1", worktree = "/repo/main", name = "Main", favorite = true)),
            globalSessions = listOf(focused),
        )

        advanceUntilIdle()

        assertEquals("Session 1", viewModel.state.value.title)
        assertEquals(1, viewModel.state.value.turns.size)
        assertEquals(listOf("new", "help"), viewModel.state.value.slashSuggestions.map { it.name })
    }

    @Test
    fun quickSwitchLongPressLoadsMenuSessions() = runTest(TestCoroutineScheduler()) {
        val main = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(main)
        val worker = StandardTestDispatcher(testScheduler)
        val read = StubSessionReadService()
        val viewModel = viewModel(read, main, worker)
        val focused = SessionState(
            id = "s2",
            title = "Focused",
            version = "1",
            directory = "/repo/main",
            updatedAt = 200,
        )
        read.sessionsByWorktree["/repo/main"] = listOf(
            SessionState(id = "s1", title = "Old", version = "1", directory = "/repo/main", updatedAt = 100),
            SessionState(id = "s2", title = "Focused", version = "1", directory = "/repo/main", updatedAt = 200),
        )
        read.state.value = state(
            focusedSession = focused,
            projects = listOf(ProjectState(id = "p1", worktree = "/repo/main", name = "Main", favorite = true)),
            globalSessions = listOf(focused),
        )

        advanceUntilIdle()
        viewModel.onEvent(ConversationEvent.QuickSwitchLongPressed("/repo/main"))
        advanceUntilIdle()

        val menu = viewModel.state.value.quickSwitchMenu
        assertTrue(menu != null)
        assertEquals(listOf("s2", "s1"), menu!!.sessions.map { it.id })
        assertEquals(listOf(11, 11), read.requestedLimits)
    }

    @Test
    fun quickSwitchUsesGlobalSessionsAcrossProjects() = runTest(TestCoroutineScheduler()) {
        val main = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(main)
        val worker = StandardTestDispatcher(testScheduler)
        val read = StubSessionReadService()
        val viewModel = viewModel(read, main, worker)
        read.state.value = state(
            projects = listOf(
                ProjectState(id = "p1", worktree = "/repo/main", name = "Main", favorite = true),
                ProjectState(id = "p2", worktree = "/repo/other", name = "Other", favorite = true),
            ),
            selectedProject = "/repo/main",
            globalSessions = listOf(
                SessionState(id = "s1", title = "Main", version = "1", directory = "/repo/main", updatedAt = 100),
                SessionState(id = "s2", title = "Other", version = "1", directory = "/repo/other", updatedAt = 200),
            ),
        )

        advanceUntilIdle()

        assertEquals(setOf("/repo/main", "/repo/other"), viewModel.state.value.quickSwitches.map { it.worktree }.toSet())
    }

    @Test
    fun quickSwitchPinTapUpdatesMenuPinnedStateImmediately() = runTest(TestCoroutineScheduler()) {
        val main = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(main)
        val worker = StandardTestDispatcher(testScheduler)
        val read = StubSessionReadService()
        val viewModel = viewModel(read, main, worker)
        val rows = listOf(
            SessionState(id = "s1", title = "One", version = "1", directory = "/repo/main", updatedAt = 100),
            SessionState(id = "s2", title = "Two", version = "1", directory = "/repo/main", updatedAt = 200),
        )
        read.sessionsByWorktree["/repo/main"] = rows
        read.state.value = state(
            projects = listOf(ProjectState(id = "p1", worktree = "/repo/main", name = "Main", favorite = true)),
            selectedProject = "/repo/main",
            globalSessions = rows,
        )

        advanceUntilIdle()
        viewModel.onEvent(ConversationEvent.QuickSwitchLongPressed("/repo/main"))
        advanceUntilIdle()
        viewModel.onEvent(ConversationEvent.QuickSwitchMenuPinTapped(rows.first(), false))
        advanceUntilIdle()

        val menu = viewModel.state.value.quickSwitchMenu
        assertTrue(menu != null)
        assertTrue(menu!!.pinned.contains("s1"))
    }

    @Test
    fun sendTappedUsesSendMessageUseCaseWithFocusedSessionContext() = runTest(TestCoroutineScheduler()) {
        val main = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(main)
        val worker = StandardTestDispatcher(testScheduler)
        val read = StubSessionReadService()
        val message = RecordingMessageActionService()
        val viewModel = viewModel(
            read = read,
            main = main,
            worker = worker,
            sendMessage = SendMessageUseCase(message),
            executeCommand = ExecuteCommandUseCase(message),
        )
        read.state.value = state(
            focusedSession = SessionState(
                id = "s-1",
                title = "Session 1",
                version = "1",
                directory = "/repo/main",
            ),
            projects = listOf(ProjectState(id = "p-1", worktree = "/repo/main", name = "Main", favorite = true)),
        )

        advanceUntilIdle()
        viewModel.onEvent(ConversationEvent.DraftChanged("hello"))
        viewModel.onEvent(ConversationEvent.SendTapped)
        advanceUntilIdle()

        assertEquals(
            listOf(
                SendMessageInput(
                    text = "hello",
                    agent = "build",
                    sessionId = "s-1",
                    directory = "/repo/main",
                )
            ),
            message.sendCalls,
        )
        assertEquals("", viewModel.state.value.draft)
    }

    @Test
    fun loadMoreUsesRequestMessagePageWithEarliestMessage() = runTest(TestCoroutineScheduler()) {
        val main = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(main)
        val worker = StandardTestDispatcher(testScheduler)
        val read = StubSessionReadService()
        val session = RecordingSessionActionService()
        val viewModel = viewModel(
            read = read,
            main = main,
            worker = worker,
            requestMessagePage = RequestMessagePageUseCase(session),
        )
        read.state.value = state(
            focusedSession = SessionState(
                id = "s1",
                title = "Session 1",
                version = "1",
                directory = "/repo/main",
            ),
            focusedMessages = listOf(
                de.chennemann.opencode.mobile.domain.session.MessageState(id = "m2", role = "assistant", text = "2", sort = "2"),
                de.chennemann.opencode.mobile.domain.session.MessageState(id = "m1", role = "user", text = "1", sort = "1"),
            ),
        )

        advanceUntilIdle()
        viewModel.onEvent(ConversationEvent.LoadMoreMessagesTapped)
        advanceUntilIdle()

        assertEquals(
            listOf(MessagePageInput(sessionId = "s1", beforeMessageId = "m1", limit = 100)),
            session.pageCalls,
        )
    }

    @Test
    fun quickSwitchArchiveForwardsSessionDirectory() = runTest(TestCoroutineScheduler()) {
        val main = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(main)
        val worker = StandardTestDispatcher(testScheduler)
        val read = StubSessionReadService()
        val session = RecordingSessionActionService()
        val viewModel = viewModel(
            read = read,
            main = main,
            worker = worker,
            archiveSession = ArchiveSessionUseCase(session),
        )
        val row = SessionState(id = "s1", title = "One", version = "1", directory = "/repo/main", updatedAt = 100)
        read.sessionsByWorktree["/repo/main"] = listOf(row)
        read.state.value = state(
            projects = listOf(ProjectState(id = "p1", worktree = "/repo/main", name = "Main", favorite = true)),
            selectedProject = "/repo/main",
            globalSessions = listOf(row),
        )

        advanceUntilIdle()
        viewModel.onEvent(ConversationEvent.QuickSwitchLongPressed("/repo/main"))
        advanceUntilIdle()
        viewModel.onEvent(ConversationEvent.QuickSwitchMenuArchiveTapped(row))
        advanceUntilIdle()

        assertEquals(listOf("s1:/repo/main"), session.archiveCalls)
    }

    @Test
    fun sendCommandUsesSelectedProjectId() = runTest(TestCoroutineScheduler()) {
        val main = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(main)
        val worker = StandardTestDispatcher(testScheduler)
        val read = StubSessionReadService()
        val message = RecordingMessageActionService()
        val viewModel = viewModel(
            read = read,
            main = main,
            worker = worker,
            sendMessage = SendMessageUseCase(message),
            executeCommand = ExecuteCommandUseCase(message),
        )
        read.state.value = state(
            selectedProject = "/repo/main",
            selectedProjectId = "p-main",
            commands = listOf(CommandState(name = "help", description = "Help")),
            focusedSession = SessionState(id = "s-1", title = "Session 1", version = "1", directory = "/repo/main"),
        )

        advanceUntilIdle()
        viewModel.onEvent(ConversationEvent.DraftChanged("/help"))
        viewModel.onEvent(ConversationEvent.SendTapped)
        advanceUntilIdle()

        assertEquals(listOf("p-main"), message.commandCalls.map { it.projectId })
    }

    @Test
    fun builtinNewCreatesSessionFromFocusedDirectory() = runTest(TestCoroutineScheduler()) {
        val main = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(main)
        val worker = StandardTestDispatcher(testScheduler)
        val read = StubSessionReadService()
        val gateway = FakeProjectGateway()
        val viewModel = viewModel(
            read = read,
            main = main,
            worker = worker,
            createSession = CreateSessionUseCase(gateway, FakeProjectRepository(), FakeSessionRepository()),
        )
        read.state.value = state(
            selectedProject = "/repo/fallback",
            focusedSession = SessionState(id = "s-1", title = "Session 1", version = "1", directory = "/repo/main"),
        )

        advanceUntilIdle()
        viewModel.onEvent(ConversationEvent.DraftChanged("/new"))
        viewModel.onEvent(ConversationEvent.SendTapped)
        advanceUntilIdle()

        assertEquals(listOf("/repo/main"), gateway.createCalls)
    }

    private fun state(
        status: ServerState = ServerState.Idle,
        commands: List<CommandState> = emptyList(),
        focusedSession: SessionState? = null,
        focusedMessages: List<de.chennemann.opencode.mobile.domain.session.MessageState> = emptyList(),
        projects: List<ProjectState> = emptyList(),
        globalSessions: List<SessionState> = emptyList(),
        selectedProject: String? = projects.firstOrNull()?.worktree,
        selectedProjectId: String? = projects.firstOrNull()?.id,
    ): SessionUiState {
        return SessionUiState(
            url = "http://127.0.0.1",
            discovered = null,
            status = status,
            projects = projects,
            selectedProject = selectedProject,
            selectedProjectId = selectedProjectId,
            commands = commands,
            sessions = emptyList(),
            globalSessions = globalSessions,
            activeSessions = globalSessions,
            focusedSession = focusedSession,
            focusedMessages = focusedMessages,
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
        sendMessage: SendMessageUseCase = SendMessageUseCase(RecordingMessageActionService()),
        executeCommand: ExecuteCommandUseCase = ExecuteCommandUseCase(RecordingMessageActionService()),
        requestMessagePage: RequestMessagePageUseCase = RequestMessagePageUseCase(RecordingSessionActionService()),
        archiveSession: ArchiveSessionUseCase = ArchiveSessionUseCase(RecordingSessionActionService()),
        renameSession: RenameSessionUseCase = RenameSessionUseCase(RecordingSessionActionService()),
        focusSession: FocusSessionUseCase = FocusSessionUseCase(RecordingSessionActionService()),
        createSession: CreateSessionUseCase = CreateSessionUseCase(FakeProjectGateway(), FakeProjectRepository(), FakeSessionRepository()),
    ): ConversationViewModel {
        return ConversationViewModel(
            read = read,
            dispatchers = lanes(main, worker),
            focusSession = focusSession,
            sendMessage = sendMessage,
            executeCommand = executeCommand,
            requestMessagePage = requestMessagePage,
            archiveSession = archiveSession,
            renameSession = renameSession,
            createSession = createSession,
            refreshServer = RefreshServerUseCase(FakeConnectionActionService()),
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
    val sessionsByWorktree = linkedMapOf<String, List<SessionState>>()
    val requestedLimits = mutableListOf<Int>()

    override suspend fun sessionsForProject(worktree: String, limit: Int?): List<SessionState> {
        if (limit != null) {
            requestedLimits += limit
        }
        return sessionsByWorktree[worktree].orEmpty().let {
            if (limit == null) it else it.take(limit)
        }
    }
}

private class RecordingSessionActionService : SessionActionService {
    val pageCalls = mutableListOf<MessagePageInput>()
    val archiveCalls = mutableListOf<String>()

    override suspend fun focus(sessionId: String) = Unit

    override suspend fun requestMessagePage(input: MessagePageInput): MessagePageRequestResult {
        pageCalls += input
        return MessagePageRequestResult(accepted = true, reason = null)
    }

    override suspend fun archive(sessionId: String, directory: String?) {
        archiveCalls += "$sessionId:${directory.orEmpty()}"
    }

    override suspend fun rename(input: RenameInput) = Unit

    override suspend fun requestSync(sessionId: String, reason: SyncReason) = Unit
}

private class RecordingMessageActionService : MessageActionService {
    val sendCalls = mutableListOf<SendMessageInput>()
    val commandCalls = mutableListOf<CommandInput>()

    override suspend fun send(input: SendMessageInput): SendMessageResult {
        sendCalls += input
        return SendMessageResult(accepted = true, sessionId = input.sessionId, reason = null)
    }

    override suspend fun execute(input: CommandInput): CommandResult {
        commandCalls += input
        return CommandResult(accepted = true, reason = null)
    }
}

private class FakeConnectionActionService : ConnectionActionService {
    override suspend fun refresh(input: RefreshInput): RefreshResult {
        return RefreshResult(accepted = true, reason = null)
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

private class FakeProjectRepository : ProjectRepository {
    override fun observeProjects(): Flow<List<ProjectState>> {
        return flowOf(emptyList())
    }

    override fun observeSelectedProject(): Flow<ProjectState?> {
        return flowOf(null)
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
