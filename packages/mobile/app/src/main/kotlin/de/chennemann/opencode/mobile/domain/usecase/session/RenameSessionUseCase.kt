package de.chennemann.opencode.mobile.domain.usecase.session

import de.chennemann.opencode.mobile.domain.service.model.RenameInput
import de.chennemann.opencode.mobile.domain.service.session.SessionActionService

class RenameSessionUseCase(
    private val action: SessionActionService,
) {
    suspend operator fun invoke(input: RenameInput) {
        val id = input.sessionId.trim()
        val title = input.title.trim()
        if (id.isBlank() || title.isBlank()) return
        action.rename(
            RenameInput(
                sessionId = id,
                title = title,
                directory = input.directory?.trim(),
            )
        )
    }
}
