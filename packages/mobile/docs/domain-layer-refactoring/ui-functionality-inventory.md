---
title: UI inventory
description: Current app interactions, flows, and visible state
---

## 1) Define scope

- **Purpose**: Document all functionality currently used by UI code in `packages/mobile`.
- **Scope**: Covers app-module screens, viewmodels, contracts, navigation routes/events, shared behavior-bearing components, UI-visible domain state/actions, and UI-used boundary collaborators.
- **Boundary collaborators included**: `SessionServiceApi`, `LogStoreGateway`, `ConversationRenderMapper`, and the message part/decorator path as consumed by UI state.
- **Method**: Traced each UI event from composable trigger to viewmodel handler, then to domain/data call and resulting visible state/effect.
- **Core references**: `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/navigation/AppNavHost.kt:19`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/manage/ManageViewModel.kt:104`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/conversation/ConversationViewModel.kt:156`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/logs/LogsViewModel.kt:111`.

---

## 2) Catalog surfaces

- **Routes**: `ConversationRoute`, `ManageProjectsRoute`, `LogsRoute` (`packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/navigation/AppRoute.kt:7`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/navigation/AppRoute.kt:10`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/navigation/AppRoute.kt:13`).
- **Nav events**: `ToManage`, `ToConversation`, `ToLogs`, `Back` (`packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/navigation/NavEvent.kt:3`).
- **Screens**: `ConversationScreen`, `ManageScreen`, `LogsScreen` (`packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/conversation/ConversationScreen.kt:74`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/manage/ManageScreen.kt:56`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/logs/LogsScreen.kt:59`).
- **Viewmodels**: `ConversationViewModel`, `ManageViewModel`, `LogsViewModel` (`packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/conversation/ConversationViewModel.kt:24`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/manage/ManageViewModel.kt:21`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/logs/LogsViewModel.kt:27`).
- **Contracts**: `ConversationContract` (`ConversationUiState`, `ConversationEvent`), `ManageContract` (`ManageUiState`, `ManageEvent`), `LogsContract` (`LogsUiState`, `LogsEvent`) (`packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/conversation/ConversationContract.kt:18`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/manage/ManageContract.kt:18`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/logs/LogsContract.kt:8`).
- **Shared behavior components**: `MessageComposer`, `ConversationHeader`, `ToolCallCard`, `TurnTimer` (`packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/components/MessageComposer.kt:105`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/components/ConversationHeader.kt:22`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/components/ToolCallCard.kt:35`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/components/TurnTimer.kt:14`).

---

## 3) Group capabilities

### Connection management

- **Connect and endpoint selection**
    - Triggering UI event(s): `UrlChanged`, `UseDiscoveredTapped`, `ConnectTapped` from server controls (`packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/manage/ManageScreen.kt:185`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/manage/ManageScreen.kt:192`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/manage/ManageScreen.kt:203`).
    - Viewmodel handler(s): `ManageViewModel.onEvent` service calls (`packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/manage/ManageViewModel.kt:106`).
    - Domain/data calls invoked: `updateUrl`, `useDiscovered`, `refresh` (`packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionServiceApi.kt:11`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionServiceApi.kt:13`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionServiceApi.kt:15`).
    - State fields consumed/updated: `ManageUiState.url`, `discovered`, `status` backed by combined connection + local state (`packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/manage/ManageContract.kt:19`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:171`).
    - Visible user effect: status text/color and server card expansion behavior update (`packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/manage/ManageScreen.kt:171`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/manage/ManageScreen.kt:176`).

### Project/workspace management

