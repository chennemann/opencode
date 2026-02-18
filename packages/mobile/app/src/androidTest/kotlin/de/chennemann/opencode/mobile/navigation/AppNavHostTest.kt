package de.chennemann.opencode.mobile.navigation

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.chennemann.opencode.mobile.data.repository.SyncReason
import de.chennemann.opencode.mobile.domain.service.model.MessagePageInput
import de.chennemann.opencode.mobile.domain.service.model.MessagePageRequestResult
import de.chennemann.opencode.mobile.domain.service.model.RenameInput
import de.chennemann.opencode.mobile.domain.service.project.ProjectActionService
import de.chennemann.opencode.mobile.domain.service.session.SessionActionService
import de.chennemann.opencode.mobile.domain.service.session.SessionReadService
import de.chennemann.opencode.mobile.domain.session.CommandState
import de.chennemann.opencode.mobile.domain.session.MessageState
import de.chennemann.opencode.mobile.domain.session.ProjectState
import de.chennemann.opencode.mobile.domain.session.ServerState
import de.chennemann.opencode.mobile.domain.session.SessionState
import de.chennemann.opencode.mobile.domain.session.SessionUiState
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.loadKoinModules
import org.koin.core.context.unloadKoinModules
import org.koin.dsl.module

@RunWith(AndroidJUnit4::class)
class AppNavHostTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var service: FakeSessionReadService

    private val sessionHome = SessionState(
        id = "session-home",
        title = "Conversation Home",
        version = "v1",
        directory = "/workspace/demo",
        updatedAt = 1L,
    )

    private val sessionTarget = SessionState(
        id = "session-target",
        title = "Resume Session",
        version = "v1",
        directory = "/workspace/demo",
        updatedAt = 2L,
    )

    private val testModule = module {
        single<SessionReadService> { service }
        single<ProjectActionService> { FakeProjectActionService(service) }
        single<SessionActionService> { FakeSessionActionService(service) }
    }

    @Before
    fun setUp() {
        service = FakeSessionReadService(sessionHome, sessionTarget)
        loadKoinModules(testModule)
    }

    @After
    fun tearDown() {
        unloadKoinModules(testModule)
    }

    @Test
    fun handles_manage_navigation_events_and_back_stack_transitions() {
        compose.setContent {
            AppNavHost()
        }

        compose.onNodeWithText("Conversation Home").assertIsDisplayed()

        compose.onNodeWithContentDescription("Open settings").performClick()
        compose.onNodeWithText("Workspace Hub").assertIsDisplayed()

        compose.onNodeWithText("Logs").performClick()
        compose.onNodeWithText("Application Logs").assertIsDisplayed()
        compose.onNodeWithText("Back").performClick()
        compose.onNodeWithText("Workspace Hub").assertIsDisplayed()

        compose.onNodeWithText("Back").performClick()
        compose.onNodeWithText("Conversation Home").assertIsDisplayed()

        compose.onNodeWithContentDescription("Open settings").performClick()
        compose.onNodeWithText("Workspace Hub").assertIsDisplayed()

        compose.onNodeWithText("Resume Session").performClick()
        compose.onAllNodesWithText("Workspace Hub").assertCountEquals(0)
        compose.onNodeWithText("Resume Session").assertIsDisplayed()
    }
}

private class FakeSessionReadService(
    home: SessionState,
    target: SessionState,
) : SessionReadService {
    private val project = ProjectState(
        id = "project-demo",
        worktree = home.directory,
        name = "Demo",
        favorite = true,
    )

    private val sessions = listOf(target, home)

    private val flow = MutableStateFlow(
        SessionUiState(
            url = "http://127.0.0.1:4096",
            discovered = null,
            status = ServerState.Connected("http://demo.local:4096", "v1"),
            projects = listOf(project),
            selectedProject = project.worktree,
            selectedProjectId = project.id,
            commands = emptyList<CommandState>(),
            sessions = sessions,
            globalSessions = sessions,
            activeSessions = sessions,
            focusedSession = home,
            focusedMessages = emptyList<MessageState>(),
            canLoadMoreMessages = false,
            loadingMoreMessages = false,
            loadingProjects = false,
            loadingSessions = false,
            sessionRecentOnly = false,
            message = null,
        )
    )

    override val state = flow

    fun updateUrl(value: String) {
        flow.value = flow.value.copy(url = value)
    }

    fun selectProject(worktree: String) {
        flow.value = flow.value.copy(selectedProject = worktree)
    }

    fun focusSession(sessionId: String) {
        val next = sessions.firstOrNull { it.id == sessionId } ?: return
        flow.value = flow.value.copy(
            selectedProject = next.directory,
            focusedSession = next,
        )
    }

    override suspend fun sessionsForProject(worktree: String, limit: Int?): List<SessionState> {
        return sessions.filter { it.directory == worktree }.let {
            if (limit == null) it else it.take(limit)
        }
    }
}

private class FakeProjectActionService(
    private val read: FakeSessionReadService,
) : ProjectActionService {
    override suspend fun select(projectId: String) {
        read.selectProject(projectId)
    }

    override suspend fun toggleFavorite(projectId: String): Boolean {
        return false
    }

    override suspend fun toggleHidden(projectId: String): Boolean {
        return true
    }

    override suspend fun refreshProjectContext(projectId: String) {
    }
}

private class FakeSessionActionService(
    private val read: FakeSessionReadService,
) : SessionActionService {
    override suspend fun focus(sessionId: String) {
        read.focusSession(sessionId)
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
