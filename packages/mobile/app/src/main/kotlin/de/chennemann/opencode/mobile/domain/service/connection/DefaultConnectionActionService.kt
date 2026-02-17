package de.chennemann.opencode.mobile.domain.service.connection

import de.chennemann.opencode.mobile.domain.service.model.RefreshInput
import de.chennemann.opencode.mobile.domain.service.model.RefreshResult
import de.chennemann.opencode.mobile.domain.session.ConnectionGateway
import de.chennemann.opencode.mobile.domain.session.ConnectionState

class DefaultConnectionActionService(
    private val connection: ConnectionGateway,
) : ConnectionActionService {
    override suspend fun refresh(input: RefreshInput): RefreshResult {
        val endpoint = input.endpoint.trim()
        if (endpoint.isBlank()) {
            return RefreshResult(
                accepted = false,
                reason = "endpoint_blank",
            )
        }
        connection.setUrl(endpoint)
        connection.refresh(input.userInitiated)
        return when (val value = connection.status.value) {
            is ConnectionState.Connected -> {
                RefreshResult(
                    accepted = true,
                    reason = null,
                )
            }

            is ConnectionState.Failed -> {
                RefreshResult(
                    accepted = false,
                    reason = value.reason,
                )
            }

            is ConnectionState.Loading -> {
                RefreshResult(
                    accepted = false,
                    reason = "connection_loading",
                )
            }

            is ConnectionState.Idle -> {
                RefreshResult(
                    accepted = false,
                    reason = "connection_idle",
                )
            }
        }
    }
}
