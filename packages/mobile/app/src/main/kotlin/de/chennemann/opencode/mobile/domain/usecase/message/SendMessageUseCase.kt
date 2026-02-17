package de.chennemann.opencode.mobile.domain.usecase.message

import de.chennemann.opencode.mobile.domain.service.message.MessageActionService
import de.chennemann.opencode.mobile.domain.service.model.SendMessageInput
import de.chennemann.opencode.mobile.domain.service.model.SendMessageResult

class SendMessageUseCase(
    private val action: MessageActionService,
) {
    suspend operator fun invoke(input: SendMessageInput): SendMessageResult {
        return action.send(input)
    }
}
