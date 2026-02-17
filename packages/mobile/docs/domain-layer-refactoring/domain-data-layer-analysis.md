---
title: Mobile core map
description: Baseline for Android state and storage refactors.
---

## Set purpose

- Purpose: document the current domain and data architecture in `packages/mobile` as a refactor baseline.
- Scope: only `:app`, `:api`, and `:streaming-markdown` in `packages/mobile`, with deep detail on domain/data and only boundary context for UI/DI/navigation.
- Method: static code tracing across module wiring, gateways, coordinators, reducers, caches, and tests, with line-anchored references for every key claim.

---

## Map modules

| Module                | Role in runtime                                                             | Domain/data relevance                                         | Key refs                                                                                                                                                                                                                                                                                      |
| --------------------- | --------------------------------------------------------------------------- | ------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `:app`                | Android app, DI, UI, navigation, domain orchestration, data implementations | All domain/data logic lives here today                        | `packages/mobile/settings.gradle.kts:23`, `packages/mobile/app/build.gradle.kts:52`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:24`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/data/ServerRepository.kt:28` |
| `:api`                | OpenAPI-generated Kotlin client and transport models                        | Transport dependency used by `ServerService`; no domain logic | `packages/mobile/settings.gradle.kts:24`, `packages/mobile/api/build.gradle.kts:11`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/data/ServerService.kt:3`                                                                                                              |
| `:streaming-markdown` | Compose rendering library for streamed markdown                             | UI rendering boundary only; no domain/data orchestration      | `packages/mobile/settings.gradle.kts:25`, `packages/mobile/app/build.gradle.kts:53`, `packages/mobile/streaming-markdown/build.gradle.kts:39`                                                                                                                                                 |

- Domain and data are package-scoped under `de.chennemann.opencode.mobile.domain` and `de.chennemann.opencode.mobile.data` in `:app`.
- A guard test prevents domain imports from `data`, `ui`, `navigation`, and `android` namespaces.

Refs: `packages/mobile/app/src/test/kotlin/de/chennemann/opencode/mobile/domain/ArchitectureBoundaryTest.kt:12`

---

## Define layers

- Layer direction is `ui -> domain`, `navigation -> ui`, `domain -> data` through interfaces, and `data` avoids UI coupling.
- `SessionService` is the domain orchestration boundary consumed by view models through `SessionServiceApi`.
- Port contracts live in domain (`ConnectionGateway`, `ProjectGateway`, `MessageGateway`, `StreamGateway`, `SessionCacheGateway`, `ConnectivityGateway`, `LogGateway`, `LogStoreGateway`).
- Adapters live in data (`ServerRepository`, `ServerService`, `SessionCacheRepository`, `NetworkService`, `AndroidLogGateway`, `LocalLogRepository`, `MdnsService`).

Refs: `packages/mobile/docs/architecture.md:12`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionServiceApi.kt:6`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/di/AppModule.kt:83`

---

## Catalog components

### Domain components

| Component                  | Responsibility                                                                                                                                 | Inbound dependencies                                                   | Outbound dependencies                                                                      | Key refs                                                                                                                                                                                                                     |
| -------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------- | ------------------------------------------------------------------------------------------ | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `SessionServiceApi`        | Public use-case contract for session/project/message actions                                                                                   | `ConversationViewModel`, `ManageViewModel`                             | Implemented by `SessionService`                                                            | `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionServiceApi.kt:6`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/conversation/ConversationViewModel.kt:25`   |
| `SessionService`           | Central state machine and orchestrator for connection, project/session loading, message send/sync, SSE ingest, cache writes, and UI projection | Koin singleton eager start, view models calling API                    | All domain ports, planner/reducer/coordinators/projector/parser/decorator, coroutine lanes | `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:24`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/di/AppModule.kt:96`                              |
| `SessionStreamCoordinator` | Owns SSE loop, cursor resume, retry gating on network change                                                                                   | `SessionService.stream`                                                | `ConnectionGateway`, `StreamGateway`, `ConnectivityGateway`, `LogGateway`                  | `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionStreamCoordinator.kt:10`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:1164` |
| `ReconcileCoordinator`     | Periodic trigger loop for reconcile passes                                                                                                     | `SessionService.reconcile`                                             | Callback block only                                                                        | `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/ReconcileCoordinator.kt:9`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:1169`      |
| `SyncCoordinator`          | Debounced per-session scheduling and active-run guard                                                                                          | `SessionService.scheduleSync`, `SessionService.beginSync/endSync`      | Coroutines mutex/jobs                                                                      | `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SyncCoordinator.kt:10`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:1531`          |
| `SessionSyncPlanner`       | Deterministic plan for upsert/remove/sort and optimistic claim outcome                                                                         | `SessionService.syncRemote`                                            | Caller-provided functions (`remoteSort`, `knownSort`, `claimPendingSort`, `retainRemoved`) | `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionSyncPlanner.kt:18`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:849`        |
| `SessionEventReducer`      | Maps raw SSE payloads into typed domain actions or drops                                                                                       | `SessionService.onEventNow`                                            | JSON parsing helpers only                                                                  | `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionEventReducer.kt:57`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:1302`      |
| `FocusedMessageProjector`  | Merges DB base + staged overlay + pending + parts and decorates tool calls                                                                     | `SessionService.publishFocused`                                        | `MessageDecorator`                                                                         | `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/FocusedMessageProjector.kt:6`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:1692`   |
| `PendingBuffer`            | Tracks optimistic local user messages and claim/trim lifecycle                                                                                 | `SessionService.send`, `SessionService.syncRemote`, event handlers     | `PassCounter`                                                                              | `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/PendingBuffer.kt:8`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:580`              |
| `PassCounter`              | Generic pass-based retain budget map for IDs per key                                                                                           | `PendingBuffer`, `SessionService.retainPass`                           | In-memory map only                                                                         | `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/PassCounter.kt:3`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:115`                |
| `SessionResolver`          | Cooldown gate for repeated session resolution attempts                                                                                         | `SessionService.resolveSession`                                        | In-memory timestamp map                                                                    | `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionResolver.kt:3`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:1542`           |
| `MessagePartParser`        | Parses structured message parts from API/SSE JSON                                                                                              | `SessionService.syncRemote`, `SessionService.handleMessagePartUpdated` | JSON helpers                                                                               | `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/message/MessagePartParser.kt:8`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:822`          |
| `MessageDecorator`         | Renders assistant text from parts and builds tool-call view model payloads                                                                     | `SessionService`, `FocusedMessageProjector`                            | JSON helper methods                                                                        | `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/message/MessageDecorator.kt:10`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/FocusedMessageProjector.kt:59`  |
| `LogRedactor`              | Redacts sensitive log message/context/throwable content                                                                                        | `AndroidLogGateway`                                                    | Regex rules                                                                                | `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/LogRedactor.kt:3`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/data/AndroidLogGateway.kt:33`                        |
| `ConnectionGateway`        | Port for endpoint state, discovery, and health refresh                                                                                         | `SessionService`, `SessionStreamCoordinator`                           | Implemented by `ServerRepository`                                                          | `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/ConnectionGateway.kt:6`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/data/ServerRepository.kt:35`                   |
| `ProjectGateway`           | Port for projects and session lifecycle operations                                                                                             | `SessionService`                                                       | Implemented by `ServerRepository`                                                          | `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/ProjectGateway.kt:3`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/data/ServerRepository.kt:35`                      |
| `CommandGateway`           | Port for workspace command list                                                                                                                | `SessionService`                                                       | Implemented by `ServerRepository`                                                          | `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/CommandGateway.kt:3`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/data/ServerRepository.kt:35`                      |
| `MessageGateway`           | Port for message list/status/sending                                                                                                           | `SessionService`                                                       | Implemented by `ServerRepository`                                                          | `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/MessageGateway.kt:3`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/data/ServerRepository.kt:35`                      |
| `StreamGateway`            | Port for SSE stream and cursor persistence                                                                                                     | `SessionStreamCoordinator`                                             | Implemented by `ServerRepository`                                                          | `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/StreamGateway.kt:3`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/data/ServerRepository.kt:35`                       |
| `SessionCacheGateway`      | Port for session/message/settings cache reads/writes                                                                                           | `SessionService`, `ConversationViewModel` (cached list via API path)   | Implemented by `SessionCacheRepository`                                                    | `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionCacheGateway.kt:16`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/data/SessionCacheRepository.kt:15`          |
| `ConnectivityGateway`      | Port for network online/change signals                                                                                                         | `SessionStreamCoordinator`, `ServerRepository` internal trigger wiring | Implemented by `NetworkService`                                                            | `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/ConnectivityGateway.kt:5`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/data/NetworkService.kt:12`                   |
| `LogGateway`               | Port for domain logging calls                                                                                                                  | `SessionService`, `SessionStreamCoordinator`, `ServerRepository`       | Implemented by `AndroidLogGateway`                                                         | `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/LogGateway.kt:3`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/data/AndroidLogGateway.kt:13`                         |
| `LogStoreGateway`          | Port for persisted log append/query/facets/prune                                                                                               | `AndroidLogGateway`, `LogsViewModel`                                   | Implemented by `LocalLogRepository`                                                        | `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/LogStoreGateway.kt:78`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/data/LocalLogRepository.kt:27`                  |

