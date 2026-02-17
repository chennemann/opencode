package de.chennemann.opencode.mobile.domain.session

interface StreamGateway {
    suspend fun streamEvents(lastEventId: String?, onRawEvent: suspend (String) -> Unit, onEvent: suspend (SessionStreamEvent) -> Unit): String?
}
