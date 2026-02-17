---
title: UI-first redesign
description: Practical blueprint for stable DB-first mobile state.
---

## Set purpose

- Purpose: define a new domain and data design for `packages/mobile` where UI needs drive contracts, flows, and persistence strategy.
- Scope: conversation, manage, and logs surfaces in `:app`, plus supporting sync, DB, and remote adapters that feed those screens.
- Assumption: current domain orchestration is replaced incrementally, so this proposal does not preserve existing service internals.
- Assumption: SQLDelight stays the local source of truth and continues to expose reactive queries.
- Assumption: Koin remains the DI mechanism and feature work must ship during migration.
- Non-goal: rewrite navigation, Compose design system, or generated `:api` client behavior.
- Non-goal: introduce offline-first conflict-free replication across multiple writable clients.

---

## Distill UI needs

- Conversation needs fast session opening, stable message ordering, optimistic send feedback, streaming updates, and load-more pagination.
- Conversation needs command send, processing status, per-session unread, and quick-switch triggers without direct network coupling.
- Manage needs connection status, project list, favorite and hidden toggles, archive and rename actions, and recent session context.
- Manage needs deterministic project selection and quick project/session switching with low-latency local reads.
- Logs needs filtered query, facets, paging, retention pruning visibility, and stable ordering under concurrent writes.
- All screens need one-way state updates from observed repository-backed DB models, not from direct use-case memory state.

---

## Define principles

- Keep every domain component focused on one user intent or one sync concern to satisfy easy unit testing.
- Ban god services, but keep specialized services for shared glue logic (server communication, sync, outbox) with strict scopes.
- Minimize per-component dependencies by injecting only the repositories and helpers required for one action.
- Route all write and read state through data repositories exposed as interfaces in the data layer.
- Separate command paths from query paths so write behavior and read-model projection evolve independently.
- Enforce DB-first propagation: every remote event and sync result is persisted before UI can observe changes.
- Treat repository-observed DB flows as the only UI source of truth across conversation, manage, and logs.

---

## Propose layers

- `UI layer`: Compose screens, ViewModels, and route handlers translate gestures into use-case calls and collect read-model flows.
- `Domain layer`: tiny use-case components validate intent and delegate to services without storing canonical UI state.
- `Service layer`: specialized background and remote services centralize server communication and workflow glue while writing through repositories.
- `Data layer`: repository interfaces plus implementations that own DB mapping, transaction boundaries, and projection updates.
- `Local DB layer`: SQLDelight tables for canonical entities and derived read models, with all observer flows sourced here.
- `Remote source layer`: API and stream adapters return transport models only and never mutate UI state directly.
- Direction: `ui -> domain(use cases) -> service/repository commands -> db`, while services perform `remote -> repository -> db` writes.
- Rule: remote adapters can only affect UI by going through services that persist via repositories into DB tables.

---

## Catalog repositories

- Repository contracts live in `packages/mobile/app/src/main/kotlin/.../data/repository` and are injected into services.
- Session, message, and sync metadata are intentionally combined in one `SessionRepository` because they move together for both read and write paths.
- Services also depend on the same repository interfaces so all server results are persisted through one consistent write path.
- Every repository exposes small query and command surfaces, with command methods owning transaction semantics.
- Read models are versioned by table or query contract, not by ad-hoc in-memory DTO builders.

