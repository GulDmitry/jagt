# 0049 — Unattended starts have a daily ceiling

[← decisions](README.md) · rule: [`unattended-work`](../../openspec/specs/unattended-work/spec.md)

## Status

Accepted 2026-10-19. Adds to [0043](0043-act-stops-itself-after-three-bad-endings.md); a bound of the kind
[0045](0045-every-store-jagt-writes-has-a-ceiling.md) sets.

## Context

- 0043 stops `act` after bad endings only; a run of good-looking starts, from `act`, intake or auto-review, has no bound.
- Intake and auto-review spend and write with nobody watching, as `act` does.

## Decision

- Starts are counted per local day in `DailyStarts`, in memory: a restart begins at zero.
- Counted origins: `ActionOrigin.MASTER` presses that start work (`MasterPress`, `ConflictHandOff`, so `MasterDeployJob`
  and `MasterShip`), tracker intake (`IntakeJob`), auto-review sweeps (`AutoReviewScheduler`).
- Not counted: ticket close, Master judge reads (`MasterReviewJob`, `MasterAnswerJob`), `TicketPrefetchJob`.
- At `maxStartsPerDay` (default 50) the file `starts-stopped` holds the date and the number beside `state.json`.
  `ActStops`, which answers both stops, makes `ConfigService` read `act` as `judge` while it stands; intake and auto-review start nothing.
- It lifts on the next local day, or by the header's `act stopped`, the one chip 0043 made; its hover names the cause.
- A human press is never refused.

## Rejected

- Reusing `act-stopped`: it never lifts by itself and cannot say which cause stopped it.
- Persisting the count: a restart is the human's act, and the stop file already outlives it.
- A second chip: one question, one control.

## Reopen when

- The ceiling stops a day the human calls normal more than once a week: the default is too low.
- A start path other than the four counted ones is found.
