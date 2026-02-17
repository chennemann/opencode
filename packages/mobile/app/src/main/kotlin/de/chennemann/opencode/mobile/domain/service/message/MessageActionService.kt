package de.chennemann.opencode.mobile.domain.service.message

import de.chennemann.opencode.mobile.domain.service.model.CommandInput
import de.chennemann.opencode.mobile.domain.service.model.CommandResult
import de.chennemann.opencode.mobile.domain.service.model.SendMessageInput
import de.chennemann.opencode.mobile.domain.service.model.SendMessageResult

interface MessageActionService {
    suspend fun send(input: SendMessageInput): SendMessageResult
    suspend fun execute(input: CommandInput): CommandResult
}