| Repository              | Type           | Responsibility                                                                                        | Key methods                                                                                                                                                                                                                                                                                                                                                          |
| ----------------------- | -------------- | ----------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `SessionRepository`     | read + command | Session aggregate store: session lifecycle, focus, timeline messages, optimistic flags, sync metadata | `observeSessionList(projectId, filter)`, `observeFocusedSession()`, `observeMessagePage(sessionId, request)`, `observeSyncState(sessionId)`, `focus(sessionId)`, `appendLocalMessage(input)`, `applyRemoteBatch(batch)`, `requestMessagePage(sessionId, beforeMessageId, limit)`, `archive(sessionId)`, `rename(sessionId, title)`, `requestSync(sessionId, reason)` |
| `ProjectRepository`     | read + command | Project catalogs, selection state, favorite and hidden policies                                       | `observeProjects()`, `observeSelectedProject()`, `select(projectId)`, `toggleFavorite(projectId)`, `toggleHidden(projectId)`, `upsertProjects(items)`, `requestRefresh(projectId)`                                                                                                                                                                                   |
| `CommandRepository`     | read + command | Slash command catalog per project for UI suggestions and command validation                           | `observeCommands(projectId)`, `replaceCommands(projectId, commands)`, `find(projectId, commandName)`                                                                                                                                                                                                                                                                 |
| `ConnectionRepository`  | read + command | Endpoint config, health state, discovery state, reconnect metadata                                    | `observeConnection()`, `setEndpoint(url)`, `setStatus(status)`, `recordDiscovery(items)`                                                                                                                                                                                                                                                                             |
| `LogRepository`         | read + command | Log append, filter query, facets, retention pruning                                                   | `observeLogs(filter, page)`, `observeFacets()`, `append(entry)`, `prune(policy)`                                                                                                                                                                                                                                                                                     |
| `PreferencesRepository` | read + command | UI preferences and non-domain local toggles scoped per user/device                                    | `observePrefs()`, `setQuickSwitchScope(scope)`, `setSortMode(mode)`, `setLogsFilter(filter)`, `setLogsRetentionPolicy(policy)`                                                                                                                                                                                                                                       |

- Read-model first contracts: `observe*` methods return DB-backed flows only.
- Command contracts: mutating methods return operation results and never return ad-hoc screen state payloads.
- `CommandRepository` is intentionally narrow: it owns command definitions only, while command execution effects are persisted through `SessionRepository` as timeline/outbox state.
- Why `CommandRepository` exists: slash command definitions are project-scoped reference data with different refresh cadence than timeline/session rows, so a focused repository keeps catalog reads and refresh logic isolated.
- If `SessionRepository` grows internally, split implementation modules (`SessionReadStore`, `SessionWriteStore`, `SessionSyncStore`) behind one public interface to keep call sites simple.

---

## Catalog services

- Services are long-lived glue components that remove remote and orchestration duplication from use cases.
- Services never expose UI state; they read and write only through repository interfaces.
- Services are specialized and intentionally small to avoid a new god service.

| Category   | Service                   | Responsibility                                                                                       | Dependencies                                                                                    | Called by                             |
| ---------- | ------------------------- | ---------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------- | ------------------------------------- |
| Action     | `ProjectActionService`    | Handle UI-triggered project intents (select, favorite, hide, refresh request)                        | `ProjectRepository`, `SessionRepository`, `ProjectSyncService`                                  | project-related use cases             |
| Action     | `SessionActionService`    | Handle UI-triggered session intents (focus, load more, archive, rename, sync request)                | `SessionRepository`, `SessionStateSyncService`                                                  | session-related use cases             |
| Action     | `MessageActionService`    | Handle send-message and execute-command intents, enqueue outbox actions, and trigger immediate sync  | `SessionRepository`, `CommandRepository`, `OutboxService`, `SessionStateSyncService`            | send and command use cases            |
| Action     | `ConnectionActionService` | Handle endpoint/discovery/health updates and forward refresh triggers to project/session sync        | `ConnectionRepository`, `ProjectSyncService`, `SessionStateSyncService`                         | connection use cases                  |
| Action     | `QuickSwitchReadService`  | Build quick-switch read model from repository-backed session/project data                            | `SessionRepository`, `ProjectRepository`, `PreferencesRepository`                               | quick-switch use case                 |
| Action     | `LogsService`             | Apply logs filter preferences and expose logs/facet query handles                                    | `PreferencesRepository`, `LogRepository`                                                        | logs use cases                        |
| Background | `ServerService`           | Centralize HTTP/SSE calls, retry policy, auth/context headers, error mapping                         | remote adapters, `ConnectionRepository`                                                         | all sync/outbox services              |
| Background | `ProjectSyncService`      | Refresh projects, commands, and project session indices from server and persist snapshots            | `ServerService`, `ProjectRepository`, `CommandRepository`, `SessionRepository`, `LogRepository` | `SyncRuntime`, `ProjectActionService` |
| Background | `SessionStateSyncService` | Unified session sync engine: periodic snapshot sync + stream ingest + reconciliation in one pipeline | `ServerService`, `SessionRepository`, `LogRepository`                                           | `SyncRuntime`, action services        |
| Background | `OutboxService`           | Drain pending send/command/archive/rename actions and apply remote acks/errors                       | `ServerService`, `SessionRepository`, `LogRepository`                                           | `SyncRuntime`, `MessageActionService` |

