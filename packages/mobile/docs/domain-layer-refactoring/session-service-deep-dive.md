---
title: Orchestration core
description: Trace startup, sync, and boundary tradeoffs
---

## Set purpose and scope

- Purpose: document `SessionService` as the mobile domain orchestrator, including behavior, state model, collaborators, and architecture quality in `packages/mobile` only.
- Scope: only code under `packages/mobile`, with emphasis on `:app` domain/data and wiring points that instantiate or consume `SessionService`.
- Method: static code trace with explicit `path:line` anchors, plus cross-checks from unit tests that exercise key flows.

Refs: `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:24`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionServiceApi.kt:6`, `packages/mobile/app/src/test/kotlin/de/chennemann/opencode/mobile/domain/session/SessionServiceTest.kt:44`

---

## Place in architecture and startup wiring

- `SessionService` is the domain boundary consumed by view models through `SessionServiceApi`, while implementation is hidden behind DI binding.
- Koin eagerly creates `SessionService` (`createdAtStart = true`) and immediately calls `start(appScope)`, so orchestration starts before any screen is opened.
- `ConversationViewModel` and `ManageViewModel` both call `service.start(viewModelScope)`, but startup is idempotent via `if (started) return`.
- Startup wiring composes output state from `input + conn.found + conn.status + local`, starts endpoint/status collectors, hydrates last session, and launches reconcile plus stream loops.

Refs: `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/di/AppModule.kt:96`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/di/AppModule.kt:100`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/conversation/ConversationViewModel.kt:152`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/manage/ManageViewModel.kt:100`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:154`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:171`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:211`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:214`

---

## Explain public API

| API method                                     | Behavior                                                                                | Notes and caveats                                                            | Key refs                                                                                                                                                                                                                                                                                                                                                                                                                                 |
| ---------------------------------------------- | --------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `state`                                        | Exposes aggregated `SessionUiState` as `StateFlow`                                      | Aggregation includes connection, discovered endpoint, and domain-local state | `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionServiceApi.kt:7`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:143`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:171`                                                                                                            |
| `start(scope)`                                 | Initializes executors, collectors, hydration, reconcile, stream                         | Idempotent and safe to call from multiple callers                            | `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionServiceApi.kt:9`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:154`                                                                                                                                                                                                                      |
| `updateUrl(value)`                             | Sets manual URL input without immediate refresh                                         | `manual = true` changes cache-server selection semantics                     | `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionServiceApi.kt:11`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:226`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:1868`                                                                                                          |
| `useDiscovered()`                              | Copies `conn.found` into input URL                                                      | No-op when nothing discovered                                                | `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionServiceApi.kt:13`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:233`                                                                                                                                                                                                                     |
| `refresh()`                                    | Persists current input URL via connection gateway and runs health refresh               | Triggers project load indirectly when status transitions to connected        | `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionServiceApi.kt:15`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:241`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:205`                                                                                                           |
| `selectProject(worktree)`                      | Switches selected project, resets session list/limit, reloads sessions and commands     | Also marks directory policy as activated for backoff reset                   | `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionServiceApi.kt:17`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:321`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:2084`                                                                                                          |
| `toggleProjectFavorite(worktree)`              | Optimistic favorite toggle with async cache persistence and rollback                    | Favorite activation preloads active sessions for quick switching             | `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionServiceApi.kt:19`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:337`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:1125`                                                                                                          |
| `removeProject(worktree)`                      | Marks project as hidden optimistically and persists hidden flag                         | Not server delete, only local hidden overlay                                 | `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionServiceApi.kt:21`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:377`                                                                                                                                                                                                                     |
| `toggleSessionQuickPin(session, systemPinned)` | Adjusts include/exclude pin sets, persists to cache, rolls back on failure              | If pinning becomes active, injects into active sessions immediately          | `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionServiceApi.kt:23`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:410`                                                                                                                                                                                                                     |
| `createSessionAndFocus(worktree)`              | Defers real server session creation and clears focus for a fresh draft lane             | Actual create happens on first non-command send                              | `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionServiceApi.kt:25`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:483`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:510`                                                                                                           |
| `openSession(session)`                         | Upserts active session, moves focus, clears unread, starts DB observe, forces sync      | Flushes staged overlay from previous focus before switching                  | `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionServiceApi.kt:27`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:532`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:730`                                                                                                           |
| `send(text, agent)`                            | Handles `/new`, command send, or optimistic message send                                | Creates server session lazily when no focused session exists                 | `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionServiceApi.kt:29`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:538`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:553`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:580` |
| `loadMoreMessages()`                           | Increases per-session message limit and forces sync in “more” mode                      | `more=true` bypasses sync gate checks and shows loading indicator            | `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionServiceApi.kt:31`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:638`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:761`                                                                                                           |
| `archiveSession(session)`                      | Optimistically removes local session, calls remote archive, reloads sessions on failure | Local remove clears pins, processing/unread, overlay, cache rows             | `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionServiceApi.kt:33`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:648`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:1730`                                                                                                          |
| `renameSession(session, title)`                | Optimistic local rename plus cache persist, rollback on remote failure                  | Updates focused, listed, and active copies consistently                      | `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionServiceApi.kt:35`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:668`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:1786`                                                                                                          |
| `cachedSessionsForProject(worktree, limit)`    | Reads project sessions from cache                                                       | Used by quick-switch menu for fast initial paint                             | `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionServiceApi.kt:37`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:991`                                                                                                                                                                                                                     |
| `sessionsForProject(worktree, limit)`          | Fetches live sessions across worktree + sandboxes and refreshes cache snapshot          | Filters archived and child sessions before returning                         | `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionServiceApi.kt:39`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:985`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:1035`                                                                                                          |

