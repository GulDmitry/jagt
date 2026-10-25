# 0048 — Every headless call has a spend ceiling

[← decisions](README.md) · rule: [`seams`](../rules/seams.md#every-assistant-call-is-metered)

## Status

Accepted 2026-10-19.

## Context

- [0024](0024-every-headless-call-is-metered-where-it-runs.md) books a call after it returns; nothing stopped one mid-run.
- Metered intake calls 2026-10-16..18 (`usage-by-day.jsonl`): $0.04 to $0.08 on average, 95 calls.
- Headless transcripts, 1331 runs priced at list: the largest single run cost $0.234.
- A Master review runs `--no-session-persistence` on the role's model: no transcript, no per-call record.
- Re-derived 2026-10-25, `usage-by-day.jsonl` days 2026-10-16..24, `maxCallCostUsd` nonzero on 3 days.
- Largest call $0.576 (Master review, 2026-10-22), times 3 is 1.73, rounded up to 1.75.

## Decision

- `HeadlessClaude.run`, the one path of every headless call, passes the CLI's `--max-budget-usd`.
- One key, `orchestrator.assistant.maxCostUsd`, defaults to 1.75: the largest measured call times 3, a guard against a runaway call.
- The CLI ends such a run with `subtype: error_max_budget_usd`; it maps to a failure naming the ceiling.

## Rejected

- A ceiling per kind: one number answers the question; the key is raised where a kind needs more.

## Reopen when

- A legitimate call reaches the ceiling at the default.
- The refresh re-derives the default from `maxCallCostUsd` in `usage-by-day.jsonl`: the largest per-call maximum times 3.
