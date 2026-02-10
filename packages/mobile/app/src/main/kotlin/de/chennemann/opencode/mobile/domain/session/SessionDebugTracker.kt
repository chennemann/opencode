package de.chennemann.opencode.mobile.domain.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SessionDebugTracker(
    private val log: LogGateway,
    private val tag: String,
    private val limit: Int = 300,
) {
    private val stateFlow = MutableStateFlow(DebugState())
    private val lines = ArrayDeque<String>()
    private var seen = 0
    private var raw = 0
    private var applied = 0
    private var dropped = 0
    private var connected = 0
    private var errors = 0
    private var syncRuns = 0
    private var syncFails = 0

    val state: StateFlow<DebugState> = stateFlow.asStateFlow()

    fun onSyncRun() {
        syncRuns += 1
        stateFlow.value = stateFlow.value.copy(syncRuns = syncRuns)
    }

    fun onSyncFail() {
        syncFails += 1
        stateFlow.value = stateFlow.value.copy(syncFails = syncFails)
    }

    fun onSeen() {
        seen += 1
        stateFlow.value = stateFlow.value.copy(sseSeen = seen)
    }

    fun onConnected() {
        connected += 1
        stateFlow.value = stateFlow.value.copy(sseConnected = connected, lastStreamError = null)
    }

    fun onRaw(chunk: String) {
        raw += 1
        stateFlow.value = stateFlow.value.copy(sseRaw = raw)
        push("raw ${chunk.replace("\n", "\\n")}")
    }

    fun onStreamError(reason: String) {
        errors += 1
        stateFlow.value = stateFlow.value.copy(sseErrors = errors, lastStreamError = reason)
    }

    fun onApplied(type: String, sessionId: String?) {
        applied += 1
        stateFlow.value = stateFlow.value.copy(sseApplied = applied)
        if (applied % 5 != 0) return
        log.debug(
            tag,
            "sse applied=$applied dropped=$dropped seen=$seen connected=$connected errors=$errors last=$type session=$sessionId",
        )
    }

    fun onDropped(type: String, reason: String) {
        dropped += 1
        stateFlow.value = stateFlow.value.copy(sseDropped = dropped, lastDrop = "$type: $reason")
        push("drop type=$type reason=$reason")
        log.warn(tag, "sse drop type=$type reason=$reason seen=$seen applied=$applied dropped=$dropped")
    }

    fun push(line: String) {
        val stamp = System.currentTimeMillis().toString()
        lines.addLast("$stamp | $line")
        while (lines.size > limit) {
            lines.removeFirst()
        }
        stateFlow.value = stateFlow.value.copy(sseLog = lines.toList())
    }

    fun clear() {
        lines.clear()
        stateFlow.value = stateFlow.value.copy(sseLog = emptyList())
    }
}