Behavior checks in tests: `packages/mobile/app/src/test/kotlin/de/chennemann/opencode/mobile/domain/session/SessionServiceTest.kt:109`, `packages/mobile/app/src/test/kotlin/de/chennemann/opencode/mobile/domain/session/SessionServiceTest.kt:137`, `packages/mobile/app/src/test/kotlin/de/chennemann/opencode/mobile/domain/session/SessionServiceTest.kt:173`, `packages/mobile/app/src/test/kotlin/de/chennemann/opencode/mobile/domain/session/SessionServiceTest.kt:218`, `packages/mobile/app/src/test/kotlin/de/chennemann/opencode/mobile/domain/session/SessionServiceTest.kt:246`

---

## Model internal state and invariants

- `LocalState` is the mutable domain projection source for projects, pins, sessions, focus, focused messages, loading flags, and user-facing error message.
- `output` is a separate `MutableStateFlow<SessionUiState>` derived by combining `input`, discovery/connection flows, and `local`, which keeps transport signals and domain snapshots loosely coupled.
- Key indexed maps hold orchestration working sets: `active`, `sessionProject`, `policy`, `directoryPolicy`, `messageLimit`, `pending`, `stickySort`, `order`, `part`, `role`, `overlay`, and `overlayDirty`.
- `focusedKey` is the primary identity cursor for message observe/publish paths and is expected to correspond to one `active` entry formatted as `server::sessionId`.

Refs: `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:41`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:83`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:110`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:130`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:171`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:137`

### Track invariant groups

- **Identity invariant:** active-map keys are always `server::sessionId`, and all message-level indexes use `server::sessionId::messageId`.
- **Focus invariant:** when `focusedSession` is null, focused message list and load-more flags are reset.
- **Processing invariant:** `quickUnread` only increments when a session transitions from running to idle and is not focused.
- **Sync invariant:** only one sync run per session may execute at once via `SyncCoordinator.begin/end`.
- **Overlay invariant:** staged overlay writes are persisted with delayed flush and eventually cleared after full sync.

Refs: `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:1950`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:1596`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:497`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:742`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:1820`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:1954`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:1616`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:1642`

### Clarify map responsibilities

