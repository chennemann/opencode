package de.chennemann.opencode.mobile.di

import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import de.chennemann.opencode.mobile.data.AndroidLogGateway
import de.chennemann.opencode.mobile.data.ApiServerSource
import de.chennemann.opencode.mobile.data.LocalLogRepository
import de.chennemann.opencode.mobile.data.MdnsGateway
import de.chennemann.opencode.mobile.data.MdnsService
import de.chennemann.opencode.mobile.data.NetworkService
import de.chennemann.opencode.mobile.data.ServerGateway
import de.chennemann.opencode.mobile.data.ServerRepository
import de.chennemann.opencode.mobile.data.repository.CommandRepository
import de.chennemann.opencode.mobile.data.repository.ConnectionRepository
import de.chennemann.opencode.mobile.data.repository.LogRepository
import de.chennemann.opencode.mobile.data.repository.PreferencesRepository
import de.chennemann.opencode.mobile.data.repository.ProjectRepository
import de.chennemann.opencode.mobile.data.repository.SessionRepository
import de.chennemann.opencode.mobile.data.repository.sql.SqlDelightCommandRepository
import de.chennemann.opencode.mobile.data.repository.sql.SqlDelightConnectionRepository
import de.chennemann.opencode.mobile.data.repository.sql.SqlDelightLogRepository
import de.chennemann.opencode.mobile.data.repository.sql.SqlDelightOutboxStore
import de.chennemann.opencode.mobile.data.repository.sql.SqlDelightPreferencesRepository
import de.chennemann.opencode.mobile.data.repository.sql.SqlDelightProjectRepository
import de.chennemann.opencode.mobile.data.repository.sql.SqlDelightSessionRepository
import de.chennemann.opencode.mobile.db.AppDatabase
import de.chennemann.opencode.mobile.domain.service.connection.ConnectionActionService
import de.chennemann.opencode.mobile.domain.service.connection.DefaultConnectionActionService
import de.chennemann.opencode.mobile.domain.service.logs.DefaultLogsService
import de.chennemann.opencode.mobile.domain.service.logs.LogsService
import de.chennemann.opencode.mobile.domain.service.message.DefaultMessageActionService
import de.chennemann.opencode.mobile.domain.service.message.MessageActionService
import de.chennemann.opencode.mobile.domain.service.outbox.DefaultOutboxService
import de.chennemann.opencode.mobile.domain.service.outbox.OutboxMutationStore
import de.chennemann.opencode.mobile.domain.service.outbox.OutboxService
import de.chennemann.opencode.mobile.domain.service.outbox.OutboxStore
import de.chennemann.opencode.mobile.domain.service.outbox.PendingMessageOutcomeStore
import de.chennemann.opencode.mobile.domain.service.project.DefaultProjectActionService
import de.chennemann.opencode.mobile.domain.service.project.ProjectActionService
import de.chennemann.opencode.mobile.domain.service.session.DefaultSessionActionService
import de.chennemann.opencode.mobile.domain.service.session.DefaultSessionReadService
import de.chennemann.opencode.mobile.domain.service.session.SessionActionService
import de.chennemann.opencode.mobile.domain.service.session.SessionReadService
import de.chennemann.opencode.mobile.domain.service.sync.DefaultProjectSyncService
import de.chennemann.opencode.mobile.domain.service.sync.DefaultServerService
import de.chennemann.opencode.mobile.domain.service.sync.DefaultSessionStateSyncService
import de.chennemann.opencode.mobile.domain.service.sync.DefaultSyncRuntime
import de.chennemann.opencode.mobile.domain.service.sync.ProjectSyncService
import de.chennemann.opencode.mobile.domain.service.sync.ServerService as SyncServerService
import de.chennemann.opencode.mobile.domain.service.sync.SessionStateSyncService
import de.chennemann.opencode.mobile.domain.service.sync.SyncRuntime
import de.chennemann.opencode.mobile.domain.session.CommandGateway
import de.chennemann.opencode.mobile.domain.session.ConnectivityGateway
import de.chennemann.opencode.mobile.domain.session.ConnectionGateway
import de.chennemann.opencode.mobile.domain.session.LogGateway
import de.chennemann.opencode.mobile.domain.session.LogRedactor
import de.chennemann.opencode.mobile.domain.session.LogStoreGateway
import de.chennemann.opencode.mobile.domain.session.MessageGateway
import de.chennemann.opencode.mobile.domain.session.ProjectGateway
import de.chennemann.opencode.mobile.domain.session.StreamGateway
import de.chennemann.opencode.mobile.domain.usecase.connection.RefreshServerUseCase
import de.chennemann.opencode.mobile.domain.usecase.message.ExecuteCommandUseCase
import de.chennemann.opencode.mobile.domain.usecase.message.SendMessageUseCase
import de.chennemann.opencode.mobile.domain.usecase.project.RemoveProjectUseCase
import de.chennemann.opencode.mobile.domain.usecase.project.RequestProjectRefreshUseCase
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
import io.ktor.client.engine.okhttp.OkHttp
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module

