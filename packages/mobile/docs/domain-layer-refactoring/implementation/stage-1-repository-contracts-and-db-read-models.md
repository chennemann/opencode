---
title: Stage 1 - Repository Contracts and DB Read Models
description: Add repository contracts and SQLDelight read models without changing UI behavior.
---

## Goal

- Introduce repository interfaces and SQLDelight-backed canonical/read-model tables beside existing gateways.
- Keep `SessionServiceApi` and all current screen behavior unchanged during this stage.

## Required Contract Definitions

Create `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/data/repository/RepositoryContracts.kt` with:

```kotlin
package de.chennemann.opencode.mobile.data.repository

import de.chennemann.opencode.mobile.domain.session.CommandState
import de.chennemann.opencode.mobile.domain.session.LogEntry
import de.chennemann.opencode.mobile.domain.session.LogFacet
import de.chennemann.opencode.mobile.domain.session.LogFilter
import de.chennemann.opencode.mobile.domain.session.LogRecord
import de.chennemann.opencode.mobile.domain.session.MessageState
import de.chennemann.opencode.mobile.domain.session.ProjectState
import de.chennemann.opencode.mobile.domain.session.SessionState
import kotlinx.coroutines.flow.Flow

data class SessionListFilter(val query: String = "", val includeArchived: Boolean = false, val limit: Long = 50)
data class MessagePageRequest(val beforeMessageId: String? = null, val limit: Long = 100)
data class MessagePage(
    val items: List<MessageState>,
    val hasMore: Boolean,
    val nextBeforeMessageId: String?,
)
data class PendingMessageRef(val localMessageId: String)
enum class RemoteBatchSource { STREAM_HINT, SNAPSHOT_SYNC, OUTBOX_ACK }
enum class SessionSyncStatus { IDLE, QUEUED, RUNNING, BACKOFF, FAILED }
data class SessionSyncState(
    val sessionId: String,
    val status: SessionSyncStatus,
    val lastSnapshotAt: Long?,
    val lastStreamSeenAt: Long?,
    val lastErrorAt: Long?,
)
data class AppendLocalMessageInput(
    val sessionId: String,
    val directory: String,
    val text: String,
    val agent: String,
    val createdAt: Long,
)
data class ConfirmSentMessageInput(
    val sessionId: String,
    val localMessageId: String,
    val serverUserMessageId: String,
    val serverAssistantMessageId: String,
    val confirmedAt: Long,
)
data class SessionRemoteBatch(
    val sessionId: String,
    val payloadJson: String,
    val receivedAt: Long,
    val source: RemoteBatchSource,
)
data class RepoResult(val ok: Boolean, val reason: String? = null)
enum class SyncReason { FOCUS, USER_SEND, REFRESH, RECONNECT, SCHEDULED, OUTBOX_DRAIN }

data class ConnectionSnapshot(val endpoint: String, val discovered: String?, val status: String)
data class LogPage(val offset: Long = 0, val size: Long = 100)
data class PrefsState(
    val quickSwitchScope: String,
    val sortMode: String,
    val logsFilterJson: String,
    val logsRetentionPolicy: String,
)

interface SessionRepository {
    fun observeSessionList(projectId: String, filter: SessionListFilter): Flow<List<SessionState>>
    fun observeFocusedSession(): Flow<SessionState?>
    fun observeMessagePage(sessionId: String, request: MessagePageRequest): Flow<MessagePage>
    fun observeSyncState(sessionId: String): Flow<SessionSyncState>
    suspend fun focus(sessionId: String)
    suspend fun appendLocalMessage(input: AppendLocalMessageInput): PendingMessageRef
    suspend fun confirmSentMessage(input: ConfirmSentMessageInput): RepoResult
    suspend fun applyRemoteBatch(batch: SessionRemoteBatch): RepoResult
    suspend fun requestMessagePage(sessionId: String, beforeMessageId: String?, limit: Long): RepoResult
    suspend fun archive(sessionId: String): RepoResult
    suspend fun rename(sessionId: String, title: String): RepoResult
    suspend fun requestSync(sessionId: String, reason: SyncReason)
}

interface ProjectRepository {
    fun observeProjects(): Flow<List<ProjectState>>
    fun observeSelectedProject(): Flow<ProjectState?>
    suspend fun select(projectId: String)
    suspend fun toggleFavorite(projectId: String): Boolean
    suspend fun toggleHidden(projectId: String): Boolean
    suspend fun upsertProjects(items: List<ProjectState>)
    suspend fun requestRefresh(projectId: String)
}

interface CommandRepository {
    fun observeCommands(projectId: String): Flow<List<CommandState>>
    suspend fun replaceCommands(projectId: String, commands: List<CommandState>)
    suspend fun find(projectId: String, commandName: String): CommandState?
}

interface ConnectionRepository {
    fun observeConnection(): Flow<ConnectionSnapshot>
    suspend fun setEndpoint(url: String)
    suspend fun setStatus(status: String)
    suspend fun recordDiscovery(items: List<String>)
}

interface LogRepository {
    fun observeLogs(filter: LogFilter, page: LogPage): Flow<List<LogEntry>>
    fun observeFacets(): Flow<LogFacet>
    suspend fun append(entry: LogRecord)
    suspend fun prune(policy: String)
}

interface PreferencesRepository {
    fun observePrefs(): Flow<PrefsState>
    suspend fun setQuickSwitchScope(scope: String)
    suspend fun setSortMode(mode: String)
    suspend fun setLogsFilter(filterJson: String)
    suspend fun setLogsRetentionPolicy(policy: String)
}
```