- **Project search/open/select/list expansion**
    - Triggering UI event(s): `ProjectQueryChanged`, `ProjectPathChanged`, `OpenProjectTapped`, `ProjectSelected`, `ProjectListToggleTapped` (`packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/manage/ManageScreen.kt:285`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/manage/ManageScreen.kt:299`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/manage/ManageScreen.kt:310`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/manage/ManageScreen.kt:372`).
    - Viewmodel handler(s): local state update + `selectProject` when opening/selecting (`packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/manage/ManageViewModel.kt:113`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/manage/ManageViewModel.kt:121`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/manage/ManageViewModel.kt:132`).
    - Domain/data calls invoked: `selectProject` (`packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionServiceApi.kt:17`).
    - State fields consumed/updated: local `projectPath`/`projectQuery`/`projectsExpanded` plus derived `favoriteProjects`/`otherProjects`/`selectedProjectName` (`packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/manage/ManageContract.kt:22`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/manage/ManageContract.kt:26`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/manage/ManageViewModel.kt:49`).
    - Visible user effect: project cards filter, selected card changes, hidden list expands/collapses.

- **Favorites, removal, and workspace target selection**
    - Triggering UI event(s): `ProjectFavoriteToggled`, `ProjectRemoved`, `WorkspaceSelected` (`packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/manage/ManageScreen.kt:339`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/manage/ManageScreen.kt:358`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/manage/ManageScreen.kt:620`).
    - Viewmodel handler(s): service favorite/remove plus local selected workspace update (`packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/manage/ManageViewModel.kt:141`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/manage/ManageViewModel.kt:145`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/manage/ManageViewModel.kt:150`).
    - Domain/data calls invoked: `toggleProjectFavorite`, `removeProject` (`packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionServiceApi.kt:19`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionServiceApi.kt:21`).
    - State fields consumed/updated: favorites/others/workspace options and selected workspace label (`packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/manage/ManageContract.kt:27`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/manage/ManageContract.kt:32`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/manage/ManageContract.kt:34`).
    - Visible user effect: star/heart state changes, remove button availability changes, create target label changes.

### Session lifecycle operations

- **Create/open/archive/rename sessions**
    - Triggering UI event(s): `CreateSessionTapped`, `OpenSessionTapped`, quick-switch menu create/archive/rename/session tap (`packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/manage/ManageScreen.kt:587`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/manage/ManageScreen.kt:562`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/conversation/ConversationContract.kt:83`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/conversation/ConversationContract.kt:87`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/conversation/ConversationContract.kt:89`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/conversation/ConversationContract.kt:93`).
    - Viewmodel handler(s): manage create/open path and conversation quick-switch handlers (`packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/manage/ManageViewModel.kt:153`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/manage/ManageViewModel.kt:164`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/conversation/ConversationViewModel.kt:219`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/conversation/ConversationViewModel.kt:229`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/conversation/ConversationViewModel.kt:233`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/conversation/ConversationViewModel.kt:240`).
    - Domain/data calls invoked: `createSessionAndFocus`, `openSession`, `archiveSession`, `renameSession` (`packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionServiceApi.kt:25`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionServiceApi.kt:27`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionServiceApi.kt:33`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionServiceApi.kt:35`).
    - State fields consumed/updated: `sessions`, `activeSessions`, `focusedSession`, manage `sessionSections`, conversation `quickSwitchMenu.sessions` (`packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionUiState.kt:53`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionUiState.kt:54`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionUiState.kt:55`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/manage/ManageContract.kt:35`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/conversation/ConversationContract.kt:54`).
    - Visible user effect: session list/buttons update, quick-switch rows mutate, and successful manage create/open transitions to conversation.

- **Load older messages**
    - Triggering UI event(s): `LoadMoreMessagesTapped` (`packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/conversation/ConversationScreen.kt:184`).
    - Viewmodel handler(s): `service.loadMoreMessages()` (`packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/conversation/ConversationViewModel.kt:260`).
    - Domain/data calls invoked: `loadMoreMessages` and remote sync with `more=true` (`packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:638`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:644`).
    - State fields consumed/updated: `canLoadMoreMessages`, `loadingMoreMessages`, `focusedMessages` (`packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionUiState.kt:57`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionUiState.kt:58`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionUiState.kt:56`).
    - Visible user effect: load button state changes and older turns appear at top.

### Message composition and sending

