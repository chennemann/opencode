package de.chennemann.opencode.mobile.data

import de.chennemann.opencode.mobile.home.ServerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ServerRepository(
    private val service: ServerService,
) {
    private val state = MutableStateFlow<ServerState>(ServerState.Idle)
    val status: StateFlow<ServerState> = state.asStateFlow()

    suspend fun refresh() {
        state.value = ServerState.Loading
        val result = runCatching { service.health() }
        state.value = result.fold(
            onSuccess = { ServerState.Connected(it.version) },
            onFailure = { ServerState.Failed(it.message ?: "Connection failed") },
        )
    }
}
