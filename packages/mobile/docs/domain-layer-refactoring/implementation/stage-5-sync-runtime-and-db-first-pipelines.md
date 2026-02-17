---
title: Stage 5 - Sync Runtime and DB-First Pipelines
description: Replace stream/reconcile loops with specialized sync services writing through repositories.
---

## Goal

- Add dedicated sync runtime and background services.
- Ensure remote events affect UI only by repository transactions and DB-observed flows.
- Converge state without relying on SSE replay support.
- Improve battery behavior by keeping SSE connected only when it adds value.

## Capability Boundaries

- Treat `/global/event` as live best-effort SSE.
- Do not assume server replay via `Last-Event-ID`.
- Use `sync_state` metadata, periodic snapshot sync, and sync-request markers for convergence.
- Keep stream disconnected when focused session is idle and no immediate remote updates are expected.

## Required Sync Definitions

Create `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/service/sync/SyncServices.kt`:

```kotlin
package de.chennemann.opencode.mobile.domain.service.sync

import kotlinx.coroutines.CoroutineScope

data class StreamEvent(val type: String, val payloadJson: String, val receivedAt: Long)

interface ServerService {
    suspend fun connectStream(onEvent: suspend (StreamEvent) -> Unit)
    suspend fun disconnectStream()
    suspend fun fetchProjects(): String
    suspend fun fetchSessions(projectId: String): String
    suspend fun fetchCommands(projectId: String): String
    suspend fun sendOutbox(actionId: String): Boolean
}

interface ProjectSyncService {
    suspend fun run(projectId: String? = null)
}

interface SessionStateSyncService {
    suspend fun ingestEvent(event: StreamEvent)
    suspend fun runDue(now: Long = System.currentTimeMillis())
    suspend fun onFocusedSessionChanged(sessionId: String?)
    suspend fun onExpectationChanged(sessionId: String, expectsRemoteUpdates: Boolean)
    suspend fun evaluateStreamPolicy(now: Long = System.currentTimeMillis())
}

interface SyncRuntime {
    fun start(scope: CoroutineScope)
    suspend fun tick(now: Long = System.currentTimeMillis())
}

enum class SyncTrigger {
    STREAM_HINT,
    PERIODIC,
    FOREGROUND,
    CONNECTED,
    FOCUS_CHANGED,
    EXPECTATION_CHANGED,
    IDLE_GRACE_EXPIRED,
}
```

Note: logging features are intentionally centralized in one `LogsService` (defined in Stage 2), including retention execution (`runRetention(...)`).

Outbox note:

- `ServerService.sendOutbox(...)` is exercised for all outbox types in this plan (`SEND_MESSAGE`, `EXECUTE_COMMAND`, `ARCHIVE_SESSION`, `RENAME_SESSION`).
- Outbox runtime wiring is required in this stage.

## Responsibilities and Collaboration

- `ServerService`: transport-only adapter for SSE and HTTP snapshot/outbox calls; no domain state changes.
- `SessionStateSyncService`: owns session sync behavior end-to-end:
    - stream ingest
    - periodic session snapshot sync
    - SSE connect/disconnect policy (battery-aware)
- `ProjectSyncService`: refreshes projects and commands through repositories.
- `LogsService`: single owner for logs querying/facets/retention.
- `SyncRuntime`: scheduler/composer that periodically invokes sync + outbox + logs retention work.

How they work together:

1. ViewModel focus changes update repository focus state.
2. `SessionStateSyncService.onFocusedSessionChanged(...)` and `onExpectationChanged(...)` receive new context.
3. `SessionStateSyncService.evaluateStreamPolicy(...)` decides whether SSE should be connected right now.
4. If focused session expects remote updates, SSE stays connected.
5. If focused session becomes idle, service starts a 30-second grace timer.
6. If still idle after grace timer, service disconnects SSE and relies on periodic sync.
7. Any stream event is passed to `SessionStateSyncService.ingestEvent(...)`, which writes DB state first.
8. `SyncRuntime.tick()` continues periodic snapshot sync, outbox draining, and logs retention whether SSE is connected or not.

Retention policy purpose:

- Keep local log storage bounded so long-running usage does not cause unbounded DB growth.
- Reduce on-device lifetime of potentially sensitive operational data.
- Keep logs queries/facets responsive by pruning stale/low-value rows.
- Start with a simple profile (for example `window_7d_max_5000`) and evolve later without changing `LogsService` API.

## Checklist

- [ ] S5-01: Resolve naming conflict by renaming existing transport adapter `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/data/ServerService.kt` to `ApiServerSource.kt` (or equivalent) and keep behavior unchanged.
- [ ] S5-02: Add new domain-level `ServerService` implementation in `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/service/sync/DefaultServerService.kt` that wraps remote adapters and maps payloads.
- [ ] S5-03: Implement `DefaultProjectSyncService` in `.../domain/service/sync/DefaultProjectSyncService.kt`:
    - fetch projects
    - fetch project commands
    - persist through `ProjectRepository` and `CommandRepository`