| Structure                                                | Purpose                                                                             | Lifecycle touchpoints                                                       |
| -------------------------------------------------------- | ----------------------------------------------------------------------------------- | --------------------------------------------------------------------------- |
| `active` / `sessionProject`                              | Track quick-switch eligible sessions and their owning project                       | upsert on focus/preload/ensure, delete on remove                            |
| `policy`                                                 | Per-session sync gate policy with backoff, SSE timing, check support, tool watchdog | read/write during `syncGate`, `markSseApplied`, `markSync`, `syncToolState` |
| `directoryPolicy`                                        | Per-directory status polling policy for favorite running discovery                  | touched by `discoverFavoriteRunningSessions`, project activation/reset      |
| `pending` + `retainPass`                                 | Optimistic user messages and short retention budget for reconciliation              | add/claim/remove/trim around send and sync                                  |
| `stickySort` + `order`                                   | Preserve stable sort while optimistic claims reconcile to remote IDs                | updated in event handlers and sync planner apply                            |
| `part` + `role`                                          | Message-part cache and role cache for render/streaming updates                      | updated in sync fetch and SSE part handlers                                 |
| `overlay` + `overlayDirty`                               | Staged message overlay for immediate UI updates and delayed cache flush             | stage on SSE updates, flush on timer/focus switch, clear after sync         |
| `flush` / `observe` / `publish` / `stream` / `reconcile` | Job registry for lifecycle and cancellation controls                                | reset in focus switch, stop, and restart paths                              |

Refs: `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:110`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:119`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:120`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:114`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:116`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:117`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:112`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:130`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:1531`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:1730`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:2259`

---

## Break down constructor dependencies

| Dependency                              | What it does                                         | How `SessionService` uses it                                        | Where responsibility could move                                               |
| --------------------------------------- | ---------------------------------------------------- | ------------------------------------------------------------------- | ----------------------------------------------------------------------------- |
| `ConnectionGateway` (`conn`)            | Endpoint state, discovered endpoint, health refresh  | input/url sync, refresh, connected-triggered project load, startup  | keep as port; move URL/manual state adapter out of service                    |
| `ProjectGateway` (`proj`)               | Project list and session lifecycle operations        | project load, session list by workspace, archive/rename/create      | move project/session collection policies into dedicated workspace coordinator |
| `CommandGateway` (`cmd`)                | Slash command catalog per directory                  | load commands on selected/focused workspace                         | keep port; cache command list in a small command store component              |
| `MessageGateway` (`msg`)                | messages, send, status, updated-at checks            | send message/command, sync fetch, status probes, updated-at checks  | split into `MessageSendPort` and `MessageSyncPort` to reduce API breadth      |
| `SessionCacheGateway` (`cache`)         | cache snapshots, observe messages, metadata overlays | favorites/hidden/pins, focused DB observe, cache writes and cleanup | extract cache-write policy from service into `SessionCacheWriter`             |
| `LogGateway` (`log`)                    | structured logs facade                               | perf markers, sync/stream/workspace warnings                        | keep as port; centralize perf marker helper outside service                   |
| `MessagePartParser` (`parser`)          | parse raw JSON parts to domain parts                 | parse in sync and SSE part handlers                                 | fold into message ingest component to avoid parser leakage into service       |
| `MessageDecorator` (`decorator`)        | render assistant text and tool-call metadata         | assistant text fallback in sync/event paths                         | use only in projector path to keep reducer/sync free from render logic        |
| `FocusedMessageProjector` (`projector`) | merge base/staged/pending/parts and decorate         | compute focused projection on CPU lane                              | keep as extracted component, but own cache invalidation contract explicitly   |
| `SessionSyncPlanner` (`planner`)        | pure merge plan for upsert/remove/sort/claim         | produce sync plan with injected sort/retain functions               | keep pure core and expand into full `SyncEngine` wrapper                      |
| `SessionEventReducer` (`reducer`)       | map stream events to typed actions                   | central dispatch in `onEventNow`                                    | keep pure; move action handling to dedicated handlers map                     |
| `SessionStreamCoordinator` (`streamer`) | stream loop with retry and cursor persistence        | started once at service start, callback into reducer/handlers       | keep owner separate; surface richer typed stream errors for policy decisions  |
| `ReconcileCoordinator` (`reconciler`)   | periodic callback loop                               | drives periodic `syncTrackedSessions` and refresh-if-disconnected   | keep small scheduler; consider generic ticker service shared across domain    |
| `DispatcherProvider` (`dispatchers`)    | lane abstraction (`io`, `default`)                   | derives io/cpu/mutation lanes and serial mutation execution         | keep global provider, but wrap lane usage in smaller executors per feature    |
| `CoroutineRolloutFlag` (`rollout`)      | selects legacy vs pipeline mutation executor         | chooses `LegacyMutationExecutor` vs `PipelineMutationExecutor`      | remove runtime dual mode once pipeline stabilizes                             |

