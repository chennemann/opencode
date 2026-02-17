package de.chennemann.opencode.mobile.domain.service.sync

import de.chennemann.opencode.mobile.di.DispatcherProvider
import de.chennemann.opencode.mobile.domain.service.logs.LogsService
import de.chennemann.opencode.mobile.domain.service.outbox.OutboxService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class DefaultSyncRuntime(
    private val project: ProjectSyncService,
    private val session: SessionStateSyncService,
    private val outbox: OutboxService,
    private val logs: LogsService,
    private val dispatchers: DispatcherProvider,
    private val intervalMs: Long = 15_000,
) : SyncRuntime {
    private var job: Job? = null

    override fun start(scope: CoroutineScope) {
        if (job != null) return
        job = scope.launch(dispatchers.default) {
            while (isActive) {
                tick(System.currentTimeMillis())
                delay(intervalMs)
            }
        }
    }

    override suspend fun tick(now: Long) {
        runCatching { project.run() }
        runCatching { session.evaluateStreamPolicy(now) }
        runCatching { session.runDue(now) }
        runCatching { outbox.drain() }
        runCatching { logs.runRetention(now) }
    }
}
