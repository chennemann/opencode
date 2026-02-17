package de.chennemann.opencode.mobile.domain.usecase.message

import de.chennemann.opencode.mobile.domain.service.message.MessageActionService
import de.chennemann.opencode.mobile.domain.service.model.CommandInput
import de.chennemann.opencode.mobile.domain.service.model.CommandResult

class ExecuteCommandUseCase(
    private val action: MessageActionService,
) {
    suspend operator fun invoke(input: CommandInput): CommandResult {
        return action.execute(input)
    }
}
