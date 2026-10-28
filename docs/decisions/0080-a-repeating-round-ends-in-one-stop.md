# 0080 — A repeating round ends in one stop

[← decisions](README.md) · rules: [`unattended-work`](../../openspec/specs/unattended-work/spec.md),
[`master`](../../openspec/specs/master/spec.md) · supersedes in part:
[0061](0061-jagt-stops-a-task-it-suspects-it-is-looping.md) (where the stop lives, the guard's name)

## Status

Accepted 2026-10-24. Records the round ceiling 7ef6aa0a introduced without a record.
Amended by [0100](0100-a-stop-is-scoped-to-its-request.md): a new request replacing the counted one also clears the stop.

## Context

- A red verification or a not-ready round went back with no ceiling (0065 rule E); 7ef6aa0a stopped the fourth
  of one step running within 24h, a constant.
- Two not-ready rounds of only repeats became a question to the human; in `act` the Master answered it itself.
- The stop sat inside the round trail (`TaskState.loop.stop`), written and read by five names.

## Decision

- A round sent back counts toward `loopBreaker.rounds` (default 3, at most 19) in `jagt.yml`; past it the task stops
  with trigger `ROUNDS`, its status kept.
- A round of only repeats is a round like any: relayed `[repeat]`, counted, never a question.
- `task/TaskStop` stands on `TaskState.stop`; `task/RoundTrail` keeps the rounds alone. `service/TaskStops` is its one
  writer, `lift` clearing it and restarting the window; `StopGuard` refuses at the door.
- A `state.json` holding `loop.stop` is read once into the two fields.

## Rejected

- A second ceiling for repeats: two stops for one loop, and a repeat-only round is no worse than a fresh one.
- A question at the ceiling: in `act` the Master is the one asked.

## Reopen when

- A Master names one finding with a new anchor or kind each round and the ceiling trips on rounds that progressed.
