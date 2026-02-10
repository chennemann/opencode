package de.chennemann.opencode.mobile.domain.session

interface MessageGateway {
    suspend fun messages(sessionId: String, directory: String, limit: Int? = 400): List<SessionMessage>

    suspend fun sendMessage(sessionId: String, directory: String, text: String)
}
