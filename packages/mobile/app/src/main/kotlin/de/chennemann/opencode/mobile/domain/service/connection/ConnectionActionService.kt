package de.chennemann.opencode.mobile.domain.service.connection

import de.chennemann.opencode.mobile.domain.service.model.RefreshInput

interface ConnectionActionService {
    suspend fun refresh(input: RefreshInput)
}
