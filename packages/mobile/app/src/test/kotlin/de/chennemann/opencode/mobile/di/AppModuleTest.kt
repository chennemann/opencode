package de.chennemann.opencode.mobile.di

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import de.chennemann.opencode.mobile.data.CommandInfo
import de.chennemann.opencode.mobile.data.GlobalStreamEvent
import de.chennemann.opencode.mobile.data.Health
import de.chennemann.opencode.mobile.data.MdnsEntry
import de.chennemann.opencode.mobile.data.MdnsGateway
import de.chennemann.opencode.mobile.data.ProjectInfo
import de.chennemann.opencode.mobile.data.PromptResponseIds
import de.chennemann.opencode.mobile.data.ServerGateway
import de.chennemann.opencode.mobile.data.ServerRepository
import de.chennemann.opencode.mobile.data.SessionInfo
import de.chennemann.opencode.mobile.data.SessionMessageInfo
import de.chennemann.opencode.mobile.db.AppDatabase
import de.chennemann.opencode.mobile.domain.service.connection.ConnectionActionService
import de.chennemann.opencode.mobile.domain.service.logs.LogsService
import de.chennemann.opencode.mobile.domain.service.message.MessageActionService
import de.chennemann.opencode.mobile.domain.service.project.ProjectActionService
import de.chennemann.opencode.mobile.domain.service.session.SessionActionService
import de.chennemann.opencode.mobile.domain.service.session.SessionReadService
import de.chennemann.opencode.mobile.domain.session.CommandGateway
import de.chennemann.opencode.mobile.domain.session.ConnectionGateway
import de.chennemann.opencode.mobile.domain.session.ConnectivityGateway
import de.chennemann.opencode.mobile.domain.session.LogGateway
import de.chennemann.opencode.mobile.domain.session.MessageGateway
import de.chennemann.opencode.mobile.domain.session.ProjectGateway
import de.chennemann.opencode.mobile.domain.session.StreamGateway
import de.chennemann.opencode.mobile.domain.usecase.connection.RefreshServerUseCase
import de.chennemann.opencode.mobile.domain.usecase.connection.SetServerUrlUseCase
import de.chennemann.opencode.mobile.domain.usecase.message.ExecuteCommandUseCase
import de.chennemann.opencode.mobile.domain.usecase.message.SendMessageUseCase
import de.chennemann.opencode.mobile.domain.usecase.project.RemoveProjectUseCase
import de.chennemann.opencode.mobile.domain.usecase.project.SelectProjectUseCase
import de.chennemann.opencode.mobile.domain.usecase.project.ToggleProjectFavoriteUseCase
import de.chennemann.opencode.mobile.domain.usecase.session.ArchiveSessionUseCase
import de.chennemann.opencode.mobile.domain.usecase.session.CreateSessionUseCase
import de.chennemann.opencode.mobile.domain.usecase.session.FocusSessionUseCase
import de.chennemann.opencode.mobile.domain.usecase.session.RenameSessionUseCase
import de.chennemann.opencode.mobile.domain.usecase.session.RequestMessagePageUseCase
import de.chennemann.opencode.mobile.ui.conversation.ConversationViewModel
import de.chennemann.opencode.mobile.ui.logs.LogsViewModel
import de.chennemann.opencode.mobile.ui.manage.ManageViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.koin.core.Koin
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

class AppModuleTest {
    @AfterEach
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun resolvesReadServiceUseCasesAndViewModels() {
        val koin = startTestKoin()

        val read = koin.get<SessionReadService>()
        val conversation = koin.get<ConversationViewModel>()
        val manage = koin.get<ManageViewModel>()
        val logs = koin.get<LogsViewModel>()
        assertNotNull(conversation)
        assertNotNull(manage)
        assertNotNull(logs)
        assertSame(read, viewModelRead(conversation))
        assertSame(read, viewModelRead(manage))

        assertNotNull(koin.get<ProjectActionService>())
        assertNotNull(koin.get<SessionActionService>())
        assertNotNull(koin.get<MessageActionService>())
        assertNotNull(koin.get<ConnectionActionService>())
        assertNotNull(koin.get<LogsService>())
        assertNotNull(koin.get<SelectProjectUseCase>())
        assertNotNull(koin.get<ToggleProjectFavoriteUseCase>())
        assertNotNull(koin.get<RemoveProjectUseCase>())
        assertNotNull(koin.get<FocusSessionUseCase>())
        assertNotNull(koin.get<CreateSessionUseCase>())
        assertNotNull(koin.get<SetServerUrlUseCase>())
        assertNotNull(koin.get<RefreshServerUseCase>())
        assertNotNull(koin.get<SendMessageUseCase>())
        assertNotNull(koin.get<ExecuteCommandUseCase>())
        assertNotNull(koin.get<RequestMessagePageUseCase>())
        assertNotNull(koin.get<ArchiveSessionUseCase>())
        assertNotNull(koin.get<RenameSessionUseCase>())
    }

    @Test
    fun resolvesRepositoryAndGatewayBindings() {
        val koin = startTestKoin()

        val repo = koin.get<ServerRepository>()
        assertSame(repo, koin.get<ConnectionGateway>())
        assertSame(repo, koin.get<ProjectGateway>())
        assertSame(repo, koin.get<CommandGateway>())
        assertSame(repo, koin.get<MessageGateway>())
        assertSame(repo, koin.get<StreamGateway>())
    }

