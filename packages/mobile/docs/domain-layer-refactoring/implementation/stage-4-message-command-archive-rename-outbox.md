---
title: Stage 4 - Message, Command, Archive, Rename via Outbox
description: Route mutation intents through action services and DB-first outbox flow.
---

## Goal

- Move send, command execute, archive, rename, and message page requests to service/use-case paths.
- Persist optimistic writes and outbox actions in DB transactions before network calls.
- Keep terminology aligned with Stage 1: local pending IDs and canonical server message IDs.
- Send messages with synchronous `session.prompt` without providing `messageID`; persist canonical IDs from response (`assistant.info.id`, `assistant.info.parentID`).

## Required Outbox Definitions

Create `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/service/outbox/OutboxModels.kt`:

```kotlin
package de.chennemann.opencode.mobile.domain.service.outbox

enum class OutboxType { SEND_MESSAGE, EXECUTE_COMMAND, ARCHIVE_SESSION, RENAME_SESSION }
enum class OutboxStatus { PENDING, SENDING, FAILED, DONE }

data class OutboxAction(
    val outboxId: String,
    val type: OutboxType,
    val sessionId: String,
    val directory: String,
    val payloadJson: String,
    val localMessageId: String?,
    val serverMessageId: String?,
    val createdAt: Long,
)

interface OutboxService {
    suspend fun enqueue(action: OutboxAction)
    suspend fun drain(limit: Int = 50)
}
```

Outbox purpose and scope:

- Outbox is a local retry queue for send/command/archive/rename actions that may fail while offline or during transient network issues.
- Send actions in outbox use synchronous `session.prompt` calls and patch local rows from response IDs (`parentID`, `info.id`).
- `outboxId` is a local queue-row identifier (not a message ID). It is used for retry bookkeeping, backoff scheduling, and exact completion/failure updates for one queued action.
- `OutboxService.drain(...)` is called by `SyncRuntime` and by immediate action triggers (for example after reconnect) to flush pending queue rows.

## Required Use Case Definitions

Create the following files:

```kotlin
// SendMessageUseCase.kt
class SendMessageUseCase(private val action: de.chennemann.opencode.mobile.domain.service.message.MessageActionService) {
    suspend operator fun invoke(input: de.chennemann.opencode.mobile.domain.service.model.SendMessageInput): de.chennemann.opencode.mobile.domain.service.model.SendMessageResult
}

// ExecuteCommandUseCase.kt
class ExecuteCommandUseCase(private val action: de.chennemann.opencode.mobile.domain.service.message.MessageActionService) {
    suspend operator fun invoke(input: de.chennemann.opencode.mobile.domain.service.model.CommandInput): de.chennemann.opencode.mobile.domain.service.model.CommandResult
}

// ArchiveSessionUseCase.kt
class ArchiveSessionUseCase(private val action: de.chennemann.opencode.mobile.domain.service.session.SessionActionService) {
    suspend operator fun invoke(sessionId: String)
}

// RenameSessionUseCase.kt
class RenameSessionUseCase(private val action: de.chennemann.opencode.mobile.domain.service.session.SessionActionService) {
    suspend operator fun invoke(input: de.chennemann.opencode.mobile.domain.service.model.RenameInput)
}

// RequestMessagePageUseCase.kt
class RequestMessagePageUseCase(private val action: de.chennemann.opencode.mobile.domain.service.session.SessionActionService) {
    suspend operator fun invoke(input: de.chennemann.opencode.mobile.domain.service.model.MessagePageInput): de.chennemann.opencode.mobile.domain.service.model.MessagePageRequestResult
}
```

## Checklist

- [ ] S4-01: Add outbox table DDL and queries to `packages/mobile/app/src/main/sqldelight/de/chennemann/opencode/mobile/db/AppDatabase.sq`.
- [ ] S4-02: Add migration `packages/mobile/app/src/main/sqldelight/de/chennemann/opencode/mobile/db/migrations/7.sqm` for outbox schema.
- [ ] S4-03: Implement outbox store in `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/data/repository/sql/SqlDelightOutboxStore.kt`.
- [ ] S4-04: Implement `DefaultOutboxService` in `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/service/outbox/DefaultOutboxService.kt`.
- [ ] S4-04a: `DefaultOutboxService` handles `SEND_MESSAGE`/`EXECUTE_COMMAND`/`ARCHIVE_SESSION`/`RENAME_SESSION`.
- [ ] S4-05: Replace legacy message/session action service implementations with DB-first implementations:
    - `DefaultMessageActionService.kt`
    - `DefaultSessionActionService.kt`
- [ ] S4-06: Ensure send path writes optimistic local pending row + enqueue `SEND_MESSAGE` outbox action + `requestSync(sessionId, SyncReason.USER_SEND)` in one DB transaction.
- [ ] S4-06a: Ensure send network call uses synchronous `POST /session/{sessionID}/message` (`session.prompt`) without sending `messageID`.
- [ ] S4-06b: After sync response arrives, patch local pending row using response IDs:
    - user message ID from `response.info.parentID`
    - assistant message ID from `response.info.id`
