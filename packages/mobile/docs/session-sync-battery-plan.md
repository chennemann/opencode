# Sync cadence

Cut battery and data burn during session updates.

## Frame problem and constraints

- Current behavior syncs tracked sessions too often and frequently downloads full message history.
- Frequent full syncs create unnecessary radio wakeups, CPU work, and DB churn in background.
- Plan must preserve fresh UI when users actively view a session.
- Plan must support mixed server versions, including older servers without lightweight checks.

---

## Differentiate endpoint roles

- `GET /session/{sessionID}` (`session.get`) is the per-session change detector.
- Use `time.updated` from `session.get` as the canonical signal for "messages may have changed".
- `GET /session/status` (`session.status`) is the runtime activity detector.
- Use `busy`/`retry`/`idle` to classify whether a session is actively running, retrying, or settled.
- Use both together: `session.status` decides urgency, `session.get.time.updated` decides whether full sync is needed.
- This pairing avoids full message fetches when runtime state changes but message history did not.

---

## Track per-session state

- `session_id`: stable key for local tracking.
- `last_check_at`: last lightweight check timestamp.
- `last_full_sync_at`: last full message sync timestamp.
- `last_seen_updated_at`: most recent `time.updated` seen from `session.get`.
- `last_status`: most recent status from `session.status` (`busy`/`retry`/`idle`/`unknown`).
- `last_sse_at`: last SSE activity timestamp relevant to this session.
- `active_tool_name`: current running tool name when present.
- `active_tool_since`: timestamp when current tool became active.
- `backoff_step`: current step index in backoff schedule.
- `supports_lightweight_check`: capability flag per server or workspace.
- `is_user_focused`: true when session is currently focused by user.
- `is_project_open`: true when containing project is open/selected.

---

## Apply decision flow

1. On app start, load tracked sessions and reset each `backoff_step` to base.
2. On project open/select, mark `is_project_open=true` and reset `backoff_step` for its sessions.
3. On session focus/activation, mark `is_user_focused=true`, reset backoff, and run immediate lightweight check.
4. On SSE event for a session, update `last_sse_at`, reset backoff, and enqueue lightweight check.
5. Before polling, if `last_status` is `busy` and `now - last_sse_at <= 20s`, skip poll and reschedule.
6. Run lightweight checks when server supports them:
    - call `session.status` to classify runtime urgency
    - call `session.get` and compare `time.updated` with `last_seen_updated_at`
7. If `time.updated` changed, run full message sync immediately and set `last_seen_updated_at`.
8. If unchanged, skip full sync and advance backoff for inactive/completed cadence.
9. If status remains running but same active tool lasts more than 5 minutes, treat as completed and switch to completed cadence.
10. If server lacks lightweight support, run conservative full sync on backoff schedule and never exceed max interval.

---

## Define backoff schedule

- Base interval starts at 15 seconds.
- Use exponential backoff with max interval capped at 30 minutes.
- Reset backoff on app restart, project open/select, session focus, relevant SSE event, and detected change.
- Always execute an immediate check when user activates a session.

| Step | Delay | Notes               |
| ---- | ----- | ------------------- |
| 0    | 15s   | Base check interval |
| 1    | 30s   | First backoff       |
| 2    | 1m    |                     |
| 3    | 2m    |                     |
| 4    | 4m    |                     |
| 5    | 8m    |                     |
| 6    | 16m   |                     |
| 7+   | 30m   | Cap until reset     |

---

## Roll out safely

- Phase 1: add per-session state fields and scheduler plumbing behind a feature flag.
- Phase 2: enable lightweight check path (`session.status` + `session.get`) for compatible servers.
- Phase 3: enable fallback path for older servers with reduced full-sync cadence and same backoff/reset rules.
- Phase 4: ramp flag by cohort, monitor metrics, then switch default on.
- Phase 5: remove legacy frequent-poll path after one stable release cycle.

---

## Measure and guard

- Emit counters for lightweight checks, full syncs, skipped polls, and fallback full-sync runs.
- Emit timers for time between checks, time-to-change-detection, and full-sync latency.
- Track battery-sensitive proxies: background wakeups per hour, mobile-data bytes per session, and CPU time in sync worker.
- Add guardrails: minimum check floor 15s, hard cap 30m, and max full-sync attempts per hour per inactive session.
- Alert on regressions: increased stale-session complaints, failed sync rate, or high battery drain deltas.

---

## Validate behavior

- Unit: backoff progression, reset triggers, SSE suppression window, stuck-tool timeout, and change-detection gate.
- Integration: mock endpoint combinations for changed/unchanged `time.updated`, status transitions, and no-lightweight fallback.
- Integration: verify full sync only occurs when change detected or fallback cadence requires it.
- Manual battery validation: compare baseline vs new build on same device profile over 24h idle + active mix.
- Manual UX validation: confirm immediate freshness on session focus and no visible delay after SSE bursts.

---

## Track risks and questions

- Should lightweight capability be detected via explicit server capability endpoint or inferred by response shape?
- How should multi-device editing reconcile when `time.updated` changes quickly with sparse SSE delivery?
- Do we need project-level fairness limits so one noisy project does not starve checks for others?
- What is acceptable stale window for inactive sessions on metered networks?
- Should stuck-tool timeout be globally fixed at 5 minutes or remotely configurable?
