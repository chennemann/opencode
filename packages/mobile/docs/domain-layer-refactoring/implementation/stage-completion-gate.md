---
title: Stage Completion Gate
description: Mandatory done criteria for each migration stage.
---

Use this gate at the end of each stage before starting the next stage.

## Completion Checklist

- [ ] Assigned stage tasks are fully implemented.
- [ ] All concrete test cases (`T*`) in the stage file are checked.
- [ ] No tasks from later stages were started.
- [ ] `./gradlew ktlintCheck` passes.
- [ ] `./gradlew clean build` passes.
- [ ] Stage-specific tests pass.
- [ ] Architecture boundary tests pass.
- [ ] Stage risk metrics are recorded:
    - sync lag
    - duplicate message rate
    - stale unread mismatches
    - stream connected ratio (Stage >= 5)

## Cross-Stage Consistency Checks

Legacy-compatibility note:

- Legacy behavior is acceptable while compatibility adapters are active, as long as the stage goal is met and current UI behavior is not regressed.

Milestone checks (apply when that stage is reached):

- [ ] Stage >= 1: Sync status is enum-based (`SessionSyncStatus`), not free-text strings.
- [ ] Stage >= 2: Message loading contracts use page models (`MessagePageRequest`, `MessagePage`), even if legacy adapters temporarily shim behavior.
- [ ] Stage >= 4: Send/outbox sync reasons use refined names (`USER_SEND`, `OUTBOX_DRAIN`).
- [ ] Stage >= 4: Message identity terminology is consistent (`messageId` / `messageID`), with no custom idempotency-key field introduced.
- [ ] Stage >= 4: Send/command paths use synchronous server endpoints.
- [ ] Stage >= 4: Send path does not provide `messageID`; canonical IDs are persisted from synchronous response payload.
- [ ] Stage >= 4: Logs responsibilities are centralized in one `LogsService` (query + facets + retention).
- [ ] Stage >= 5: Sync design for new runtime does not depend on SSE replay/cursor resume.
- [ ] Stage >= 5: Stream policy is battery-aware (connect only when focused session expects updates, disconnect after 30 seconds idle grace).
- [ ] Stage >= 6: Legacy cursor helpers and window-based message APIs are removed from mobile send/sync paths.
- [ ] All stages: migration strategy remains purge-and-start (no legacy backfill/bootstrap).

## Stage-Specific Test Matrix

- [ ] Stage 1: repository contracts + SQLDelight read-model tests + purge migration tests.
- [ ] Stage 2: legacy action service adapter tests with pagination shim behavior documented.
- [ ] Stage 3: use-case and ViewModel mapping tests for project/focus intents.
- [ ] Stage 4: outbox and mutation action service tests + page request use-case tests.
- [ ] Stage 5: sync runtime and DB-first integration tests (including reconnect convergence without SSE replay and outbox integration).
- [ ] Stage 6: architecture, migration, parity, and performance regression tests after cutover.

## Commands

Run all commands from `packages/mobile`:

```bash
./gradlew ktlintCheck
./gradlew clean build
```
