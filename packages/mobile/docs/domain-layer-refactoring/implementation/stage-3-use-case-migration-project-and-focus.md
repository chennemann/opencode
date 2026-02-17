---
title: Stage 3 - Use Case Migration (Project Selection and Session Focus)
description: Move first user intents to dedicated use cases while preserving existing UI state contracts.
---

## Goal

- Introduce focused use cases with one public `invoke`.
- Migrate project selection and session focus intents first.
- Keep read paths and message pagination behavior untouched in this stage.

## Required Use Case Definitions

Create the following files and signatures:

```kotlin
// packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/usecase/project/SelectProjectUseCase.kt
package de.chennemann.opencode.mobile.domain.usecase.project

import de.chennemann.opencode.mobile.domain.service.project.ProjectActionService

class SelectProjectUseCase(private val action: ProjectActionService) {
    suspend operator fun invoke(projectId: String) {
        val id = projectId.trim()
        if (id.isBlank()) return
        action.select(id)
    }
}

// packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/usecase/project/RequestProjectRefreshUseCase.kt
package de.chennemann.opencode.mobile.domain.usecase.project

import de.chennemann.opencode.mobile.domain.service.project.ProjectActionService

class RequestProjectRefreshUseCase(private val action: ProjectActionService) {
    suspend operator fun invoke(projectId: String) {
        val id = projectId.trim()
        if (id.isBlank()) return
        action.refreshProjectContext(id)
    }
}

// packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/domain/usecase/session/FocusSessionUseCase.kt
package de.chennemann.opencode.mobile.domain.usecase.session

import de.chennemann.opencode.mobile.domain.service.session.SessionActionService

class FocusSessionUseCase(private val action: SessionActionService) {
    suspend operator fun invoke(sessionId: String) {
        val id = sessionId.trim()
        if (id.isBlank()) return
        action.focus(id)
    }
}
```

## Checklist

- [ ] S3-01: Add `SelectProjectUseCase.kt` exactly with trim + blank guard + single service call.
- [ ] S3-02: Add `RequestProjectRefreshUseCase.kt` exactly with trim + blank guard + single service call.
- [ ] S3-03: Add `FocusSessionUseCase.kt` exactly with trim + blank guard + single service call.
- [ ] S3-04: Register use cases in `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/di/AppModule.kt`.
- [ ] S3-05: Update `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/manage/ManageViewModel.kt`:
    - replace direct `service.selectProject(...)` in `OpenProjectTapped` with `SelectProjectUseCase`
    - replace direct `service.selectProject(...)` in `ProjectSelected` with `SelectProjectUseCase`
- [ ] S3-06: Update `ManageViewModel` open-session path to call `FocusSessionUseCase(session.id)` before navigation.
- [ ] S3-07: Update `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/conversation/ConversationViewModel.kt`:
    - `QuickSwitchMenuSessionTapped` uses `FocusSessionUseCase(session.id)`
    - `openToolCallSession(...)` uses `FocusSessionUseCase(id)` when session already exists
- [ ] S3-08: Keep all read paths untouched (`service.state` remains source for UI state in this stage).
- [ ] S3-09: Do not migrate send/archive/rename/requestMessagePage in this stage.

## Concrete Test Cases

Every test case below is required:

- [ ] T1: `SelectProjectUseCase` calls action with trimmed non-blank project ID.
- [ ] T2: `SelectProjectUseCase` does not call action for blank input.
- [ ] T3: `RequestProjectRefreshUseCase` calls action with trimmed non-blank project ID.
- [ ] T4: `RequestProjectRefreshUseCase` does not call action for blank input.
- [ ] T5: `FocusSessionUseCase` calls action with trimmed non-blank session ID.
- [ ] T6: `FocusSessionUseCase` does not call action for blank input.
- [ ] T7: `ManageViewModel.OpenProjectTapped` invokes `SelectProjectUseCase` exactly once.
- [ ] T8: `ManageViewModel.ProjectSelected` invokes `SelectProjectUseCase` exactly once.
- [ ] T9: `ManageViewModel.OpenSessionTapped` invokes `FocusSessionUseCase` before emitting navigation event.
- [ ] T10: `ConversationViewModel.QuickSwitchMenuSessionTapped` invokes `FocusSessionUseCase` and keeps menu close behavior.
- [ ] T11: `ConversationViewModel.openToolCallSession` uses `FocusSessionUseCase` for existing session IDs.
- [ ] T12: `ConversationViewModel` still derives UI state from `service.state` with no repository flow migration yet.

## Verification

- [ ] Run `./gradlew ktlintCheck`.
- [ ] Run `./gradlew :app:test --tests "*SelectProjectUseCase*" --tests "*RequestProjectRefreshUseCase*" --tests "*FocusSessionUseCase*" --tests "*ManageViewModelTest*" --tests "*ConversationViewModelTest*"`.
