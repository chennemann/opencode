package de.chennemann.opencode.mobile.domain.usecase.connection

import de.chennemann.opencode.mobile.domain.session.ConnectionGateway

class SetServerUrlUseCase(
    private val connection: ConnectionGateway,
) {
    suspend operator fun invoke(url: String) {
        val endpoint = url.trim()
        if (endpoint.isBlank()) return
        connection.setUrl(endpoint)
    }
}
