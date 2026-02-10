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
    fun start(scope: CoroutineScope, debug: SessionDebugTracker, onEvent: suspend (SessionStreamEvent) -> Unit): Job {
        return scope.launch {
            var attempt = 0
            var cursor = runCatching { feed.streamCursor() }.getOrNull()
            while (isActive) {
                val endpoint = conn.endpoint.value
                log.debug(SessionLogTag, "sse connect attempt=${attempt + 1} endpoint=$endpoint cursor=$cursor")
                debug.push("connect attempt=${attempt + 1} endpoint=$endpoint cursor=$cursor")
                val result = runCatching {
                    debug.onConnected()
                    feed.streamEvents(cursor, { chunk ->
                        debug.onRaw(chunk)
                    }) { event ->
                        log.debug(SessionLogTag, "sse event type=${event.type} dir=${event.directory} id=${event.id}")
                        debug.push(
                            "event type=${event.type} dir=${event.directory} id=${event.id} retry=${event.retry} properties=${event.properties}",
                        )
                        if (!event.id.isNullOrBlank()) {
                            cursor = event.id
                            runCatching { feed.setStreamCursor(cursor) }
                        }
                        onEvent(event)
                    }
                }
                if (result.isSuccess) {
                    log.debug(SessionLogTag, "sse stream ended normally reconnecting")
                    debug.push("stream ended; reconnecting")
                    attempt = 0
                    continue
                }
                val reason = result.exceptionOrNull()?.message ?: "unknown stream error"
                debug.onStreamError(reason)
                log.warn(SessionLogTag, "sse stream error attempt=${attempt + 1} reason=$reason")
                debug.push("error attempt=${attempt + 1} reason=$reason")
                attempt += 1
                val seen = net.changed.value
                if (!net.online.value) {
                    debug.push("offline; waiting for network change")
                } else {
                    debug.push("waiting for network change before reconnect")
                }
                net.changed.first { it > seen }
                debug.push("network changed; retrying stream")
                delay(StreamRestartDelay)
            }
        }
    }
}

private const val SessionLogTag = "SessionDomainService"
private const val StreamRestartDelay = 3000L
