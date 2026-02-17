package de.chennemann.opencode.mobile.domain.usecase.connection

import de.chennemann.opencode.mobile.domain.service.connection.ConnectionActionService
import de.chennemann.opencode.mobile.domain.service.model.RefreshInput

class RefreshServerUseCase(
    private val action: ConnectionActionService,
) {
    suspend operator fun invoke(url: String) {
        val endpoint = url.trim()
        if (endpoint.isBlank()) return
        action.refresh(
            RefreshInput(
                endpoint = endpoint,
                userInitiated = true,
            )
        )
    }
}
