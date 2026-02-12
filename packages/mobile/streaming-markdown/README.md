# Streaming Markdown

Compose-specific streaming markdown rendering module for mobile.

## Scope (v1)

- Stream-safe inline code rendering based on backticks.
- Compose adapter that produces `AnnotatedString` output.
- Drop-in composable API via `StreamingMarkdownText`.

## Non-goals (v1)

- Full markdown specification support.
- Fenced code blocks, links, tables, and nested markdown structures.
- Rich text editor behavior.

## Planned API shape

- Parser lifecycle methods for chunked input (`start`, `write`, `end`, `reset`).
- Parser output runs for plain text and inline code segments.
- Compose adapter from runs to `AnnotatedString`.
- `StreamingMarkdownText` for direct UI usage.
