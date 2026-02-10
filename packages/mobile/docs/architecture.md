# Mobile Architecture

## Layers

- `ui`: screen rendering, Compose components, screen contracts, and view models.
- `navigation`: route keys, nav host wiring, and nav event handling.
- `domain`: business orchestration, stream/event reduction, message projection, and gateway contracts.
- `data`: network/discovery/cache implementations for domain ports.

Dependency direction:

- `ui -> domain`
- `navigation -> ui`
- `domain -> data` through interfaces only
- `data` has no dependency on `ui`

## Screen contract

Each screen defines:

- `UiState`
- `Event` sealed interface
- dedicated view model
- one `onEvent(event)` entry point

Screens do not invoke arbitrary view model methods and do not own business logic.

## Domain session flow

`SessionDomainService` is the orchestration boundary used by view models.

Key collaborators:

- `SessionEventReducer` for stream payload classification
- `SessionStreamCoordinator` for stream connection/retry loop and callback dispatch
- `FocusedMessageProjector` for focused message projection/decorating
- `PassCounter` for optimistic/reconcile pass tracking
- `SyncCoordinator` for scheduled sync and active-run guard
- `SessionResolver` for session resolve cooldown and deduplicated resolve attempts
- `SessionDebugTracker` for stream/sync debug counters and debug log lines

Domain ports:

- `ConnectionGateway`
- `ProjectGateway`
- `MessageGateway`
- `StreamGateway`
- `SessionCacheGateway`
- `ConnectivityGateway`
- `LogGateway`

## Data implementations

- `ServerRepository` implements connection/project/message/stream ports.
- `SessionCacheRepository` implements cache port over SQLDelight.
- `NetworkService` implements connectivity port.
- `AndroidLogGateway` implements logging port.
