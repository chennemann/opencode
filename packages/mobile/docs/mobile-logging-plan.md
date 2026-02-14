# Logging plan

Keep app diagnostics local, structured, and private.

## Set scope

- [ ] Ship a local-first logging pipeline that writes to SQLDelight first and keeps Android Logcat as a secondary sink.
- [ ] Keep architecture boundaries clear: `ui/navigation` reads only prepared state, `domain` owns policy, and `data` owns persistence.
- [ ] Preserve existing `LogGateway` call sites in session orchestration while enabling richer structured payloads.
- [ ] Launch from Workspace Hub with filtering by logical unit and severity.

---

## Plan phases

- [ ] **Phase 1 - Model and store entries**
- [ ] Add log tables and queries in `packages/mobile/app/src/main/sqldelight/de/chennemann/opencode/mobile/db/AppDatabase.sq`.
- [ ] Add `packages/mobile/app/src/main/sqldelight/de/chennemann/opencode/mobile/db/migrations/4.sqm` with table creation, indexes, and backfill-safe defaults.
- [ ] Add repository implementation in `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/data/LocalLogRepository.kt`.
- [ ] Add domain contracts in `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/LogStoreGateway.kt` and `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/LogEntry.kt`.

- [ ] **Phase 2 - Route writes through gateway**
- [ ] Expand `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/LogGateway.kt` to structured writes with unit, severity, event, and metadata.
- [ ] Replace direct string logs in `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt` and `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionStreamCoordinator.kt` with logical-unit aware calls.
- [ ] Implement fan-out gateway in `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/data/AndroidLogGateway.kt` so writes go to local DB and Logcat.
- [ ] Wire dependencies in `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/di/AppModule.kt`.

- [ ] **Phase 3 - Add Workspace Hub screen**
- [ ] Add route key in `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/navigation/AppRoute.kt` and wiring in `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/navigation/AppNavHost.kt`.
- [ ] Add manage event and state fields in `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/manage/ManageContract.kt`.
- [ ] Add launch action from Workspace Hub in `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/manage/ManageScreen.kt`.
- [ ] Add log screen and filters in `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/logs/LogsScreen.kt` and `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/logs/LogsViewModel.kt`.

- [ ] **Phase 4 - Enforce retention and privacy**
- [ ] Add retention sweeper in `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/data/LocalLogRepository.kt` on app start and periodic write thresholds.
- [ ] Add redaction utility in `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/LogRedactor.kt` and apply before persistence.
- [ ] Add settings keys for retention days and max rows in `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/data/SessionCacheRepository.kt` helper pattern.
- [ ] Add rollout flags in `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/di/CoroutineRolloutFlag.kt` or a new logging flag file.

---

## Design schema

- [ ] Create `app_log` table with `id`, `created_at`, `session_id`, `project_id`, `workspace_id`, `logical_unit`, `severity`, `event`, `message`, `metadata_json`, and `redacted`.
- [ ] Create `log_retention_state` table with `singleton_id`, `last_prune_at`, and `last_pruned_id` to keep pruning incremental.
- [ ] Add indexes for `(created_at DESC)`, `(logical_unit, created_at DESC)`, `(session_id, created_at DESC)`, and `(severity, created_at DESC)`.
- [ ] Add SQLDelight queries for insert, paged list with optional filters, delete-before timestamp, delete-over-limit, and counts by unit.
- [ ] Migration `4.sqm` should be additive only and must not rewrite existing tables.

---

## Define tagging

- [ ] Introduce canonical logical units: `sync`, `stream`, `message`, `workspace`, `network`, `navigation`, `cache`, `security`, and `ui`.
- [ ] Add one mapper in `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/LogUnit.kt` to prevent free-form strings.
- [ ] Keep event names short and stable, for example `sync_ok`, `sync_failed`, `sse_connect`, `open_session`, and `workspace_select`.
- [ ] Include optional correlation fields in metadata: `sessionId`, `projectId`, `requestId`, and `attempt`.
- [ ] Reject unknown logical units at compile time through enum or sealed value usage.