Refs: `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:24`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/ConnectionGateway.kt:6`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/ProjectGateway.kt:3`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/MessageGateway.kt:3`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionCacheGateway.kt:16`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/message/MessagePartParser.kt:8`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/message/MessageDecorator.kt:10`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/FocusedMessageProjector.kt:6`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionSyncPlanner.kt:18`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionEventReducer.kt:57`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionStreamCoordinator.kt:10`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/ReconcileCoordinator.kt:9`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/di/CoroutineRolloutFlag.kt:3`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/di/AppModule.kt:97`

---

## Trace functional flows

### Walk startup and hydration

1. `start` creates mutation executor mode, logs mode, starts connection gateway, and starts state aggregation collector.
2. It subscribes endpoint mirroring (`conn.endpoint -> input`) when not in manual mode, subscribes connected-status project load, and launches `hydrateLastNow`.
3. `hydrateLastNow` restores recent server/session from cache, sets URL, refreshes without loading spinner, then focuses restored session.
4. Reconcile and stream loops start immediately after base wiring.

Refs: `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:154`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:163`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:198`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:205`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:1151`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:1169`

### Walk project, session, and command load

1. `loadProjectsNow` reads favorite/hidden/pin overlays from cache and fetches remote projects.
2. Projects are canonicalized and merged with favorite/hidden overlays, then selected project is chosen and session/command lists are loaded.
3. `loadSessions` collects sessions for worktree plus sandboxes, filters archived and child sessions, persists cache snapshot, and applies workspace display cap.
4. `loadCommands` fetches command catalog for selected directory and updates state.

Refs: `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:254`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:282`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:940`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:1035`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:1106`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:1872`

### Walk open and focus behavior

1. `openSession` calls `focusSession(session, project)` which upserts active map and policy baseline.
2. Focus switch cancels and flushes previous overlay, sets new focus, clears unread for focused session, refreshes focused projection from cache observe, and forces sync.
3. If a project is known, command list reloads for that project to align slash commands with focused directory.

Refs: `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:532`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:712`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:730`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:1654`

### Walk send message and send command

1. `send` trims input, handles `/new`, then resolves slash command against loaded command list.
2. Command path calls `msg.sendCommand`, then schedules forced sync on success or sets error message on failure.
3. Message path lazily creates a session if none focused, inserts optimistic local user message into `pending` and `focusedMessages`, and calls `msg.sendMessage`.
4. Message send failure removes pending optimistic row and re-observes focused data to reconcile UI.

Refs: `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:538`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:620`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:628`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:553`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:510`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:580`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:611`

Flow checks: `packages/mobile/app/src/test/kotlin/de/chennemann/opencode/mobile/domain/session/SessionServiceTest.kt:109`, `packages/mobile/app/src/test/kotlin/de/chennemann/opencode/mobile/domain/session/SessionServiceTest.kt:137`, `packages/mobile/app/src/test/kotlin/de/chennemann/opencode/mobile/domain/session/SessionServiceTest.kt:173`

### Walk stream reduction and handlers

1. `SessionStreamCoordinator` runs SSE loop, logs raw chunks and events, updates persisted cursor before callback, and retries only after network-change signal.
2. `SessionEventReducer` maps raw event payload to typed actions (`ReloadProjects`, `SessionChanged`, `MessageUpdated`, `MessagePartUpdated`, `SessionStatus`, or drop/ignore).
3. `onEventNow` dispatches typed actions to focused handlers and each handler updates local state, maps, overlay/cache, and sync schedule.
4. Unknown or malformed events are dropped with structured warning logs.

Refs: `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionStreamCoordinator.kt:16`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionStreamCoordinator.kt:57`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionStreamCoordinator.kt:95`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionEventReducer.kt:58`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:1293`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:1301`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:1362`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:1418`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:1491`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:2194`

### Walk sync planning and reconciliation

1. `syncRemote` gate-checks, prevents concurrent per-session runs, fetches remote messages, reads cached messages, parses parts, and builds planner input.
2. `SessionSyncPlanner.plan` computes deterministic upserts/removals/sorts while optionally claiming optimistic pending sort values.
3. Service applies plan to in-memory indexes and cache, trims pending, updates tool/processing policy, and refreshes focused projection.
4. Reconcile loop periodically calls `syncTrackedSessions`, which includes favorite running-session discovery and sync of focused plus pinned sessions.

Refs: `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:757`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:785`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:811`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:849`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionSyncPlanner.kt:19`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:902`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:1169`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:1181`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:1186`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:1236`

