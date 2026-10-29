# 0106 — A session waits for a dependency, and jagt says when to probe

[← decisions](README.md) · rule: [`infra-wait`](../../openspec/specs/infra-wait/spec.md)

## Status

Accepted 2026-10-29. Extends [0105](0105-infrastructure-failures-are-waited-out.md) from pipelines to sessions.

## Context

- A session whose database was down asked a human (ABC-43): nobody could bring it up, and it came back by itself.
- A question is the only way out of a turn that does not end in a hand-back, and it asks for a human.

## Decision

- `outcome=wait` with IN_PROGRESS: the turn stays the session's, no badge, no question.
- The wait is the same `infraWait.backoff`: one list bounds both features, `[]` turns both off.
- The wake is a mark on the task's turn, `wake-at=<turnId>:<epoch>:<step>`: it survives a restart and dies with its turn.
- After the last wait the task stops as `WAIT`: jagt, not the session, decides when to give up.
- A session that reported a wait is not watched for silence: it is idle by design.

## Rejected

- A wait the session times itself: it cannot sleep across a turn end.
- A separate backoff list: two knobs for one policy.
- A wake that outlives its turn: a human's word would be followed by a stale line.

## Reopen when

- A wait is answered by `wait-over` and the session reports the same dependency down three times in a row on most tasks: 3 waits are too few.