- [ ] S5-04: Implement `DefaultSessionStateSyncService` in `.../domain/service/sync/DefaultSessionStateSyncService.kt`:
    - ingest stream events via `ingestEvent(event)`
    - map impacted project/session scopes
    - update `sync_state.last_stream_seen_at`
    - write `requestSync(sessionId, SyncReason.SCHEDULED)` markers for impacted scopes
    - apply event payload directly only when payload is self-contained and deterministic
- [ ] S5-05: Implement periodic session snapshot sync in `runDue(...)` using repository metadata rows and `next_sync_at` scheduling fields.
- [ ] S5-06: Implement battery-aware stream policy in `DefaultSessionStateSyncService`:
    - connect stream only when focused session expects remote updates
    - when switching to idle focused session, schedule disconnect after 30 seconds
    - cancel pending disconnect if focused session becomes active again within grace window
    - while disconnected, rely on periodic sync only
- [ ] S5-07: Persist stream-policy state in DB (`stream_connected`, `disconnect_deadline_at`, `focused_session_id`) so behavior survives process restarts.
- [ ] S5-08: On stream reconnect and app foreground transition, run bounded catch-up snapshot sync for selected project, focused session, and pinned sessions.
- [ ] S5-09: Implement `SessionSyncStatus` transitions in `sync_state` with valid finite transitions only:
    - `IDLE -> QUEUED -> RUNNING -> IDLE`
    - `RUNNING -> BACKOFF -> QUEUED`
    - `RUNNING -> FAILED -> BACKOFF`
- [ ] S5-10: Implement retention execution inside `LogsService.runRetention(now)` using `PreferencesRepository` policy and `LogRepository.prune(...)` (default to `window_7d_max_5000` when unset).
- [ ] S5-11: Implement `DefaultSyncRuntime` scheduler that invokes:
    - `ProjectSyncService`
    - `SessionStateSyncService` (policy + ingest + due sync)
    - `OutboxService`
    - `LogsService.runRetention(...)`
- [ ] S5-12: Register sync services and runtime in `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/di/AppModule.kt`.
- [ ] S5-13: Start `SyncRuntime` from app scope in DI startup.
- [ ] S5-14: Gate old stream/reconcile loops (`SessionStreamCoordinator`, `ReconcileCoordinator`) behind migration flag and disable by default in this stage.
- [ ] S5-15: Ensure no sync service writes directly to UI state objects.
- [ ] S5-16: Add explicit comment/doc note in sync services: stream events are invalidation hints, not replayable source of truth.
- [ ] S5-17: Add metrics logging for risk checkpoints:
    - `sync_lag_ms`
    - `duplicate_message_rate`
    - `stale_unread_mismatch`
    - `stream_connected_ratio`

## Concrete Test Cases

Every test case below is required:

- [ ] T1: `DefaultServerService.connectStream` opens SSE and emits parsed `StreamEvent` values.
- [ ] T2: malformed SSE payloads are logged and skipped without crashing ingest loop.
- [ ] T3: `DefaultSessionStateSyncService.ingestEvent` writes `last_stream_seen_at` when event is processed.
- [ ] T4: stream event affecting one session writes exactly one `requestSync(..., SCHEDULED)` marker.
- [ ] T5: repeated equivalent stream hints are coalesced into one pending sync request per session.
- [ ] T6: `runDue` picks sessions where `next_sync_at <= now` only.
- [ ] T7: successful snapshot sync transitions status `QUEUED -> RUNNING -> IDLE`.
- [ ] T8: snapshot failure transitions status `RUNNING -> BACKOFF` and sets retry time.
- [ ] T9: unrecoverable decode error transitions to `FAILED` and logs one error entry.
- [ ] T10: `SessionStateSyncService` connects SSE when focused session has `expects_remote_updates=true`.
- [ ] T11: `SessionStateSyncService` starts 30-second disconnect timer when focused session becomes idle.
- [ ] T12: reconnecting to active session before timer expiry cancels pending disconnect.
- [ ] T13: timer expiry disconnects SSE exactly once and sets `stream_connected=false`.
- [ ] T14: while SSE is disconnected, periodic sync still converges data.
- [ ] T15: foreground trigger schedules bounded catch-up for selected project.
- [ ] T16: sync writes are transaction-bound; observers see updates only after commit.
- [ ] T17: outbox drain success triggers follow-up `requestSync(..., OUTBOX_DRAIN)`.
- [ ] T18: outbox drain failure leaves action in retryable state with backoff.
- [ ] T19: `DefaultProjectSyncService` upserts projects and command catalogs atomically.
- [ ] T20: `LogsService.runRetention(now)` prunes rows according to policy and leaves recent rows.
- [ ] T21: stream disconnect/reconnect converges correctly without any stored SSE cursor.
- [ ] T22: code search test confirms no `Last-Event-ID` usage in new sync services.

## Verification

- [ ] Run `./gradlew ktlintCheck`.
- [ ] Run `./gradlew :app:test --tests "*ProjectSyncService*" --tests "*SessionStateSyncService*" --tests "*SyncRuntime*" --tests "*OutboxService*" --tests "*LogsService*"`.
- [ ] Run `./gradlew :app:test --tests "*ArchitectureBoundaryTest*"`.
