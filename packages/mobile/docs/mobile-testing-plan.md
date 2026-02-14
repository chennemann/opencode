# Mobile Testing Plan

## Align objectives

- [ ] Keep scope limited to `packages/mobile` modules: `:app`, `:streaming-markdown`, and `:api` behavior as consumed by `:app`
- [ ] Protect end-user flows first: connect, select project, open/create session, stream updates, send message, and recover from network failures
- [ ] Prefer fast unit coverage for domain/data logic and reserve instrumentation for Compose rendering, navigation, and Android framework integrations
- [ ] Track progress with P0/P1/P2 workstreams and file-level targets so tasks can be picked up independently

---

## Map baseline

- [x] `:streaming-markdown` has strong parser/text coverage in `packages/mobile/streaming-markdown/src/test/kotlin/de/chennemann/opencode/mobile/streamingmarkdown/StreamingMarkdownParserTest.kt`, `packages/mobile/streaming-markdown/src/test/kotlin/de/chennemann/opencode/mobile/streamingmarkdown/StreamingMarkdownTextTest.kt`, `packages/mobile/streaming-markdown/src/test/kotlin/de/chennemann/opencode/mobile/streamingmarkdown/StreamingMarkdownValidationTest.kt`, and `packages/mobile/streaming-markdown/src/test/kotlin/de/chennemann/opencode/mobile/streamingmarkdown/StreamingMarkdownTimingTest.kt`
- [x] Domain utility coverage exists for reducers/coordinators in `packages/mobile/app/src/test/kotlin/de/chennemann/opencode/mobile/domain/session/SessionEventReducerTest.kt`, `packages/mobile/app/src/test/kotlin/de/chennemann/opencode/mobile/domain/session/SessionSyncPlannerTest.kt`, `packages/mobile/app/src/test/kotlin/de/chennemann/opencode/mobile/domain/session/MutationPipelineTest.kt`, `packages/mobile/app/src/test/kotlin/de/chennemann/opencode/mobile/domain/session/SyncCoordinatorTest.kt`, `packages/mobile/app/src/test/kotlin/de/chennemann/opencode/mobile/domain/session/ReconcileCoordinatorTest.kt`, `packages/mobile/app/src/test/kotlin/de/chennemann/opencode/mobile/domain/session/SessionResolverTest.kt`, `packages/mobile/app/src/test/kotlin/de/chennemann/opencode/mobile/domain/session/PendingBufferTest.kt`, and `packages/mobile/app/src/test/kotlin/de/chennemann/opencode/mobile/domain/session/PassCounterTest.kt`
- [x] Message transformation coverage exists in `packages/mobile/app/src/test/kotlin/de/chennemann/opencode/mobile/domain/message/MessagePartParserTest.kt`, `packages/mobile/app/src/test/kotlin/de/chennemann/opencode/mobile/domain/message/MessageDecoratorTest.kt`, and `packages/mobile/app/src/test/kotlin/de/chennemann/opencode/mobile/domain/session/FocusedMessageProjectorTest.kt`
- [x] ViewModel mapping coverage exists in `packages/mobile/app/src/test/kotlin/de/chennemann/opencode/mobile/ui/conversation/ConversationViewModelTest.kt`, `packages/mobile/app/src/test/kotlin/de/chennemann/opencode/mobile/ui/conversation/ConversationRenderMapperTest.kt`, and `packages/mobile/app/src/test/kotlin/de/chennemann/opencode/mobile/ui/manage/ManageViewModelTest.kt`
- [x] One Compose instrumentation suite exists in `packages/mobile/app/src/androidTest/kotlin/de/chennemann/opencode/mobile/ui/conversation/ConversationScreenStreamingMarkdownTest.kt`
- [ ] Critical gaps remain: no tests for `NetworkService`/`MdnsService`, and no nav/DI/activity tests

---

## Protect critical paths

- [x] Guard session orchestration in `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt` for optimistic messages, SSE actions, sync/reconcile loops, cache persistence, and failure recovery
- [x] Guard API consumption in `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/data/ServerService.kt` and `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/data/ServerRepository.kt` for payload parsing, cursor handling, endpoint normalization, and error mapping
- [x] Guard cache correctness in `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/data/SessionCacheRepository.kt` for dedupe/order/removal and project preference state
- [x] Guard UI contract behavior in `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/conversation/ConversationViewModel.kt` and `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/manage/ManageViewModel.kt` for event handling and state transitions
- [ ] Guard Android-only integrations in `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/navigation/AppNavHost.kt`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/MainActivity.kt`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/data/NetworkService.kt`, and `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/data/MdnsService.kt`

---

## Implement by priority