- This structure keeps server communication centralized in one place (`ServerService`) without collapsing all behavior into one orchestrator.
- `SessionStateSyncService` intentionally combines former stream-ingest and periodic session-sync concerns so reconciliation and stream-gap recovery are defined once.

---

## Catalog use cases

- Use cases are small classes or functions in `domain/usecase/<feature>` with one public `invoke`.
- Each entry uses only needed service interfaces and optional pure helpers.
- Test strategy defaults to fake service interfaces plus deterministic scheduler control.

| Use case                       | Dependencies              | Input -> output                                | Side effects                                                                                                   | Test strategy                                     |
| ------------------------------ | ------------------------- | ---------------------------------------------- | -------------------------------------------------------------------------------------------------------------- | ------------------------------------------------- |
| `SelectProjectUseCase`         | `ProjectActionService`    | `projectId -> Unit`                            | delegates to project selection flow and focus fallback handling                                                | verify one service call + argument mapping        |
| `FocusSessionUseCase`          | `SessionActionService`    | `sessionId -> Unit`                            | delegates to focus flow that also requests immediate sync                                                      | verify one service call + immediate-sync intent   |
| `SendMessageUseCase`           | `MessageActionService`    | `SendMessageInput -> SendMessageResult`        | delegates send intent; service writes optimistic row, enqueues send outbox action, and requests immediate sync | test validation + mapped service result           |
| `RequestMessagePageUseCase`    | `SessionActionService`    | `MessagePageInput -> MessagePageRequestResult` | delegates page-based message fetch request                                                                     | verify `beforeMessageId` and `limit` mapping      |
| `ToggleFavoriteUseCase`        | `ProjectActionService`    | `projectId -> Boolean`                         | delegates favorite toggle flow                                                                                 | verify toggle call and returned state             |
| `ArchiveSessionUseCase`        | `SessionActionService`    | `sessionId -> Unit`                            | delegates archive flow (local mark + remote action request)                                                    | verify archive call ordering via service contract |
| `RenameSessionUseCase`         | `SessionActionService`    | `RenameInput -> Unit`                          | delegates rename flow and reconcile request                                                                    | verify rename call and input trimming             |
| `OpenQuickSwitchMenuUseCase`   | `QuickSwitchReadService`  | `trigger -> QuickSwitchModel`                  | delegates quick-switch read-model composition                                                                  | test model composition via fake service           |
| `ExecuteCommandUseCase`        | `MessageActionService`    | `CommandInput -> CommandResult`                | delegates command validation + enqueue/send path                                                               | verify command path mapping                       |
| `SetLogsFilterUseCase`         | `LogsService`             | `LogsFilter -> Unit`                           | delegates logs query request; filter persistence is implicit in `observeLogs(...)`                             | verify filter call                                |
| `RefreshConnectionUseCase`     | `ConnectionActionService` | `RefreshInput -> Unit`                         | delegates endpoint status update and downstream refresh trigger                                                | verify connection refresh call                    |
| `RequestProjectRefreshUseCase` | `ProjectActionService`    | `projectId -> Unit`                            | delegates to project-context refresh flow (project data plus session/message refresh trigger)                  | verify refresh call and refresh scope             |

- No use case owns or executes synchronization loops directly.
- Stream ingest and periodic sync are background services that react to DB state and repository request markers.
- No use case calls repositories or remote endpoints directly; all orchestration is centralized in services.
- Cross-feature orchestration is done by composing use-case calls in ViewModel or a narrow coordinator, not by a global owner service.

---

## Design synchronization

- Stream ingest and periodic sync share the same repository command APIs so write semantics stay identical.
- Each pipeline writes DB state first and emits UI-visible changes only through DB observers.
- No dedicated sync use case exists; synchronization is performed by background services.
- A lightweight `SyncRuntime` schedules specialized services: `ProjectSyncService`, `SessionStateSyncService`, `OutboxService`, and `LogsService.runRetention(...)`.

