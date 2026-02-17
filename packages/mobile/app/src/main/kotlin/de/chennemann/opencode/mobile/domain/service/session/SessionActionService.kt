package de.chennemann.opencode.mobile.domain.service.session

import de.chennemann.opencode.mobile.data.repository.SyncReason
import de.chennemann.opencode.mobile.domain.service.model.MessagePageInput
import de.chennemann.opencode.mobile.domain.service.model.MessagePageRequestResult
import de.chennemann.opencode.mobile.domain.service.model.RenameInput

interface SessionActionService {
    suspend fun focus(sessionId: String)
    suspend fun requestMessagePage(input: MessagePageInput): MessagePageRequestResult
    suspend fun archive(sessionId: String, directory: String? = null)
    suspend fun rename(input: RenameInput)
    suspend fun requestSync(sessionId: String, reason: SyncReason)
}