- [ ] S4-06c: On timeout/transport failure with unknown server outcome, do not auto-retry blindly; mark local message `send_unknown`, keep outbox row in failed state, and require refresh/manual retry path.
- [ ] S4-07: Ensure `SEND_MESSAGE` entries are enqueued for app-originated sends and drained via `DefaultOutboxService`.
- [ ] S4-07a: Do not introduce custom dedupe headers/fields.
- [ ] S4-08: Ensure execute-command path validates command through `CommandRepository.find(...)` before outbox enqueue.
- [ ] S4-08a: Ensure command send uses synchronous `POST /session/{sessionID}/command` and patches local rows from response IDs.
- [ ] S4-09: Ensure archive and rename write local change first, then enqueue outbox action.
- [ ] S4-09a: Ensure follow-up sync reason is `OUTBOX_DRAIN` after outbox drain success.
- [ ] S4-10: Implement `requestMessagePage(input)` on `DefaultSessionActionService` via `SessionRepository.requestMessagePage(...)`.
- [ ] S4-11: Add the five use case files listed above in `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/usecase/`.
- [ ] S4-12: Update `ConversationViewModel` to use:
    - `SendMessageUseCase`
    - `ExecuteCommandUseCase`
    - `RequestMessagePageUseCase`
    - `ArchiveSessionUseCase`
    - `RenameSessionUseCase`
- [ ] S4-13: Remove direct calls from `ConversationViewModel` to `SessionServiceApi.send(...)`, `archiveSession(...)`, `renameSession(...)`, and `loadMoreMessages()`.
- [ ] S4-14: Keep `ConversationContract` event surface stable; map `LoadMoreMessagesTapped` to `RequestMessagePageUseCase` internally.

## Concrete Test Cases

Every test case below is required:

- [ ] T1: `DefaultMessageActionService.send` rejects blank message text.
- [ ] T2: `DefaultMessageActionService.send` creates optimistic message row with pending status.
- [ ] T3: `DefaultMessageActionService.send` enqueues `SEND_MESSAGE` outbox action and triggers one immediate drain attempt.
- [ ] T4: send local-write transaction is atomic (forced failure rolls back pending row and sync-request marker).
- [ ] T5: `DefaultMessageActionService.send` requests sync with `SyncReason.USER_SEND`.
- [ ] T6: send response patches pending row with canonical server IDs (`parentID` + `info.id`).
- [ ] T6a: send path uses `session.prompt` endpoint and does not use `session.prompt_async`.
- [ ] T6b: timeout/transport failure marks message as `send_unknown`, sets outbox row to failed, and does not auto-retry in the same execution path.
- [ ] T7: `DefaultMessageActionService.execute` rejects unknown slash command via `CommandRepository.find`.
- [ ] T8: `DefaultMessageActionService.execute` enqueues `EXECUTE_COMMAND` action on valid command.
- [ ] T8a: `DefaultOutboxService.drain` retries `SEND_MESSAGE`/`EXECUTE_COMMAND`/`ARCHIVE_SESSION`/`RENAME_SESSION` and updates row status per result.
- [ ] T9: `DefaultSessionActionService.archive` updates session state locally before outbox enqueue.
- [ ] T10: `DefaultSessionActionService.rename` trims title and writes local rename before outbox enqueue.
- [ ] T11: archive/rename actions request sync with `SyncReason.OUTBOX_DRAIN`.
- [ ] T12: `DefaultSessionActionService.requestMessagePage` forwards exact `beforeMessageId` and `limit` to repository.
- [ ] T13: `RequestMessagePageUseCase` ignores blank session IDs.
- [ ] T14: `SendMessageUseCase` maps service result unchanged.
- [ ] T15: `ExecuteCommandUseCase` maps service result unchanged.
- [ ] T16: `ArchiveSessionUseCase` invokes session action service exactly once.
- [ ] T17: `RenameSessionUseCase` trims title and invokes service once.
- [ ] T18: `ConversationViewModel.SendTapped` uses `SendMessageUseCase` and preserves draft-clear behavior.
- [ ] T19: `ConversationViewModel.LoadMoreMessagesTapped` uses `RequestMessagePageUseCase` with earliest loaded message ID.
- [ ] T20: `ConversationViewModel` no longer references `SessionServiceApi.loadMoreMessages`.
- [ ] T21: HTTP payload generated by mobile send path does not include `messageID`.
- [ ] T22: HTTP payload generated by mobile send/command paths does not contain custom dedupe fields.

## Verification

- [ ] Run `./gradlew generateSqlDelightInterface`.
- [ ] Run `./gradlew ktlintCheck`.
- [ ] Run `./gradlew :app:test --tests "*Outbox*" --tests "*MessageActionService*" --tests "*SessionActionService*" --tests "*RequestMessagePageUseCase*" --tests "*ConversationViewModelTest*"`.