**Ingest stream pipeline**

- `SessionStateSyncService` opens stream consumption through `ServerService` without assuming replay support.
- Receive stream event with payload type and event timestamp.
- Decode into normalized domain event and map impacted project/session scopes.
- Execute repository command transaction: apply event delta when possible, update `sync_state.last_stream_seen_at`, and set sync-request markers.
- Commit transaction, then return success to `SessionStateSyncService` for next event pull.
- If decode or write fails, persist error log via `LogRepository` and retry with backoff; rely on periodic snapshot sync for convergence after gaps.

**Run periodic sync pipeline**

- Scheduler tick selects due sync jobs from `SessionRepository` and `ProjectRepository` metadata rows.
- `ProjectSyncService` and `SessionStateSyncService` fetch remote snapshots through `ServerService`, convert to canonical entities, and write through repositories.
- Repository layer performs merge and projection updates inside DB transactions.
- UI updates only after commit because read flows observe updated tables.

**Apply reconciliation policy**

- Reconciliation compares remote snapshot version and local row version for each entity key.
- If local row is optimistic and newer, keep local row and mark as `pending_reconcile`.
- If remote row is authoritative and newer, replace local row and clear optimistic flags.
- If entity disappears remotely, mark soft-deleted first and hard-delete after retention window.

**Handle conflicts optimistically**

- Optimistic writes create local IDs, logical clocks, and status flags in DB before remote send.
- Remote ack matches by client token or content hash and upgrades local row to confirmed.
- Remote rejection marks row failed with recoverable reason so UI can offer retry.
- Retry path always calls repository command, never mutates ViewModel state directly.

**Enforce DB-first propagation**

- Sync services cannot call UI callbacks or mutate ViewModel state.
- Service outputs are repository commands and logs only.
- UI consumes `observe*` flows from repositories and maps them to screen state.

---

## Shape state model

- Canonical tables store durable entities and operation metadata.
- Derived tables or SQL queries expose screen-ready read models.
- Transient UI-only state stays in ViewModel and never bypasses repository-backed domain state.

| State kind               | Location                          | Examples                                                                                          | Notes                                                                            |
| ------------------------ | --------------------------------- | ------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------- |
| Source of truth entities | SQLDelight tables                 | `project`, `session`, `message`, `command`, `connection`, `sync_state`, `log_entry`, `preference` | all durable business state and sync markers                                      |
| Derived read models      | SQLDelight views or query mappers | `conversation_list_item`, `focused_timeline_item`, `quick_switch_item`, `logs_filtered_item`      | generated from canonical rows and stable ordering rules                          |
| Transient UI state       | ViewModel memory                  | text input draft, currently expanded bottom sheet, selected tab index, one-shot snackbar queue    | recreated from DB plus local widget context after process restore where possible |

- `sync_state` purpose: store sync metadata per scope, such as `last_snapshot_at`, `last_stream_seen_at`, and `next_sync_at`.
- Why it matters: after app restart or reconnect, `SessionStateSyncService` schedules bounded snapshot sync to close potential stream gaps.
- Failure behavior: if stream delivery is interrupted or event payloads are invalid, services keep converging state through snapshot sync and outbox retries.
- Use immutable IDs and monotonic sort keys for timeline rows to prevent flicker during reconcile.
- Keep unread counts and processing flags in DB so manage and conversation stay consistent.
- Keep heavy derived decoration outside DB when purely visual, but drive it from DB-observed models.

---

## Walk flows

**Connect**

- UI event: user taps connect from manage screen.
- Use case: `RefreshConnectionUseCase` validates endpoint and requests status refresh.
- Service call: `ConnectionActionService.refresh(input)`.
- Repository writes (inside service): `ConnectionRepository.setEndpoint` and health updates, then refresh triggers for project/session sync.
- DB change: `connection` row status changes and sync services observe this change to enqueue project/session refresh requests.
- Observer update: manage observers receive updated connection status flow.
- UI state: connect banner, retry CTA, and project refresh spinner update from flow.

**Load projects and sessions**