### Walk optimistic update and reconciliation path

1. Optimistic user message insertion creates local ID `local-<ts>` with monotonic `z-` sort and stores it in `pending`.
2. SSE `message.updated` for user role attempts text-based claim from `pending`, then moves claimed sort into `stickySort` for remote ID adoption.
3. Sync planner prefers sticky or claimed pending sort for first-seen remote user message and marks `claimed=true` when optimistic sort is adopted.
4. On claim or trim changes, focused observer/projection refresh keeps visible list stable while reconciling with cache.

Refs: `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:578`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:580`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:1371`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:1378`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionSyncPlanner.kt:38`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionSyncPlanner.kt:44`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:907`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:1722`

### Walk archive, rename, and remove path

1. Archive removes session locally first and asynchronously calls remote archive.
2. Rename updates active/local/cache optimistically and rolls back with reload on remote failure.
3. `removeSession` clears policy, quick pins, processing/unread flags, cache session/messages, active-map entries, pending/retain/sort/overlay maps, flush jobs, and focus fallback.

Refs: `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:648`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:668`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:1730`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:1843`

Failure-path checks: `packages/mobile/app/src/test/kotlin/de/chennemann/opencode/mobile/domain/session/SessionServiceTest.kt:218`, `packages/mobile/app/src/test/kotlin/de/chennemann/opencode/mobile/domain/session/SessionServiceTest.kt:246`

---

## Explain concurrency model

### Define lanes and execution contexts

- `ioLane` is used for network and SQLDelight operations.
- `cpuLane` runs projection work (`projector.project`) away from mutation lane.
- `mutationLane` is single-threaded (`limitedParallelism(1)`) and owns nearly all state mutation.

Refs: `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:139`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:140`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:141`, `packages/mobile/app/src/test/kotlin/de/chennemann/opencode/mobile/domain/ArchitectureBoundaryTest.kt:28`

### Define mutation executors

- Legacy mode launches directly on mutation dispatcher and tracks live jobs in a synchronized set.
- Pipeline mode uses unbounded channel plus key de-dup (`pending` set) and a single worker consuming queue entries.
- Feature flag `useMigratedExecution` chooses mode at startup, backed by system property/env override.

Refs: `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:158`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:2302`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:2333`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/di/CoroutineRolloutFlag.kt:8`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/di/CoroutineRolloutFlag.kt:18`

### Define jobs and synchronization primitives

- Long-lived jobs: `stream`, `reconcile`, endpoint/status collectors, and focused-message observer.
- Short-lived keyed jobs: `flush[key]` and `sync.schedule(id)` timers.
- Synchronization primitives: `SyncCoordinator` mutex (`guard`) for active sync set and intrinsic lock `mapLock` for part/order/overlay snapshot consistency.
- Race controls include publish token cancelation (`publishToken`), focus-key checks before publish apply, and resolver cooldown for repeated unknown session resolution.

Refs: `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:123`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:124`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:127`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:128`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:132`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SyncCoordinator.kt:13`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:1689`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:1696`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionResolver.kt:8`

### Flag race-sensitive spots

- `keyForSession` is linear search over active keys and can return first match when duplicate session IDs exist across servers, so server-scoped identity assumptions are fragile.
- Overlay and cache observers run asynchronously across lanes, so stale publish is prevented by token checks but still increases complexity around eventual consistency.
- Pipeline executor de-dup by key can drop duplicate queued intents for same key, which is useful for coalescing but implicit for callers.

