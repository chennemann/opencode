package de.chennemann.opencode.mobile.domain.usecase.session

import de.chennemann.opencode.mobile.domain.service.model.MessagePageInput
import de.chennemann.opencode.mobile.domain.service.model.MessagePageRequestResult
import de.chennemann.opencode.mobile.domain.service.session.SessionActionService

class RequestMessagePageUseCase(
    private val action: SessionActionService,
) {
    suspend operator fun invoke(input: MessagePageInput): MessagePageRequestResult {
        val id = input.sessionId.trim()
        if (id.isBlank()) {
            return MessagePageRequestResult(accepted = false, reason = "session_id_blank")
        }
        return action.requestMessagePage(
            input.copy(
                sessionId = id,
                beforeMessageId = input.beforeMessageId?.trim()?.ifBlank { null },
            )
        )
    }
}
