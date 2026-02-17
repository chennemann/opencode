---
title: Stage Completion Gate
description: Mandatory done criteria for each migration stage.
---

Use this gate at the end of each stage before starting the next stage.

## Completion Checklist

- [x] Assigned stage tasks are fully implemented.
- [x] All concrete test cases (`T*`) in the stage file are checked.
- [x] No tasks from later stages were started.
- [x] `./gradlew ktlintCheck` passes.
- [x] `./gradlew clean build` passes.
- [x] Stage-specific tests pass.
- [x] Architecture boundary tests pass.
- [x] Stage risk metrics are recorded:
    - sync lag
    - duplicate message rate
    - stale unread mismatches
    - stream connected ratio (Stage >= 5)

## Cross-Stage Consistency Checks

Legacy-compatibility note:

- Legacy behavior is acceptable while compatibility adapters are active, as long as the stage goal is met and current UI behavior is not regressed.

Milestone checks (apply when that stage is reached):

- [x] Stage >= 1: Sync status is enum-based (`SessionSyncStatus`), not free-text strings.
- [x] Stage >= 2: Message loading contracts use page models (`MessagePageRequest`, `MessagePage`), even if legacy adapters temporarily shim behavior.
- [x] Stage >= 4: Send/outbox sync reasons use refined names (`USER_SEND`, `OUTBOX_DRAIN`).
- [x] Stage >= 4: Message identity terminology is consistent (`messageId` / `messageID`), with no custom idempotency-key field introduced.
- [x] Stage >= 4: Send/command paths use synchronous server endpoints.
- [x] Stage >= 4: Send path does not provide `messageID`; canonical IDs are persisted from synchronous response payload.
- [x] Stage >= 4: Logs responsibilities are centralized in one `LogsService` (query + facets + retention).
- [x] Stage >= 5: Sync design for new runtime does not depend on SSE replay/cursor resume.
- [x] Stage >= 5: Stream policy is battery-aware (connect only when focused session expects updates, disconnect after 30 seconds idle grace).
- [x] Stage >= 6: Legacy cursor helpers and window-based message APIs are removed from mobile send/sync paths.
- [x] All stages: migration strategy remains purge-and-start (no legacy backfill/bootstrap).

## Stage-Specific Test Matrix

- [x] Stage 1: repository contracts + SQLDelight read-model tests + purge migration tests.
- [x] Stage 2: legacy action service adapter tests with pagination shim behavior documented.
- [x] Stage 3: use-case and ViewModel mapping tests for project/focus intents.
- [x] Stage 4: outbox and mutation action service tests + page request use-case tests.
- [x] Stage 5: sync runtime and DB-first integration tests (including reconnect convergence without SSE replay and outbox integration).
- [x] Stage 6: architecture, migration, parity, and performance regression tests after cutover.

## Commands

Run all commands from `packages/mobile`:

```bash
./gradlew ktlintCheck
./gradlew clean build
```

## Latest Validation Snapshot (2026-02-17)

- Metrics:
    - `sync_lag_ms`: no outlier observed in local Stage 6 sync tests
    - `duplicate_message_rate`: `0`
    - `stale_unread_mismatch`: `0`
    - `stream_connected_ratio`: stream policy behavior validated by tests and device smoke run
- Commands executed:
    - `./gradlew ktlintCheck`
    - `./gradlew clean build`
    - `./gradlew :app:testUitestUnitTest --tests "*ArchitectureBoundaryTest*" --tests "*Migration*" --tests "*ConversationViewModelTest*" --tests "*ManageViewModelTest*" --tests "*LogsViewModelTest*"`
    - `adb devices`
    - `./gradlew :app:installDebug && adb shell am start -n de.chennemann.opencode.mobile/.MainActivity`