- UI event: manage screen enters foreground or pull-to-refresh fires.
- Use case: `RequestProjectRefreshUseCase` for selected endpoint/project.
- Service call: `ProjectActionService.refreshProjectContext(projectId)`.
- Repository writes (inside service chain): action service writes refresh marker, then `ProjectSyncService` fetches remote projects/sessions through `ServerService` and writes `upsertProjects` plus `upsertSessions`.
- DB change: request marker is consumed and project/session tables and derived list rows update in transactions.
- Observer update: manage and quick-switch models emit new list snapshots.
- UI state: selected project, favorites, and recent sessions refresh without manual view mutation.

**Open session**

- UI event: user opens session from list or quick switch.
- Use case: `FocusSessionUseCase`.
- Service call: `SessionActionService.focus(sessionId)`.
- Repository writes (inside service): `SessionRepository.focus(sessionId)` and `SessionRepository.requestSync(sessionId, reason=SyncReason.FOCUS)`.
- DB change: focused session pointer and unread markers update.
- Observer update: conversation timeline flow rebinds to focused session query.
- UI state: title, timeline, and processing badge refresh from observer values.

**Send message**

- UI event: user submits text in conversation.
- Use case: `SendMessageUseCase`.
- Service call: `MessageActionService.send(input)`.
- Repository writes (inside service): `SessionRepository.appendLocalMessage(input)` inserts optimistic message, enqueues `SEND_MESSAGE` outbox action, then requests immediate sync.
- DB change: message status becomes `pending_send` and timeline derived row appears immediately.
- Observer update: conversation timeline emits pending bubble and send spinner.
- UI state: composer clears, pending message appears, and retry controls depend on DB status.

**Ingest stream event**

- UI event: none, remote pushes event.
- Use case: none; `SessionStateSyncService` stream loop invokes normalization handler and repository commands.
- Repository writes (inside service): `SessionRepository.applyRemoteBatch(batch)` upserts message/session status, updates `last_stream_seen_at`, and sets sync-request markers in the same transaction.
- DB change: confirmed message parts or processing state rows update.
- Observer update: conversation and manage observers emit new derived models.
- UI state: streaming content and status badges update without direct stream-to-UI callback.

**Run sync tick**

- UI event: none, scheduler tick fires.
- Use case: none; sync services execute due jobs in background.
- Service call: `SyncRuntime.tick()` triggers `SessionStateSyncService` and `ProjectSyncService`.
- Repository writes (inside services): sync services read due jobs from `SessionRepository`/`ProjectRepository`, fetch snapshots through `ServerService`, and apply reconciliation writes through repositories.
- DB change: stale rows are updated or tombstoned and `sync_state` timestamps/markers advance.
- Observer update: all impacted screen flows refresh after commit.
- UI state: lists and details converge with remote state while preserving local optimistic markers.

**Archive and rename**

- UI event: user archives or renames from conversation or manage.
- Use case: `ArchiveSessionUseCase` or `RenameSessionUseCase`.
- Service call: `SessionActionService.archive(...)` or `SessionActionService.rename(...)`.
- Repository writes (inside service): `SessionRepository.archive(...)` or `SessionRepository.rename(...)` applies local command first and stores remote action request in outbox.
- DB change: session row `archived` or `title` fields update immediately.
- Observer update: session disappears or title updates in all lists.
- UI state: consistent list and header updates occur before remote confirmation.

**Use quick switch**

- UI event: user opens quick switch and enters query.
- Use case: `OpenQuickSwitchMenuUseCase` plus query filter in ViewModel.
- Service call: `QuickSwitchReadService.open(request)`.
- Repository reads/writes (inside service): reads quick-switch derived model and applies optional preference write.
- DB change: only preference rows change when scope or sort option is altered.
- Observer update: quick-switch list and chips update via derived query.
- UI state: fast navigation options appear from local DB state.

**Filter logs**

- UI event: user applies log level or tag filters.
- Use case: `SetLogsFilterUseCase`.
- Service call: `LogsService.observeLogs(filter, page)` and `LogsService.observeFacets(filter)`.
- Repository writes/reads (inside service): persist filter implicitly on first `observeLogs(...)` subscription and query logs via `LogRepository.observeLogs`.
- DB change: preference row updates while log rows stay unchanged.
- Observer update: logs list observer emits filtered page and facet counts.
- UI state: logs screen refreshes results deterministically from DB-backed queries.