Sync metadata semantics:

- `RemoteBatchSource` tells repository merge code why a batch is being applied:
    - `STREAM_HINT`: lightweight stream-delivered delta/invalidation payload.
    - `SNAPSHOT_SYNC`: periodic or on-demand snapshot refresh payload.
    - `OUTBOX_ACK`: server-confirmed result of a previously submitted local action (including send/command/archive/rename).
- Why this exists: merge/reconcile policy and metrics differ by source (for example, stream gaps should schedule snapshot follow-up, while outbox acks should prioritize patching optimistic rows).
- If we later decide merge behavior is identical across all three sources, we can remove this enum and simplify `applyRemoteBatch(...)`.

- `SyncReason.USER_SEND`: immediate sync request produced from a UI send intent.
- `SyncReason.OUTBOX_DRAIN`: follow-up sync request produced after outbox processing (including send actions) to reconcile local optimistic state with server state.
- `appendLocalMessage(...)` creates a pending local row with local-only identifier (`localMessageId`).
- `confirmSentMessage(...)` patches that local row with server IDs returned from synchronous send response.
- Send request does not include `messageID`; server-generated IDs are treated as canonical.

Retention policy semantics:

- `PrefsState.logsRetentionPolicy` stores the active logs retention profile used by `LogsService.runRetention(...)`.
- Purpose: cap local DB growth, reduce sensitive data lifetime on-device, and keep logs browsing responsive.
- Default policy can be `window_7d_max_5000` until product settings expose richer controls.

## Checklist

- [x] S1-01: Add `RepositoryContracts.kt` exactly with the interfaces and models above.
- [x] S1-02: Extend `packages/mobile/app/src/main/sqldelight/de/chennemann/opencode/mobile/db/AppDatabase.sq` with canonical tables:
    - `project`
    - `session`
    - `message`
    - `command`
    - `connection`
    - `sync_state`
    - `preference`
- [x] S1-03: Add migration file `packages/mobile/app/src/main/sqldelight/de/chennemann/opencode/mobile/db/migrations/6.sqm` for new tables and indexes.
- [x] S1-04: Add SQL read-model queries in `AppDatabase.sq`:
    - `observeSessionList`
    - `observeFocusedSession`
    - `observeMessagePage`
    - `observeSyncState`
    - `observeProjects`
    - `observeSelectedProject`
    - `observeCommands`
    - `observeConnection`
    - `observePrefs`
