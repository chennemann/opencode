---
title: Stage 2 - Service Interfaces and Legacy Adapters
description: Introduce focused service contracts and bridge them to current SessionServiceApi behavior.
---

## Goal

- Add focused service interfaces used by use cases.
- Implement legacy adapters over existing `SessionServiceApi` and `LogStoreGateway` so behavior stays unchanged.
- Keep contracts aligned with Stage 1 refinements: enum-based sync state, explicit pagination request model, and server-canonical message IDs.
- Keep one interface per concern, with one explicit exception: all logs features are grouped in one `LogsService`.

## Required Contract Definitions

Create shared models in `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/service/model/ActionModels.kt`:

```kotlin
package de.chennemann.opencode.mobile.domain.service.model

enum class QuickSwitchOpenReason {
    HOTKEY,
    MANAGE_BUTTON,
    EMPTY_STATE,
    SESSION_SWITCH,
}

data class SendMessageInput(val text: String, val agent: String, val sessionId: String?, val directory: String?)
data class SendMessageResult(val accepted: Boolean, val sessionId: String?, val reason: String?)
data class CommandInput(val raw: String, val sessionId: String, val directory: String, val agent: String)
data class CommandResult(val accepted: Boolean, val reason: String?)
data class RenameInput(val sessionId: String, val title: String)
data class RefreshInput(val endpoint: String, val userInitiated: Boolean)
data class MessagePageInput(val sessionId: String, val beforeMessageId: String? = null, val limit: Long = 100)
data class MessagePageRequestResult(val accepted: Boolean, val reason: String?)
data class QuickSwitchRequest(val openReason: QuickSwitchOpenReason, val searchText: String)
```

Create one interface per file:

```kotlin
// domain/service/project/ProjectActionService.kt
package de.chennemann.opencode.mobile.domain.service.project

interface ProjectActionService {
    suspend fun select(projectId: String)
    suspend fun toggleFavorite(projectId: String): Boolean
    suspend fun toggleHidden(projectId: String): Boolean
    suspend fun refreshProjectContext(projectId: String)
}

// domain/service/session/SessionActionService.kt
package de.chennemann.opencode.mobile.domain.service.session

import de.chennemann.opencode.mobile.data.repository.SyncReason
import de.chennemann.opencode.mobile.domain.service.model.MessagePageInput
import de.chennemann.opencode.mobile.domain.service.model.MessagePageRequestResult
import de.chennemann.opencode.mobile.domain.service.model.RenameInput

interface SessionActionService {
    suspend fun focus(sessionId: String)
    suspend fun requestMessagePage(input: MessagePageInput): MessagePageRequestResult
    suspend fun archive(sessionId: String)
    suspend fun rename(input: RenameInput)
    suspend fun requestSync(sessionId: String, reason: SyncReason)
}

// domain/service/message/MessageActionService.kt
package de.chennemann.opencode.mobile.domain.service.message

import de.chennemann.opencode.mobile.domain.service.model.CommandInput
import de.chennemann.opencode.mobile.domain.service.model.CommandResult
import de.chennemann.opencode.mobile.domain.service.model.SendMessageInput
import de.chennemann.opencode.mobile.domain.service.model.SendMessageResult

interface MessageActionService {
    suspend fun send(input: SendMessageInput): SendMessageResult
    suspend fun execute(input: CommandInput): CommandResult
}

// domain/service/connection/ConnectionActionService.kt
package de.chennemann.opencode.mobile.domain.service.connection

import de.chennemann.opencode.mobile.domain.service.model.RefreshInput

interface ConnectionActionService {
    suspend fun refresh(input: RefreshInput)
}

// domain/service/quickswitch/QuickSwitchReadService.kt
package de.chennemann.opencode.mobile.domain.service.quickswitch

import de.chennemann.opencode.mobile.domain.service.model.QuickSwitchRequest
import de.chennemann.opencode.mobile.domain.session.SessionState

interface QuickSwitchReadService {
    suspend fun open(request: QuickSwitchRequest): List<SessionState>
}

// domain/service/logs/LogsService.kt
package de.chennemann.opencode.mobile.domain.service.logs

import de.chennemann.opencode.mobile.data.repository.LogPage
import de.chennemann.opencode.mobile.domain.session.LogEntry
import de.chennemann.opencode.mobile.domain.session.LogFacet
import de.chennemann.opencode.mobile.domain.session.LogFilter
import kotlinx.coroutines.flow.Flow

interface LogsService {
    fun observeLogs(filter: LogFilter, page: LogPage): Flow<List<LogEntry>>
    fun observeFacets(filter: LogFilter): Flow<LogFacet>
    suspend fun runRetention(now: Long = System.currentTimeMillis())
}
```

Definitions:

- `QuickSwitchRequest.openReason`: why quick-switch was opened (keyboard shortcut, manage action, etc.).
- `QuickSwitchRequest.searchText`: current user-entered search text for local filtering.
- `LogFacet`: aggregated counts used for logs filter chips (for example by level, source, or tag).

Logging behavior rule:

- There is no explicit `setFilter(...)` service method.
- `LogsService.observeLogs(filter, page)` implicitly persists the last-used filter before first emission.
- `LogsService.observeFacets(filter)` computes facets for the same active filter context.
- `LogsService.runRetention(now)` reads retention policy from preferences (`logsRetentionPolicy`), with a safe default when unset.

Compatibility note for this stage:

- `LegacySessionActionService.requestMessagePage(...)` is a temporary shim over `SessionServiceApi.loadMoreMessages()`.
- It does not implement full `beforeMessageId` semantics yet.
- Full repository-backed pagination behavior is implemented in Stage 4.
- `LegacyLogsService.runRetention(...)` can be a no-op until repository-backed retention exists.