---

## Enforce boundaries

- Allowed dependencies: `ui -> domain(use cases)`, `domain -> service interfaces`, `service -> data(repository interfaces) + remote`, `data -> db`.
- Allowed observation path: `ui` collects repository read flows backed by SQLDelight queries.
- Forbidden: `ui` calling remote adapters directly or mutating shared mutable caches.
- Forbidden: sync services publishing screen state events or writing into ViewModel-owned state stores.
- Forbidden: repository implementations depending on ViewModel, navigation, or Compose classes.
- Forbidden: single coordinator owning connection, projects, sessions, messages, commands, and logs end-to-end.

---

## Plan tests

- Use-case unit tests: validate input rules and service-call mapping with fake service interfaces.
- Service unit tests: validate orchestration and repository call ordering with fake repositories and fake server adapters.
- Repository contract tests: run against in-memory SQLDelight and fake remote adapters to verify interface semantics.
- DB-first integration tests: assert stream and sync writes become visible only after transaction commit.
- Deterministic reconcile tests: freeze clock and remote snapshots to prove stable conflict outcomes.
- Observer propagation tests: verify UI-facing flows emit from DB changes and not from direct callback mutation.
- Regression matrix: conversation send, archive, rename, quick switch, and logs filters under online and offline scenarios.

---

## Stage migration

- Stage 1: add new repository interfaces and DB read-model queries beside existing gateways without changing screen contracts.
- Stage 2: introduce service interfaces (`ProjectActionService`, `SessionActionService`, `MessageActionService`, `ConnectionActionService`, `QuickSwitchReadService`, `LogsService`) with adapters over existing logic.
- Stage 3: move one user intent at a time to focused use cases that call services, starting with project selection and session focus.
- Stage 4: route send message, archive, rename, and command execution through action services and outbox flow.
- Stage 5: introduce `ServerService` and `SessionStateSyncService`, then replace stream and periodic sync handlers with DB-first services that only call repositories.
- Stage 6: remove legacy gateway orchestration and delete compatibility glue after parity checks pass.

- Compatibility shim: temporary adapters translate old gateway calls into new repository commands.
- Risk checkpoint: compare old and new read models for key screens behind debug toggle before default switch.
- Risk checkpoint: track sync lag, duplicate message rate, and stale unread mismatches per build.
- Success metric: zero direct remote-to-UI mutations in code search and architecture tests.
- Success metric: median conversation open latency stays within current baseline while reducing flaky sync bugs.

---

## Map responsibilities

| Current responsibility area               | Proposed component set                                                                               | Notes                                                |
| ----------------------------------------- | ---------------------------------------------------------------------------------------------------- | ---------------------------------------------------- |
| Central session orchestration             | focused use cases + focused action services + scoped background sync services                        | removes single owner and isolates concerns           |
| Project/session loading and selection     | `SelectProjectUseCase`, `RequestProjectRefreshUseCase`, `ProjectActionService`, `ProjectSyncService` | preserves feature behavior with smaller components   |
| Message send and optimistic lifecycle     | `SendMessageUseCase`, `MessageActionService`, `OutboxService`, `SessionStateSyncService`             | keeps optimistic UX while enforcing DB-first state   |
| Stream reduction and update fan-out       | `SessionStateSyncService` + repository transaction pipeline                                          | prevents direct UI mutation from stream callbacks    |
| Sync scheduling and reconciliation        | `SessionStateSyncService` and `ProjectSyncService` using repository sync metadata rows               | deterministic and independently testable             |
| Archive, rename, favorite, hidden actions | dedicated command use cases per intent                                                               | simple dependency sets and clear tests               |
| Quick-switch assembly                     | `OpenQuickSwitchMenuUseCase` + `QuickSwitchReadService` + derived DB read model                      | fast local reads with minimal orchestration          |
| Logs filtering and retention              | `SetLogsFilterUseCase` + `LogsService` + `LogRepository` + preference persistence                    | keeps logs feature isolated from conversation domain |

- This mapping keeps components small, testable, and explicit about dependency direction.
- This mapping ensures every screen change is observable from repository-backed DB state.