    @Test
    fun usesUpdatedViewModelConstructorSignatures() {
        assertEquals(
            listOf(
                SessionReadService::class.java,
                DispatcherProvider::class.java,
                FocusSessionUseCase::class.java,
                SendMessageUseCase::class.java,
                ExecuteCommandUseCase::class.java,
                RequestMessagePageUseCase::class.java,
                ArchiveSessionUseCase::class.java,
                RenameSessionUseCase::class.java,
                CreateSessionUseCase::class.java,
                RefreshServerUseCase::class.java,
            ),
            ConversationViewModel::class.java.declaredConstructors.single().parameterTypes.toList(),
        )
        assertEquals(
            listOf(
                SessionReadService::class.java,
                DispatcherProvider::class.java,
                SelectProjectUseCase::class.java,
                FocusSessionUseCase::class.java,
                ToggleProjectFavoriteUseCase::class.java,
                RemoveProjectUseCase::class.java,
                SetServerUrlUseCase::class.java,
                RefreshServerUseCase::class.java,
                CreateSessionUseCase::class.java,
            ),
            ManageViewModel::class.java.declaredConstructors.single().parameterTypes.toList(),
        )
        assertEquals(
            listOf(LogsService::class.java, DispatcherProvider::class.java),
            LogsViewModel::class.java.declaredConstructors.single().parameterTypes.toList(),
        )
    }

    private fun startTestKoin(): Koin {
        return startKoin {
            allowOverride(true)
            modules(
                appModule,
                testCoroutineModule(),
                module {
                    single { db() }
                    single<MdnsGateway> { FakeMdnsGateway() }
                    single<ConnectivityGateway> { FakeConnectivityGateway() }
                    single<ServerGateway> { FakeServerGateway() }
                    single<LogGateway> { FakeLogGateway() }
                }
            )
        }.koin
    }

    private fun viewModelRead(viewModel: Any): SessionReadService {
        val field = viewModel.javaClass.getDeclaredField("read")
        field.isAccessible = true
        return field.get(viewModel) as SessionReadService
    }

    private fun db(): AppDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AppDatabase.Schema.create(driver)
        return AppDatabase(driver)
    }
}

private class FakeMdnsGateway : MdnsGateway {
    override fun discover(): Flow<MdnsEntry> {
        return emptyFlow()
    }
}

private class FakeConnectivityGateway : ConnectivityGateway {
    private val onlineState = MutableStateFlow(true)
    private val changedState = MutableStateFlow(0L)

    override val online: StateFlow<Boolean> = onlineState.asStateFlow()
    override val changed: StateFlow<Long> = changedState.asStateFlow()
}

private class FakeLogGateway : LogGateway {
    override fun log(
        level: de.chennemann.opencode.mobile.domain.session.LogLevel,
        unit: de.chennemann.opencode.mobile.domain.session.LogUnit,
        tag: String,
        event: String,
        message: String,
        context: Map<String, String>,
        error: Throwable?,
    ) {
    }
}

private class FakeServerGateway : ServerGateway {
    override suspend fun health(baseUrl: String): Health {
        return Health(healthy = true, version = "test")
    }

    override suspend fun projects(baseUrl: String): List<ProjectInfo> {
        return listOf(
            ProjectInfo(
                id = "p1",
                worktree = "/repo/main",
                name = "Main",
                sandboxes = listOf("/repo/main/s1"),
            )
        )
    }

    override suspend fun sessions(baseUrl: String, worktree: String, limit: Int?): List<SessionInfo> {
        return emptyList()
    }

    override suspend fun archiveSession(baseUrl: String, sessionId: String, directory: String) {
    }

    override suspend fun renameSession(baseUrl: String, sessionId: String, directory: String, title: String) {
    }

    override suspend fun createSession(baseUrl: String, worktree: String, title: String): SessionInfo {
        return SessionInfo(
            id = "created",
            title = title,
            version = "1",
            directory = worktree,
        )
    }

    override suspend fun commands(baseUrl: String, directory: String): List<CommandInfo> {
        return emptyList()
    }

    override suspend fun sessionMessages(baseUrl: String, sessionId: String, directory: String, limit: Int?): List<SessionMessageInfo> {
        return emptyList()
    }

    override suspend fun sessionUpdatedAt(baseUrl: String, sessionId: String, directory: String): Long? {
        return null
    }

    override suspend fun sessionStatus(baseUrl: String, directory: String): Map<String, String> {
        return emptyMap()
    }

    override suspend fun streamEvents(
        baseUrl: String,
        lastEventId: String?,
        onRawEvent: suspend (String) -> Unit,
        onEvent: suspend (GlobalStreamEvent) -> Unit,
    ): String? {
        onRawEvent("noop")
        onEvent(
            GlobalStreamEvent(
                directory = "global",
                type = "server.heartbeat",
                properties = JsonObject(emptyMap()),
                id = null,
                retry = null,
            )
        )
        return null
    }

    override suspend fun sendMessage(baseUrl: String, sessionId: String, directory: String, text: String, agent: String): PromptResponseIds {
        return PromptResponseIds(parentId = "user", messageId = "assistant")
    }

    override suspend fun sendCommand(baseUrl: String, sessionId: String, directory: String, name: String, arguments: String, agent: String): PromptResponseIds {
        return PromptResponseIds(parentId = "user", messageId = "assistant")
    }
}
