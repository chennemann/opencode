package de.chennemann.opencode.mobile

import android.app.Application
import de.chennemann.opencode.mobile.di.AppScopeName
import de.chennemann.opencode.mobile.di.appModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import org.koin.android.ext.android.getKoin
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.core.context.startKoin

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@App)
            modules(appModule)
        }
    }

    override fun onTerminate() {
        getKoin().getOrNull<CoroutineScope>(named(AppScopeName))?.cancel()
        super.onTerminate()
    }
}