- [x] **P0 - Harden domain and API integration**
- [x] Add `packages/mobile/app/src/test/kotlin/de/chennemann/opencode/mobile/domain/session/SessionServiceTest.kt` covering start idempotency, project/session loading, send command/message success-failure paths, quick pin persistence rollback, archive behavior, and event-driven state updates
- [x] Add `packages/mobile/app/src/test/kotlin/de/chennemann/opencode/mobile/data/ServerServiceTest.kt` using `MockEngine` to cover `health`, `projects`, `sessions`, `commands`, `sessionMessages`, `sendMessage`, `sendCommand`, and SSE parsing/cursor continuation
- [x] Expand `packages/mobile/app/src/test/kotlin/de/chennemann/opencode/mobile/data/ServerRepositoryTest.kt` for `refresh` success/failure transitions, `normalizeUrl` behavior via public methods, stream cursor keying by URL, and network-change triggered refresh
- [x] Expand `packages/mobile/app/src/test/kotlin/de/chennemann/opencode/mobile/domain/session/SessionStreamCoordinatorTest.kt` for retry-after-failure, wait-for-network-change behavior, cursor persistence, and callback ordering
- [x] Add `packages/mobile/api/src/test/kotlin/de/chennemann/opencode/mobile/api/GeneratedClientSmokeTest.kt` after creating `packages/mobile/api/src/test/kotlin` to validate generated client serialization/deserialization and one request-path smoke with mocked transport

- [x] **P1 - Close ViewModel, cache, and UI interaction gaps**
- [x] Expand `packages/mobile/app/src/test/kotlin/de/chennemann/opencode/mobile/ui/conversation/ConversationViewModelTest.kt` for quick switch cycle ordering, menu load failure fallback, send event draft-clearing rules, and slash filtering edge cases
- [x] Expand `packages/mobile/app/src/test/kotlin/de/chennemann/opencode/mobile/ui/manage/ManageViewModelTest.kt` for filtering, workspace fallback rules, connect/discovered actions, and open-session navigation events
- [x] Expand `packages/mobile/app/src/test/kotlin/de/chennemann/opencode/mobile/data/SessionCacheRepositoryTest.kt` for favorites/hidden/quick-pin dedupe semantics, `recentSession` mapping, and delete-message/session cleanup edge cases
- [x] Add `packages/mobile/app/src/androidTest/kotlin/de/chennemann/opencode/mobile/navigation/AppNavHostTest.kt` to verify back stack transitions between conversation/manage routes and `NavEvent` handling
- [x] Expand `packages/mobile/app/src/androidTest/kotlin/de/chennemann/opencode/mobile/ui/conversation/ConversationScreenStreamingMarkdownTest.kt` for long-turn rendering, tool call expansion state, and load-more interaction signals

- [x] **P2 - Add platform and wiring confidence**
- [x] Add `packages/mobile/app/src/test/kotlin/de/chennemann/opencode/mobile/di/AppModuleTest.kt` to validate Koin graph wiring for `SessionServiceApi`, repositories, gateways, and view models
- [x] Add `packages/mobile/app/src/androidTest/kotlin/de/chennemann/opencode/mobile/MainActivityTest.kt` for launch smoke and initial Compose host rendering
- [x] Add `packages/mobile/app/src/test/kotlin/de/chennemann/opencode/mobile/data/NetworkServiceTest.kt` with fakes or Robolectric to verify connectivity state and `changed` increments
- [x] Add `packages/mobile/app/src/test/kotlin/de/chennemann/opencode/mobile/data/MdnsEntryTest.kt` for URL formatting and IPv6 normalization, and add `MdnsService` instrumentation coverage only if testability hooks are introduced
    - `MdnsService` instrumentation coverage deferred because no additional testability hooks were introduced.
- [x] Add `packages/mobile/streaming-markdown/src/androidTest/kotlin/de/chennemann/opencode/mobile/streamingmarkdown/StreamingMarkdownComposeTest.kt` for Compose rendering parity between streaming and snapshot modes

---

## Run commands

- [x] Run all JVM unit tests in mobile workspace with `cd packages/mobile && ./gradlew test`
- [x] Run app unit tests only with `cd packages/mobile && ./gradlew :app:testDebugUnitTest`
- [x] Run streaming markdown unit tests only with `cd packages/mobile && ./gradlew :streaming-markdown:testDebugUnitTest`
- [x] Run API module tests only with `cd packages/mobile && ./gradlew :api:test`
- [ ] Run Android instrumentation suites with `cd packages/mobile && ./gradlew :app:connectedDebugAndroidTest`
- [ ] Run a focused instrumentation class during iteration with `cd packages/mobile && ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=de.chennemann.opencode.mobile.ui.conversation.ConversationScreenStreamingMarkdownTest`
    - Attempted; currently blocked by `No connected devices!` in local environment.

---

## Meet done criteria

- [ ] Every P0 item is complete and merged with passing tests
- [ ] P1 items are complete or explicitly deferred with an owner and follow-up issue
- [ ] New tests are deterministic, avoid sleeps where possible, and run green in CI on at least two consecutive runs
- [ ] No test plan item references work outside `packages/mobile`
- [ ] `./gradlew test` and `./gradlew :app:connectedDebugAndroidTest` pass from `packages/mobile`
- [ ] Coverage gaps listed in this plan are either closed or tracked with concrete file-level TODO issues
