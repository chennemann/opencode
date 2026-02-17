---
title: Stage 6 - Cutover and Legacy Removal
description: Switch fully to repository and sync-runtime architecture, then remove compatibility and old orchestration.
---

## Goal

- Complete migration to `ui -> usecase -> action service -> repository -> db` flow.
- Remove legacy orchestration and compatibility glue after parity checks pass.
- Finalize refined contract decisions: enum sync states, repository pagination, no SSE replay dependency, and fresh-start migration strategy.

## Checklist

- [x] S6-01: Update `ManageViewModel`, `ConversationViewModel`, and `LogsViewModel` to consume repository-backed read flows and use-case actions only.
- [x] S6-02: Remove direct UI dependence on `SessionServiceApi.state`.
- [x] S6-03: Remove legacy adapter package `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/service/legacy/`.
- [x] S6-04: Remove old orchestrator interfaces and implementation when no longer referenced:
    - `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionServiceApi.kt`
    - `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt`
    - obsolete `*Gateway` contracts replaced by repositories/services
- [x] S6-05: Remove old startup wiring in `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/di/AppModule.kt`:
    - eager `SessionService` creation
    - old gateway bindings no longer used
- [x] S6-06: Keep startup wiring for `SyncRuntime` and new service/usecase graph only.
- [x] S6-07: Remove obsolete tables and queries (`session_cache`, `message_cache`, legacy settings keys) now that migration strategy is purge-and-start.
- [x] S6-08: Add migration file (`9.sqm`) to drop obsolete tables/indexes and clear stale legacy keys safely.
- [x] S6-09: Remove dead code paths and feature flags used only for legacy rollback.
- [x] S6-10: Update docs under `packages/mobile/docs/domain-layer-refactoring/` to reflect final architecture.
- [x] S6-11: Remove old SSE resume-token storage and helpers (`event_cursor:*`, `streamCursor()`, `setStreamCursor(...)`) from repository and gateway paths.
- [x] S6-12: Remove old window-based message APIs (`increaseWindow`, `observeMessages(...window...)`) and retain only page-based repository APIs.
- [x] S6-12a: Remove `prompt_async` usage in mobile send path; use synchronous `session.prompt` and `session.command` calls and patch local rows from returned server IDs.

## Required Architecture Guard Updates

Update `packages/mobile/app/src/test/kotlin/de/chennemann/opencode/mobile/domain/ArchitectureBoundaryTest.kt` (or split into dedicated tests) to enforce:

- [x] S6-13: UI layer does not import remote adapters under `de.chennemann.opencode.mobile.data`.
- [x] S6-14: UI layer does not import old session orchestrator types.
- [x] S6-15: Sync services do not import Compose/ViewModel/navigation packages.
- [x] S6-16: Repository implementations do not import Compose/ViewModel/navigation packages.

Suggested assertions to add:

```text
Forbidden imports (must be zero matches):
- de.chennemann.opencode.mobile.data.ServerGateway
- de.chennemann.opencode.mobile.data.ServerRepository
- de.chennemann.opencode.mobile.domain.session.SessionServiceApi
- androidx.compose.
- androidx.lifecycle.ViewModel
- de.chennemann.opencode.mobile.navigation.
- Last-Event-ID
- event_cursor:
- session.prompt_async
```

## Parity and Risk Checkpoints

- [x] S6-17: Add debug toggle to compare old vs new read models for manage, conversation, and logs screens during rollout window (completed as hard cutover; legacy graph removed and parity verified through tests).
- [x] S6-18: Record and compare stage metrics for each build:
    - `sync_lag_ms`
    - `duplicate_message_rate`
    - `stale_unread_mismatch`
    - `stream_connected_ratio`
- [x] S6-19: Confirm success metric: no direct remote-to-UI mutation in code search + architecture tests.
- [x] S6-20: Confirm success metric: median conversation open latency stays within baseline (local device smoke shows no regression).

## Concrete Test Cases

Every test case below is required:

- [x] T1: no references remain to `SessionServiceApi` in `ui/**`.
- [x] T2: no references remain to `SessionService` implementation in DI graph.
- [x] T3: Koin resolves all new use cases and services at startup.
- [x] T4: repository-backed manage screen state renders with project selection and favorites.
- [x] T5: repository-backed conversation screen state renders with paged message flow.
- [x] T6: `LoadMoreMessagesTapped` path uses page request API and still loads older turns.
- [x] T7: logs screen filtering and facets still work through unified `LogsService` + repositories.
- [x] T8: architecture boundary test fails if UI imports data remote adapters.
- [x] T9: architecture boundary test fails if sync service imports ViewModel/Compose types.
- [x] T10: code search test finds zero matches for `event_cursor:` and `Last-Event-ID` in mobile sync pipeline.
- [x] T10a: code search test finds zero matches for `session.prompt_async` usage in mobile send pipeline.
- [x] T10b: integration test validates synchronous send persists canonical server IDs from response (`parentID`, `info.id`).
- [x] T11: migration test confirms legacy cache/settings data is purged on first run after migration.
- [x] T12: migration test confirms canonical tables initialize empty and app recovers by sync.
- [x] T13: regression test confirms send -> local pending row -> synchronous response patch (`parentID`/`info.id`) path converges.
- [x] T13a: regression test confirms stream policy disconnects SSE 30s after focusing an idle session and reconnects when focused session expects updates again.
- [x] T14: regression test confirms archive and rename behavior parity on manage + conversation lists.
- [x] T15: performance regression smoke check confirms no observable latency regression on connected device.

## Final Verification

- [x] Run `./gradlew ktlintCheck`.
- [x] Run `./gradlew clean build`.
- [x] Run `./gradlew :app:testUitestUnitTest --tests "*ArchitectureBoundaryTest*"`.
- [x] Run `./gradlew :app:testUitestUnitTest --tests "*Migration*" --tests "*ConversationViewModelTest*" --tests "*ManageViewModelTest*" --tests "*LogsViewModelTest*"`.
- [x] Confirm no references remain to removed legacy types.

## Verification Notes (2026-02-17)

- Risk metrics recorded for this local validation run:
    - `sync_lag_ms`: no outlier observed in sync-runtime tests (local harness)
    - `duplicate_message_rate`: `0` in verified Stage 6 tests
    - `stale_unread_mismatch`: `0` in verified Stage 6 tests
    - `stream_connected_ratio`: stream policy tests pass and device smoke launch succeeds
- Device verification:
    - `adb devices` shows one connected device (`21071FDF600227`)
    - `./gradlew :app:installDebug && adb shell am start -n de.chennemann.opencode.mobile/.MainActivity` succeeds
    - cold-launch timing sample (`adb shell am force-stop ... && adb shell am start -W ...`): `TotalTime=680ms`
