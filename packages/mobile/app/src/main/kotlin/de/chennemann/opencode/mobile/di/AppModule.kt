package de.chennemann.opencode.mobile.di

import de.chennemann.opencode.mobile.api.apis.DefaultApi
import de.chennemann.opencode.mobile.data.ServerRepository
import de.chennemann.opencode.mobile.data.ServerService
import de.chennemann.opencode.mobile.home.HomeViewModel
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.serialization.json.Json
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

private const val ApiUrl = "http://opencode.local:4000"

val appModule = module {
    single {
        Json {
            ignoreUnknownKeys = true
        }
    }
    single { OkHttp.create() }
    single {
        DefaultApi(
            baseUrl = ApiUrl,
            httpClientEngine = get(),
        )
    }
    single { ServerService(get(), get()) }
    single { ServerRepository(get()) }
    viewModel { HomeViewModel(get()) }
}
