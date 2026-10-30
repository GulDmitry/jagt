## Why

A read call counted as a report, and a restart forgot when the turn began, so such a turn ended unrefused.

## What Changes

- A turn end is refused when nothing was reported since the turn began, a read call included, also after a restart.

## Capabilities

### Modified Capabilities

- `runtime`: the turn-end refusal reads the session record.