Refs: `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:1702`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:1674`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:1690`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:2355`

---

## Assess abstraction quality

### Keep what works well

- Boundary direction is explicit and enforced by test guard that blocks domain imports from `data`, `ui`, `navigation`, and Android namespaces.
- Stream reduction (`SessionEventReducer`) and sync planning (`SessionSyncPlanner`) are pure, testable components that reduce accidental complexity in handler code.
- Focus projection is extracted to `FocusedMessageProjector`, and caching in projector avoids repeated decoration work for unchanged messages.
- Ports are mapped in DI cleanly, and the service remains the single orchestrator entry point for view models.

Refs: `packages/mobile/app/src/test/kotlin/de/chennemann/opencode/mobile/domain/ArchitectureBoundaryTest.kt:12`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionEventReducer.kt:57`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionSyncPlanner.kt:18`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/FocusedMessageProjector.kt:6`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/di/AppModule.kt:83`

### Call out problematic boundaries

- `SessionService` mixes many concerns: startup orchestration, URL/manual mode, project/session browse, optimistic send, stream handling, sync policy math, cache persistence, and perf logging.
- Message rendering concerns leak into orchestration (`decorator.render` in sync/event handlers), so domain state mutation is coupled to presentation formatting details.
- Session identity coupling leaks through string-composed keys (`server::session`, `server::session::message`) spread through service internals.
- `ServerRepository` implements five domain ports in one class, which reduces isolation of connection/project/message/stream responsibilities and test seam granularity.

Refs: `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:24`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:833`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:1384`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:1950`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/data/ServerRepository.kt:35`

### Show concrete leak examples

| Example                                                                                                               | Why it leaks                                                                          | Evidence                                                                                                                                                                                                                                                                                                                                                                                                                                   |
| --------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Manual URL mode (`manual`) affects cache server key selection                                                         | Connection-edit UI concern leaks into cache partitioning and sync identity            | `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:122`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:713`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:1868`                                                                                                              |
| Sync gate combines transport capability probe, status probe, updated-at probe, tool watchdog, and UI processing flags | Crosses transport policy, domain sync strategy, and UI flag mutation in one path      | `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:1967`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:1985`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:2135`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:1820` |
| Event handlers directly mutate overlay, part cache, role map, and cache rows                                          | Handler layer carries both reduction response and persistence/write-model details     | `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:1362`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:1393`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:1418`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:1625` |
| `SessionCacheRepository` stores both domain snapshots and UI overlays (favorites/hidden/pins)                         | Data adapter becomes mixed persistence model for lifecycle cache and user preferences | `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/data/SessionCacheRepository.kt:49`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/data/SessionCacheRepository.kt:120`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/data/SessionCacheRepository.kt:168`                                                                                                                      |

---

## Propose simplification roadmap

### Plan short-term safe refactors

| Refactor                                        | Goal                                                                                 | Impact                                        | Risk       |
| ----------------------------------------------- | ------------------------------------------------------------------------------------ | --------------------------------------------- | ---------- |
| Extract `SessionKey`/`MessageKey` value objects | Remove ad-hoc string composition and parsing                                         | Better type safety and fewer key bugs         | Low        |
| Extract `SessionFocusStore`                     | Centralize `focusedKey`, `observeFocused`, `publishFocused`, overlay flush lifecycle | Smaller `SessionService`, easier race testing | Low-medium |
| Extract `ProjectCatalogService`                 | Isolate project merge/favorite/hidden selection and session/command load bootstrap   | Reduces size of top-level service methods     | Low-medium |
| Extract `SyncPolicyEngine`                      | Move `policy`/`directoryPolicy` gate math into dedicated stateful component          | Clarifies sync decisions and testing          | Medium     |

Refs: `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:1702`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:1654`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:254`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:1967`

### Plan medium-term structural changes

| Change                                                       | Proposed boundary                                                                                         | Impact                                      | Risk   |
| ------------------------------------------------------------ | --------------------------------------------------------------------------------------------------------- | ------------------------------------------- | ------ |
| Split `MessageGateway`                                       | `MessageSendPort` + `MessageQueryPort` + `MessageStatusPort`                                              | Decouples send path from sync/status probes | Medium |
| Split `ServerRepository`                                     | `ConnectionRepository`, `ProjectRepository`, `MessageRepository`, `StreamRepository`, `CommandRepository` | Better ownership and smaller test doubles   | Medium |
| Move optimistic/pending logic into `OptimisticMessageEngine` | Single unit for `pending`, `stickySort`, `retainPass`, claim/trim policy                                  | Cleaner sync and event handlers             | Medium |
| Move metadata overlays out of session cache adapter          | Dedicated `WorkspacePreferenceStore` for favorites/hidden/pins                                            | Clarifies persistence intent and migration  | Medium |