### Data components

| Component                         | Responsibility                                                                                           | Inbound dependencies                                       | Outbound dependencies                                                                                | Key refs                                                                                                                                                                                            |
| --------------------------------- | -------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------- | ---------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `ServerRepository`                | Composite adapter implementing five domain ports and URL/cursor persistence                              | `SessionService`, `SessionStreamCoordinator`, DI bindings  | `MdnsGateway`, `ServerGateway`, `ConnectivityGateway`, `AppDatabase`, dispatcher lanes, `LogGateway` | `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/data/ServerRepository.kt:28`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/di/AppModule.kt:83`             |
| `ServerService` (`ServerGateway`) | HTTP/OpenAPI transport adapter for health/projects/sessions/messages/status/SSE/commands/send operations | `ServerRepository`                                         | `DefaultApi`, raw `HttpClient` + SSE, JSON serialization                                             | `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/data/ServerService.kt:80`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/data/ServerRepository.kt:31`       |
| `SessionCacheRepository`          | SQLDelight cache adapter for sessions/messages/settings and quick pin/favorite/hidden metadata           | `SessionService` through `SessionCacheGateway`             | `AppDatabase` queries, SQLDelight flow bridge                                                        | `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/data/SessionCacheRepository.kt:15`, `packages/mobile/app/src/main/sqldelight/de/chennemann/opencode/mobile/db/AppDatabase.sq:71` |
| `NetworkService`                  | Android connectivity adapter exposed as domain signals                                                   | `ServerRepository` and `SessionStreamCoordinator` via port | `ConnectivityManager` callbacks                                                                      | `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/data/NetworkService.kt:12`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/data/ServerRepository.kt:50`      |
| `MdnsService` (`MdnsGateway`)     | mDNS endpoint discovery flow                                                                             | `ServerRepository.start`                                   | `NsdManager`, `WifiManager` multicast lock                                                           | `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/data/MdnsService.kt:30`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/data/ServerRepository.kt:55`         |
| `AndroidLogGateway`               | Log adapter that redacts, writes to Logcat, and appends to store                                         | Domain logging callers                                     | `LogStoreGateway`, `LogRedactor`, app scope coroutine                                                | `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/data/AndroidLogGateway.kt:13`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/di/AppModule.kt:79`            |
| `LocalLogRepository`              | SQLDelight-backed log store with reactive filters/facets and retention policy                            | `AndroidLogGateway`, `LogsViewModel`                       | `AppDatabase` queries, JSON encode/decode                                                            | `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/data/LocalLogRepository.kt:27`, `packages/mobile/app/src/main/sqldelight/de/chennemann/opencode/mobile/db/AppDatabase.sq:173`    |

---

## Map ports

| Domain port                                                   | Concrete adapter         | DI binding location                                                                                                                               |
| ------------------------------------------------------------- | ------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------- |
| `ConnectionGateway`                                           | `ServerRepository`       | `single<ConnectionGateway> { get<ServerRepository>() }` in `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/di/AppModule.kt:83` |
| `ProjectGateway`                                              | `ServerRepository`       | `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/di/AppModule.kt:84`                                                            |
| `CommandGateway`                                              | `ServerRepository`       | `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/di/AppModule.kt:85`                                                            |
| `MessageGateway`                                              | `ServerRepository`       | `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/di/AppModule.kt:86`                                                            |
| `StreamGateway`                                               | `ServerRepository`       | `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/di/AppModule.kt:87`                                                            |
| `SessionCacheGateway`                                         | `SessionCacheRepository` | `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/di/AppModule.kt:88`                                                            |
| `ConnectivityGateway`                                         | `NetworkService`         | `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/di/AppModule.kt:76`                                                            |
| `LogGateway`                                                  | `AndroidLogGateway`      | `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/di/AppModule.kt:79`                                                            |
| `LogStoreGateway`                                             | `LocalLogRepository`     | `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/di/AppModule.kt:78`                                                            |
| `MdnsGateway` (data-side dependency for `ServerRepository`)   | `MdnsService`            | `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/di/AppModule.kt:69`                                                            |
| `ServerGateway` (data-side dependency for `ServerRepository`) | `ServerService`          | `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/di/AppModule.kt:80`                                                            |

- `SessionService` is bound as a created-at-start singleton and exported as `SessionServiceApi`.
- This makes domain orchestration live before any screen requests it.

Refs: `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/di/AppModule.kt:96`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/di/AppModule.kt:100`

