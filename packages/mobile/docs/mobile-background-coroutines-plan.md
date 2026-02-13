# Coroutine migration

Keep heavy work off main thread safely.

## Frame context

- [ ] Problem statement: service and viewmodel paths still run mixed main/background work, which risks UI jank and hard-to-reason ordering.
- [ ] Objective: run service, repository, and mapping work on background dispatchers, and keep main-thread usage limited to UI state collection and rendering.
- [ ] Success signal: no direct `Dispatchers.*` calls are scattered in feature code outside the dispatcher provider and explicit UI collection boundaries.

---

## 1) Build dispatcher and app-scope infrastructure

- [ ] Add a dispatcher provider abstraction with `io`, `default`, and `main_immediate` so call sites stop hardcoding `Dispatchers.*`.
- [ ] Introduce an app-level coroutine scope with `SupervisorJob` and explicit dispatcher wiring in `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/di/AppModule.kt`.
- [ ] Create or update DI wiring for dispatcher and scope singletons in `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/di/AppModule.kt`.
- [ ] Add lifecycle shutdown handling for the app scope in `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/App.kt`.
- [ ] Add test dispatcher bindings for unit tests under `packages/mobile/app/src/test/kotlin` so threading behavior can be verified deterministically.

---

## 2) Harden data layer threading

- [ ] Audit gateway implementations for blocking calls and assign each path to `io` or `default` explicitly.
- [ ] Refactor `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/data/SessionCacheRepository.kt` to rely on injected dispatcher context instead of direct `Dispatchers.IO` usage.
- [ ] Refactor `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/data/ServerRepository.kt` to keep network, parsing, and cache writes off main.
- [ ] Ensure SQLDelight flow mapping stays on background dispatchers and does not hop back to main before viewmodel collection.
- [ ] Add focused repository tests under `packages/mobile/app/src/test/kotlin/de/chennemann/opencode/mobile` that assert calls complete when main dispatcher is paused.

---

## 3) Define SessionService serialization/background execution model

- [ ] Replace ad-hoc launches in `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt` with a single serialized event pipeline for state mutations.
- [ ] Split service work into explicit lanes: mutation lane (single-threaded), IO lane, and CPU lane, each backed by injected dispatchers.
- [ ] Keep `MutableStateFlow` writes serialized in one lane and confine map/mutable structure access to that lane to remove lock contention risk.
- [ ] Move cache hydration, message reconciliation, and stream buffering to background lanes while preserving current ordering guarantees.
- [ ] Keep UI-facing flow emission semantics stable so existing collectors in viewmodels do not need contract changes.
- [ ] Add concurrency regression tests in `packages/mobile/app/src/test/kotlin/de/chennemann/opencode/mobile/domain/session` for ordering, cancellation, and duplicate-event handling.

---

## 4) Move ViewModel background processing

- [ ] Keep `viewModelScope` as the lifecycle owner, but move heavy transforms in `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/conversation/ConversationViewModel.kt` to injected background dispatchers.
- [ ] Replace direct `flowOn(Dispatchers.Default)` usage with dispatcher provider wiring in `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/conversation/ConversationViewModel.kt`.
- [ ] Apply the same mapping strategy in `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/manage/ManageViewModel.kt` for filtering, grouping, and sorting work.
- [ ] Keep event handlers lightweight on main and delegate service calls that trigger heavy work to background-safe service APIs.
- [ ] Add viewmodel tests under `packages/mobile/app/src/test/kotlin/de/chennemann/opencode/mobile/ui` to verify state updates remain correct under deterministic test dispatchers.

---

## 5) Add observability and verification

- [ ] Add lightweight timing and lane markers around high-volume service paths in `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt`.
- [ ] Add StrictMode and main-thread policy checks in debug builds in `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/MainActivity.kt` and `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/App.kt` to catch accidental blocking calls.
- [ ] Add regression checks for conversation rendering throughput in `packages/mobile/app/src/androidTest/kotlin/de/chennemann/opencode/mobile/ui/conversation/ConversationScreenStreamingMarkdownTest.kt` or a sibling test.
- [ ] Run and record baseline-versus-after metrics for frame drops, message load latency, and session switching responsiveness.
- [ ] Add a short runbook note in `docs/markdown/mobile-background-coroutines-plan.md` with commands and expected logs for manual verification.

---

## 6) Plan rollout strategy and guardrails

- [ ] Land infrastructure first, then migrate repositories, then migrate service, and finally migrate viewmodels to reduce blast radius.
- [ ] Gate behavior behind a temporary runtime flag so fallback to current execution model stays possible during rollout.
- [ ] Roll out in slices by feature area and monitor crash-free sessions plus jank metrics after each slice.
- [ ] Add guardrails that fail CI on new direct `Dispatchers.*` usage in feature files outside dispatcher infrastructure.
- [ ] Remove temporary flags and fallback paths only after acceptance criteria pass for at least one full release cycle.

---

## Set acceptance criteria

- [ ] Service state mutation paths run through one serialized background lane with deterministic ordering.
- [ ] Repository and cache operations execute on injected background dispatchers, with no blocking work on main.
- [ ] Viewmodels collect and publish UI state on main, while mapping and heavy transforms run on background dispatchers.
- [ ] Debug checks report no main-thread disk or network violations in core conversation and manage flows.
- [ ] Existing behavior for session switching, message send, and stream updates remains functionally unchanged.
- [ ] Unit and instrumentation suites pass with deterministic dispatcher-backed tests for migrated components.

---

## Clarify non-goals

- [ ] Rewriting feature behavior, UX flows, or domain contracts is out of scope.
- [ ] Migrating unrelated modules outside `packages/mobile/app` is out of scope.
- [ ] Replacing Koin, SQLDelight, or navigation architecture is out of scope.
- [ ] Performance tuning beyond thread-affinity correctness and obvious regressions is out of scope.

---

## Track risks / Open questions

- [ ] Decide whether `SessionService` lifecycle should be app-scoped singleton or viewmodel-scoped orchestration wrapper, and document trade-offs before migration starts.
- [ ] Confirm whether current stream ordering assumptions remain valid when IO and CPU lanes are decoupled from mutation lane timing.
- [ ] Define how cancellation should propagate when screens unsubscribe while app-scope work is still in flight.
- [ ] Confirm test strategy for race conditions that only appear under high-frequency stream updates.
