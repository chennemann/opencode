package de.chennemann.opencode.mobile.domain.session

sealed interface ServerState {
    data object Idle : ServerState

    data object Loading : ServerState

    data class Connected(val url: String, val version: String) : ServerState

    data class Failed(val reason: String) : ServerState
}