---

## Trace startup

1. `App.onCreate` starts Koin with `appModule`.
2. Koin constructs `SessionService` eagerly (`createdAtStart = true`) and immediately calls `start(appScope)`.
3. `SessionService.start` configures mutation executor mode, starts connection discovery/health flow, hydrates last session, starts reconcile loop, and starts stream loop.
4. `MainActivity` sets Compose content and renders `AppNavHost`.
5. Navigation stack starts on `ConversationRoute`, so conversation screen is first.
6. `ConversationViewModel` and `ManageViewModel` both call `service.start(viewModelScope)`, but service startup is idempotent (`if (started) return`).

Refs: `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/App.kt:36`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/di/AppModule.kt:96`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:154`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/navigation/AppNavHost.kt:20`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/conversation/ConversationViewModel.kt:152`

---

## Walk flows

### Connect and refresh

1. UI emits `ConnectTapped` -> `service.refresh()`.
2. Service mutates URL into `ConnectionGateway`, then calls `conn.refresh()`.
3. `ServerRepository.refresh` calls `ServerService.health` and updates `ConnectionState`.
4. Connected state triggers `loadProjectsNow` via `conn.status.collect` in service startup.

Refs: `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/manage/ManageViewModel.kt:109`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:241`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/data/ServerRepository.kt:69`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:205`

### Load projects and sessions

1. `loadProjectsNow` sets loading state and reads cached favorites/hidden/quick-pin metadata.
2. It fetches remote projects, canonicalizes worktrees/sandboxes, merges hidden/favorite overlays, and chooses `selectedProject`.
3. It preloads favorite sessions and then triggers `loadSessions` and `loadCommands` for selected project.
4. `loadSessions` fetches sessions for primary worktree plus sandboxes, drops archived/child sessions, persists snapshots, and applies per-workspace limits.

Refs: `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:254`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:282`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:940`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:1035`