- **Draft/mode/slash/send/reload**
    - Triggering UI event(s): `DraftChanged`, `ModeChanged`, `SlashCommandSelected`, `SendTapped`, `ReloadTapped` (`packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/conversation/ConversationScreen.kt:269`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/conversation/ConversationScreen.kt:270`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/conversation/ConversationScreen.kt:273`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/conversation/ConversationScreen.kt:271`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/conversation/ConversationScreen.kt:272`).
    - Viewmodel handler(s): local draft/mode updates, slash template insertion, send/reload service calls (`packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/conversation/ConversationViewModel.kt:187`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/conversation/ConversationViewModel.kt:193`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/conversation/ConversationViewModel.kt:199`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/conversation/ConversationViewModel.kt:248`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/conversation/ConversationViewModel.kt:256`).
    - Domain/data calls invoked: `send`, `refresh` (`packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionServiceApi.kt:29`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionServiceApi.kt:15`).
    - State fields consumed/updated: conversation `draft`/`mode`/`slashSuggestions` plus service `message` and optimistic focused message append (`packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/conversation/ConversationContract.kt:26`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/conversation/ConversationContract.kt:27`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/conversation/ConversationContract.kt:28`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:591`).
    - Visible user effect: composer mode indicator changes, slash menu appears, send clears non-blank input, and new user message appears quickly.

### Quick switch and menu behaviors

- **Tap/long-press/project cycling/menu actions**
    - Triggering UI event(s): quick-switch tap/long-press and menu action events (`packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/conversation/ConversationContract.kt:77`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/conversation/ConversationContract.kt:79`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/conversation/ConversationContract.kt:81`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/conversation/ConversationContract.kt:91`).
    - Viewmodel handler(s): `quickSwitchTap`, `quickSwitchLongPress`, `quickSwitchLoadMore`, `fetchQuickSwitchMenu` (`packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/conversation/ConversationViewModel.kt:292`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/conversation/ConversationViewModel.kt:332`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/conversation/ConversationViewModel.kt:399`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/conversation/ConversationViewModel.kt:416`).
    - Domain/data calls invoked: `openSession`, `createSessionAndFocus`, `cachedSessionsForProject`, `sessionsForProject`, `toggleSessionQuickPin` (`packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionServiceApi.kt:27`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionServiceApi.kt:25`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionServiceApi.kt:37`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionServiceApi.kt:39`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionServiceApi.kt:23`).
    - State fields consumed/updated: `quickSwitches`, `quickSwitchMenu`, `quickPinInclude`, `quickPinExclude`, `quickProcessing`, `quickUnread` (`packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/conversation/ConversationContract.kt:29`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionUiState.kt:62`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionUiState.kt:65`).
    - Visible user effect: switch dots/processing indicators update, bottom sheet opens with paging, and actions apply to selected session rows.

### Stream/progressive rendering behaviors exposed in UI

- **Incremental message/part projection to turns and tool cards**
    - Triggering UI event(s): local expand/collapse events and streaming updates delivered through service state (`packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/conversation/ConversationContract.kt:65`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/conversation/ConversationContract.kt:67`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:1301`).
    - Viewmodel handler(s): `ToggleSteps`/`ToggleToolCall` local maps; turns mapped by `ConversationRenderMapper` (`packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/conversation/ConversationViewModel.kt:162`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/conversation/ConversationViewModel.kt:170`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/conversation/ConversationViewModel.kt:84`).
    - Domain/data calls invoked: parser/decorator/projector path in service and projector (`packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:821`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:833`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/FocusedMessageProjector.kt:59`).
    - State fields consumed/updated: `focusedMessages -> turns`, `stepOpen`, `callOpen`, turn `answerWriting/toolCalls/systemTexts` (`packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionUiState.kt:56`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/conversation/ConversationContract.kt:22`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/conversation/ConversationContract.kt:31`).
    - Visible user effect: markdown appears progressively, tool call cards animate open/close, and timer updates while active (`packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/conversation/ConversationScreen.kt:359`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/conversation/ConversationScreen.kt:498`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/components/TurnTimer.kt:24`).

### Logs browsing/filtering

