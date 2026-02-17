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

- [x] S3-01: Add `SelectProjectUseCase.kt` exactly with trim + blank guard + single service call.
- [x] S3-02: Add `RequestProjectRefreshUseCase.kt` exactly with trim + blank guard + single service call.
- [x] S3-03: Add `FocusSessionUseCase.kt` exactly with trim + blank guard + single service call.
- [x] S3-04: Register use cases in `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/di/AppModule.kt`.
- [x] S3-05: Update `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/manage/ManageViewModel.kt`:
    - replace direct `service.selectProject(...)` in `OpenProjectTapped` with `SelectProjectUseCase`
    - replace direct `service.selectProject(...)` in `ProjectSelected` with `SelectProjectUseCase`
- [x] S3-06: Update `ManageViewModel` open-session path to call `FocusSessionUseCase(session.id)` before navigation.
- [x] S3-07: Update `packages/mobile/app/src/main/kotlin/de/chennemann/opencode/mobile/ui/conversation/ConversationViewModel.kt`:
    - `QuickSwitchMenuSessionTapped` uses `FocusSessionUseCase(session.id)`
    - `openToolCallSession(...)` uses `FocusSessionUseCase(id)` when session already exists
- [x] S3-08: Keep all read paths untouched (`service.state` remains source for UI state in this stage).
- [x] S3-09: Do not migrate send/archive/rename/requestMessagePage in this stage.

## Concrete Test Cases

Every test case below is required:

- [x] T1: `SelectProjectUseCase` calls action with trimmed non-blank project ID.
- [x] T2: `SelectProjectUseCase` does not call action for blank input.
- [x] T3: `RequestProjectRefreshUseCase` calls action with trimmed non-blank project ID.
- [x] T4: `RequestProjectRefreshUseCase` does not call action for blank input.
- [x] T5: `FocusSessionUseCase` calls action with trimmed non-blank session ID.
- [x] T6: `FocusSessionUseCase` does not call action for blank input.
- [x] T7: `ManageViewModel.OpenProjectTapped` invokes `SelectProjectUseCase` exactly once.
- [x] T8: `ManageViewModel.ProjectSelected` invokes `SelectProjectUseCase` exactly once.
- [x] T9: `ManageViewModel.OpenSessionTapped` invokes `FocusSessionUseCase` before emitting navigation event.
- [x] T10: `ConversationViewModel.QuickSwitchMenuSessionTapped` invokes `FocusSessionUseCase` and keeps menu close behavior.
- [x] T11: `ConversationViewModel.openToolCallSession` uses `FocusSessionUseCase` for existing session IDs.
- [x] T12: `ConversationViewModel` still derives UI state from `service.state` with no repository flow migration yet.

## Verification

- [x] Run `./gradlew ktlintCheck`.
- [x] Run `./gradlew :app:test --tests "*SelectProjectUseCase*" --tests "*RequestProjectRefreshUseCase*" --tests "*FocusSessionUseCase*" --tests "*ManageViewModelTest*" --tests "*ConversationViewModelTest*"`.
