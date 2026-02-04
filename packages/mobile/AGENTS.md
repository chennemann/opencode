# Agentic - A native opencode client for Android

## Project Overview

Agentic is a native opencode client for Android built with:

- **Kotlin 2.3.0** - Modern Kotlin with latest features
- **Jetpack Compose** - Declarative UI with Material 3
- **SQLDelight** - Type-safe SQL with Kotlin code generation
- **KTLint** - Kotlin formatting and style checks
- **Koin** - Lightweight dependency injection
- **Navigation 3** - Type-safe navigation with serializable routes

## Quick Reference Commands

```bash
# Build & Run
./gradlew clean build assembleDebug # Build debug APK
./gradlew installDebug && adb logcat --clear && adb shell am start -W -n de.chennemann.opencode.mobile/.MainActivity  # Install & Start app on device

# Logcat
adb logcat -d | grep -E "de\.chennemann\.opencode\.mobile|AndroidRuntime" # Check for errors when asked

# Code Quality
./gradlew ktlintCheck            # Check Kotlin style
./gradlew ktlintFormat           # Auto-fix style issues

# Testing
./gradlew clean test                   # Run unit tests

# Database
# SQLDelight generates code in build/generated/sqldelight/
./gradlew generateSqlDelightInterface
```

## Critical Rules

### Architecture

1. **UI layer has NO business logic** – ViewModels only transform data for display
2. **Repositories are the single source of truth** – UI observes via Flow
3. **Services orchestrate the business logic** – Fully tested
4. **Type-safe navigation** – All routes use sealed interface with @Serializable
5. **Koin for DI** – No manual instance creation

### Compose

1. **State hoisting** – UI state lives in ViewModel, not composables
2. **Unidirectional data flow** – State down, events up
3. **Material 3** – Use M3 components, theming, and adaptive layouts
4. **No previews** – Do not add @Preview composables or preview-only helpers

### SQLDelight

1. **Schema in .sq files** – All tables defined in sqldelight/ directory
2. **Queries return Flow** – Use asFlow() for reactive updates
3. **Mapper functions** – Map database models to domain models in repository
4. **schema migrations** – ALWAYS add a new migration file for each schema change

## Task Finalization

### 1. Quality Gate (Mandatory — Gradle)

After completing the task, perform the following steps **in order**:

1. **Run linting**

    ```bash
    ./gradlew ktlintFormat
    ```

    - Fix any linting errors.
    - Re-run linting until it passes with zero issues.

2. **Run a clean build with all tests**

    ```bash
    ./gradlew clean build
    ```

3. **Failure handling**
    - If any tests fail:
        - Fix **only** the issues required to make tests pass.
        - Re-run:
            ```bash
            ./gradlew ktlintFormat
            ./gradlew clean build
            ```
    - Repeat until both linting and tests pass cleanly.

### 2. Completion Criteria (Stop Condition)

Stop when **all** of the following are true:

- The assigned task is fully implemented.
- `./gradlew ktlintCheck` passes with zero errors.
- `./gradlew clean build` passes with all tests green.
- **No additional tasks are started or modified.**

### 3. Create the Commit (Exactly One)

Use the following commit message format:

```text
<type>(<component>): <description>

Context:
- Why the change was made

Changes:
- Bullet list of what changed
```

Create exactly one commit using **JJ**:

```bash
jj commit -m "<formatted commit message>"
```

Once all is done, **stop immediately**.
Do **not** suggest next steps, and do **not** continue with any additional work.
