# Mobile Architecture Migration Plan

## Status

Package-only migration target is complete. Ongoing work is incremental hardening and collaborator extraction within domain orchestration.

## Goal

Move the app to a package-based layered architecture where:

- `data` handles transport, discovery, storage, and repository implementations.
- `domain` owns business logic and stream/message orchestration.
- `navigation` owns routes and nav host behavior.
- `ui` owns rendering with screen-specific view models and event contracts.

Steady-state architecture reference: `packages/mobile/docs/architecture.md`.

## Current baseline

Completed snapshots:

1. `feat(mobile): split navigation and UI into screen packages`
2. `refactor(mobile): extract conversation UI components`
3. `feat(mobile): enrich streamed messages with parsed parts and tool calls`

Additionally, message enrichment work is now part of the migration baseline:

- raw message `parts` are returned by `ServerService`.
- `MessageState` includes `toolCalls`.
- `SessionDomainService` parses structured parts and enriches assistant messages.
- stream handling includes `session.diff`.

## Dependency direction

- `ui -> domain`
- `navigation -> ui` (host/wiring only)
- `domain -> data` (through interfaces)
- `data` has no dependency on `ui`

## Screen contract standard

Each screen must define:

- `UiState` data class
- `Event` sealed interface
- dedicated `ViewModel`
- one `onEvent(event)` entrypoint

Screens do not call arbitrary view model functions and do not receive business-action callbacks.

## Migration phases

### Phase 1: Keep enriched streaming behavior stable

- Keep structured part parsing behavior intact while moving code.
- Preserve tool-call enrichment fields in UI state.
- Keep parity for SSE handling, including `session.diff`.

### Phase 2: Domain extraction from SessionService

Extract domain services from session orchestration incrementally:

- `domain/session/SessionOrchestrator`
- `domain/session/StreamEventReducer`
- `domain/message/MessagePartParser`
- `domain/message/MessageDecorator`

Session orchestration is now fully in `SessionDomainService`.

Progress:

- `domain/message/MessagePartParser` extracted and used by `SessionDomainService`.
- `domain/message/MessageDecorator` extracted and used by `SessionDomainService`.
- `SessionDomainService` is now a concrete domain wrapper instead of a typealias.
- Stream-event payload parsing/classification moved into `SessionEventReducer`.
- `MessageDecorator` now returns domain render models and no longer depends on UI state types.
- `SessionDomainService` stream handler was split into focused action handlers (`handleMessageUpdated`, `handleMessageRemoved`, etc.) to reduce monolithic event logic.
- Session orchestration was moved from `service/SessionService` into `domain/session/SessionDomainService` and DI now wires view models directly to domain.
- Focused message projection/decoration for UI state was extracted to `FocusedMessageProjector` with dedicated unit tests.
- Pending/reconcile pass bookkeeping was extracted into `PassCounter` to simplify `SessionDomainService` flow control.
- `SessionEventReducer` coverage was expanded for ignore/session-delete/session-diff and drop-path handling.
- Sync job scheduling/active-guard behavior was extracted into `SyncCoordinator` to shrink orchestration internals.
- Stream connection/retry loop was extracted into `SessionStreamCoordinator` with dedicated unit coverage.
- Session resolve cooldown bookkeeping was extracted into `SessionResolver` with dedicated unit coverage.
- Remote message diff/upsert/remove planning was extracted into `SessionSyncPlanner` with dedicated unit coverage.
- Pending optimistic-message lifecycle bookkeeping was extracted into `PendingBuffer` with dedicated unit coverage.

### Phase 3: Data/domain contracts

- Introduce repository interfaces consumed by domain:
    - `SessionRepository`
    - `MessageRepository`
    - `StreamRepository`
- Keep concrete implementations in `data` package.
- Map API/db models to domain models in `data` mappers.

Progress:

- Split domain data access into focused ports: `ConnectionGateway`, `ProjectGateway`, `MessageGateway`, and `StreamGateway`.
- `ServerRepository` now implements all gateway ports and `SessionDomainService` depends on those interfaces.
- Added `ConnectivityGateway` so `SessionDomainService` depends on an interface instead of the concrete network service.
- Added `SessionCacheGateway` with `SessionCacheRepository` so `SessionDomainService` no longer depends on SQLDelight/AppDatabase directly.
- Added `LogGateway` with Android implementation so `SessionDomainService` no longer depends on `android.util.Log` directly.
- Added domain session models (`SessionProject`, `SessionSummary`, `SessionMessage`, `SessionStreamEvent`) to stop leaking data-layer DTOs across the boundary.
- Added domain `ConnectionState` and mapped it to UI state in `SessionDomainService`, removing domain-to-UI dependency from connection/data ports.

### Phase 4: UI event-driven parity

- Keep per-screen view models (`ConversationViewModel`, `ManageViewModel`).
- Ensure all UI interactions remain event-driven via `onEvent`.
- Reintroduce/extend reusable tool-call components under `ui/components` while keeping logic in view models/domain.

Progress:

- Tool-call sections and cards were reintroduced in conversation UI.
- Expand/collapse state is managed in `ConversationViewModel` and driven via screen events.
- Screen-facing state models were moved from `home` package and now live in `domain/session`, so UI consumes state from domain instead of owning shared app state models.
- Removed legacy `managementOpen` shared state; manage screen visibility is now navigation-driven only.

### Phase 5: Navigation event handling

- Keep `NavEvent` emission in screen view models.
- Keep navigation execution in `AppNavHost` only.
- No navigation side effects directly from composables.

### Phase 6: DI and cleanup

- Wire new domain services in `AppModule`.
- Remove legacy paths after parity checks.
- Keep app behavior stable across connect, project/session load, message send, stream updates, and load-more.

## Snapshot strategy

Commit in small, behavior-preserving slices:

1. stream/message enrichment baseline
2. domain parser/reducer extraction
3. view model wiring updates
4. final service cleanup + DI finalization

## Non-goals for now

- UI tests are secondary while UI structure evolves quickly.
- No Gradle multi-module split yet (package-only architecture is the selected path).