### Open and focus session

1. UI calls `service.openSession(session)`.
2. Service upserts active session tracking, resets focus-related overlays, updates `focusedSession`, and clears unread for that session.
3. Service starts DB observer for focused messages and forces a remote sync.

Refs: `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:532`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:730`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:1654`

### Send message

1. `send(text, agent)` trims input and handles `/new` as a built-in command.
2. If no focused session exists, service creates one lazily from first message and focuses it.
3. It adds an optimistic local user message to `PendingBuffer` and `focusedMessages` with `local-<ts>` ID and monotonic `z-` sort.
4. It sends message via `MessageGateway.sendMessage`; success schedules forced sync and failure rolls back optimistic state.

Refs: `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:538`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:510`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:580`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:600`

### Send command

1. Slash text is matched against loaded command catalog.
2. Service sends command via `MessageGateway.sendCommand` with parsed arguments.
3. Success schedules forced sync; failure sets user-facing message.

Refs: `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:628`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:555`

### Ingest stream and mutate state

1. `SessionStreamCoordinator` opens SSE with persisted cursor and logs raw/event chunks.
2. Each stream event is reduced by `SessionEventReducer` into typed action.
3. `SessionService` handler updates overlay/cache/processing flags depending on action and schedules sync.
4. Event cursor is persisted before callback, so retries resume from latest seen event ID.

Refs: `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionStreamCoordinator.kt:34`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionEventReducer.kt:58`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:1301`, `packages/mobile/app/src/test/kotlin/de/chennemann/opencode/mobile/domain/session/SessionStreamCoordinatorTest.kt:150`

### Run sync and reconcile cycles

1. Sync is scheduled per session via `SyncCoordinator.schedule` with burst/normal delays.
2. `syncRemote` applies gate checks, ensures single active run per session, fetches messages, computes plan, updates cache, and updates focused projection.
3. Reconcile loop runs every 15 seconds and triggers `syncTrackedSessions`, including favorite-running discovery by directory status checks.

