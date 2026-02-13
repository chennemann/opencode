# Mobile Background Coroutines Phase 5 Runbook

Use this runbook to verify Phase 5 observability changes and record before/after metrics.

## Commands

```bash
cd packages/mobile
./gradlew installDebug
adb logcat -c
adb shell am start -W -n de.chennemann.opencode.mobile/.MainActivity
adb logcat -d | rg "SessionService|ConversationPerf|StrictMode"
adb shell am instrument -w de.chennemann.opencode.mobile.test/androidx.test.runner.AndroidJUnitRunner -e class de.chennemann.opencode.mobile.ui.conversation.ConversationScreenStreamingMarkdownTest#tracks_render_throughput_for_streamed_markdown_updates
```

## Expected log markers

- `SessionService: perf lane=mutation path=sse.event ...`
- `SessionService: perf lane=io path=sync.fetch ...`
- `SessionService: perf lane=cpu path=focused.project ...`
- `ConversationPerf: phase5_render updates=... avg_ms=... p95_ms=...`
- `StrictMode` violations appear as `StrictMode policy violation` with thread and stack details when blocking work occurs on main.

## Metrics capture (baseline vs after)

Fill this table from captured logs. If no baseline exists yet, keep `BASELINE_PLACEHOLDER` until first run.

| Metric                               | Baseline             | After             | Source                                                               |
| ------------------------------------ | -------------------- | ----------------- | -------------------------------------------------------------------- |
| Frame drops (janky frames)           | BASELINE_PLACEHOLDER | AFTER_PLACEHOLDER | `adb shell dumpsys gfxinfo de.chennemann.opencode.mobile framestats` |
| Message load latency (sync.fetch dt) | BASELINE_PLACEHOLDER | AFTER_PLACEHOLDER | `SessionService perf lane=io path=sync.fetch`                        |
| Session switching responsiveness     | BASELINE_PLACEHOLDER | AFTER_PLACEHOLDER | `SessionService perf lane=mutation path=focused.observe`             |
