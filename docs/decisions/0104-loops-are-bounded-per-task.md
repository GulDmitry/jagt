# 0104 — Loops are bounded per task

[← decisions](README.md) · rule: [`unattended-work`](../../openspec/specs/unattended-work/spec.md)

## Status

Accepted 2026-10-29. Supersedes [0049](0049-unattended-starts-have-a-daily-ceiling.md); amends
[0061](0061-jagt-stops-a-task-it-suspects-it-is-looping.md): what a `ROUNDS` run counts.

## Context

- A task was stopped on "plan-not-ready sent 3 rounds running in 24h" although each round answered a reworked plan: work, not a loop.
- 0049's one counter serves every task: twenty healthy tasks spend the day's allowance, and one looping task spends it for all.

## Decision

- A step's `run` of rounds continues only while the round answers the session report the previous round answered.
- That report is fingerprinted from the session's last own history entry (`MCP` origin: status and `why`) and kept in `Turn.marks`.
- A report that changed starts the run at one; `loopBreaker.rounds` is otherwise unchanged.
- `loopBreaker.perHour` (default 20) bounds the unattended moves of one task in the last hour since its lift.
- An unattended move is a history entry whose origin is `MASTER`, `AUTO_REVIEW` or `TRACKER`.
- The ceiling is read before a Master press, a conflict deploy, an auto-review poll and a round; past it the task stops as `PACE`.
- 20 is measured 2026-10-29: the busiest normal task of 34 in `state.json` and `finished.jsonl` made 11 in one hour.
- `maxStartsPerDay`, `DailyStarts`, `starts-stopped` and the `DAILY_CEILING` cause are gone; intake only opens tasks and counts nothing.
- Known limit: a report alternating A, B, A evades the unchanged-report count; `perHour` bounds it.

## Rejected

- A global daily count: one task's loop spends every task's allowance, and a healthy day trips it.
- Counting every relay of a step: three reworked plans stop by design.
- A stop carrying no cause of its own: `PACE` names the hour's moves, so a human can read what each changed.

## Reopen when

- A task stops on `PACE` while its moves were each different work: 20 is too low.
- A loop passes by changing its report each round without changing the work.