- **Facet filters, row chips, and copy flow**
    - Triggering UI event(s): all filter events plus row chip actions and clear/remove (`packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/logs/LogsContract.kt:24`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/logs/LogsContract.kt:40`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/logs/LogsContract.kt:42`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/logs/LogsContract.kt:44`).
    - Viewmodel handler(s): local filter updates in `onEvent` and reactive `flatMapLatest` query (`packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/logs/LogsViewModel.kt:55`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/logs/LogsViewModel.kt:141`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/logs/LogsViewModel.kt:153`).
    - Domain/data calls invoked: `observe(LogFilter)` and `observeFacet()` (`packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/LogStoreGateway.kt:81`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/LogStoreGateway.kt:83`).
    - State fields consumed/updated: selected filter fields, `facet`, and `rows` (`packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/logs/LogsContract.kt:11`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/logs/LogsContract.kt:20`).
    - Visible user effect: filtered rows/chips refresh and copy dialog outputs selected row count as raw log text (`packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/logs/LogsScreen.kt:242`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/logs/LogsScreen.kt:204`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/logs/LogsScreen.kt:438`).

### Navigation behaviors

- **Route transitions from UI events**
    - Triggering UI event(s): `OpenManageTapped`, manage `OpenLogsTapped`/`BackTapped`/session actions, logs `BackTapped` (`packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/conversation/ConversationContract.kt:63`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/manage/ManageContract.kt:66`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/manage/ManageContract.kt:68`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/logs/LogsContract.kt:46`).
    - Viewmodel handler(s): `navFlow.tryEmit(...)` in all three viewmodels (`packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/conversation/ConversationViewModel.kt:159`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/manage/ManageViewModel.kt:170`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/logs/LogsViewModel.kt:174`).
    - Domain/data calls invoked: none for pure nav events.
    - State fields consumed/updated: nav shared flows consumed by `AppNavHost` collectors (`packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/navigation/AppNavHost.kt:29`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/navigation/AppNavHost.kt:45`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/navigation/AppNavHost.kt:70`).
    - Visible user effect: stack push/pop or unwind-to-conversation behavior (`packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/navigation/AppNavHost.kt:32`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/navigation/AppNavHost.kt:48`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/navigation/AppNavHost.kt:52`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/navigation/AppNavHost.kt:57`).

---

## 5) Map events

### ManageEvent

| Event                     | Classification      | Handler path                                               |
| ------------------------- | ------------------- | ---------------------------------------------------------- |
| `UrlChanged`              | Domain call         | `ManageViewModel.onEvent -> service.updateUrl`             |
| `UseDiscoveredTapped`     | Domain call         | `ManageViewModel.onEvent -> service.useDiscovered`         |
| `ConnectTapped`           | Domain call         | `ManageViewModel.onEvent -> service.refresh`               |
| `ProjectPathChanged`      | Local-only UI state | `ManageViewModel.onEvent -> local.projectPath`             |
| `ProjectQueryChanged`     | Local-only UI state | `ManageViewModel.onEvent -> local.projectQuery`            |
| `ProjectListToggleTapped` | Local-only UI state | `ManageViewModel.onEvent -> local.projectsExpanded`        |
| `OpenProjectTapped`       | Mixed               | local updates + `service.selectProject`                    |
| `ProjectSelected`         | Mixed               | local updates + `service.selectProject`                    |
| `ProjectFavoriteToggled`  | Domain call         | `ManageViewModel.onEvent -> service.toggleProjectFavorite` |
| `ProjectRemoved`          | Domain call         | `ManageViewModel.onEvent -> service.removeProject`         |
| `WorkspaceSelected`       | Local-only UI state | `ManageViewModel.onEvent -> local.selectedWorkspace`       |
| `CreateSessionTapped`     | Mixed               | `service.createSessionAndFocus` + nav emit                 |
| `OpenSessionTapped`       | Mixed               | `service.openSession` + nav emit                           |
| `OpenLogsTapped`          | Navigation          | nav emit `ToLogs`                                          |
| `BackTapped`              | Navigation          | nav emit `Back`                                            |

Reference: `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/manage/ManageViewModel.kt:104`.

### ConversationEvent

| Event                            | Classification      | Handler path                                       |
| -------------------------------- | ------------------- | -------------------------------------------------- |
| `OpenManageTapped`               | Navigation          | nav emit `ToManage`                                |
| `ToggleSteps`                    | Local-only UI state | local `stepOpen` map toggle                        |
| `ToggleToolCall`                 | Local-only UI state | local `callOpen` map toggle                        |
| `ToolCallSessionTapped`          | Domain call         | `openToolCallSession -> service.openSession`       |
| `DraftChanged`                   | Local-only UI state | local `draft` update                               |
| `ModeChanged`                    | Local-only UI state | local `mode` update                                |
| `SlashCommandSelected`           | Local-only UI state | local draft template update                        |
| `QuickSwitchTapped`              | Mixed               | local menu close + open/create logic               |
| `QuickSwitchLongPressed`         | Mixed               | local menu state + cached/remote session loads     |
| `QuickSwitchMenuDismissed`       | Local-only UI state | local menu clear                                   |
| `QuickSwitchMenuSessionTapped`   | Mixed               | local menu clear + `service.openSession`           |
| `QuickSwitchMenuPinTapped`       | Domain call         | `service.toggleSessionQuickPin`                    |
| `QuickSwitchMenuArchiveTapped`   | Mixed               | local menu update + `service.archiveSession`       |
| `QuickSwitchMenuRenameSubmitted` | Mixed               | local menu update + `service.renameSession`        |
| `QuickSwitchMenuLoadMoreTapped`  | Mixed               | local paging update + remote fetch                 |
| `QuickSwitchMenuCreateTapped`    | Mixed               | local menu clear + `service.createSessionAndFocus` |
| `SendTapped`                     | Mixed               | `service.send` + local draft/scroll update         |
| `ReloadTapped`                   | Domain call         | `service.refresh`                                  |
| `LoadMoreMessagesTapped`         | Domain call         | `service.loadMoreMessages`                         |

Reference: `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/conversation/ConversationViewModel.kt:156`.

### LogsEvent

| Event              | Classification | Handler path                                  |
| ------------------ | -------------- | --------------------------------------------- |
| `UnitChanged`      | Mixed          | local filter update -> reactive store observe |
| `LevelChanged`     | Mixed          | local filter update -> reactive store observe |
| `ProjectChanged`   | Mixed          | local filter update -> reactive store observe |
| `SessionChanged`   | Mixed          | local filter update -> reactive store observe |
| `EventChanged`     | Mixed          | local filter update -> reactive store observe |
| `FromChanged`      | Mixed          | local filter update -> reactive store observe |
| `UntilChanged`     | Mixed          | local filter update -> reactive store observe |
| `QueryChanged`     | Mixed          | local filter update -> reactive store observe |
| `AddFilterFromRow` | Mixed          | row chip -> local filter mutation             |
| `RemoveFilter`     | Mixed          | local single-filter clear                     |
| `ClearFilter`      | Mixed          | reset local filter state (query kept)         |
| `BackTapped`       | Navigation     | nav emit `Back`                               |

Reference: `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/logs/LogsViewModel.kt:111`.

---

## 6) Trace state

- **ManageUiState path**: `SessionServiceApi.state` + manage local state combine into `ManageUiState` with derived project/workspace/session sections (`packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/manage/ManageViewModel.kt:48`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/manage/ManageViewModel.kt:70`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/manage/ManageViewModel.kt:241`).
- **ConversationUiState path**: global service mapping (`focusedMessages`, commands, quick-pin sets) + local conversation state combine into UI state (`packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/conversation/ConversationViewModel.kt:77`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/conversation/ConversationViewModel.kt:98`).
- **Turn transformation path**: `SessionUiState.focusedMessages` -> `ConversationRenderMapper.map` -> `ConversationUiState.turns` -> `ConversationScreen` (`packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionUiState.kt:56`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/conversation/ConversationRenderMapper.kt:17`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/conversation/ConversationScreen.kt:196`).
- **Message parse/decorate path for UI**: stream/remote message parts parsed (`MessagePartParser`) and decorated (`MessageDecorator`) before projection into `focusedMessages` (`packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:821`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:833`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/FocusedMessageProjector.kt:59`).
- **LogsUiState path**: local filter state drives `LogFilter`, then `observe(filter)` and `observeFacet()` combine into rows/facets/selected-filter fields (`packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/logs/LogsViewModel.kt:55`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/logs/LogsViewModel.kt:57`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/logs/LogsViewModel.kt:73`).

