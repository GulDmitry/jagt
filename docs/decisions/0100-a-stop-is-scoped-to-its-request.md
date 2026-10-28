# 0100 — A stop is scoped to the request it was counted on

[← decisions](README.md) · rule: [`unattended-work`](../../openspec/specs/unattended-work/spec.md)

## Status

Accepted 2026-10-28. Amends [0061](0061-jagt-stops-a-task-it-suspects-it-is-looping.md) and
[0080](0080-a-repeating-round-ends-in-one-stop.md): `lift` is no longer the only way a stop clears.

## Context

- The Master shipped ABC-42 four times on !571, answering it with nothing new; !571 merged and a SHIPS stop stood.
- The task worked on for a day on the human's word; the session opened !573 from the same branch.
- Auto-review refused !573 as stopped: its evidence was counted on a request that no longer existed.

## Decision

- `TaskStop.request` records the request URLs a SHIPS or STALLED stop was counted on; other triggers record none.
- A request written for a repository that leaves a recorded URL out of the task's requests voids the stop.
- It clears through `TaskState.lifted`, the one clearing path, and logs `stop lifted` with `cause=new request`.
- A stop with no request, and the same request written again, stand.
- Safe because `StopGuard` refuses an unattended ship on a stopped task: a new request on one comes from the human, or
  from the session on the human's word.

## Rejected

- Lifting on any write of a request URL: a repeated CI_POLLING on the same request would clear the stop.
- Recording a request on every trigger: a failed verification or plan says nothing about a request.

## Reopen when

- An unattended path opens a request on a stopped task: the premise of the clearing no longer holds.