Refs: `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:1531`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:757`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/ReconcileCoordinator.kt:10`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:1181`

### Handle offline and online transitions

1. `NetworkService` increments `changed` on availability/loss/capability transitions.
2. `ServerRepository.start` watches `network.changed.drop(1)` and refreshes health without loading spinner.
3. `SessionStreamCoordinator` waits for network change before retrying stream after failures, then applies fixed reconnect delay.
4. Reconcile loop also triggers `conn.refresh(false)` when status is not connected.

Refs: `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/data/NetworkService.kt:24`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/data/ServerRepository.kt:50`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionStreamCoordinator.kt:95`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:1173`

### Load more messages

1. UI taps load-more and calls `service.loadMoreMessages()`.
2. Service increments per-session message limit and forces `syncRemote(..., more = true)`.
3. Sync sets `loadingMoreMessages`, fetches larger window, computes `complete = next.size < limit`, and updates `canLoadMoreMessages`.

Refs: `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:638`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:791`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:910`

### Archive, rename, and remove session

1. Archive path optimistically removes local session, calls remote archive, and reloads sessions on failure.
2. Rename path optimistically updates active/focused/session lists and cache, then rolls back on remote failure.
3. Hard removal is handled by `removeSession` (called on delete events/archive path), which clears policy, pins, processing state, cache rows, messages, overlays, and focus.

Refs: `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:648`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:668`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:1730`

---

## Explain synchronization

### Describe control objects

| Object                     | Core behavior                                                           | Key refs                                                                                                          |
| -------------------------- | ----------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------- |
| `SessionStreamCoordinator` | Long-lived SSE job with persisted cursor and network-gated retries      | `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionStreamCoordinator.kt:16` |
| `SyncCoordinator`          | Per-session delayed schedule + mutex-protected active set               | `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SyncCoordinator.kt:15`          |
| `ReconcileCoordinator`     | Periodic callback driver for global sync sweeps                         | `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/ReconcileCoordinator.kt:12`     |
| `SessionSyncPlanner`       | Pure planning function for merge/sort/remove + optimistic claim flag    | `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionSyncPlanner.kt:19`       |
| `PendingBuffer`            | Tracks optimistic local user messages and claim-by-text matching        | `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/PendingBuffer.kt:34`            |
| `PassCounter`              | One-pass retain budget for optimistic/reconcile delete tolerance        | `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/PassCounter.kt:22`              |
| `SessionResolver`          | Cooldown guard before resolving unknown session IDs from workspace list | `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionResolver.kt:8`           |

### Describe optimistic and reconcile behavior

- Optimistic send inserts local message into pending and focused projection before network send returns.
- SSE `message.updated` for user role tries to claim pending by text and transfers optimistic sort into sticky sort map.
- Remote sync planner prefers sticky/pending sort for newly matched user rows and sets `claimed = true` when optimistic `z-` sort was used.
- `observeFocused` is refreshed after claim/trim events to reconcile staged and persisted state.

