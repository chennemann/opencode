package de.chennemann.opencode.mobile.di

import de.chennemann.opencode.mobile.data.AndroidLogGateway
import de.chennemann.opencode.mobile.data.ServerRepository
import de.chennemann.opencode.mobile.data.ServerService
import de.chennemann.opencode.mobile.data.SessionCacheRepository
import de.chennemann.opencode.mobile.domain.message.MessageDecorator
import de.chennemann.opencode.mobile.domain.message.MessagePartParser
import de.chennemann.opencode.mobile.domain.session.ConnectivityGateway
import de.chennemann.opencode.mobile.domain.session.ConnectionGateway
import de.chennemann.opencode.mobile.domain.session.FocusedMessageProjector
import de.chennemann.opencode.mobile.domain.session.MessageGateway
import de.chennemann.opencode.mobile.domain.session.ProjectGateway
import de.chennemann.opencode.mobile.domain.session.SessionCacheGateway
import de.chennemann.opencode.mobile.domain.session.SessionDomainService
import de.chennemann.opencode.mobile.domain.session.SessionEventReducer
import de.chennemann.opencode.mobile.domain.session.SessionStreamCoordinator
import de.chennemann.opencode.mobile.domain.session.StreamGateway
import de.chennemann.opencode.mobile.domain.session.LogGateway
import de.chennemann.opencode.mobile.ui.conversation.ConversationViewModel
import de.chennemann.opencode.mobile.data.MdnsService
import de.chennemann.opencode.mobile.data.NetworkService
import de.chennemann.opencode.mobile.db.AppDatabase
import de.chennemann.opencode.mobile.ui.manage.ManageViewModel
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.serialization.json.Json
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import org.koin.androidx.viewmodel.dsl.viewModel
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
    single { MdnsService(get()) }
    single { NetworkService(get()) }
    single<ConnectivityGateway> { get<NetworkService>() }
    single<LogGateway> { AndroidLogGateway() }
    single { ServerService(get(), get()) }
    single { ServerRepository(get(), get(), get(), get()) }
    single { SessionCacheRepository(get()) }
    single<ConnectionGateway> { get<ServerRepository>() }
    single<ProjectGateway> { get<ServerRepository>() }
    single<MessageGateway> { get<ServerRepository>() }
    single<StreamGateway> { get<ServerRepository>() }
    single<SessionCacheGateway> { get<SessionCacheRepository>() }
    single { MessagePartParser() }
    single { MessageDecorator() }
    single { FocusedMessageProjector(get()) }
    single { SessionEventReducer() }
    single { SessionStreamCoordinator(get(), get(), get(), get()) }
    single { SessionDomainService(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { ConversationViewModel(get()) }
    viewModel { ManageViewModel(get()) }
}
