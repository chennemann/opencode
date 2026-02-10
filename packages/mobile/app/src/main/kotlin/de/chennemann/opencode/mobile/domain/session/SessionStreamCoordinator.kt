package de.chennemann.opencode.mobile.domain.session

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class SessionStreamCoordinator(
    private val conn: ConnectionGateway,
    private val feed: StreamGateway,
    private val net: ConnectivityGateway,
    private val log: LogGateway,
) {
    fun start(scope: CoroutineScope, onEvent: suspend (SessionStreamEvent) -> Unit): Job {
        return scope.launch {
            var attempt = 0
            var cursor = runCatching { feed.streamCursor() }.getOrNull()
            while (isActive) {
                val endpoint = conn.endpoint.value
                log.debug(SessionLogTag, "sse connect attempt=${attempt + 1} endpoint=$endpoint cursor=$cursor")
                val result = runCatching {
                    feed.streamEvents(cursor, { chunk ->
                        log.debug(
                            SessionLogTag,
                            "sse raw len=${chunk.length} head=${chunk.take(140).replace("\n", "\\n")}",
                        )
                    }) { event ->
                        log.debug(SessionLogTag, "sse event type=${event.type} dir=${event.directory} id=${event.id}")
                        if (!event.id.isNullOrBlank()) {
                            cursor = event.id
                            runCatching { feed.setStreamCursor(cursor) }
                        }
                        onEvent(event)
                    }
                }
                if (result.isSuccess) {
                    log.debug(SessionLogTag, "sse stream ended normally reconnecting")
                    attempt = 0
                    continue
                }
                val reason = result.exceptionOrNull()?.message ?: "unknown stream error"
                log.warn(SessionLogTag, "sse stream error attempt=${attempt + 1} reason=$reason")
                attempt += 1
                val seen = net.changed.value
                val mode = if (!net.online.value) "offline; waiting for network change" else "waiting for network change before reconnect"
                log.debug(SessionLogTag, mode)
                net.changed.first { it > seen }
                log.debug(SessionLogTag, "network changed; retrying stream")
                delay(StreamRestartDelay)
            }
        }
    }
}

private const val SessionLogTag = "SessionService"
private const val StreamRestartDelay = 3000L
