# 0101 — A strike is the Master's only after its own last move

[← decisions](README.md) · rule: [`unattended-work`](../../openspec/specs/unattended-work/spec.md)

## Status

Accepted 2026-10-28. Amends [0043](0043-act-stops-itself-after-three-bad-endings.md): a silence strikes only after
the Master's own last step.

## Context

- `MasterBrake` treated a task as the Master's once any history step had its origin, so every later silence struck.
- Three silences seen 2026-10-28 followed the human's conversation, a closed session and the session's own report; none
  followed a Master move. They stopped `act` twice in an hour and the human restored it in between.

## Decision

- A silence strikes when the task's last history step before the silence began has origin `MASTER`.
- A silence after a step from the session, the human, the tracker or auto-review does not strike.
- `DEPLOY_CONFLICT` and `REVERTED` stay as in 0043: each is a status step of its own, not an inference from silence.

## Rejected

- Counting every silence on a task the Master ever moved: a silence the Master did not cause is not its bad ending.

## Reopen when

- A failure the Master caused ends after a non-Master step and goes uncounted.
