package de.chennemann.opencode.mobile.domain.service.connection

import de.chennemann.opencode.mobile.domain.service.model.RefreshInput
import de.chennemann.opencode.mobile.domain.service.model.RefreshResult

interface ConnectionActionService {
    suspend fun refresh(input: RefreshInput): RefreshResult
}
