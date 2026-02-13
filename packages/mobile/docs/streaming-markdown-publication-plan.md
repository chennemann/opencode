# Streaming Markdown Publication Plan

## Internal-first release path

- Publish as an internal Gradle module in the mobile workspace (`:streaming-markdown`).
- Stabilize public API (`StreamingMarkdownParser`, `MarkdownRun`, `StreamingMarkdownText`) behind semantic versioning.
- Add a changelog section in the module README for internal consumers.
- Add binary compatibility checks before external publication.

## Externalization criteria

- Keep parser API and run model stable for at least one release cycle.
- Validate Android app integration stability across streaming and snapshot modes.
- Add migration notes for any breaking token behavior changes.
- Add usage examples for Compose and non-Compose adapters.

## Packaging and distribution

- Add Gradle publishing configuration for Maven publication.
- Publish to an internal package registry first (GitHub Packages or equivalent).
- Validate dependency metadata, source jars, and documentation artifacts.
- Promote to public Maven Central only after API freeze and docs review.
