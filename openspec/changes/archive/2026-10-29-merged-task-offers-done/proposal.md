## Why

A squash-merged request left the card at `REVIEWED`, "awaiting approval", polled and waiting for an approval that cannot come.

## What Changes

- Where every request is merged, a `REVIEWED` or `APPROVED` card reads `merged` in place of `awaiting approval` / `approved`; no new status, colour or mark.
- Its move is `OPTIONAL` "you can close the task", `done` highlighted; closing stays the human's.
- The poller leaves a task whose every request is merged.

## Capabilities

### Modified Capabilities

- `attention`: the merged card and its quiet move.
- `unattended-work`: no poll on a merged request.