---

## Apply redaction

- [ ] Redact server URLs, auth headers, API keys, absolute user paths, and raw message text by default.
- [ ] Keep only safe derivatives in metadata, for example host name, workspace hash, payload size, and status code.
- [ ] Mark each stored row with `redacted=1` after sanitization and fail closed by dropping unredacted writes.
- [ ] Add unit tests for redaction patterns in `packages/mobile/app/src/test/kotlin/de/chennemann/opencode/mobile/domain/session/LogRedactorTest.kt`.

---

## Manage retention

- [ ] Default retention policy: keep 7 days and cap at 5000 rows per server.
- [ ] Prune on startup, every 200 inserts, and when opening the log screen.
- [ ] Delete oldest rows first using `created_at` and tie-break with `id` for deterministic cleanup.
- [ ] Store retention knobs in settings so policy can change without schema changes.
- [ ] Emit one summary log after each prune with removed row count and elapsed time.

---

## Build screen

- [ ] Add a Workspace Hub action label like `Open logs` near existing session actions in `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/manage/ManageScreen.kt`.
- [ ] Build `LogsUiState` with filter chips for logical unit, severity, and quick ranges like `15m`, `1h`, `24h`.
- [ ] Show grouped rows by time with expandable metadata and copy-safe text that never includes redacted secrets.
- [ ] Keep list pagination local-first from SQLDelight and avoid network reads.
- [ ] Add empty, loading, and capped-history states so users understand retention effects.

---

## Wire dependencies

- [ ] Register `LocalLogRepository` and updated `LogGateway` in `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/di/AppModule.kt`.
- [ ] Inject logging use cases into `ManageViewModel` and new `LogsViewModel` through existing Koin patterns.
- [ ] Keep navigation events typed by adding `NavEvent.ToLogs` and `NavEvent.Back` handling updates in `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/navigation/NavEvent.kt`.
- [ ] Keep compose modules free of direct SQLDelight access.

---

## Validate rollout

- [ ] **Unit tests**
- [ ] Add `packages/mobile/app/src/test/kotlin/de/chennemann/opencode/mobile/data/LocalLogRepositoryTest.kt` for insert, filter, prune-by-time, prune-by-limit, and ordering.
- [ ] Expand `packages/mobile/app/src/test/kotlin/de/chennemann/opencode/mobile/domain/session/SessionServiceTest.kt` and `packages/mobile/app/src/test/kotlin/de/chennemann/opencode/mobile/domain/session/SessionStreamCoordinatorTest.kt` to assert logical-unit tagging.
- [ ] Add `packages/mobile/app/src/test/kotlin/de/chennemann/opencode/mobile/ui/logs/LogsViewModelTest.kt` for filter transitions and pagination.

- [ ] **Instrumentation tests**
- [ ] Add `packages/mobile/app/src/androidTest/kotlin/de/chennemann/opencode/mobile/ui/logs/LogsScreenTest.kt` for Workspace Hub entry, filter chip behavior, and metadata expansion.
- [ ] Expand `packages/mobile/app/src/androidTest/kotlin/de/chennemann/opencode/mobile/navigation/AppNavHostTest.kt` for `Manage -> Logs -> Back` flow.

- [ ] **Run commands**
- [ ] Run `cd packages/mobile && ./gradlew :app:testDebugUnitTest`.
- [ ] Run `cd packages/mobile && ./gradlew :app:connectedDebugAndroidTest`.
- [ ] Run `cd packages/mobile && ./gradlew :app:generateSqlDelightInterface` after schema updates.

---

## Gate completion

- [ ] Structured logs persist locally with logical-unit filters working end to end.
- [ ] Redaction blocks sensitive values in both DB rows and UI rendering.
- [ ] Retention policy is enforced deterministically and covered by tests.
- [ ] Workspace Hub can open the log screen and return without breaking existing flows.
- [ ] Feature flag rollout reaches 100 percent only after two stable CI runs and manual smoke on one physical Android device.
