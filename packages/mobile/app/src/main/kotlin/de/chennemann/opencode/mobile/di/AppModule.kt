package de.chennemann.opencode.mobile.di

import de.chennemann.opencode.mobile.data.AndroidLogGateway
import de.chennemann.opencode.mobile.data.MdnsService
import de.chennemann.opencode.mobile.data.MdnsGateway
import de.chennemann.opencode.mobile.data.NetworkService
import de.chennemann.opencode.mobile.data.ServerRepository
import de.chennemann.opencode.mobile.data.ServerService
import de.chennemann.opencode.mobile.data.ServerGateway
import de.chennemann.opencode.mobile.data.SessionCacheRepository
import de.chennemann.opencode.mobile.db.AppDatabase
import de.chennemann.opencode.mobile.domain.message.MessageDecorator
import de.chennemann.opencode.mobile.domain.message.MessagePartParser
import de.chennemann.opencode.mobile.domain.session.CommandGateway
import de.chennemann.opencode.mobile.domain.session.ConnectivityGateway
import de.chennemann.opencode.mobile.domain.session.ConnectionGateway
import de.chennemann.opencode.mobile.domain.session.FocusedMessageProjector
import de.chennemann.opencode.mobile.domain.session.LogGateway
import de.chennemann.opencode.mobile.domain.session.MessageGateway
import de.chennemann.opencode.mobile.domain.session.ProjectGateway
import de.chennemann.opencode.mobile.domain.session.ReconcileCoordinator
import de.chennemann.opencode.mobile.domain.session.SessionCacheGateway
import de.chennemann.opencode.mobile.domain.session.SessionEventReducer
import de.chennemann.opencode.mobile.domain.session.SessionService
import de.chennemann.opencode.mobile.domain.session.SessionSyncPlanner
import de.chennemann.opencode.mobile.domain.session.SessionStreamCoordinator
import de.chennemann.opencode.mobile.domain.session.StreamGateway
import de.chennemann.opencode.mobile.ui.conversation.ConversationViewModel
import de.chennemann.opencode.mobile.ui.manage.ManageViewModel
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module
import java.util.concurrent.TimeUnit

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
    single { NetworkService(get()) }
    single<DispatcherProvider> { DefaultDispatcherProvider() }
    single<CoroutineScope>(named(AppScopeName)) {
        CoroutineScope(SupervisorJob() + get<DispatcherProvider>().default)
    }
    single<ConnectivityGateway> { get<NetworkService>() }
    single<LogGateway> { AndroidLogGateway() }
    single<ServerGateway> { ServerService(get(), get()) }
    single { ServerRepository(get(), get(), get(), get(), get()) }
    single { SessionCacheRepository(get(), get()) }
    single<ConnectionGateway> { get<ServerRepository>() }
    single<ProjectGateway> { get<ServerRepository>() }
    single<CommandGateway> { get<ServerRepository>() }
    single<MessageGateway> { get<ServerRepository>() }
    single<StreamGateway> { get<ServerRepository>() }
    single<SessionCacheGateway> { get<SessionCacheRepository>() }
    single { MessagePartParser() }
    single { MessageDecorator() }
    single { FocusedMessageProjector(get()) }
    single { SessionSyncPlanner() }
    single { SessionEventReducer() }
    single { SessionStreamCoordinator(get(), get(), get(), get()) }
    single { ReconcileCoordinator() }
    single(createdAtStart = true) {
        SessionService(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get())
            .also { it.start(get(named(AppScopeName))) }
    }
    viewModel { ConversationViewModel(get()) }
    viewModel { ManageViewModel(get()) }
}