Refs: `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:580`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:1371`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionSyncPlanner.kt:38`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:907`

### Describe backoff and gating decisions

- `syncGate` enforces interval backoff, recent-SSE quiet window, and force overrides.
- `checkFirst` runs status check and updated-at check unless API reports unsupported checks (`404`/`405`), then falls back to fetch with expanded interval.
- Unchanged updated-at expands interval exponentially up to max.
- Applied SSE events reset backoff for affected sessions.
- Directory-level checks use separate `DirectoryPolicy` with independent intervals and unsupported-check disable path.

Refs: `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:1967`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:1985`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:2061`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:2176`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:2067`

---

## Explain persistence

### Describe SQLDelight schema

| Table           | Purpose                                                                 | Important columns                                                                                                |
| --------------- | ----------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------- |
| `settings`      | Generic key-value storage for endpoint, cursors, and user metadata sets | `key`, `value`                                                                                                   |
| `session_cache` | Per-server session snapshot + recency marker                            | `server_url`, `session_id`, `project_id`, `directory`, `title`, `version`, `last_opened_at`, `updated_at`        |
| `message_cache` | Per-session ordered message cache                                       | `server_url`, `session_id`, `message_id`, `role`, `text`, `sort_key`, `created_at`, `completed_at`, `updated_at` |
| `app_log`       | Structured local observability records                                  | `created_at`, `level`, `logical_unit`, `event`, project/session facets, context JSON, throwable                  |

Refs: `packages/mobile/app/src/main/sqldelight/de/chennemann/opencode/mobile/db/AppDatabase.sq:6`, `packages/mobile/app/src/main/sqldelight/de/chennemann/opencode/mobile/db/AppDatabase.sq:11`, `packages/mobile/app/src/main/sqldelight/de/chennemann/opencode/mobile/db/AppDatabase.sq:23`, `packages/mobile/app/src/main/sqldelight/de/chennemann/opencode/mobile/db/AppDatabase.sq:36`

### Describe key queries

- Session cache upserts use `upsertSessionCache` for open/active updates and `upsertSessionCacheSnapshot` for sync snapshots.
- Project lists come from `listProjectSessionCache` and `listProjectSessionCacheLimited`, ordered by `updated_at DESC`.
- Message reads use `listMessageCache` ordered by `sort_key ASC`.
- Log reads use `listAppLog` with optional facet filters and text search.
- Retention helpers include `deleteAppLogBefore`, `deleteAppLogOverflow`, and `deleteAppLogAll`.

Refs: `packages/mobile/app/src/main/sqldelight/de/chennemann/opencode/mobile/db/AppDatabase.sq:71`, `packages/mobile/app/src/main/sqldelight/de/chennemann/opencode/mobile/db/AppDatabase.sq:121`, `packages/mobile/app/src/main/sqldelight/de/chennemann/opencode/mobile/db/AppDatabase.sq:138`, `packages/mobile/app/src/main/sqldelight/de/chennemann/opencode/mobile/db/AppDatabase.sq:191`, `packages/mobile/app/src/main/sqldelight/de/chennemann/opencode/mobile/db/AppDatabase.sq:236`

### Describe mapping rules

- `ServerRepository` maps transport DTOs into domain models (`SessionProject`, `SessionSummary`, `SessionMessage`, `CommandState`).
- `SessionCacheRepository` maps SQL rows into `SessionState` and `MessageState`, preserving `sort_key` ordering and optional timestamps.
- `LocalLogRepository` maps `App_log` rows into `LogEntry` and decodes context JSON into `Map<String, String>`.

Refs: `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/data/ServerRepository.kt:98`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/data/SessionCacheRepository.kt:244`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/data/LocalLogRepository.kt:138`

### Describe cleanup and retention

- `syncProjectSessions` removes stale cached sessions for a project and deletes their message rows.
- `removeSession` deletes a session cache row and all its message rows.
- Log retention keeps 7 days and caps to 5000 rows, with periodic prune every 200 appends.

Refs: `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/data/SessionCacheRepository.kt:64`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:1740`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/data/LocalLogRepository.kt:52`

### Describe settings keys

| Key                              | Owner                    | Meaning                                       |
| -------------------------------- | ------------------------ | --------------------------------------------- |
| `server_url`                     | `ServerRepository`       | Current normalized base URL                   |
| `event_cursor:<url>`             | `ServerRepository`       | Last successful SSE event ID per endpoint     |
| `project_favorite:<server>`      | `SessionCacheRepository` | Newline-delimited favorite worktree IDs       |
| `project_hidden:<server>`        | `SessionCacheRepository` | Newline-delimited hidden worktree IDs         |
| `session_quick_include:<server>` | `SessionCacheRepository` | Forced quick-switch session pins              |
| `session_quick_exclude:<server>` | `SessionCacheRepository` | Forced unpinned overrides against system pins |

Refs: `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/data/ServerRepository.kt:277`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/data/ServerRepository.kt:278`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/data/SessionCacheRepository.kt:289`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/data/SessionCacheRepository.kt:297`

---

## Explain logging

1. Domain/data code emits logs through `LogGateway` (`debug/info/warn/error`).
2. `AndroidLogGateway` redacts content, prints line to Logcat, and appends structured `LogRecord` asynchronously.
3. `LocalLogRepository` persists to `app_log`, exposes filtered reactive queries and facets, and runs prune policy.
4. `LogsViewModel` builds filter state and subscribes to `observe` + `observeFacet` for the logs screen.

Refs: `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/LogGateway.kt:3`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/data/AndroidLogGateway.kt:24`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/data/LocalLogRepository.kt:34`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/logs/LogsViewModel.kt:55`

---

## Explain concurrency

### Define lanes

- Mutation lane: single-threaded dispatcher (`default.limitedParallelism(1)`) for deterministic state mutation.
- IO lane: `dispatchers.io` for network and SQLDelight operations.
- CPU lane: `dispatchers.default` for projection/decorating work.

Refs: `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:139`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:141`

### Define executor modes