## Checklist

- [x] S2-01: Add `ActionModels.kt` exactly with the models above.
- [x] S2-02: Add one interface file per service as listed above.
- [x] S2-03: Add legacy adapter implementations in `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/service/legacy/`:
    - `LegacyProjectActionService.kt`
    - `LegacySessionActionService.kt`
    - `LegacyMessageActionService.kt`
    - `LegacyConnectionActionService.kt`
    - `LegacyQuickSwitchReadService.kt`
    - `LegacyLogsService.kt`
- [x] S2-04: `LegacyProjectActionService.select(projectId)` calls `SessionServiceApi.selectProject(projectId)`.
- [x] S2-05: `LegacyProjectActionService.toggleFavorite(projectId)` calls `SessionServiceApi.toggleProjectFavorite(projectId)` and returns state after update.
- [x] S2-06: `LegacyProjectActionService.toggleHidden(projectId)` calls `SessionServiceApi.removeProject(projectId)` for now and returns `true` when project no longer appears in visible list.
- [x] S2-07: `LegacySessionActionService.focus(sessionId)` resolves `SessionState` from `SessionServiceApi.state.value` and calls `openSession(...)`.
- [x] S2-08: `LegacySessionActionService.requestMessagePage(input)` maps to current behavior:
    - focuses session when needed
    - calls `loadMoreMessages()`
    - returns `MessagePageRequestResult(accepted=true)` when call succeeds
- [x] S2-09: `LegacySessionActionService.archive(sessionId)` and `rename(input)` resolve `SessionState` and forward to `archiveSession(...)` / `renameSession(...)`.
- [x] S2-10: `LegacySessionActionService.requestSync(sessionId, reason)` maps to `SessionServiceApi.refresh()` for reasons that require immediate network check in legacy mode.
- [x] S2-11: `LegacyMessageActionService.send(input)` and `execute(input)` forward to `SessionServiceApi.send(...)` using existing slash-command behavior.
- [x] S2-12: `LegacyConnectionActionService.refresh(input)` calls `updateUrl(...)` then `refresh()`.
- [x] S2-13: `LegacyQuickSwitchReadService.open(request)` uses `cachedSessionsForProject(...)` + in-memory filtering (`request.searchText`) and keeps `request.openReason` for analytics/debug only.
- [x] S2-14: `LegacyLogsService.observeLogs(filter, page)` persists `filter` implicitly before delegating to `LogStoreGateway.observe(filter)`.
- [x] S2-15: `LegacyLogsService.observeFacets(filter)` delegates to `LogStoreGateway.observeFacet()` and maps counts for active filter context.
- [x] S2-16: `LegacyLogsService.runRetention(now)` is explicitly implemented (no-op in legacy stage, with TODO linked to Stage 5 migration).
- [x] S2-17: Add DI bindings in `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/di/AppModule.kt` for all service interfaces.
- [x] S2-18: Keep existing `SessionServiceApi` and UI usage untouched in this stage.

## Concrete Test Cases

Every test case below is required:

- [x] T1: `LegacyProjectActionService.select` forwards exact `projectId`.
- [x] T2: `LegacyProjectActionService.toggleFavorite` toggles and returns updated value.
- [x] T3: `LegacyProjectActionService.toggleHidden` hides project and returns `true` when missing from visible list.
- [x] T4: `LegacySessionActionService.focus` opens target session when present in `state`.
- [x] T5: `LegacySessionActionService.focus` returns without crash when session ID is missing.
- [x] T6: `LegacySessionActionService.requestMessagePage` focuses if needed before `loadMoreMessages()`.
- [x] T7: `LegacySessionActionService.requestMessagePage` succeeds when `beforeMessageId=null`.
- [x] T8: `LegacySessionActionService.requestMessagePage` returns accepted result even though legacy path ignores `beforeMessageId`.
- [x] T9: `LegacySessionActionService.archive` calls `archiveSession(...)` for resolved session.
- [x] T10: `LegacySessionActionService.rename` trims title and calls `renameSession(...)`.
- [x] T11: `LegacySessionActionService.requestSync(..., USER_SEND)` triggers one refresh call.
- [x] T12: `LegacySessionActionService.requestSync(..., OUTBOX_DRAIN)` triggers one refresh call.
- [x] T13: `LegacyMessageActionService.send` forwards agent and text unchanged.
- [x] T14: `LegacyMessageActionService.execute` forwards command input through slash-command path.
- [x] T15: `LegacyConnectionActionService.refresh` updates URL first and refreshes second.
- [x] T16: `LegacyQuickSwitchReadService.open` filters cached sessions by `searchText`.
- [x] T17: `LegacyQuickSwitchReadService.open` accepts each `QuickSwitchOpenReason` without changing result semantics.
- [x] T18: `LegacyLogsService.observeLogs` persists last-used filter implicitly before first emission.
- [x] T19: `LegacyLogsService.observeLogs` delegates to `LogStoreGateway.observe(filter)`.
- [x] T20: `LegacyLogsService.observeFacets(filter)` emits facet counts for the same active filter context.
- [x] T21: `LegacyLogsService.runRetention(now)` is callable and does not throw in legacy mode.
- [x] T22: `AppModule` resolves all new service interfaces from Koin.
- [x] T23: No UI ViewModel constructor signatures change in this stage.

## Verification

- [x] Run `./gradlew ktlintCheck`.
- [x] Run `./gradlew :app:test --tests "*Legacy*Service*"`.
- [x] Run `./gradlew :app:test --tests "*AppModuleTest*"`.
