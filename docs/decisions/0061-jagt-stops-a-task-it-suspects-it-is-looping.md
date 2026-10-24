# 0061 — jagt stops a task it suspects it is looping

[← decisions](README.md) · rule: [`unattended-work`](../../openspec/specs/unattended-work/spec.md)

## Status

Accepted 2026-10-22. Adds to [0043](0043-act-stops-itself-after-three-bad-endings.md) and
[0049](0049-unattended-starts-have-a-daily-ceiling.md).
Superseded in part by [0070](0070-a-ship-counts-only-without-a-new-low.md): ships with progress no longer count.
Superseded in part by [0080](0080-a-repeating-round-ends-in-one-stop.md): the stop stands on the task.

## Context

- One task in `act` shipped five times in an hour: $72.52 of agent and $7.88 of reads, no guard anywhere.
- Bot threads the human had dismissed stayed unresolved; each ship opened a round, each poll relayed them again.
- The relay's dedup compared the brief with `task_context.md`, which the ship instruction overwrote in between.
- 0043 counts bad endings across tasks and 0049 starts per day; neither sees one task going round.

## Decision

- A task carries its relayed rounds on `TaskState.loop`: threads, the failing job, a digest of the brief.
- A brief equal to the last one relayed is not relayed again.
- `task/LoopBreaker`, pure, trips on N stalled rounds or a ship past the ceiling within the auto-review window.
- A stop is a fact on the task, not a status: the flow table keeps its fourteen, the work keeps its place in it.
- `lift`, human-only, is the one control; `LoopStopGuard` refuses an unattended ship or deploy at the door.
- The board reuses the amber ring, `stopped, yours to restart`, its hover the evidence and the proposal.

## Rejected

- A status `LOOP_STOPPED`: every rule would need it in its from-set, and lifting would need to recall the status left.
- Folding it into `act stopped`: that stops every task for one task's loop, and is not on the card.
- A model judging whether rounds progress: a loop guard must hold when the model is the loop.

## Reopen when

- The human lifts a stop and the task finishes cleanly more often than not: the numbers are too low.