- Legacy mode: direct launches on mutation lane with per-job tracking.
- Pipeline mode: queued `MutationPipeline` with optional key de-duplication and a single worker consuming channel entries.
- Selection is controlled by `CoroutineRolloutFlag` (`opencode.mobile.coroutines.legacy` property or `OPENCODE_MOBILE_COROUTINES_LEGACY` env).

Refs: `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:158`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:2302`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:2333`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/di/CoroutineRolloutFlag.kt:11`

### Define lifecycle and race controls

- `started` guard keeps `SessionService.start` idempotent.
- `SyncCoordinator.begin/end` blocks concurrent sync per session.
- Overlay writes are protected by `mapLock`, and focused publication uses token cancellation to prevent stale writes.
- Pending/flush jobs are keyed by session and canceled on focus switch/removal.
- Stream retries are network-change gated to avoid tight loops when offline.

Refs: `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:155`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SyncCoordinator.kt:23`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:132`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:1689`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:1616`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionStreamCoordinator.kt:95`

---

## List interactions

| Caller                     | Callee                     | Interaction type              | Why it exists                             |
| -------------------------- | -------------------------- | ----------------------------- | ----------------------------------------- |
| `ConversationViewModel`    | `SessionServiceApi`        | Commands + state subscription | Conversation actions and rendering state  |
| `ManageViewModel`          | `SessionServiceApi`        | Commands + state subscription | Project/session management actions        |
| `SessionService`           | `ConnectionGateway`        | Read/command                  | Endpoint state + refresh                  |
| `SessionService`           | `ProjectGateway`           | Read/write                    | Projects and session metadata lifecycle   |
| `SessionService`           | `CommandGateway`           | Read                          | Slash command catalog                     |
| `SessionService`           | `MessageGateway`           | Read/write                    | Message sync, send, status checks         |
| `SessionService`           | `SessionCacheGateway`      | Read/write/observe            | Session/message cache + user metadata     |
| `SessionService`           | `SessionEventReducer`      | Pure reduction                | Normalize SSE payloads to actions         |
| `SessionService`           | `SessionSyncPlanner`       | Pure planning                 | Deterministic upsert/remove/sort decision |
| `SessionService`           | `FocusedMessageProjector`  | Pure projection               | Merge base/staged/pending/parts for UI    |
| `SessionService`           | `SessionStreamCoordinator` | Job ownership                 | Start SSE ingest loop                     |
| `SessionService`           | `ReconcileCoordinator`     | Job ownership                 | Start periodic reconcile loop             |
| `SessionStreamCoordinator` | `StreamGateway`            | Stream read/write cursor      | Resume-safe SSE ingest                    |
| `SessionStreamCoordinator` | `ConnectivityGateway`      | State wait                    | Retry only after network changes          |
| `ServerRepository`         | `ServerService`            | Transport calls               | API access and SSE decode                 |
| `ServerRepository`         | `AppDatabase`              | Setting read/write            | URL and event cursor persistence          |
| `ServerRepository`         | `MdnsGateway`              | Discovery flow                | Auto-discover local endpoint              |
| `SessionCacheRepository`   | `AppDatabase`              | Query/command                 | Session/message/settings cache            |
| `AndroidLogGateway`        | `LocalLogRepository`       | Append + prune                | Durable observability store               |
| `LogsViewModel`            | `LogStoreGateway`          | Observe                       | Log query UX                              |

Refs: `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/conversation/ConversationViewModel.kt:77`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/manage/ManageViewModel.kt:48`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:24`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/data/ServerRepository.kt:28`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/data/SessionCacheRepository.kt:15`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/data/AndroidLogGateway.kt:43`

---

## Capture invariants

- Domain boundary invariant: domain files do not import data/ui/navigation/android namespaces.
- Dispatcher invariant: direct `Dispatchers.*` usage is restricted to dispatcher infrastructure.
- Startup invariant: service `start` is idempotent and safe when called from eager DI and view models.
- Session list invariant: archived and child sessions are excluded from primary lists.
- Ordering invariant: message order is deterministic via `sort` keys (`r-*` remote and `z-*` optimistic).
- Optimistic invariant: failed sends remove local optimistic entries.
- Sync concurrency invariant: only one sync run per session ID at a time.
- Pin/favorite invariant: cache write failures roll back optimistic metadata toggles.
- Unread invariant: unread is only marked when processing transitions to idle for non-focused session.

Refs: `packages/mobile/app/src/test/kotlin/de/chennemann/opencode/mobile/domain/ArchitectureBoundaryTest.kt:12`, `packages/mobile/app/src/test/kotlin/de/chennemann/opencode/mobile/domain/ArchitectureBoundaryTest.kt:28`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:155`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:1048`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:1710`, `packages/mobile/app/src/test/kotlin/de/chennemann/opencode/mobile/domain/session/SessionServiceTest.kt:137`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SyncCoordinator.kt:23`, `packages/mobile/app/src/test/kotlin/de/chennemann/opencode/mobile/domain/session/SessionServiceTest.kt:218`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:1827`