- [x] S1-05: Implement SQLDelight repository adapters:
    - `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/data/repository/sql/SqlDelightSessionRepository.kt`
    - `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/data/repository/sql/SqlDelightProjectRepository.kt`
    - `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/data/repository/sql/SqlDelightCommandRepository.kt`
    - `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/data/repository/sql/SqlDelightConnectionRepository.kt`
    - `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/data/repository/sql/SqlDelightLogRepository.kt`
    - `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/data/repository/sql/SqlDelightPreferencesRepository.kt`
- [x] S1-06: Add migration logic to purge all existing local app data and start canonical tables from empty state (no backfill/bootstrap from legacy tables).
- [x] S1-07: Register new repositories in `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/di/AppModule.kt` without replacing current `SessionServiceApi` consumers.
- [x] S1-08: Add repository contract tests under `packages/mobile/app/src/test/kotlin/de/chennemann/opencode/mobile/data/repository/`.
- [x] S1-09: Confirm no UI/ViewModel files are modified in this stage.
- [x] S1-10: Do not add SSE resume token persistence (`cursor`, `last_event_id`, `event_cursor`); server-side replay is not guaranteed.

## Concrete Test Cases

Every test case below is required (no placeholders):

- [x] T1: `observeSessionList` returns only sessions from requested project when `includeArchived=false`.
- [x] T2: `observeSessionList` includes archived sessions when `includeArchived=true`.
- [x] T3: `observeSessionList` applies query filter and deterministic sort order.
- [x] T4: `focus(sessionId)` updates focused pointer and `observeFocusedSession` emits the new session after commit.
- [x] T5: `appendLocalMessage` inserts one pending local row and returns non-blank `localMessageId`.
- [x] T6: `confirmSentMessage` patches pending row from `localMessageId` to server IDs in one transaction.
- [x] T7: `applyRemoteBatch` upserts message/session changes in one transaction.
- [x] T8: `applyRemoteBatch` patches existing rows by canonical server IDs and does not duplicate local messages.
- [x] T9: `archive(sessionId)` hides archived session from non-archived list and keeps row available for archived queries.
- [x] T10: `rename(sessionId, title)` updates title in both session list and focused session query.
- [x] T11: `requestSync(sessionId, SyncReason.USER_SEND)` sets sync state to queued.
- [x] T12: `requestSync(sessionId, SyncReason.OUTBOX_DRAIN)` updates same sync metadata without creating duplicate queued rows.
- [x] T13: `observeMessagePage(sessionId, before=null, limit=N)` returns latest N messages with `hasMore` flag.
- [x] T14: `observeMessagePage` with `beforeMessageId` returns older page with no overlaps or gaps.
- [x] T15: final page returns `hasMore=false` and `nextBeforeMessageId=null`.
- [x] T16: concurrent insert during paging keeps stable ordering and does not reorder already emitted page rows.
- [x] T17: `toggleFavorite(projectId)` persists and re-emits from `observeProjects`.
- [x] T18: `toggleHidden(projectId)` persists and re-emits from `observeProjects`.
- [x] T19: `select(projectId)` updates `observeSelectedProject` and is persisted across DB reopen.
- [x] T20: `replaceCommands(projectId, commands)` atomically replaces catalog for that project.
- [x] T21: `find(projectId, commandName)` returns exact command by name and null for missing command.
- [x] T22: `setEndpoint` + `setStatus` + `recordDiscovery` update `observeConnection` in commit order.
- [x] T23: `setQuickSwitchScope`, `setSortMode`, `setLogsFilter`, and `setLogsRetentionPolicy` persist and re-emit from `observePrefs`.
- [x] T24: `append(log)` writes one row and `observeLogs` reflects it under matching filter.
- [x] T25: `observeFacets` counts update after multiple `append(log)` writes.
- [x] T26: `prune(policy)` removes expected rows only and keeps rows outside policy window.
- [x] T27: observer flows do not emit uncommitted intermediate states (post-commit visibility only).
- [x] T28: stage migration purge removes legacy cache/settings rows and starts with empty canonical state.

## Verification

- [x] Run `./gradlew generateSqlDelightInterface`.
- [x] Run `./gradlew ktlintCheck`.
- [x] Run `./gradlew :app:test --tests "*Repository*"`.
