package de.chennemann.opencode.mobile.domain.usecase.session

import de.chennemann.opencode.mobile.domain.service.session.SessionActionService

class ArchiveSessionUseCase(
    private val action: SessionActionService,
) {
    suspend operator fun invoke(sessionId: String) {
        val id = sessionId.trim()
        if (id.isBlank()) return
        action.archive(id)
    }
}
