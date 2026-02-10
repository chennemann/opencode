# Mobile Architecture Migration Plan (Archived)

## Status

Package-only migration is complete.

The current source of truth is `packages/mobile/docs/architecture.md`.

## Scope completed

- Domain orchestration consolidated in `SessionService`
- Domain ports split by responsibility and implemented in data layer
- Navigation isolated in `navigation` package
- Screen contracts are event-driven with dedicated view models
- Shared produced state renamed to `SessionUiState`

## Current focus

Incremental hardening only (tests, collaborator extractions, and guardrails).