---

## Flag risks

- `SessionService` is very large and mixes orchestration, sync policy, projection triggers, and cache lifecycle in one class, which increases refactor blast radius.
- Session identity is keyed as `<server>::<session>` in many maps, so key schema changes affect active state, cache joins, and overlays together.
- Soft parsing of server JSON in `ServerService` can hide contract drift until runtime.
- Stringly-typed status values (`busy`, `retry`, `idle`) appear across transport, policy, and UI indicators.
- Fallback behavior for unsupported check endpoints (`404`/`405`) is pragmatic but can mask partial API compatibility issues.
- Log context promotion and redaction is centralized but key-name based, so new sensitive fields can leak if key naming changes.

Refs: `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:24`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:1950`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/data/ServerService.kt:154`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:2172`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:2056`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/data/AndroidLogGateway.kt:71`

---

## Index files

### Track domain entry and orchestration

- `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt`
- `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionServiceApi.kt`
- `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionStreamCoordinator.kt`
- `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionEventReducer.kt`
- `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionSyncPlanner.kt`
- `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SyncCoordinator.kt`
- `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/ReconcileCoordinator.kt`
- `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/PendingBuffer.kt`
- `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/PassCounter.kt`
- `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionResolver.kt`
- `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/FocusedMessageProjector.kt`
- `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/message/MessagePartParser.kt`
- `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/message/MessageDecorator.kt`

### Track ports and models

- `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/ConnectionGateway.kt`
- `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/ProjectGateway.kt`
- `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/CommandGateway.kt`
- `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/MessageGateway.kt`
- `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/StreamGateway.kt`
- `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionCacheGateway.kt`
- `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/ConnectivityGateway.kt`
- `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/LogGateway.kt`
- `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/LogStoreGateway.kt`
- `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionModels.kt`
- `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionUiState.kt`

### Track data adapters and schema

- `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/data/ServerRepository.kt`
- `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/data/ServerService.kt`
- `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/data/SessionCacheRepository.kt`
- `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/data/NetworkService.kt`
- `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/data/MdnsService.kt`
- `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/data/AndroidLogGateway.kt`
- `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/data/LocalLogRepository.kt`
- `packages/mobile/app/src/main/sqldelight/de/chennemann/opencode/mobile/db/AppDatabase.sq`

### Track DI and boundaries

- `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/di/AppModule.kt`
- `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/di/DispatcherProvider.kt`
- `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/di/CoroutineRolloutFlag.kt`
- `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/App.kt`
- `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/navigation/AppNavHost.kt`
- `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/conversation/ConversationViewModel.kt`
- `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/manage/ManageViewModel.kt`
- `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/logs/LogsViewModel.kt`

### Track behavior tests

- `packages/mobile/app/src/test/kotlin/de/chennemann/opencode/mobile/domain/session/SessionServiceTest.kt`
- `packages/mobile/app/src/test/kotlin/de/chennemann/opencode/mobile/domain/session/SessionStreamCoordinatorTest.kt`
- `packages/mobile/app/src/test/kotlin/de/chennemann/opencode/mobile/domain/session/SessionSyncPlannerTest.kt`
- `packages/mobile/app/src/test/kotlin/de/chennemann/opencode/mobile/domain/session/PendingBufferTest.kt`
- `packages/mobile/app/src/test/kotlin/de/chennemann/opencode/mobile/domain/session/PassCounterTest.kt`
- `packages/mobile/app/src/test/kotlin/de/chennemann/opencode/mobile/domain/session/SessionResolverTest.kt`
- `packages/mobile/app/src/test/kotlin/de/chennemann/opencode/mobile/data/SessionCacheRepositoryTest.kt`
- `packages/mobile/app/src/test/kotlin/de/chennemann/opencode/mobile/data/LocalLogRepositoryTest.kt`
- `packages/mobile/app/src/test/kotlin/de/chennemann/opencode/mobile/domain/ArchitectureBoundaryTest.kt`
