package de.chennemann.opencode.mobile.home

data class HomeState(
    val url: String,
    val discovered: String?,
    val status: ServerState,
)