val appModule = module {
    single {
        Json {
            ignoreUnknownKeys = true
        }
    }
    single {
        OkHttp.create {
            config {
                connectTimeout(5, TimeUnit.SECONDS)
                readTimeout(75, TimeUnit.SECONDS)
                writeTimeout(30, TimeUnit.SECONDS)
            }
        }
    }
    single {
        AppDatabase(
            AndroidSqliteDriver(
                AppDatabase.Schema,
                get(),
                "app.db",
            )
        )
    }
    single<MdnsGateway> { MdnsService(get()) }
    single { NetworkService(get<android.content.Context>()) }
    single<DispatcherProvider> { DefaultDispatcherProvider() }
    single<CoroutineScope>(named(AppScopeName)) {
        CoroutineScope(SupervisorJob() + get<DispatcherProvider>().default)
    }
    single<ConnectivityGateway> { get<NetworkService>() }
    single { LogRedactor() }
    single<LogStoreGateway> { LocalLogRepository(get(), get(), get()) }
    single<LogGateway> { AndroidLogGateway(get(), get(named(AppScopeName)), get()) }
    single<ServerGateway> { ApiServerSource(get(), get()) }
    single { ServerRepository(get(), get(), get(), get(), get(), get()) }
    single<ConnectionGateway> { get<ServerRepository>() }
    single<ProjectGateway> { get<ServerRepository>() }
    single<CommandGateway> { get<ServerRepository>() }
    single<MessageGateway> { get<ServerRepository>() }
    single<StreamGateway> { get<ServerRepository>() }
    single<SessionRepository> { SqlDelightSessionRepository(get(), get(), get()) }
    single<ProjectRepository> { SqlDelightProjectRepository(get(), get(), get()) }
    single<CommandRepository> { SqlDelightCommandRepository(get(), get()) }
    single<ConnectionRepository> { SqlDelightConnectionRepository(get(), get()) }
    single<LogRepository> { SqlDelightLogRepository(get(), get(), get()) }
    single<PreferencesRepository> { SqlDelightPreferencesRepository(get(), get()) }
    single { SqlDelightOutboxStore(get(), get()) }
    single<OutboxStore> { get<SqlDelightOutboxStore>() }
    single<OutboxMutationStore> { get<SqlDelightOutboxStore>() }
    single<PendingMessageOutcomeStore> { get<SqlDelightOutboxStore>() }
    single<SessionReadService> { DefaultSessionReadService(get(), get(), get(), get(), get(named(AppScopeName))) }
    single(createdAtStart = true) {
        get<ConnectionGateway>().start(get(named(AppScopeName)))
    }
    single<SyncServerService> { DefaultServerService(get(), get(), get()) }
    single<ProjectSyncService> { DefaultProjectSyncService(get(), get(), get(), get()) }
    single<SessionStateSyncService> { DefaultSessionStateSyncService(get(), get(), get(), get(), get()) }
    single<SyncRuntime>(createdAtStart = true) {
        DefaultSyncRuntime(get(), get(), get(), get(), get())
            .also { it.start(get(named(AppScopeName))) }
    }
    single<ProjectActionService> { DefaultProjectActionService(get(), get()) }
    single<SessionActionService> { DefaultSessionActionService(get(), get()) }
    single<MessageActionService> { DefaultMessageActionService(get(), get(), get()) }
    single<OutboxService> { DefaultOutboxService(get(), get(), get(), get(), get(), get()) }
    single<ConnectionActionService> { DefaultConnectionActionService(get()) }
    single<LogsService> { DefaultLogsService(get(), get()) }
    single { SelectProjectUseCase(get()) }
    single { RequestProjectRefreshUseCase(get()) }
    single { ToggleProjectFavoriteUseCase(get()) }
    single { RemoveProjectUseCase(get()) }
    single { FocusSessionUseCase(get()) }
    single { CreateSessionUseCase(get(), get(), get()) }
    single { RefreshServerUseCase(get()) }
    single { SendMessageUseCase(get()) }
    single { ExecuteCommandUseCase(get()) }
    single { ArchiveSessionUseCase(get()) }
    single { RenameSessionUseCase(get()) }
    single { RequestMessagePageUseCase(get()) }
    viewModel { ConversationViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { ManageViewModel(get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { LogsViewModel(get(), get()) }
}
