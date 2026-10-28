## Why

`MasterBrake` counted any silence on a task the Master had ever moved: a session idling after the human's conversation,
a session the human closed or one that had just reported itself stopped `act` twice in an hour (seen 2026-10-28).

## What Changes

- A silence strikes only when the Master's move is the task's last step before the silence began.

## Capabilities

### Modified Capabilities

- `unattended-work`: the brake requirement counts a silence only after the Master's own last move.
