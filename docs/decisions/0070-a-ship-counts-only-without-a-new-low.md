# 0070 — A ship counts toward the loop ceiling only without a new low

[← decisions](README.md) · rule: [`unattended-work`](../../openspec/specs/unattended-work/spec.md)

## Status

Accepted 2026-10-23. Supersedes the ships-with-progress part of
[0061](0061-jagt-stops-a-task-it-suspects-it-is-looping.md).

## Context

- 0061 counted every ship in `autoReview.windowHours`, progress or not, after five ships in an hour cost $72.
- A task in `act` was stopped on its fifth ship in a day: "threads per round: 3, 2, 1; ships in 24h: 4".
- Its open threads fell every round, a normal bot-review back-and-forth, and the task was finishing.
- This is the case 0061's "Reopen when" names: the human lifted, the task finished cleanly.

## Decision

- A ship counts toward `loopBreaker.ships` only when the round it answers did not bring the open threads below the
  lowest of the earlier rounds since the last `lift`.
- The first round sets that minimum and counts as no new low, so its ship counts.
- A ship before any round, or in a task whose rounds carry no thread data, counts.
- Threads 3, 2, 1 never trip; 5, 6, 5, 6 trips at the ceiling; 10, 10, 10 stays with `stalledRounds`.
- `LoopBreaker` stays pure; the hover reads `ships without a new low in 24h: N`.

## Rejected

- Raising `loopBreaker.ships`: the five-in-an-hour runaway would take longer to stop, and a falling task still trips.
- Exempting any ship while threads fall: a task falling by one per ship forever is not bounded by anything.

## Reopen when

- A task trips this ceiling and lifting it finishes cleanly.
- A task ships unbounded while making new lows.
