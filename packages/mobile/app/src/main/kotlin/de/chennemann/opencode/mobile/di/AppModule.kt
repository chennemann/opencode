package de.chennemann.opencode.mobile.di

import de.chennemann.opencode.mobile.data.ServerRepository
import de.chennemann.opencode.mobile.data.ServerService
import de.chennemann.opencode.mobile.home.HomeViewModel
import de.chennemann.opencode.mobile.data.MdnsService
import de.chennemann.opencode.mobile.db.AppDatabase
import de.chennemann.opencode.mobile.service.HomeService
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
    single { ServerService(get(), get()) }
    single { ServerRepository(get(), get(), get()) }
    single { HomeService(get(), get()) }
    viewModel { HomeViewModel(get()) }
}