---

## 7) List navigation flow

- **Startup route**: app starts at `ConversationRoute` in `AppNavHost` (`packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/navigation/AppNavHost.kt:20`).
- **Conversation -> Manage**: `OpenManageTapped` emits `ToManage`, nav host pushes `ManageProjectsRoute` (`packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/conversation/ConversationViewModel.kt:159`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/navigation/AppNavHost.kt:31`).
- **Manage -> Conversation**: session create/open emits `ToConversation`, nav host unwinds stack to conversation or adds it if absent (`packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/manage/ManageViewModel.kt:159`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/navigation/AppNavHost.kt:50`).
- **Manage -> Logs**: `OpenLogsTapped` emits `ToLogs`, nav host pushes `LogsRoute` (`packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/manage/ManageViewModel.kt:170`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/navigation/AppNavHost.kt:56`).
- **Back behavior**: manage/logs `BackTapped` emits `Back`, nav host pops last route (`packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/manage/ManageViewModel.kt:174`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/logs/LogsViewModel.kt:174`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/navigation/AppNavHost.kt:47`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/navigation/AppNavHost.kt:72`).

---

## 8) List contracts

- **`SessionServiceApi` methods used by UI**: `start`, `state`, `updateUrl`, `useDiscovered`, `refresh`, `selectProject`, `toggleProjectFavorite`, `removeProject`, `toggleSessionQuickPin`, `createSessionAndFocus`, `openSession`, `send`, `loadMoreMessages`, `archiveSession`, `renameSession`, `cachedSessionsForProject`, `sessionsForProject` (`packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionServiceApi.kt:6`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/manage/ManageViewModel.kt:101`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/conversation/ConversationViewModel.kt:153`).
- **`LogStoreGateway` flows/methods used by UI**: `observe(LogFilter)` and `observeFacet()` only (`packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/LogStoreGateway.kt:81`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/LogStoreGateway.kt:83`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/logs/LogsViewModel.kt:47`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/logs/LogsViewModel.kt:57`).
- **Render mapper usage in UI path**: `ConversationViewModel` instantiates and applies `ConversationRenderMapper` to `focusedMessages` (`packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/conversation/ConversationViewModel.kt:28`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/conversation/ConversationViewModel.kt:84`).
- **Message parser/decorator usage in UI path**: `SessionService` parses/decorates message parts and `FocusedMessageProjector` applies decoration to UI-visible message/tool data (`packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:821`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt:833`, `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/FocusedMessageProjector.kt:59`).

---

## 9) Append file index

- `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/MainActivity.kt`
- `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/navigation/AppNavHost.kt`
- `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/navigation/AppRoute.kt`
- `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/navigation/NavEvent.kt`
- `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/manage/ManageContract.kt`
- `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/manage/ManageViewModel.kt`
- `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/manage/ManageScreen.kt`
- `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/conversation/ConversationContract.kt`
- `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/conversation/ConversationViewModel.kt`
- `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/conversation/ConversationScreen.kt`
- `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/conversation/ConversationRenderMapper.kt`
- `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/logs/LogsContract.kt`
- `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/logs/LogsViewModel.kt`
- `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/logs/LogsScreen.kt`
- `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/components/MessageComposer.kt`
- `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/components/ConversationHeader.kt`
- `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/components/ToolCallCard.kt`
- `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/components/TurnTimer.kt`
- `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionServiceApi.kt`
- `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionService.kt`
- `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/SessionUiState.kt`
- `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/FocusedMessageProjector.kt`
- `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/session/LogStoreGateway.kt`
- `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/message/MessagePartParser.kt`
- `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/message/MessageDecorator.kt`
- `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/message/MessagePart.kt`
- `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/message/MessageRender.kt`