Refs: `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/MessageGateway.kt:3`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/data/ServerRepository.kt:35`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:114`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/data/SessionCacheRepository.kt:120`

### Plan long-term architecture shifts

| Change                                                                            | Target outcome                                                                 | Impact                                         | Risk        |
| --------------------------------------------------------------------------------- | ------------------------------------------------------------------------------ | ---------------------------------------------- | ----------- |
| Introduce domain-core state machine (`SessionCore`) with explicit commands/events | Deterministic reducer-style core, easier replay and property testing           | High maintainability payoff                    | High        |
| Isolate side effects into adapters (`SessionEffects`)                             | Networking/cache/logging become effect handlers, not inline orchestration code | Enables cleaner cancellation and observability | High        |
| Remove dual mutation executor mode                                                | Standardize on pipeline executor and simplify code paths                       | Less branch complexity in concurrency layer    | Medium-high |

Refs: `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:145`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:158`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:2278`

### Order migration incrementally

1. Introduce typed key objects and replace raw key helper methods first.
2. Extract focus/overlay subsystem (`SessionFocusStore`) without changing external API.
3. Extract sync policy engine and add isolated tests for gate outcomes.
4. Split repository/ports and update DI bindings with compatibility adapters.
5. Move optimistic engine and finally converge to single mutation executor mode.
6. After boundaries stabilize, introduce domain-core command/event model behind existing `SessionServiceApi`.

Refs: `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:1702`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:1654`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:1967`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/di/AppModule.kt:83`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:158`

---

## Sketch target architecture

| Layer                     | Component                 | Responsibility                                                                    | Talks to                                     |
| ------------------------- | ------------------------- | --------------------------------------------------------------------------------- | -------------------------------------------- |
| Application orchestration | `SessionService`          | Keep current `SessionServiceApi`, route commands to core/effects                  | `SessionCore`, `SessionEffects`, view models |
| Domain core               | `SessionCore`             | Pure state transitions from commands + stream actions + sync outcomes             | none (pure)                                  |
| Domain policy             | `SyncPolicyEngine`        | Session and directory gate/backoff decisions                                      | `SessionCore`, sync runner                   |
| Domain projection         | `FocusedProjection`       | Merge base + staged + pending + parts into render-ready messages                  | `MessageDecorator`                           |
| Domain optimistic         | `OptimisticMessageEngine` | Pending add/claim/trim and sticky sort tracking                                   | `SessionCore`, sync runner                   |
| Side effects              | `SessionEffects`          | Execute IO: load projects/sessions/commands/messages, send, archive/rename        | Ports                                        |
| Ports                     | granular gateways         | Connection, projects, commands, send, query, stream, cache, logging, connectivity | Data adapters                                |
| Data adapters             | split repositories        | Concrete HTTP/SSE/cache/network implementations                                   | API/SQLDelight/Android                       |

Design notes:

- Keep `SessionServiceApi` stable while extracting internals to avoid UI churn.
- Preserve current reducer/planner/projector components and let them become `SessionCore` dependencies.

Refs: `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionServiceApi.kt:6`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionEventReducer.kt:57`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionSyncPlanner.kt:18`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/FocusedMessageProjector.kt:6`

---

## List key files

### Keep primary service files nearby

- `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt`
- `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionServiceApi.kt`
- `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionUiState.kt`
- `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionModels.kt`

### Keep core collaborators nearby

- `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionEventReducer.kt`
- `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionSyncPlanner.kt`
- `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/FocusedMessageProjector.kt`
- `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/PendingBuffer.kt`
- `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SyncCoordinator.kt`
- `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionStreamCoordinator.kt`
- `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/ReconcileCoordinator.kt`

### Keep data adapters and wiring nearby

- `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/data/ServerRepository.kt`
- `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/data/ServerService.kt`
- `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/data/SessionCacheRepository.kt`
- `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/data/NetworkService.kt`
- `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/di/AppModule.kt`

### Keep verification references nearby

- `packages/mobile/app/src/test/kotlin/de/chennemann/opencode/mobile/domain/session/SessionServiceTest.kt`
- `packages/mobile/app/src/test/kotlin/de/chennemann/opencode/mobile/domain/ArchitectureBoundaryTest.kt`
- `packages/mobile/docs/architecture.md`
