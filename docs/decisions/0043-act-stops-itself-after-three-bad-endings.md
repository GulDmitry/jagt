# 0043 — `act` stops itself after three bad endings

[← decisions](README.md) · rule: [`unattended-work`](../../openspec/specs/unattended-work/spec.md)

## Status

Accepted 2026-10-14. Adds to [0018](0018-the-master-judges-by-default.md). Amended by
[0101](0101-a-strike-is-the-masters-only-after-its-own-last-move.md): a silence strikes only after the Master's own last move.

## Context

- An `act` that keeps pressing after its moves keep failing writes the shared branch with nobody watching.
- the-metafactory's ranger pauses its claims after three worker failures in a row.

## Decision

- Three tasks in a row the Master moved ending silent, in a deploy conflict or reverted stop `act` (`MasterBrake`):
  it judges until the human presses `act stopped` in the header. A deploy resets the count.
- The stop is the file `act-stopped` beside `state.json`, read as `judge` through `ActStops`: it outlives a restart,
  the count does not.

## Rejected

- Counting at `done`: only the human closes, so the brake would wait on the human it stands in for.
- Rewriting `master.mode` in `jagt.yml`: the human's file is the human's, and restoring would need an editor.

## Reopen when

- The human restores `act` at once more often than not: the three endings are not what the human calls bad.
