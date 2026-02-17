package de.chennemann.opencode.mobile.domain.service.connection

import de.chennemann.opencode.mobile.domain.service.model.RefreshInput
import de.chennemann.opencode.mobile.domain.session.ConnectionGateway

class DefaultConnectionActionService(
    private val connection: ConnectionGateway,
) : ConnectionActionService {
    override suspend fun refresh(input: RefreshInput) {
        val endpoint = input.endpoint.trim()
        if (endpoint.isBlank()) return
        connection.setUrl(endpoint)
        connection.refresh(input.userInitiated)
    }
}
