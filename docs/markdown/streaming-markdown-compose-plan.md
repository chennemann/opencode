# Streaming Markdown Compose Plan

## 1) Create module foundation

- [x] Create `packages/mobile/streaming-markdown/` and add module sources under `src/main/kotlin`.
- [x] Register `:streaming-markdown` in `packages/mobile/settings.gradle.kts`.
- [x] Add `packages/mobile/streaming-markdown/build.gradle.kts` with Kotlin Android + Compose setup.
- [x] Add Compose text dependencies required for `AnnotatedString` and composable rendering.
- [x] Align Kotlin toolchain, lint, and test config with existing mobile modules.

## 2) Define public contracts

- [x] Add parser lifecycle contract with `start()`, `write(chunk)`, `end()`, and `reset()`.
- [x] Add parser output run model with explicit run kind for plain text and inline code.
- [x] Add adapter contract that converts parser runs to `AnnotatedString`.
- [x] Add `StreamingMarkdownText` composable contract for drop-in `Text` replacement.
- [x] Document API stability rules for v1 so future token support stays backward compatible.

## 3) Implement streaming parser v1

- [x] Implement inline-code state that opens on first backtick seen in a line.
- [x] Implement inline-code state that closes on next backtick seen in the same line.
- [x] Reset inline-code state when a newline is emitted.
- [x] Preserve parser state across `write()` calls when delimiters split across chunks.
- [x] Emit plain text runs for non-code content without loss or reordering.
- [x] Flush buffered content exactly once when `end()` is called.

## 4) Implement Compose adapter

- [x] Convert parser runs to `AnnotatedString` with deterministic span boundaries.
- [x] Apply default inline-code style using monospace font and subtle background treatment.
- [x] Expose adapter styling inputs for base text style and inline-code style overrides.
- [x] Keep output stable when partial stream updates re-emit previously parsed content.
- [x] Avoid unnecessary allocations during frequent small run updates.

## 5) Implement drop-in composable

- [x] Implement `StreamingMarkdownText` to render incremental parser output as chunks arrive.
- [x] Support common `Text` parameters: `modifier`, `style`, `color`, `maxLines`, and `overflow`.
- [x] Add non-streaming input path for rendering prebuilt full text snapshots.
- [x] Ensure high-frequency updates do not cause unstable recomposition behavior.
- [x] Keep call-site migration from `Text` to `StreamingMarkdownText` to a minimal diff.

## 6) Integrate in conversation UI

- [x] Replace assistant message body rendering with `StreamingMarkdownText`.
- [x] Keep user message and tool call rendering unchanged for first rollout.
- [ ] Verify streaming updates preserve list follow and scroll behavior.
- [ ] Verify rendered assistant text still supports expected selection and copy behavior.
- [x] Keep parser and adapter wiring isolated from screen-specific business logic.

## 7) Add automated tests

- [x] Add parser test: opens inline-code mode on first backtick.
- [x] Add parser test: closes inline-code mode on matching backtick.
- [x] Add parser test: preserves inline-code mode across chunk boundaries.
- [x] Add parser test: resets inline-code mode at newline.
- [x] Add parser test: supports multiple inline-code spans in one line.
- [x] Add parser test: handles unmatched trailing backtick input safely.
- [x] Add adapter test: maps run boundaries to exact `AnnotatedString` span ranges.
- [x] Add adapter test: assigns default and overridden inline-code styles correctly.
- [x] Add composable test: renders stable output during rapid chunk updates.
- [x] Add integration test: conversation assistant path renders streamed markdown correctly.

## 8) Validate runtime behavior

- [x] Add benchmark or timing test for incremental append path versus full reparse path.
- [x] Validate long message streams with many small chunks for latency and jank.
- [x] Validate multiline mixed plain/code streams for rendering correctness.
- [x] Validate memory remains bounded during prolonged streaming sessions.

## 9) Run quality gates

- [x] Run `./gradlew ktlintFormat` in `packages/mobile`.
- [x] Run `./gradlew ktlintCheck` in `packages/mobile`.
- [x] Run `./gradlew clean build` in `packages/mobile`.
- [x] Resolve failures and rerun gates until all pass.

## 10) Track post-v1 follow-ups

- [x] Add escaped backtick handling.
- [x] Add multi-backtick delimiter support.
- [x] Add emphasis and strong token streaming support.
- [ ] Add fenced code block streaming state support.
- [x] Add link and autolink token support.
- [x] Add internal-first module publication plan.
