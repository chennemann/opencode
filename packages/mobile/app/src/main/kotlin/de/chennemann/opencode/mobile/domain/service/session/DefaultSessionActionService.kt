package de.chennemann.opencode.mobile.domain.service.session

import de.chennemann.opencode.mobile.data.repository.SessionRepository
import de.chennemann.opencode.mobile.data.repository.SyncReason
import de.chennemann.opencode.mobile.domain.service.model.MessagePageInput
import de.chennemann.opencode.mobile.domain.service.model.MessagePageRequestResult
import de.chennemann.opencode.mobile.domain.service.model.RenameInput
import de.chennemann.opencode.mobile.domain.service.outbox.OutboxAction
import de.chennemann.opencode.mobile.domain.service.outbox.OutboxService
import de.chennemann.opencode.mobile.domain.service.outbox.OutboxType
import de.chennemann.opencode.mobile.domain.service.sync.SessionStateSyncService
import java.util.UUID
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class DefaultSessionActionService(
    private val session: SessionRepository,
    private val outbox: OutboxService,
    private val sync: SessionStateSyncService,
) : SessionActionService {
    override suspend fun focus(sessionId: String) {
        session.focus(sessionId)
        session.requestSync(sessionId, SyncReason.FOCUS)
        sync.onFocusedSessionChanged(sessionId)
        sync.onExpectationChanged(sessionId, true)
    }

    override suspend fun requestMessagePage(input: MessagePageInput): MessagePageRequestResult {
        val result = session.requestMessagePage(input.sessionId, input.beforeMessageId, input.limit)
        return MessagePageRequestResult(accepted = result.ok, reason = result.reason)
    }

    override suspend fun archive(sessionId: String, directory: String?) {
        val id = sessionId.trim()
        if (id.isBlank()) return
        val result = session.archive(id)
        if (!result.ok) return
        val dir = directory?.trim().orEmpty()
        if (dir.isBlank()) return
        outbox.enqueue(
            OutboxAction(
                outboxId = UUID.randomUUID().toString(),
                type = OutboxType.ARCHIVE_SESSION,
                sessionId = id,
                directory = dir,
                payloadJson = "{}",
                localMessageId = null,
                serverMessageId = null,
                createdAt = System.currentTimeMillis(),
            )
        )
        outbox.drain()
    }

    override suspend fun rename(input: RenameInput) {
        val id = input.sessionId.trim()
        if (id.isBlank()) return
        val title = input.title.trim()
        if (title.isBlank()) return
        val result = session.rename(id, title)
        if (!result.ok) return
        val dir = input.directory?.trim().orEmpty()
        if (dir.isBlank()) return
        outbox.enqueue(
            OutboxAction(
                outboxId = UUID.randomUUID().toString(),
                type = OutboxType.RENAME_SESSION,
                sessionId = id,
                directory = dir,
                payloadJson = buildJsonObject {
                    put("title", title)
                }.toString(),
                localMessageId = null,
                serverMessageId = null,
                createdAt = System.currentTimeMillis(),
            )
        )
        outbox.drain()
    }

    override suspend fun requestSync(sessionId: String, reason: SyncReason) {
        session.requestSync(sessionId, reason)
    }
}
